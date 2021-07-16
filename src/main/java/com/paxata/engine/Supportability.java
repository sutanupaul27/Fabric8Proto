package com.paxata.engine;


import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Supportability {
    public static final String MONGO_NAMESPACE = "mongo";
    public static final String MY_NAMESPACE = "sutanu-2669";
    public static final String PAX_POD_NAME = "pax-installation-paxserver";
    public static final String PAX_CONTAINER = "paxserver";
    public static final String MONGO_POD_NAME = "mongo-deployment-0";
    public static final String MONGO_CONTAINER = "mongo";
    public static final String PIPELINE_POD_NAME = "pax-installation-pipeline";
    public static final String PIPELINE_MASTER_CONTAINER = "spark-kubernetes-driver";
    public static final String PIPELINE_WORKER_CONTAINER = "executor";
    public static final String PIPELINE_PROXY_POD_NAME = "pax-installation-pipeline-proxy";
    public static final String PIPELINE_PROXY_CONTAINER = "pipeline-proxy";
    public static final int LINES_OF_LOGS = 1000;
    public static final String OUTPUT_DIRECTORY = "./target/output/";

    KubernetesClient client = null;

    Supportability() {
        client = new DefaultKubernetesClient();
        //client = K8sClientFactoryImpl.getInstance();
    }

    void close(){
        client.close();
    }

    public void namespaceTree(String nameSpace){
        PodList pods = client.pods().inNamespace(nameSpace).list();
        for(Pod pod: pods.getItems())
        {
            System.out.println("Pod: " + pod.getMetadata().getName());
            List<Container> containers = pod.getSpec().getContainers();
            for(int i=0; i<containers.size();i++){
                System.out.println("-> -> Containers: " + containers.get(i).getName());
            }
        }
    }

    public void downloadFileFromContainer(String namespace, String podname, String container, String filePathInContainer, File downloadLocation){
        createFolder(downloadLocation);
        client.pods()
                .inNamespace(namespace)
                .withName(podname)
                .inContainer(container)
                .file(filePathInContainer)
                .copy(downloadLocation.toPath());
        client.namespaces();
    }

    public void readLog(String namespace, String podname, String container, int lineCount, File filePath){//https://www.programcreek.com/java-api-examples/index.php?api=io.fabric8.kubernetes.client.dsl.LogWatch
        try{
            PrettyLoggable<String, LogWatch> tailingLines = client.pods().inNamespace(namespace)
                    .withName(podname)
                    .inContainer(container)
                    .tailingLines(lineCount);
            String log = tailingLines.getLog();
            createFolder(filePath);
            if (!log.isEmpty()) {
                FileWriter fileWriter = new FileWriter(new File(filePath.getAbsolutePath()));
                fileWriter.write(log);
                fileWriter.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void readLog(String namespace, String podname, String container, File filePath){//https://www.programcreek.com/java-api-examples/index.php?api=io.fabric8.kubernetes.client.dsl.LogWatch
        try{
            String log = client.pods().inNamespace(namespace)
                    .withName(podname)
                    .inContainer(container)
                    .sinceSeconds(1800)
                    .getLog();
            createFolder(filePath);
            if (!log.isEmpty()) {
                FileWriter fileWriter = new FileWriter(new File(filePath.getAbsolutePath()));
                String []logLines=log.split("\n");
                String filteredLog=Arrays.stream(logLines).filter(p -> p.contains("listener")).reduce("",(logLine,cumulativLog) -> cumulativLog+"\n"+logLine);
                fileWriter.write(log);
                fileWriter.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    public void createFolder(File filename){
        if(!filename.exists())
        {
            String directory = filename.getParent();
            new File(directory).mkdirs();
        }
    }



    public void getCoreData(){
        final String NAME_SPACE="sutanu-supportability";
        final String POD_NAME="pax-installation-paxserver";
        final String MONGO_CONTAINER = "paxserver";
        this.downloadFileFromContainer(NAME_SPACE, POD_NAME, MONGO_CONTAINER,"/usr/local/paxata/server/config/px.properties",
                new File(OUTPUT_DIRECTORY + File.pathSeparator + "paxserver/config/px.properties"));
        this.readLog(NAME_SPACE, POD_NAME, MONGO_CONTAINER,new File(OUTPUT_DIRECTORY + File.pathSeparator + "paxserver/logs/frontend.log"));
    }

    public void getMongoData(){
        final String NAME_SPACE="mongo";
        final String POD_NAME="mongo-deployment-0";
        final String MONGO_CONTAINER = "mongo";
        this.readLog(NAME_SPACE, POD_NAME, MONGO_CONTAINER,new File(OUTPUT_DIRECTORY + File.pathSeparator + "mongo/logs/mongo.log"));
    }

    public void getPipelineProxyData(){
        final String NAME_SPACE="sutanu-supportability";
        final String POD_NAME="pax-installation-pipeline-proxy";
        final String CONTAINER_NAME = "pipeline-proxy";
        this.readLog(NAME_SPACE, POD_NAME, CONTAINER_NAME,new File(OUTPUT_DIRECTORY + File.pathSeparator + "pipeline-proxy/logs/pipeline-proxy.log"));
    }


    public void getPipelineData(){
        final String NAME_SPACE="sutanu-supportability";
        List<Pod> pods=this.client.pods().inNamespace(MY_NAMESPACE).list().getItems().stream().filter(p -> p.getMetadata().getName().contains("driver")||p.getMetadata().getName().contains("exec")).collect(Collectors.toList());
        if(!pods.isEmpty()){
            int counter=0;
            for(Pod pod:pods){
                if(pod.getMetadata().getName().contains("driver")){
                    this.getDataFromEachPod(NAME_SPACE, pod.getMetadata().getName(),"master.log");
                }else{
                    counter++;
                    this.getDataFromEachPod(NAME_SPACE, pod.getMetadata().getName(),counter+"-executor.log");
                }
            }
        }
    }

    public void getDataFromEachPod(String NAME_SPACE, String POD_NAME, String destinationFileName){
        List<Container> containers=this.client.pods().inNamespace(NAME_SPACE).withName(POD_NAME).get().getSpec().getContainers();
        for(Container container:containers) {
            this.readLog(NAME_SPACE, POD_NAME, container.getName(), new File(OUTPUT_DIRECTORY + File.pathSeparator + "pipeline/logs/"+destinationFileName));
        }
    }



    public static void main(String[] args) {
//        Supportability supportability = new Supportability();
//        supportability.getMongoData();
//        supportability.getCoreData();
//        supportability.getPipelineProxyData();
//        supportability.getPipelineData();
//        Pod pod=new PodBuilder()
//                .withNewMetadata()
//                    .withName("task-pv-pod")
//                    .withNamespace(MY_NAMESPACE)
//                .endMetadata()
//                .withNewSpec()
//                    .withVolumes(
//                        new VolumeBuilder()
//                                .withName("authkey-vol-name")
//                                .withNewPersistentVolumeClaim()
//                                .withClaimName("authkey-pvc")
//                                .endPersistentVolumeClaim()
//                                .build())
//                    .addNewContainer()
//                        .withName("task-pv-container")
//                        .withImage("nginx")
//                        .addNewPort()
//                            .withContainerPort(80)
//                            .withName("http-server")
//                        .endPort()
//                        .withVolumeMounts(
//                            new VolumeMountBuilder()
//                                .withMountPath("/usrtest2")
//                                .withName("authkey-vol-name")
//                                .build()
//                )                 .endContainer()
//                .endSpec()
//                .build();
//

String podname="task-pv-pod14";
        Pod pod1 = new PodBuilder()
                .withNewMetadata()
                .withName(podname)
                .addToLabels("paxata.com/connector-artifact-id", "connector-big-query")
                .addToLabels("app.kubernetes.io/name", "connector")
                .addToLabels("app.kubernetes.io/instance", "cebc167d-14ab-4799-b4ee-cfc834399cc2")
                .addToLabels("paxata.com/installation-name", "pax-installation")
                .withNamespace("sutanu-2669")
                .addNewOwnerReference()
                .withUid("2b962a7c-92a6-41e8-9b8d-b772e48661e6")
                .withName("pax-installation-paxserver")
                .withApiVersion("apps/v1")
                .withController(true)
                .withKind("Pod")
                .endOwnerReference()
                .endMetadata()
                .withNewSpec()
                .addNewImagePullSecret()
                .withName("pax-installation-dtr")
                .endImagePullSecret()
                .addNewContainer()
                .withName(podname)
                .withImage("011447054295.dkr.ecr.us-west-2.amazonaws.com/paxata/runnable_pr_connector_big-query:2021.2.pr.2021.2_DATAPREP-2669")
                .withImagePullPolicy("Always")
                .addNewEnv()
                .withName("ADDITIONAL_JVM_ARGS")
                .withValue("-XX:+AggressiveOpts -XX:-UseCompressedOops -XX:+HeapDumpOnOutOfMemoryError -Dpx.idle.timeout.minutes=60")
                .endEnv()
                .addNewPort().withContainerPort(8110).endPort()
                .withNewReadinessProbe()
                .withNewHttpGet()
                .withPath("/health")
                .withPort(new IntOrString(8110))
                .endHttpGet()
                .withInitialDelaySeconds(2)
                .withPeriodSeconds(1)
                .endReadinessProbe()
                .withNewResources()
                .addToRequests("cpu", new Quantity("250m"))
                .addToRequests("memory",  new Quantity("1024m"))
                .endResources()
                .withVolumeMounts(
                        new VolumeMountBuilder()
                                .withMountPath("/usr/local/paxata/connector/tmp/authkeystore")
                                .withName("authkey-vol-name")
                                .build()
                )
                .endContainer()
                .withRestartPolicy("Never")
                .withVolumes(
                        new VolumeBuilder()
                                .withName("authkey-vol-name")
                                .withNewPersistentVolumeClaim()
                                .withClaimName("pax-installation-pvc")
                                .endPersistentVolumeClaim()
                                .build())
                .endSpec()
                .build();
        new Supportability().client.pods().inNamespace(MY_NAMESPACE).create(pod1);
        System.out.println(pod1.getMetadata().getName());
    }
}
