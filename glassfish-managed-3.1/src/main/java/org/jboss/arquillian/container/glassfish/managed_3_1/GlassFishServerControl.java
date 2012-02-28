/*
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.glassfish.managed_3_1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.spi.client.container.LifecycleException;

/**
 * A class for issuing asadmin commands using the admin-cli.jar of the GlassFish distribution.
 * 
 * @author <a href="http://community.jboss.org/people/dan.j.allen">Dan Allen</a>
 */
public class GlassFishServerControl {

    private static final Logger logger = Logger.getLogger(GlassFishServerControl.class.getName());
    private GlassFishManagedContainerConfiguration config;
    
    public GlassFishServerControl(GlassFishManagedContainerConfiguration config) {
        this.config = config;
    }
    
    public void start() throws LifecycleException {
        String cmd = "start-domain";
        if (config.isDebug()) {
            cmd += " --debug";
        }
        int result = executeDomainCommand(cmd, "Starting container");
        if (result > 0) {
            throw new LifecycleException("Could not start container");
        }
    }
    
    public void stop() throws LifecycleException {
        int result = executeDomainCommand("stop-domain", "Stopping container");
        if (result > 0) {
            throw new LifecycleException("Could not stop container");
        }
    }
    
    private int executeDomainCommand(String command, String description) {
        Process process;
        
        List<String> cmd = new ArrayList<String>();
        cmd.add("java");

        cmd.add("-jar");
        cmd.add(config.getAdminCliJar().getAbsolutePath());

        for (StringTokenizer tok = new StringTokenizer(command, " "); tok.hasMoreTokens();) {
            cmd.add(tok.nextToken());
        }
        if (config.getDomain() != null) {
            cmd.add(config.getDomain());
        }
        cmd.add("-t");

        if (config.isOutputToConsole()) {
            System.out.println(description + " using command: " + cmd.toString());
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            new Thread(new ConsoleConsumer(process, config.isOutputToConsole())).start();
            try {
                return process.waitFor();
            }
            catch (InterruptedException e) {
                logger.log(Level.INFO, description + " interrupted.");
                return 1;
            }
         } catch (IOException e) {
             logger.log(Level.SEVERE, description + " failed.", e);
             return 1;
         }
    }

    private class ConsoleConsumer implements Runnable {

        private Process process;
        private boolean writeOutput;

        private ConsoleConsumer(Process process, boolean writeOutput) {
            this.process = process;
            this.writeOutput = writeOutput;
        }

        public void run() {
            final InputStream stream = process.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    if (writeOutput) {
                        System.out.println(line);
                    }
                }
            } catch (IOException e) {
            }
        }

    }
}
