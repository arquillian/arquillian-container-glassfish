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
package org.jboss.arquillian.container.glassfish.managed_4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.glassfish.CommonGlassFishManager;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * Glassfish 3.1 managed container using REST deployments
 * 
 * @author <a href="http://community.jboss.org/people/LightGuard">Jason
 *         Porter</a>
 * @author <a href="http://community.jboss.org/people/dan.j.allen">Dan Allen</a>
 * @author Vineet Reynolds
 */
public class GlassFishManagedDeployableContainer
		implements DeployableContainer<GlassFishManagedContainerConfiguration> {

	private static final Logger logger = Logger.getLogger(GlassFishManagedDeployableContainer.class.getName());

	private GlassFishManagedContainerConfiguration configuration;
	private CommonGlassFishManager<GlassFishManagedContainerConfiguration> glassFishManager;
	private boolean connectedToRunningServer;
	private Process startupProcess;
	private Thread shutdownHook;

	public Class<GlassFishManagedContainerConfiguration> getConfigurationClass() {
		return GlassFishManagedContainerConfiguration.class;
	}

	public void setup(GlassFishManagedContainerConfiguration configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException("configuration must not be null");
		}

		this.configuration = configuration;
		this.glassFishManager = new CommonGlassFishManager<GlassFishManagedContainerConfiguration>(configuration);
	}

	public void start() throws LifecycleException {
		if (glassFishManager.isDASRunning()) {
			if (configuration.isAllowConnectingToRunningServer()) {
				// If we are allowed to connect to a running server,
				// then do not issue the 'asadmin start-domain' command.
				connectedToRunningServer = true;
				glassFishManager.start();
				return;
			} else {
				throw new LifecycleException("The server is already running! "
						+ "Managed containers does not support connecting to running server instances due to the "
						+ "possible harmful effect of connecting to the wrong server. Please stop server before running or "
						+ "change to another type of container.\n"
						+ "To disable this check and allow Arquillian to connect to a running server, "
						+ "set allowConnectingToRunningServer to true in the container configuration");
			}
		} else {
			startServer();
			glassFishManager.start();
		}
	}

	public void stop() throws LifecycleException {
		if (!connectedToRunningServer) {
			if (shutdownHook != null) {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
				shutdownHook = null;
			}

			stopServer(startupProcess);
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

	private void startServer() throws LifecycleException {
		List<String> cmd = buildProcessArguments("start-domain", getStartDomainArguments());

		if (configuration.isOutputToConsole()) {
			System.out.println("Starting container using command: " + cmd.toString());
		}

		try {
			startupProcess = executeAdminCommand(cmd, false);
			final Process process = startupProcess;

			shutdownHook = new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						stopServer(process);
					} catch (LifecycleException e) {
						logger.log(Level.SEVERE, "Failed to shutdown container.", e);
					}
				}
			});
			Runtime.getRuntime().addShutdownHook(shutdownHook);

			final long startupTimeout = configuration.getStartupTimeoutInSeconds();
			long timeOutTime = System.currentTimeMillis() + startupTimeout * 1000;
			boolean serverAvailable = false;
			while (System.currentTimeMillis() < timeOutTime && serverAvailable == false) {
				serverAvailable = glassFishManager.isDASRunning();
				if (!serverAvailable) {
					Thread.sleep(100);
				}
			}
			if (!serverAvailable) {
				stopServer(startupProcess);
				throw new TimeoutException(
						String.format("Managed server was not started within [%d] s", startupTimeout));
			}

		} catch (Exception e) {
			throw new LifecycleException("Could not start container", e);
		}
	}

	private void stopServer(Process process) throws LifecycleException {
		if (process == null) {
			return;
		}

		List<String> cmd = buildProcessArguments("stop-domain", getStopDomainArguments());

		if (configuration.isOutputToConsole()) {
			System.out.println("Forcing container shutdown using command: " + cmd.toString());
		}

		Process proc = null;
		try {
			proc = executeAdminCommand(cmd, true);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to shutdown container.", e);
		} catch (InterruptedException e) {
			logger.log(Level.INFO, "Interrupted while shutting down the container.");
		}

		process.destroy();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			logger.log(Level.INFO, "Interrupted while shutting down the container.");
		}

		if (proc == null || proc.exitValue() > 0) {
			throw new LifecycleException("Could not stop container");
		}
	}

	private Process executeAdminCommand(List<String> cmd, boolean waitForTermination)
			throws IOException, InterruptedException {
		final Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		new Thread(new ConsoleConsumer(process, configuration.isOutputToConsole())).start();
		if (waitForTermination) {
			process.waitFor();
		}
		return process;
	}

	private List<String> buildProcessArguments(String admincmd, List<String> args) {
		List<String> cmd = new ArrayList<String>();
		cmd.add("java");

		cmd.add("-jar");
		cmd.add(configuration.getAdminCliJar().getAbsolutePath());

		cmd.add(admincmd);
		cmd.addAll(args);

		cmd.add("-t");
		return cmd;
	}

	private List<String> getStartDomainArguments() {
		List<String> args = new ArrayList<String>();
		args.add("--verbose");
		if (configuration.isDebug()) {
			args.add("--debug");
		}

		if (configuration.getDomain() != null) {
			args.add(configuration.getDomain());
		}
		return args;
	}

	private List<String> getStopDomainArguments() {
		List<String> args = new ArrayList<String>();
		if (configuration.getDomain() != null) {
			args.add(configuration.getDomain());
		}
		return args;
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
