package com.paxata.engine;


import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Supportability {
    public static final String MONGO_NAMESPACE = "mongo";
    public static final String MY_NAMESPACE = "sutanu-supportability";
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
                    .getLog();
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
        Supportability supportability = new Supportability();
        supportability.getMongoData();
        supportability.getCoreData();
        supportability.getPipelineProxyData();
        supportability.getPipelineData();
    }
}
