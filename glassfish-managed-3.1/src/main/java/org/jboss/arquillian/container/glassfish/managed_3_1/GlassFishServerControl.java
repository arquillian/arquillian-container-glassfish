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
    private Thread shutdownHook;
    
    public GlassFishServerControl(GlassFishManagedContainerConfiguration config) {
        this.config = config;
    }
    
    public void start() throws LifecycleException {
        String admincmd = "start-domain";
        List<String> args = new ArrayList<String>();
        if (config.isDebug()) {
            args.add("--debug");
        }
        int result = executeAdminDomainCommand("Starting container", admincmd, args);
        if (result > 0) {
            throw new LifecycleException("Could not start container");
        }
        
        shutdownHook = new Thread(new Runnable() {

            @Override
            public void run() {
                executeAdminDomainCommand("Forcing container shutdown", "stop-domain", new ArrayList<String>());
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
    
    public void stop() throws LifecycleException {
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }
        int result = executeAdminDomainCommand("Stopping container", "stop-domain", new ArrayList<String>());
        if (result > 0) {
            throw new LifecycleException("Could not stop container");
        }
    }
    
    private int executeAdminDomainCommand(String description, String admincmd, List<String> args) {
        if (config.getDomain() != null) {
            args.add(config.getDomain());
        }
        args.add("-t");
        
        return executeAdminCommand(description, admincmd, args);
    }

    public int executeAdminCommand(String description, String admincmd, List<String> args) {
        List<String> cmd = new ArrayList<String>();
        cmd.add("java");

        cmd.add("-jar");
        cmd.add(config.getAdminCliJar().getAbsolutePath());
        
        cmd.add(admincmd);
        cmd.addAll(args);
        
        if (config.isOutputToConsole()) {
            System.out.println(description + " using command: " + cmd.toString());
        }

        Process process = null;
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
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
        }

    }
}
