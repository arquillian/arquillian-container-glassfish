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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.jboss.arquillian.container.glassfish.remote_3_1.GlassFishRestConfiguration;

import com.sun.jersey.multipart.FormDataMultiPart;


public class GlassFishClientService implements GlassFishClient {

    private String target = GlassFishClient.ADMINSERVER;

    private String adminBaseUrl;

    GlassFishRestConfiguration configuration;
        
    private ServerStartegy serverInstance = null; 
    	
    private GlassFishClientUtil clientUtil;

	private static final Logger log = Logger.getLogger(GlassFishClientService.class.getName());

    // GlassFish client service constructor
    public GlassFishClientService(GlassFishRestConfiguration configuration){
    	this.configuration = configuration;
    	this.target = configuration.getTarget();

    	final StringBuilder adminUrlBuilder = new StringBuilder();

        if (this.configuration.isAdminHttps()) {
            adminUrlBuilder.append("https://");
        } else {
            adminUrlBuilder.append("http://");
        }

        adminUrlBuilder.append(this.configuration.getAdminHost()).append(":")
                .append(this.configuration.getAdminPort()).append("/management/domain");

        this.adminBaseUrl = adminUrlBuilder.toString();
        
        // Start up the jersey client layer
    	this.clientUtil = new GlassFishClientUtil(configuration, adminBaseUrl);
    }

    /**
	 * Get the node addresses list associated with the target
	 * 
	 * @param none
	 * @return list of node addresses objects
	 */    
    public List<NodeAddress> getNodeAddressList() {

    	Map<String, String> standaloneServers = new HashMap<String, String>();
    	Map<String, String> clusters = new HashMap<String, String>();

		standaloneServers = getServersList();		
		if ( GlassFishClient.ADMINSERVER.equals(getTarget()) ) {

			// The "target" is the Admin Server Instance
			serverInstance = new AdminServer();

		} else if ( standaloneServers.containsKey(getTarget()) ) {

        	// The "target" is an Standalone Server Instance
			serverInstance = new StandaloneServer();

        } else {

        	// The "target" shall be clustered instance(s) 
        	clusters = getClustersList();
            
            if ( clusters != null && clusters.containsKey(getTarget()) ) {

            	// Now we have found the cluster specified by the Target attribute
    			serverInstance = new ClusterServer();

            } else {
            	// The "target" attribute can be a domain or misspelled, but neither can be accepted
            	throw new GlassFishClientException("The Target should be your Admin Server, a Standalone Server Instance or a Cluster's name");
            }
        }

		// Fetch the HOST address & HTTP port info from the DAS server
    	return serverInstance.getNodeAddressList();
	}

    /**
	 * Do deploy an application defined by a multipart form's fileds
	 * to a target server or a cluster of GlassFish 3.1
	 * 
	 * @param name		- name of the appliacation
	 * 		  form		- a form of MediaType.MULTIPART_FORM_DATA_TYPE
	 * @return subComponents - a map of SubComponents of the application
	 */
	// the REST resource path template to retrieve the list of server server instances
    private static final String APPLICATION = "/applications/application";
	private static final String APPLICATION_RESOURCE = "/applications/application/{name}";
    private static final String LIST_SUB_COMPONENTS = "/applications/application/list-sub-components?id={application}";

    public Map<String, String> doDeploy(String name, FormDataMultiPart form) {

    	Map<String, String> SubComponents = new HashMap<String, String>();

		// Deploy the application on the GlassFish server
    	getClientUtil().POSTMultiPartRequest(APPLICATION, form);

		// Fetch the list of SubComponents of the application
		String path = LIST_SUB_COMPONENTS.replace("{application}", name );
		Map subComponentsResponce = getClientUtil().GETRequest(path);	
		SubComponents = (Map<String, String>) subComponentsResponce.get("properties");

    	return SubComponents;
    }

    /**
	 * Undeploy the component 
	 * 
	 * @param name 	- application name
	 * 		  form 	- form that include the target & operation fields
	 * @return resultMap
	 */
	public Map doUndeploy(String name, FormDataMultiPart form) 
	{
	    String path = APPLICATION_RESOURCE.replace("{name}", name);
		return getClientUtil().POSTMultiPartRequest(path, form);
	}

    /**
	 * Get the standalone servers list associated with the DAS
	 * 
	 * @param none
	 * @return map of standalone servers
	 */    
	private static final String STANALONE_SERVER_INSTACES = "/servers/server";

	private Map<String, String> getServersList() {
    	Map<String, String> standaloneServers = getClientUtil().getChildResources(STANALONE_SERVER_INSTACES);
		return standaloneServers;
	}
    
    /**
	 * Get the list of clusters
	 * 
	 * @param none
	 * @return map of clusters
	 */    
	private static final String CLUSTERED_SERVER_INSTACES = "/clusters/cluster";

