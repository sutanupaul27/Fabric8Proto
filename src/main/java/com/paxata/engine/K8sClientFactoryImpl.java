package com.paxata.engine;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import okhttp3.OkHttpClient;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class K8sClientFactoryImpl implements K8sClientFactory {
  private static final Logger logger = LoggerFactory.getLogger(K8sClientFactoryImpl.class);
  private static KubernetesClient INSTANCE = null;

  static {
    // tells the OkHttp library to use conscrypt for TLS instead of the builtin java shit
    // This allows ALPN to work on Java 8+ without having to modify the bootstrap classpath
    System.setProperty("okhttp.platform", "conscrypt");
  }

  private final boolean runningInK8s;

  public K8sClientFactoryImpl() {
    this.runningInK8s = new File(Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH).exists();
  }

  @Override
  public KubernetesClient createKubernetesClient(String namespace) {
    try {
      ConfigBuilder builder = new ConfigBuilder()
          .withApiVersion("v1")
          .withWebsocketPingInterval(0);
      File oauthTokenFile = new File(Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH);
      if (oauthTokenFile.exists()) {
        // running internal
        String caCertFile = new File(Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH).getAbsolutePath();
        builder.withOauthToken(FileUtils.readFileToString(oauthTokenFile))
            .withCaCertFile(caCertFile)
            .withMasterUrl(KUBERNETES_MASTER_INTERNAL_URL);
      } else {
        builder.withMasterUrl("https://localhost:6443/");
      }

      if (namespace != null) builder = builder.withNamespace(namespace);
      Config config = builder.build();
      OkHttpClient baseHttpClient = HttpClientUtils.createHttpClient(config);
      OkHttpClient httpClientWithCustomDispatcher = baseHttpClient.newBuilder().build();
      return new DefaultKubernetesClient(httpClientWithCustomDispatcher, config);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isRunningInK8s() {
    return this.runningInK8s;
  }

  @Override
  public String runningNamespace() {
    try {
      File file = new File(Config.KUBERNETES_NAMESPACE_PATH);
      if (file.exists()) {
        return FileUtils.readFileToString(file);
      }
    } catch (IOException e) {
      // ignore;
    }

    return null;
  }

  public static KubernetesClient getInstance() {
    if (INSTANCE == null) {
      synchronized (K8sClientFactoryImpl.class) {
        if (INSTANCE == null) {
          K8sClientFactoryImpl factory = new K8sClientFactoryImpl();
          String namespace = factory.runningNamespace();
          logger.info("Creating Kubernetes client for namespace: {}", namespace);
          INSTANCE = factory.createKubernetesClient(namespace);
        }

      }
    }
    return INSTANCE;
  }
}
