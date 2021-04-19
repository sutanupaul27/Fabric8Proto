package com.paxata.engine;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface K8sClientFactory {
  public static String KUBERNETES_MASTER_INTERNAL_URL = "https://kubernetes.default.svc";

  KubernetesClient createKubernetesClient(String namespace);
  boolean isRunningInK8s();
  String runningNamespace();
}