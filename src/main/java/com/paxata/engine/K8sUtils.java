package com.paxata.engine;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sUtils {
  private static final Logger log = LoggerFactory.getLogger(K8sUtils.class);

  public static void getPodDetails(KubernetesClient client, String namespace, String name) {
    try {
      client.pods().inNamespace(namespace).withName(name).get();
    } catch (KubernetesClientException ex) {
      log.warn(String.format("Failed to delete pod %s/%s, ignoring exception", namespace, name), ex);
    }
  }
}
