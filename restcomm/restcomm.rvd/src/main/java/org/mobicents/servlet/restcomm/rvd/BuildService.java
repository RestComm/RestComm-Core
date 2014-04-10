package org.mobicents.servlet.restcomm.rvd;

import org.apache.log4j.Logger;

import org.mobicents.servlet.restcomm.rvd.model.StepJsonDeserializer;
import org.mobicents.servlet.restcomm.rvd.model.StepJsonSerializer;
import org.mobicents.servlet.restcomm.rvd.model.client.ProjectState;
import org.mobicents.servlet.restcomm.rvd.model.client.Step;
import org.mobicents.servlet.restcomm.rvd.model.server.NodeName;
import org.mobicents.servlet.restcomm.rvd.model.server.ProjectOptions;
import org.mobicents.servlet.restcomm.rvd.storage.ProjectStorage;
import org.mobicents.servlet.restcomm.rvd.storage.exceptions.StorageException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This class is responsible for breaking the project state from a big JSON object to separate files per node/step. The
 * resulting files will be easily processed from the interpreter when the application is run.
 *
 */
public class BuildService {

    static final Logger logger = Logger.getLogger(BuildService.class.getName());

    protected Gson gson;
    private ProjectStorage projectStorage;

    public BuildService(ProjectStorage projectStorage) {
        // Parse the big project state object into a nice dto model
        gson = new GsonBuilder()
                .registerTypeAdapter(Step.class, new StepJsonDeserializer())
                .registerTypeAdapter(Step.class, new StepJsonSerializer())
                //.registerTypeAdapter(DialNoun.class, new DialNounJsonDeserializer())  // put these inside StepJsonDeserializer. Since DialNoun deserialization is part of StepDeserialization process
                //.registerTypeAdapter(DialNoun.class, new DialNounJsonSerializer())    // ...
                .create();
        this.projectStorage = projectStorage;
    }

    /**
     * Breaks the project state from a big JSON object to separate files per node/step. The resulting files will be easily
     * processed from the interpreter when the application is run.
     *
     * @param projectStateJson string representation of a big JSON object representing the project's state in the client
     * @param projectPath absolute filesystem path of the project. This is where the generated files will be stored
     * @throws IOException
     * @throws StorageException
     */
    public void buildProject(String projectName) throws StorageException {
        ProjectState projectState = gson.fromJson(projectStorage.loadProjectState(projectName), ProjectState.class);
        ProjectOptions projectOptions = new ProjectOptions();

        // Save general purpose project information
        // Use the start node name as a default target. We could use a more specialized target too here

        // Build the nodes one by one
        for (ProjectState.Node node : projectState.getNodes()) {
            buildNode(node, projectName);
            NodeName nodeName = new NodeName();
            nodeName.setName(node.getName());
            nodeName.setLabel(node.getLabel());
            projectOptions.getNodeNames().add( nodeName );
        }

        projectOptions.setDefaultTarget(projectState.getHeader().getStartNodeName());
        // Save the nodename-node-label mapping
        //File outFile = new File(projectPath + "data/" + "project");
        //FileUtils.writeStringToFile(outFile, gson.toJson(projectOptions), "UTF-8");
        projectStorage.storeProjectOptions(projectName, gson.toJson(projectOptions));
    }

    /**
     *
     * @param node
     * @param projectPath
     * @throws StorageException
     * @throws IOException
     */
    private void buildNode(ProjectState.Node node, String projectName) throws StorageException {
        logger.debug("Building module " + node.getName() );

        // TODO sanitize node name!

        projectStorage.storeNodeStepnames(projectName, node.getName(), gson.toJson(node.getStepnames()));
        // process the steps one-by-one
        for (String stepname : node.getSteps().keySet()) {
            Step step = node.getSteps().get(stepname);
            logger.debug("Building step " + step.getKind() + " - " + step.getName() );
            projectStorage.storeNodeStep(projectName, node.getName(), step.getName(), gson.toJson(step));
        }
    }
}