	private Map<String, String> getClustersList() {
    	Map<String, String> clusters = getClientUtil().getChildResources(CLUSTERED_SERVER_INSTACES);
		return clusters;
	}
    
    /**
	 * Get the contextroot associated with the application 
	 * 
	 * @param name 			- application name
	 * @return contextRoot
	 */
	public String getApplicationConterxtRoot(String name) 
	{
	    String path = APPLICATION_RESOURCE.replace("{name}", name);
		return getClientUtil().getAttributes(path).get("contextRoot");
	}

    /**
	 * Get the list of server instances of the cluster
	 * 
	 * @param target
	 * @return server instances map
	 */
	// the REST resource path template to retrieve the list of server server instances
	private static final String MEMBER_SERVERS_RESOURCE = "/clusters/cluster/{target}/server-ref";

	protected Map<String, String> getServerInstances(String target) 
	{
		String path = MEMBER_SERVERS_RESOURCE.replace("{target}", target );
		Map<String, String> serverInstances = getClientUtil().getChildResources(path);
	    return serverInstances;
	}			
	
	/**
	 * Get the serverAttributes map of a server
	 * 
	 * @param name of the server
	 * @return serverAttributes map
	 * 	nodeRef:		- reference to the node object
	 * 	configRef:		- reference to the server's configuration object
	 *  ...
	 */
	// the REST resource path template for server attributes object
	private static final String SERVER_RESOURCE = "/servers/server/{server}";

	protected Map<String, String> getServerAttributes(String server) 
	{
        String path = SERVER_RESOURCE.replace("{server}", server);
        return getClientUtil().getAttributes(path);
	}

	/**
	 * Get the HOST address (IP or name) of node associated with the server
	 * 
	 * @param node name
	 * @return nodeAttributes map 
	 */
	// the REST resource path template for the particular server object
	private static final String NODE_RESOURCE = "/nodes/node/{node}";

	protected String getHostAddress(Map<String, String> serverAttributes) 
	{
        String path = NODE_RESOURCE.replace("{node}", serverAttributes.get("nodeRef"));
        return getClientUtil().getAttributes(path).get("nodeHost");
	}
	/**
	 * Get the http/https port number of the Admin Server
	 * 
	 * @param must be "server"
	 * 		  secure 	- http port = false, https port = true	
	 * @return http/https port number 
	 */	
	// the REST resource path template for the Admin Servers's http-listener objects
	private static final String HTTP_LISTENER_A = "/configs/config/{config}/network-config/network-listeners/network-listener/{http-listener}";

	protected int getAdminServerHttpPort(Map<String, String> serverAttributes, boolean secure) 
	{
		String listenerpath = HTTP_LISTENER_A.replace("{config}", serverAttributes.get("configRef") );
		String httpListener = (!secure) ? "http-listener-1" : "http-listener-2";
		Map<String, String> listener = getClientUtil().getAttributes( 
	    		listenerpath.replace("{http-listener}", httpListener) ); 
	    return Integer.parseInt(listener.get("port"));
	}	
		
	/**
	 * Get the port number of the node associated with the server
	 * 
	 * @param serverAttributes (which contains the configRef)
	 * 		  secure: false - http port number, true - https port number
	 * @return http/https port number 
	 */
	// the REST resource path template for the http-listener objects
	private static final String HTTP_LISTENER = "/configs/config/{config}/system-property/{http-listener}";

	protected int getServerHttpPort(Map<String, String> serverAttributes, boolean secure) 
	{
		String listenerpath = HTTP_LISTENER.replace("{config}", serverAttributes.get("configRef") );
		String httpListener = (!secure) ? "HTTP_LISTENER_PORT" : "HTTP_SSL_LISTENER_PORT";		
		Map<String, String> listener = getClientUtil().getAttributes( 
	    		listenerpath.replace("{http-listener}", httpListener) ); 

		return Integer.parseInt(listener.get("value"));
	}		

	/**
	 * Get the http/https port number of the server instance
	 * 
	 * The attribute is optional, It is generated by the Glassfish server
	 * if we have more then one server instance on the same node.
	 * 
	 * @param server name
	 * 		  secure: false - http port number, true - https port number	
	 * @return http/https port number. If the attribute is not defined, gives back the default port 
	 */	
	// the REST resource path template for the Servers instance http-listener object	
	private static final String HTTP_LISTENER_INS = "/servers/server/{server}/system-property/{http-listener}";

	protected int getServerInstanceHttpPort(String server, int default_port, boolean secure) 
	{
		String listenerpath = HTTP_LISTENER_INS.replace("{server}", server);
		String httpListener = (!secure) ? "HTTP_LISTENER_PORT" : "HTTP_SSL_LISTENER_PORT";
		
		Map<String, String> listener = getClientUtil().getAttributes(
	    		listenerpath.replace("{http-listener}", httpListener) );

		return ( listener.get("value") != null) ? Integer.parseInt(listener.get("value")) : default_port;
	}			

