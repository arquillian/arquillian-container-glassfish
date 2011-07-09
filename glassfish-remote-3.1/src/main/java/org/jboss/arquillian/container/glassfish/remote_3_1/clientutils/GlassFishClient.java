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
 * @author Z.Paulovics
 */
package org.jboss.arquillian.container.glassfish.remote_3_1.clientutils;

import java.util.List;
import java.util.Map;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import com.sun.jersey.multipart.FormDataMultiPart;

public interface GlassFishClient {

    /**
     * Admin Server key for the REST response.
     */
	public static final String ADMINSERVER = "server";

	/**
	 * Get the the list node addresses list associated with the target
	 * 
	 * @param none
	 * @return list of node addresses objects
	 */    
    public List<NodeAddress> getNodeAddressList();
    	
    /**
	 * Do deploy an application defined by a multipart form's data
	 * to the target server or cluster of GlassFish 3.1 appserver
	 * 
	 * @param name		- name of the appliacation
	 * 		  form		- a form of MediaType.MULTIPART_FORM_DATA_TYPE
	 * @return subComponents - a map of SubComponents of the application
	 */
    public Map<String, String> doDeploy(String name, FormDataMultiPart form) throws DeploymentException; 

    /**
	 * Get the the context root associated by the application 
	 * 
	 * @param name 			- application name
	 * @return responseMap
	 */
	public Map doUndeploy(String name, FormDataMultiPart form); 
    
    /**
	 * Get the the context root associated by the application 
	 * 
	 * @param name 			- application name
	 * @return contextRoot
	 */
	public String getApplicationConterxtRoot(String name);
		
}
