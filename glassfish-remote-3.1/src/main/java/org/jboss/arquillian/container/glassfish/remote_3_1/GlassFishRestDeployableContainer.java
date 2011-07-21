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
/**
 *
 * @author <a href="http://community.jboss.org/people/LightGuard">Jason Porter</a>
 */
package org.jboss.arquillian.container.glassfish.remote_3_1;

import java.io.File;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;

import javax.ws.rs.core.MediaType;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.container.glassfish.remote_3_1.clientutils.GlassFishClient;
import org.jboss.arquillian.container.glassfish.remote_3_1.clientutils.GlassFishClientException;
import org.jboss.arquillian.container.glassfish.remote_3_1.clientutils.GlassFishClientService;
import org.jboss.arquillian.container.glassfish.remote_3_1.clientutils.NodeAddress;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;

import java.util.List;
import java.util.Map;

/**
 * Glassfish v3.1 remote container using REST deployment.
 *
 * @author <a href="http://community.jboss.org/people/LightGuard">Jason Porter</a>
 */
@SuppressWarnings({"HardcodedFileSeparator"})
public class GlassFishRestDeployableContainer implements DeployableContainer<GlassFishRestConfiguration> {
	
	public static final String DELETE_OPERATION = "__deleteoperation";
	
	private String deploymentName;
	
    private GlassFishRestConfiguration configuration;
	
    private List<NodeAddress> nodeAddressList;
    
    private GlassFishClient glassFishClient;
	
	private static final Logger log = Logger.getLogger(GlassFishClientUtil.class.getName());
	
    public Class<GlassFishRestConfiguration> getConfigurationClass() {
        return GlassFishRestConfiguration.class;
    }
	
    public void setup(GlassFishRestConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        this.configuration = configuration;
        
        // Start up the GlassFishClient service layer
        this.glassFishClient = new GlassFishClientService(configuration);        
    }
	
    public void start() throws LifecycleException {   	
		
    	try {			
    		// Resolve the remote server HOST address & port numbers
    		this.nodeAddressList = glassFishClient.getNodeAddressList();
			
    	} catch (GlassFishClientException e) {
            throw new LifecycleException("Error verifying the sever is running", e);
        }
    }
	
    public void stop() throws LifecycleException {
        //To change body of implemented methods use File | Settings | File Templates.
    }
	
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("Servlet 3.0");
    }
	
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        if (archive == null) {
            throw new IllegalArgumentException("archive must not be null");
        }
		
        final ProtocolMetaData protocolMetaData = new ProtocolMetaData();
		
        try {
            // Export to a file so we can send it over the wire
            URL archiveFile = ShrinkWrapUtil.toURL(archive);
            
            // Build up the POST form to send to Glassfish
            final FormDataMultiPart form = new FormDataMultiPart();
            try
            {
				form.getBodyParts().add(new FileDataBodyPart("id", new File(archiveFile.toURI())));
            }
            catch (URISyntaxException e1)
            {
				throw new DeploymentException("Could not convert exported deployment URL to URI?", e1);
            }
            
            deploymentName = createDeploymentName(archive.getName());
            addDeployFormFields(deploymentName, form);
            
            // Do Deploy the application on the remote GlassFish
            HTTPContext httpContext = glassFishClient.doDeploy(deploymentName, form);			
            protocolMetaData.addContext(httpContext);
            
        } catch (GlassFishClientException e) {
            throw new DeploymentException("Could not deploy " + archive.getName(), e);
        }
        return protocolMetaData;
    }
	
    public void undeploy(Archive<?> archive) throws DeploymentException {
		
    	if (archive == null) {
            throw new IllegalArgumentException("archive must not be null");
        } else {
			
        	deploymentName = createDeploymentName(archive.getName());
        	try {
	            // Build up the POST form to send to Glassfish
	            final FormDataMultiPart form = new FormDataMultiPart();
	            form.field("target", this.configuration.getTarget(), MediaType.TEXT_PLAIN_TYPE);        	        	
	            form.field("operation", DELETE_OPERATION, MediaType.TEXT_PLAIN_TYPE);
	        	glassFishClient.doUndeploy(this.deploymentName, form);
	        } catch (GlassFishClientException e) {
	            throw new DeploymentException("Could not undeploy " + archive.getName(), e);
	        }
        }
		
    }
	
    public void deploy(Descriptor descriptor) throws DeploymentException {
		throw new UnsupportedOperationException("Not implemented");
    }
	
    public void undeploy(Descriptor descriptor) throws DeploymentException {
		throw new UnsupportedOperationException("Not implemented");
    }
	
    private String createDeploymentName(String archiveName)
    {
		String correctedName = archiveName;
		if(correctedName.startsWith("/"))
		{
			correctedName = correctedName.substring(1);
		}
		if(correctedName.indexOf(".") != -1)
		{
			correctedName = correctedName.substring(0, correctedName.lastIndexOf("."));
		}
		return correctedName;
    }
	
    private void addDeployFormFields(String name, FormDataMultiPart deployform) {
		
        // add the name field, the name is the archive filename without extension 
        deployform.field("name", name, MediaType.TEXT_PLAIN_TYPE);
		
        // add the target field (the default is "server" - Admin Server)
        deployform.field("target", this.configuration.getTarget(), MediaType.TEXT_PLAIN_TYPE);        
		
        // add the libraries field (optional)
        if ( this.configuration.getLibraries() != null )  {
        	deployform.field("libraries", this.configuration.getLibraries(), MediaType.TEXT_PLAIN_TYPE);
        }
		
        // add the properties field (optional)
        if ( this.configuration.getProperties() != null )  {
        	deployform.field("properties", this.configuration.getProperties(), MediaType.TEXT_PLAIN_TYPE);
        }
		
        // add the type field (optional, the only valid value is "osgi", other values are ommited)
        if ( this.configuration.getType() != null && "osgi".equals(this.configuration.getType()) )  {
        	deployform.field("type", this.configuration.getType(), MediaType.TEXT_PLAIN_TYPE);
        }
    }
	
}
