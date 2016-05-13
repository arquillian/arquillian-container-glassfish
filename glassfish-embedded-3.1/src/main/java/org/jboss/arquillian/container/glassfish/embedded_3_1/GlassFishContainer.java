/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.glassfish.embedded_3_1;

import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.web.WebModule;
import org.apache.catalina.Container;
import org.glassfish.embeddable.BootstrapProperties;
import org.glassfish.embeddable.CommandResult;
import org.glassfish.embeddable.CommandRunner;
import org.glassfish.embeddable.GlassFish;
import org.glassfish.embeddable.GlassFishException;
import org.glassfish.embeddable.GlassFishProperties;
import org.glassfish.embeddable.GlassFishRuntime;
import org.glassfish.embeddable.web.Context;
import org.glassfish.embeddable.web.VirtualServer;
import org.glassfish.embeddable.web.WebContainer;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Filters;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletRegistration;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GlassfishContainer
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class GlassFishContainer implements DeployableContainer<GlassFishConfiguration> {
    private static final Logger log = Logger.getLogger(GlassFishContainer.class.getName());

    private static final String SYSTEM_PROPERTY_REGEX = "\\$\\{(.*)\\}";
    private static final String COMMAND_ADD_RESOURCES = "add-resources";

    // TODO: open configuration up for bind address
    private static final String ADDRESS = "localhost";

    private GlassFishConfiguration configuration;
    private GlassFishRuntime glassfishRuntime;
    private GlassFish glassfish;

    private boolean shouldSetPort = true;
    private int bindHttpPort;
    private int bindHttpsPort;

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#getConfigurationClass()
     */
    public Class<GlassFishConfiguration> getConfigurationClass() {
        return GlassFishConfiguration.class;
    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#getDefaultProtocol()
     */
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("Servlet 3.0");
    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#setup(org.jboss.arquillian.spi.client.container.ContainerConfiguration)
     */
    public void setup(GlassFishConfiguration configuration) {
        this.configuration = configuration;
        BootstrapProperties bootstrapProps = new BootstrapProperties();
        if (configuration.getInstallRoot() != null) {
            bootstrapProps.setInstallRoot(configuration.getInstallRoot());
        }
        try {
            glassfishRuntime = GlassFishRuntime.bootstrap(bootstrapProps);
        } catch (Exception e) {
            throw new RuntimeException("Could not setup GlassFish Embedded Bootstrap", e);
        }

        boolean cleanup = configuration.getCleanup();
        GlassFishProperties serverProps = new GlassFishProperties();

        if (configuration.getInstanceRoot() != null) {
            File instanceRoot = new File(configuration.getInstanceRoot());
            if (instanceRoot.exists()) {
                cleanup = false;
            }
            serverProps.setInstanceRoot(configuration.getInstanceRoot());
            shouldSetPort = false;
        }
        if (configuration.getConfigurationXml() != null) {
            serverProps.setConfigFileURI(configuration.getConfigurationXml());
            shouldSetPort = false;
        }
        serverProps.setConfigFileReadOnly(configuration.isConfigurationReadOnly());
        if (shouldSetPort) {
            bindHttpPort = configuration.getBindHttpPort();
            serverProps.setPort("http-listener", bindHttpPort);
            bindHttpsPort = configuration.getBindHttpsPort();
            serverProps.setPort("https-listener", bindHttpsPort);
        }

        try {
            glassfish = glassfishRuntime.newGlassFish(serverProps);
        } catch (Exception e) {
            throw new RuntimeException("Could not setup GlassFish Embedded Runtime", e);
        }

        if (cleanup) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    deleteRecursive(new File(System.getProperty(SystemPropertyConstants.INSTANCE_ROOT_PROPERTY)));
                }
            });
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#start()
     */
    public void start() throws LifecycleException {
        try {
            glassfish.start();
            if (!shouldSetPort) {
                readBindingHttpPort();
            }
            bindCommandRunner();
        } catch (Exception e) {
            throw new LifecycleException("Could not start GlassFish Embedded", e);
        }
        // Server needs to be started before we can deploy resources
        for (String resource : configuration.getResourcesXml()) {
            try {
                executeCommand(COMMAND_ADD_RESOURCES, resource);
            } catch (Throwable e) {
                throw new RuntimeException("Could not deploy sun-reosurces file: " + resource, e);
            }
        }


    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#stop()
     */
    public void stop() throws LifecycleException {
        try {
            unbindCommandRunner();
            glassfish.stop();
        } catch (Exception e) {
            throw new LifecycleException("Could not stop GlassFish Embedded", e);
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#deploy(org.jboss.shrinkwrap.api.Archive)
     */
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        String deploymentName = createDeploymentName(archive.getName());
        try {
            URL deploymentUrl = ShrinkWrapUtil.toURL(archive);

            glassfish.getDeployer().deploy(deploymentUrl.toURI(), "--name", deploymentName);
        } catch (Exception e) {
            throw new DeploymentException("Could not deploy " + archive.getName(), e);
        }

        try {
            HTTPContext httpContext = new HTTPContext(ADDRESS, bindHttpPort);

            findServlets(httpContext, resolveWebArchiveNames(archive));

            return new ProtocolMetaData()
                    .addContext(httpContext);
        } catch (GlassFishException e) {
            throw new DeploymentException("Could not probe GlassFish embedded for environment", e);
        }
    }

    /**
     * @param archive
     * @return
     */
    private String[] resolveWebArchiveNames(Archive<?> archive) {
        if (archive instanceof WebArchive) {
            return new String[]{createDeploymentName(archive.getName())};
        } else if (archive instanceof EnterpriseArchive) {
            Map<ArchivePath, Node> webArchives = archive.getContent(Filters.include(".*\\.war"));
            List<String> deploymentNames = new ArrayList<String>();
            for (ArchivePath path : webArchives.keySet()) {
                deploymentNames.add(createDeploymentName(path.get()));
            }
            return deploymentNames.toArray(new String[0]);
        }
        return new String[0];
    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#undeploy(org.jboss.shrinkwrap.api.Archive)
     */
    public void undeploy(Archive<?> archive) throws DeploymentException {
        try {
            glassfish.getDeployer().undeploy(createDeploymentName(archive.getName()));
        } catch (Exception e) {
            throw new DeploymentException("Could not undeploy " + archive.getName(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#deploy(org.jboss.shrinkwrap.descriptor.api.Descriptor)
     */
    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

    /* (non-Javadoc)
     * @see org.jboss.arquillian.spi.client.container.DeployableContainer#undeploy(org.jboss.shrinkwrap.descriptor.api.Descriptor)
     */
    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

    private String createDeploymentName(String archiveName) {
        String correctedName = archiveName;
        if (correctedName.startsWith("/")) {
            correctedName = correctedName.substring(1);
        }
        if (correctedName.indexOf(".") != -1) {
            correctedName = correctedName.substring(0, correctedName.lastIndexOf("."));
        }
        return correctedName;
    }

    public void findServlets(HTTPContext httpContext, String[] webArchiveNames) throws GlassFishException {
        WebContainer webContainer = glassfish.getService(WebContainer.class);
        for (String deploymentName : webArchiveNames) {
            for (VirtualServer server : webContainer.getVirtualServers()) {
                WebModule webModule = null;

                for (Context serverContext : server.getContexts()) {
                    if (serverContext instanceof WebModule) {
                        if (((WebModule) serverContext).getID().startsWith(deploymentName)) {
                            webModule = (WebModule) serverContext;
                        }
                    }
                }
                if (webModule == null) {
                    if (server instanceof com.sun.enterprise.web.VirtualServer) {
                        Container child = ((com.sun.enterprise.web.VirtualServer) server).findChild("/" + deploymentName);
                        if (child instanceof WebModule) {
                            webModule = (WebModule) child;
                        }
                    }
                }
                if (webModule != null) {
                    for (Map.Entry<String, ? extends ServletRegistration> servletRegistration : webModule.getServletRegistrations().entrySet()) {
                        httpContext.add(new Servlet(servletRegistration.getKey(), webModule.getContextPath()));
                    }
                }
            }
        }
    }

    private String executeCommand(String command, String... parameterList) throws Throwable {
        CommandRunner runner = glassfish.getCommandRunner();
        CommandResult result = runner.run(command, parameterList);

        String output = null;
        switch (result.getExitStatus()) {
            case FAILURE:
            case WARNING:
                throw result.getFailureCause();
            case SUCCESS:
                output = result.getOutput();
                log.info("command " + command + " parameters" + parameterList + " result: " + output);
                break;
        }
        return output;
    }

    private void bindCommandRunner() throws NamingException, GlassFishException {
        CommandRunner runner = glassfish.getCommandRunner();
        new InitialContext().bind("org.glassfish.embeddable.CommandRunner", runner);
    }

    private void unbindCommandRunner() throws NamingException {
        new InitialContext().unbind("org.glassfish.embeddable.CommandRunner");
    }

    private void deleteRecursive(File dir) {
        if (dir == null) {
            throw new IllegalArgumentException("directory cannot be null");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("directory must be a directory");
        }
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    deleteRecursive(file);
                    file.delete();
                } else {
                    file.delete();
                }
            }
            dir.delete();
        }
    }

    /**
     * Populates the bindHttpPort value by reading the GlassFish configuration.
     *
     * @throws LifecycleException When there is failure reading the contents of the GlassFish configuration
     */
    private void readBindingHttpPort() throws LifecycleException {
        // Fetch the name of the configuration ref element of the Default Admin Server
        String config = fetchAttribute("servers.server.server.config-ref");

        // Fetch the virtual servers in the configuration element
        List<String> virtualServers = fetchAttributes("configs.config." + config + ".http-service.virtual-server.*.id");
        for (String virtualServer : virtualServers) {
            // Ignore the __asadmin virtual server as no deployments will occur against this
            if (virtualServer.equals("__asadmin")) {
                continue;
            } else {
                // Fetch the network listeners of the virtual server
                String networkListenerList = fetchAttribute("configs.config." + config + ".http-service.virtual-server."
                        + virtualServer + ".network-listeners");

                // Iterate through the network listeners, resolve the port number of the first one. Ignore the rest.
                String[] networkListeners = networkListenerList.split(",");
                for (String networkListener : networkListeners) {
                    Integer listenPort = readNetworkListenerPort(config, networkListener);
                    if (listenPort != null) {
                        bindHttpPort = listenPort;
                    }
                }
            }
        }
    }

    /**
     * @param config
     * @param networkListener
     * @throws LifecycleException
     */
    private Integer readNetworkListenerPort(String config, String networkListener) throws LifecycleException {
        boolean enabled = Boolean.parseBoolean(fetchAttribute("configs.config." + config
                + ".network-config.network-listeners.network-listener." + networkListener + ".enabled"));
        // Is the listener enabled?
        if (enabled) {
            String protocol = fetchAttribute("configs.config." + config + ".network-config.network-listeners.network-listener."
                    + networkListener + ".protocol");
            boolean securityEnabled = Boolean.parseBoolean(fetchAttribute("configs.config." + config
                    + ".network-config.protocols.protocol." + protocol + ".security-enabled"));
            // Is the protocol secure? If yes, then ignore since Arquillian will not make HTTPS connections (yet) 
            if (!securityEnabled) {
                String portNum = fetchAttribute("configs.config." + config
                        + ".network-config.network-listeners.network-listener." + networkListener + ".port");
                Pattern sysProp = Pattern.compile(SYSTEM_PROPERTY_REGEX);
                Matcher matcher = sysProp.matcher(portNum);
                if (matcher.matches()) {
                    // If the port number is stored as a system property, then resolve the property value
                    String propertyName = matcher.group(1);
                    portNum = fetchAttribute("configs.config." + config + ".system-property." + propertyName + ".value");
                    return Integer.parseInt(portNum);
                } else {
                    return Integer.parseInt(portNum);
                }
            }
        }
        return null;
    }

    /**
     * Executes the 'get' command and parses the output for multiple values. Use this method if you're not sure of the number of
     * values returned in the command output.
     *
     * @param parameterList The list of parameters to be provided for the execution of the 'get' command.
     * @return The list of values of the attribute returned in the command output.
     * @throws LifecycleException When there is a failure executing the 'get' command
     */
    private List<String> fetchAttributes(String... parameterList) throws LifecycleException {
        try {
            String output = executeCommand("get", parameterList);
            String[] lines = output.split("\\n");
            List<String> result = new ArrayList<String>();
            // Omit the first line and parse the rest
            for (int ctr = 1; ctr < lines.length; ctr++) {
                String currentLine = lines[ctr];
                String[] kvPair = currentLine.split("=");
                // Obtain the value in the K=V pair
                result.add(kvPair[1]);
            }
            return result;
        } catch (Throwable ex) {
            throw new LifecycleException("Failed to read the HTTP listener configuration.", ex);
        }
    }

    /**
     * Executes the 'get' command and parses the output for a single value. Multiple values are not recognized. Use this only if
     * you are sure of a single return value.
     *
     * @param parameterList The list of parameters to be provided for the execution of the 'get' command.
     * @return The value of the attribute returned in the command output.
     * @throws LifecycleException When there is a failure executing the 'get' command
     */
    private String fetchAttribute(String... parameterList) throws LifecycleException {
        try {
            String output = executeCommand("get", parameterList);
            String[] lines = output.split("\\n");
            // Omit the first line and parse only the second
            String currentLine = lines[1];
            String[] kvPair = currentLine.split("=");
            // Obtain the value in the K=V pair
            return kvPair[1];
        } catch (Throwable ex) {
            throw new LifecycleException("Failed to read the HTTP listener configuration.", ex);
        }
    }
}