    private GlassFishRestConfiguration getConfiguration(){
		return configuration;    	
    }

    private String getTarget(){
		return target;    	
    }

    private void setTarget(String target){
		this.target = target;
    }

    private GlassFishClientUtil getClientUtil(){
		return clientUtil;    	
    }
        
	/**
	 * The GoF Strategy pattern is used to implement specific algorithm 
	 * by server type (Admin, Standalone or Clustered server)  
	 * 
	 * The attribute is optional, It is generated by the Glassfish server
	 * if we have more then one server instance on the same node or 
	 * explicitly defined by the create command parameter.
	 * 
	 * @param server name
	 * 		  secure: false - http port number, true - https port number	
	 * @return http/https port number. If the attribute is not defined, gives back the default port 
	 */		
	abstract class ServerStartegy {

	    /**
		 * Address list of the node(s) on GlassFish Appserver
		 */
	    private List<NodeAddress> nodes = new ArrayList<NodeAddress>();
	    
	    protected GlassFishClientService glassFishClient;
	    
	    protected ServerStartegy(){
	    }

	    protected List<NodeAddress> getNodes() {
			return nodes;
		}

	    protected void setNodes(List<NodeAddress> nodes) {
	    	this.nodes = nodes;
		}

	    protected void addNode(NodeAddress node) {
	    	nodes.add(node);
		}

	    public GlassFishClientService getGlassFishClient(){
			return glassFishClient;    	
	    }

		/**
		 * Get the the node address list associated with the target
		 * 
		 * @param none
		 * @return list of node address objects
		 */    
	    protected abstract List<NodeAddress> getNodeAddressList();
	    	
	}

	class AdminServer extends ServerStartegy {

		public AdminServer() {
			super();
		}

		@Override
		public List<NodeAddress> getNodeAddressList() 
		{        
	        String nodeHost = "localhost"; // default host
	        setNodes( new ArrayList<NodeAddress>() );

			Map<String, String> serverAttributes = getServerAttributes(GlassFishClient.ADMINSERVER);

			// Get the host address of the Admin Server
			nodeHost =  (String) getConfiguration().getAdminHost();        	

			// Get the port numbers
			int httpPort  = getAdminServerHttpPort(serverAttributes, false );            
	        int httpsPort = getAdminServerHttpPort(serverAttributes, true);

	        addNode(new NodeAddress(nodeHost, httpPort, httpsPort));

	        return getNodes();	        
		}		
	}

	class StandaloneServer extends ServerStartegy {

		public StandaloneServer() {
			super();
		}

		
		@Override
		public List<NodeAddress> getNodeAddressList()
		{
	        String nodeHost = "localhost"; // default host
	        setNodes( new ArrayList<NodeAddress>() );

			Map<String, String> serverAttributes = getServerAttributes(getTarget());

			// Get the host address of the Admin Server
			nodeHost = getHostAddress(serverAttributes);        	

			// Get the port numbers
	        int httpPort_def = getServerHttpPort(serverAttributes, false);
	        int httpsPort_def = getServerHttpPort(serverAttributes, true);

	        // Check whether we have instance http-listeners to override the default ports
	        int httpPort = getServerInstanceHttpPort(getTarget(), httpPort_def, false);
	        int httpsPort = getServerInstanceHttpPort(getTarget(), httpsPort_def, true);
	            
	        addNode(new NodeAddress(nodeHost, httpPort, httpsPort));
	    	return getNodes();
		}
	}

	class ClusterServer extends ServerStartegy {

		public ClusterServer() {
			super();
		}

		@Override
		public List<NodeAddress> getNodeAddressList() 
		{
			String nodeHost = "localhost"; // default host
			setNodes( new ArrayList<NodeAddress>() );
	        Map<String, String> serverAttributes;
	        
	        // Fetch the list of server instances of the cluster
	        Map<String, String>  serverInstances = getServerInstances(getTarget());
	        
	        for (Map.Entry serverInstance : serverInstances.entrySet()) 
	        {
	            String serverName = serverInstance.getKey().toString();
	            System.out.println("Server Name: " + serverName);        	

	            serverAttributes = getServerAttributes(serverName);

	            nodeHost = getHostAddress(serverAttributes);
	                       
	            // Get the port numbers
	            int httpPort_def = getServerHttpPort(serverAttributes, false);
	            int httpsPort_def = getServerHttpPort(serverAttributes, true);

	            // Check whether we have instance http-listeners to override the default ports
	            int httpPort = getServerInstanceHttpPort(serverName, httpPort_def, false);
	            int httpsPort = getServerInstanceHttpPort(serverName, httpsPort_def, true);

	            addNode(new NodeAddress(nodeHost, httpPort, httpsPort));
	        }

	        return getNodes();
		}	
	}

}
