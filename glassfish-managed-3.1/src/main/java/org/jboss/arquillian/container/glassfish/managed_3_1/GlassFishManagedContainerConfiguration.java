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

import java.io.File;

import org.jboss.arquillian.container.glassfish.CommonGlassFishConfiguration;
import org.jboss.arquillian.container.glassfish.clientutils.GlassFishClient;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.deployment.Validate;

/**
 * Configuration for Managed GlassFish containers.
 *
 * @author <a href="http://community.jboss.org/people/dan.j.allen">Dan Allen</a>
 * @author <a href="http://community.jboss.org/people/LightGuard">Jason Porter</a>
 * @author Vineet Reynolds
 */
public class GlassFishManagedContainerConfiguration extends CommonGlassFishConfiguration {

    /**
     * The local GlassFish installation directory
     */
    private String glassFishHome = System.getenv("GLASSFISH_HOME");

    /**
     * The GlassFish domain to use or the default domain if not specified
     */
    private String domain = null;

    /**
     * Show the output of the admin commands on the console
     */
    private boolean outputToConsole = false;

    /**
     * Flag to start the server in debug mode using standard GlassFish debug port
     */
    private boolean debug = false;

    /**
     * Allow Arquillian to use an already running GlassFish instance.
     */
    private boolean allowConnectingToRunningServer = false;

    public String getGlassFishHome() {
        return glassFishHome;
    }

    public void setGlassFishHome(String glassFishHome) {
        this.glassFishHome = glassFishHome;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isOutputToConsole() {
        return outputToConsole;
    }

    public void setOutputToConsole(boolean outputToConsole) {
        this.outputToConsole = outputToConsole;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public File getAdminCliJar() {
        return new File(getGlassFishHome() + "/glassfish/modules/admin-cli.jar");
    }

    public boolean isAllowConnectingToRunningServer() {
        return allowConnectingToRunningServer;
    }

    public void setAllowConnectingToRunningServer(boolean allowConnectingToRunningServer) {
        this.allowConnectingToRunningServer = allowConnectingToRunningServer;
    }

    @Override
    public String getTarget() {
        return GlassFishClient.ADMINSERVER;
    }

    /**
     * Validates if current configuration is valid, that is if all required
     * properties are set and have correct values
     */
    public void validate() throws ConfigurationException {
        Validate.notNull(getGlassFishHome(), "The property glassFishHome must be specified or the GLASSFISH_HOME environment variable must be set");
        Validate.configurationDirectoryExists(getGlassFishHome() + "/glassfish", getGlassFishHome() + " is not a valid GlassFish installation");

        if (!getAdminCliJar().isFile()) {
            throw new IllegalArgumentException("Could not locate admin-cli.jar module in GlassFish installation: " + getGlassFishHome());
        }

        if (getDomain() != null) {
            Validate.configurationDirectoryExists(getGlassFishHome() + "/glassfish/domains/" + getDomain(), "Invalid domain: " + getDomain());
        }

        super.validate();
    }
}
