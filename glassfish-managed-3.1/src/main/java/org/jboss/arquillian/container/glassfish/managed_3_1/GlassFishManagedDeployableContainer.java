/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.glassfish.managed_3_1;

import org.jboss.arquillian.container.glassfish.CommonGlassFishManager;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Glassfish 3.1 managed container using REST deployments
 * 
 * @author <a href="http://community.jboss.org/people/LightGuard">Jason Porter</a>
 * @author <a href="http://community.jboss.org/people/dan.j.allen">Dan Allen</a>
 * @author Vineet Reynolds
 */
public class GlassFishManagedDeployableContainer implements DeployableContainer<GlassFishManagedContainerConfiguration> {

    private GlassFishManagedContainerConfiguration configuration;
    private GlassFishServerControl serverControl;
    private CommonGlassFishManager<GlassFishManagedContainerConfiguration> glassFishManager;
    private boolean connectedToRunningServer;

    public Class<GlassFishManagedContainerConfiguration> getConfigurationClass() {
        return GlassFishManagedContainerConfiguration.class;
    }

    public void setup(GlassFishManagedContainerConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }

        this.configuration = configuration;
        this.serverControl = new GlassFishServerControl(configuration);
        this.glassFishManager = new CommonGlassFishManager<GlassFishManagedContainerConfiguration>(configuration);
    }

    public void start() throws LifecycleException {
       if (glassFishManager.isDASRunning())
       {
          if (configuration.isAllowConnectingToRunningServer())
          {
             // If we are allowed to connect to a running server,
             // then do not issue the 'asadmin start-domain' command.
             connectedToRunningServer = true;
             glassFishManager.start();
             return;
          }
          else
          {
             throw new LifecycleException("The server is already running! "
                   + "Managed containers does not support connecting to running server instances due to the "
                   + "possible harmful effect of connecting to the wrong server. Please stop server before running or "
                   + "change to another type of container.\n"
                   + "To disable this check and allow Arquillian to connect to a running server, "
                   + "set allowConnectingToRunningServer to true in the container configuration");
          }
       }
       else
       {
          serverControl.start();
          glassFishManager.start();

          if (configuration.getPostStartCommandFile() != null) {
             runCommands("Post start", configuration.getPostStartCommandFile());
          }
       }
    }

    public void stop() throws LifecycleException {
        if(!connectedToRunningServer)
        {
           try {
              if (configuration.getPreStopCommandFile() != null) {
                 runCommands("Pre stop", configuration.getPreStopCommandFile());
              }
           } finally {
              serverControl.stop();
           }
        }
    }

    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("Servlet 3.0");
    }

    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        return glassFishManager.deploy(archive);
    }

    public void undeploy(Archive<?> archive) throws DeploymentException {
        glassFishManager.undeploy(archive);
    }

    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

    void runCommands(final String description, final String fileName) throws LifecycleException {
        try {
            final BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "US-ASCII"));
            final String delimiter = configuration.getCommandFileDelimiter();
            String command;
            while ((command = r.readLine()) != null) {
                if (command.startsWith("#")) {
                    continue;
                }
                final String[] parts = command.split(delimiter);
                if (parts.length == 0) {
                    throw new LifecycleException("Invalid command: " + command);
                }
                final List<String> args = new ArrayList<String>(parts.length - 1);
                for (int i = 1; i < parts.length; ++i) {
                    args.add(parts[i]);
                }
                int ret = serverControl.executeAdminCommand(description, parts[0], args);
                if (ret != 0) {
                    throw new LifecycleException("Exited with code " + ret + " on command " + command);
                }
            }
        } catch (final IOException e) {
            throw new LifecycleException("", e);
        }
    }
}
