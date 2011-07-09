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

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;
import org.jboss.arquillian.container.spi.client.deployment.Validate;
import org.jboss.arquillian.container.glassfish.remote_3_1.clientutils.GlassFishClient;

public class GlassFishRestConfiguration implements ContainerConfiguration
{
	/**
	 * Glassfish Admin Server (DAS) host address.
	 * Used to build the URL for the REST request.
	 */
	private String adminHost = "localhost";
	
	/**
	 * Glassfish Admin Console port.
	 * Used to build the URL for the REST request.
	 */
	private int adminPort = 4848;
	
	/**
	 * Flag indicating the administration url uses a secure connection.
	 * Used to build the URL for the REST request.
	 */
	private boolean adminHttps = false;
	
	/**
	 * Flag indicating application urls use secure connections.
	 * Used to build the URL for the REST request.
	 */
	private boolean remoteServerHttps = false;
		
    /**
     * Flag indicating the remote server requires an admin user and password. 
     */
    private boolean authorisation = false;
    
    /**
     * Authorised admin user in the remote glassfish admin realm
     */
    private String adminUser;

    /**
     * Authorised admin user password
     */
    private String adminPassword;
	
	/**
	 * Name of the deployable component.
	 * default: deployment to filename without extension.
	 * Comment: No versioning should be used.
	 */
	private String name = null;
	
	/**
	 * Specifies the target to which you are  deploying. 
	 * 
	 * Valid values are:
	 * 	server
	 *   	Deploys the component to the default server instance
	 *   	server. This is the default value.
	 *   instance_name
	 *   	Deploys the component to  a  particular  stand-alone
	 *   	sever instance.
	 *   cluster_name
	 *   	Deploys the component to every  server  instance  in
	 *   	the cluster. (Though Arquillion use only one instance
	 *   	to run the test case.)
	 * 
	 * The domain name as a target is not a reasonable deployment 
	 * senarion in case of testing. 
	 */
	private String target = GlassFishClient.ADMINSERVER;
	
	/**
	 * Specifies the contextroot for the deployed application.
	 * 
	 * Valid only if the archive is a web module. It is ignored 
	 * for  other  archive  types; defaults deployment to filename 
	 * without extension.
	 */
	private String contextroot = null;
	
	/**
	 * A comma-separated list of library JAR files. Specify the
	 * library  JAR  files by their relative or absolute paths.
	 * Specify relative paths relative to domain-dir/lib/applibs.
	 *  
	 * The libraries are made available to the application in 
	 * the order specified.
	 */
	private String libraries = null;
	
	/**
	 * Optional keyword-value  pairs  that  specify  additional
	 * properties  for the deployment. The available properties
	 * are determined by the implementation  of  the  component
	 * that  is  being deployed or redeployed.
	 */
	private String properties = null;
	
	/**
	 * The packaging archive type of the component that is
	 * being deployed. Only possible values is: osgi
	 * 
	 * The component is packaged as an OSGi Alliance bundle.
	 * The type option is optional. If the component is packaged
	 * as a regular archive, omit this option.
	 */
	private String type = null;
	
	
	public String getAdminHost()
	{
		return adminHost;
	}
	
	public void setAdminHost(String adminHost)
	{
		this.adminHost = adminHost;
	}
	
	public int getAdminPort()
	{
		return adminPort;
	}
	
	public void setAdminPort(int adminPort)
	{
		this.adminPort = adminPort;
	}
	
	public boolean isAdminHttps()
	{
		return adminHttps;
	}
	
	public void setAdminHttps(boolean adminHttps)
	{
		this.adminHttps = adminHttps;
	}
	
	public boolean isRemoteServerHttps()
	{
		return remoteServerHttps;
	}
	
	public void setRemoteServerHttps(boolean remoteServerHttps)
	{
		this.remoteServerHttps = remoteServerHttps;
	}	
	
    public boolean isAuthorisation() {
        return authorisation;
    }

    public void setAuthorisation(boolean authorisation) {
        this.authorisation = authorisation;
    }

    public String getAdminUser() {
        return adminUser;
    }

    public void setAdminUser(String adminUser) {
        this.setAuthorisation(true);
        this.adminUser = adminUser;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

	public String getName() {
		return name; 
	}
	
	public void setName(String name) {
		this.name = name; 
	}
	
	public String getTarget() {
		return target; 
	}
	
	public void setTarget(String target) {
		this.target = target; 
	}
	
	public String getContextroot() {
		return contextroot; 
	}
	
	public void setContextroot(String contextroot) {
		if(!contextroot.startsWith("/"))
		{
			this.contextroot = "/" + contextroot;
		} else {
			this.contextroot = contextroot;
		}
	}
	
	public String getLibraries() {
		return libraries; 
	}
	
	public void setLibraries(String library) {
		this.libraries = library; 
	}
	
	public String getProperties() {
		return properties; 
	}
	
	public void setProperties(String properties) {
		this.properties = properties; 
	}
	
	public String getType() {
		return this.type;
	}
	
	public void setType(String type) {
		this.type = type; 
	}
	
    /**
     * Validates if current configuration is valid, that is if all required
     * properties are set and have correct values
     */
    public void validate() throws ConfigurationException {
       if(isAuthorisation())
       {
          Validate.notNull(getAdminUser(), "remoteServerAdminUser must be specified to use remoteServerAuthorisation");
          Validate.notNull(getAdminPassword(), "remoteServerAdminPassword must be specified to use remoteServerAuthorisation");
       }
    }
}
