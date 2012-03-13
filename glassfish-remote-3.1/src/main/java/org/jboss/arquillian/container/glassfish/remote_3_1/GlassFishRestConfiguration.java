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

import org.jboss.arquillian.container.glassfish.CommonGlassFishConfiguration;

public class GlassFishRestConfiguration extends CommonGlassFishConfiguration
{
	
	/**
	 * @deprecated
	 * Http port for application urls.
	 * Used to build the URL for the REST request.
	 */
	private int remoteServerHttpPort = 8080;
	
	/**
	 * @deprecated
	 */
	public void setRemoteServerAddress(String adminHost)
	{
		this.adminHost = adminHost;
	}
	
	/**
	 * @deprecated
	 */
	public void setRemoteServerAdminPort(int adminPort)
	{
		this.adminPort = adminPort;
	}
	
	/**
	 * @deprecated
	 */
	public void setRemoteServerAdminHttps(boolean adminHttps)
	{
		this.adminHttps = adminHttps;
	}
	
	/**
	 * @deprecated
	 */
    public int getRemoteServerHttpPort() {
    	return remoteServerHttpPort;
    }
	
	/**
	 * @deprecated
	 */
    public void setRemoteServerHttpPort(int remoteServerHttpPort) {
    	this.remoteServerHttpPort = remoteServerHttpPort;
    }

}
