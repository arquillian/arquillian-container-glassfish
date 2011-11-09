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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.arquillian.container.glassfish.remote_3_1.GlassFishRestConfiguration;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.multipart.FormDataMultiPart;


public class GlassFishClientService implements GlassFishClient {
	
	private static final String WEBMODULE = "WebModule";	
	private static final String SERVLET = "Servlet";
	private static final String RUNNING_STATUS = "RUNNING";	
	
	private String target = GlassFishClient.ADMINSERVER;
	
    private String adminBaseUrl;
	
    private String DASUrl;

    GlassFishRestConfiguration configuration;
	
    private ServerStartegy serverInstance = null; 
	
    private GlassFishClientUtil clientUtil;
    
    private NodeAddress nodeAddress = null;
	
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
        	.append(this.configuration.getAdminPort());
        DASUrl = adminUrlBuilder.toString();
        adminUrlBuilder.append("/management/domain");
        this.adminBaseUrl = adminUrlBuilder.toString();
        
        // Start up the jersey client layer
    	this.clientUtil = new GlassFishClientUtil(configuration, adminBaseUrl);
    }
	
    /**
	 * Start-up the server
	 * 
	 *  -   Get the node addresses list associated with the target
	 *  -   Pull the server instances status form mgm API
	 *  -   In case of cluster tries to fund an instance which has
	 *		RUNNING status 
	 * 
	 * @param none
	 * @return none
	 */    
    public void startUp() throws GlassFishClientException {
		
    	Map<String, String> standaloneServers = new HashMap<String, String>();
    	Map<String, String> clusters = new HashMap<String, String>();
		String message;
		
		try {
			standaloneServers = getServersList();		
		} catch (ClientHandlerException ch) {        	
	    	message = "Could not connect to DAS on: " + getDASUrl() + " | " 
	    		+ ch.getCause().getMessage();
        	throw new GlassFishClientException(message);
	    }

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
            	message = "The target property: " + getTarget() + " is not a valid target";
            	throw new GlassFishClientException(message);
            }
        }
		
		// Fetch the HOST address & HTTP port info from the DAS server
		List<NodeAddress> nodeAddressList = (List<NodeAddress>) serverInstance.getNodeAddressList();

		if ( GlassFishClient.ADMINSERVER.equals(configuration.getTarget()) ) {
			// Admin Server must running, otherwise we can not be here
			this.nodeAddress = nodeAddressList.get(0); 
		} else {
			// Returns the nodeAddress if the target instance status is RUNNING
			// In case of cluster, returns the first RUNNING instance (if any) from the list
			this.nodeAddress = runningInstanceFilter(nodeAddressList);
		}
	}
	
    /**
	 * Filtering on the status of the instances
	 *  -	If the standalone server instance status is RUNNING, returns the nodeAddress, 
	 *  	but throws an exception otherwise.
	 *  -	In case of cluster, returns the first RUNNING instance from the list,
	 *  	but throws an exception if can not find any.
	 * 
	 * @param nodeAddressList 
	 * @return nodeAddress - if any has RUNNING status
	 */
	// the REST resource path template to retrieve the list of server instances
    private static final String INSTACE_LIST = "/list-instances";

    private NodeAddress runningInstanceFilter(List<NodeAddress> nodeAddressList) 
	{
	    List<Map> instanceList = getClientUtil().getInstancesList(INSTACE_LIST);
	    
	    String instanceStatus = null;
	    for (Map instance : instanceList) {
	    	for (NodeAddress node : nodeAddressList) {
		    	if ( instance.get("name").equals(node.getServerName()) ){		    		
		    		instanceStatus = (String) instance.get("status");
		    		if (RUNNING_STATUS.equals(instanceStatus) ) {		    			
		    			return node;
		    		}
		    	}
	    	}
	    }
		
    	String message;
	    if (nodeAddressList.size() == 1) { 
	    	message = "The " + nodeAddressList.get(0).getServerName() + " server-instance status is: " 
	    		+ instanceStatus;
	    	throw new GlassFishClientException(message);
	    } else { 
        	message = "Could not fund any instance with RUNNING status in cluster: " + getTarget();
	    	throw new GlassFishClientException(message);	    	
	    }
	}    
    
    /**
	 * Do deploy an application defined by a multipart form's fileds
	 * to a target server or a cluster of GlassFish 3.1
	 * 
	 * @param name		- name of the appliacation
	 * 		  form		- a form of MediaType.MULTIPART_FORM_DATA_TYPE
	 * @return subComponents - a map of SubComponents of the application
	 */
	// the REST resource path template to retrieve the list of server instances
    private static final String APPLICATION = "/applications/application";
	private static final String APPLICATION_RESOURCE = "/applications/application/{name}";
    private static final String LIST_SUB_COMPONENTS = "/applications/application/list-sub-components?id={application}&type=servlets";
	
    public HTTPContext doDeploy(String name, FormDataMultiPart form) {
		
    	Map<String, String> SubComponents = new HashMap<String, String>();
		
		// Deploy the application on the GlassFish server
    	getClientUtil().POSTMultiPartRequest(APPLICATION, form);
		
		// Fetch the list of SubComponents of the application
		String path = LIST_SUB_COMPONENTS.replace("{application}", name );
		
		Map subComponentsResponce = getClientUtil().GETRequest(path);	
		Map<String, String> subComponents = (Map<String, String>) subComponentsResponce.get("properties");
		
        // Build up the HTTPContext object using the nodeAddress information
        int port = ( !this.configuration.isServerHttps() ) ? nodeAddress.getHttpPort() : nodeAddress.getHttpsPort();
        HTTPContext httpContext = new HTTPContext( nodeAddress.getHost(), port );
		
        // Add the servlets to the HTTPContext
        String componentName;
        String contextRoot = getApplicationContextRoot(name);
		
        for (Map.Entry subComponent : subComponents.entrySet()) 
        {
        	componentName = subComponent.getKey().toString();
        	if ( WEBMODULE.equals(subComponent.getValue()) ) {
				
        		List<Map> children = (List<Map>) subComponentsResponce.get("children");
        		// Override the application contextRoot by the webmodul's contextRoot
        		contextRoot = resolveWebModuleContextRoot(componentName, children);
	    		resolveWebModuleSubComponents(name, componentName, contextRoot, httpContext);
				
        	} else if ( SERVLET.equals(subComponent.getValue()) ) {
				
        		httpContext.add(new Servlet(componentName, contextRoot));
        	}
        }
		
    	return httpContext;
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
	 * @return contextRoot attribute of the application
	 */
	private String getApplicationContextRoot(String name) 
	{
		String path = APPLICATION_RESOURCE.replace("{name}", name);
	    Map<String, String> applicationAttributes = getClientUtil().getAttributes(path);
		
	    // pull the contextRoot from the applicasion's attributes
	    String	contextRoot = ((Object) applicationAttributes.get("contextRoot")).toString();
	    return contextRoot;
	}
	
	
	private String resolveWebModuleContextRoot(String componentName, List<Map> modules) 
	{
		String contextRoot = null;
		for (Map module : modules) 
        {	
			Map<String, String> moduleProperties = (Map<String, String>) module.get("properties");
			if ( moduleProperties != null && !moduleProperties.isEmpty() ) {
				String moduleInfo = moduleProperties.get("moduleInfo");
				if (moduleInfo.startsWith(componentName)) {
					// Get the webmodul's contextRoot 
					contextRoot = moduleInfo.substring( moduleInfo.indexOf("/") );
				}
			} else {
				throw new GlassFishClientException("Cuold not resolve the web-module contextRoot");
			}
        }
		return contextRoot;		
	}
	
    /**
	 * Lookup the servlets of WebModule & putt them to the httpContext associated with the application 
	 * 
	 * @param name 			- application name
	 * @param module 		- webmodule name
	 * @param context 		- contextRoot of the web-module
	 * @param httpContext	- httpContext to be updated
	 */
	// the REST resource path template to retrieve the servlets
	private static final String WEBMODUL_RESOURCE = "/applications/application/list-sub-components?appname={application}&id={module}&type=servlets";
	
	private void resolveWebModuleSubComponents(String name, String module, String context, HTTPContext httpContext) 
	{
		// Fetch the list of SubComponents of the application
		String applicationPath = WEBMODUL_RESOURCE.replace("{application}", name );
		String modulePath = applicationPath.replace("{module}", module );
		
		Map subComponentsResponce = getClientUtil().GETRequest(modulePath);	
		Map<String, String> subComponents = (Map<String, String>) subComponentsResponce.get("properties");
		
		String componentName;
		for (Map.Entry subComponent : subComponents.entrySet()) 
        {
        	componentName = subComponent.getKey().toString();
    		httpContext.add(new Servlet(componentName, context));
        }
	}	
	
	/**
	 * Get the list of server instances of the cluster
	 * 
	 * @param target
	 * @return server instances map
	 */
	// the REST resource path template to retrieve the list of server instances
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
     * Get the clusterAttributes map of a cluster
     * 
     * @param name of the cluster
     * @return serverAttributes map  
     *  configRef:      - reference to the cluster's configuration object
     *  ...
     */
    // the REST resource path template for cluster attributes object
    private static final String CLUSTER_RESOURCE = "/clusters/cluster/{cluster}";
    
    protected Map<String, String> getClusterAttributes(String cluster) 
    {
        String path = CLUSTER_RESOURCE.replace("{cluster}", cluster);
        return getClientUtil().getAttributes(path);
    }
    
	/**
	 * Get the HOST address (IP or name) of the node associated with the server
	 * 
	 * @param node name
	 * @return nodeAttributes map 
	 */
	// the REST resource path template for the particular server object
	private static final String NODE_RESOURCE = "/nodes/node/{node}";
	
	protected String getHostAddress(Map<String, String> serverAttributes) 
	{
        String path = NODE_RESOURCE.replace("{node}", serverAttributes.get("nodeRef"));
        String nodeHost = getClientUtil().getAttributes(path).get("nodeHost");
        
        // If the host address returned by DAS was "localhost", it could be "localhost" in the context of DAS, but not Arquillian.
        // This would result in Arquillian connecting to localhost, even though the DAS (and it's localhost) is on a separate machine.
        // This is the case when the Glassfish installer or asadmin creates a localhost node with node-host set to "localhost" instead of a FQDN.
        // Variants of "localhost" like "127.0.0.1" or ::1 are not addressed, as the installer/asadmin does not appear to set the node-host to such values.
        // In such a scenario, the adminHost (DAS) known to Arquillian (from arquillian.xml) will be used as the nodeHost.
        // All conditions are addressed:
        // 1. If adminHost is "localhost", and the node-host registered in DAS is "localhost", then the node-host is set to "localhost". No harm done.
        // 2. If adminHost is not "localhost", and the node-host registered in DAS is "localhost", then the node-host value will be set to the same as adminHost.
        // Prevents Arquillian from connecting to a wrong address (localhost) to run the tests via ArquillianTestRunner.
        // 3. If adminHost is "localhost" and the node-host registered in DAS is not "localhost", then the value from DAS will be used.
        // 4. If adminHost is not "localhost" and the node-host registered in DAS is not "localhost", then the value from DAS will be used.
        if (nodeHost.equals("localhost"))
        {
           nodeHost = configuration.getAdminHost();
        }
        return nodeHost;
	}

	private static final String SYSTEM_PROPERTY = "/configs/config/{config}/system-property/{system-property}";

	/**
	 * Get the port number defined as a system property in a configuration.
	 * 
	 * @param attributes
	 *            The attributes which references the configuration (server or
	 *            cluster configuration)
	 * @param propertyName
	 *            The name of the system property to resolve
	 * @return The port number stored in the system property
	 */
	private int getSystemProperty(Map<String, String> attributes, String propertyName)
	{
		String propertyPath = SYSTEM_PROPERTY.replace("{config}", attributes.get("configRef"));
		Map<String, String> listener = getClientUtil().getAttributes(
				propertyPath.replace("{system-property}", propertyName));
		return Integer.parseInt(listener.get("value"));
	}

	private static final String SERVER_PROPERTY = "/servers/server/{server}/system-property/{system-property}";

	/**
	 * Get the port number defined as a system property in a configuration, and
	 * overridden at the level of the server instance.
	 * 
	 * @param server
	 *            The name of the server instance
	 * @param propertyName
	 *            The name of the system property to resolve
	 * @param defaultValue
	 *            The default port number to be used, in case the system
	 *            property is not overridden
	 * @return The port number stored in the system property
	 */
	private int getServerSystemProperty(String server, String propertyName, int defaultValue)
	{
		String listenerpath = SERVER_PROPERTY.replace("{server}", server);
		Map<String, String> listener = getClientUtil().getAttributes(
				listenerpath.replace("{system-property}", propertyName));

		return (listener.get("value") != null) ? Integer.parseInt(listener.get("value")) : defaultValue;
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

	private static final String VIRTUAL_SERVERS = "/configs/config/{config}/http-service/list-virtual-servers?target={target}";

	/**
	 * Obtains the list of virtual servers associated with the deployment
	 * target. This method omits '__asadmin' in the result, as no deployments
	 * can target this virtual server.
	 * 
	 * @param attributes
	 *            The attributes which references the configuration (server or
	 *            cluster configuration)
	 * @return A list of virtual server names that have been found in the
	 *         server/cluster configuration
	 */
	private List<String> getVirtualServers(Map<String, String> attributes)
	{
		String virtualServerPath = VIRTUAL_SERVERS.replace("{config}", attributes.get("configRef")).replace("{target}",
				attributes.get("name"));
		Map virtualServersResponse = getClientUtil().GETRequest(virtualServerPath);
		List<Map> virtualServers = (List<Map>) virtualServersResponse.get("children");
		List<String> virtualServerNames = new ArrayList<String>();
		for (Map virtualServer : virtualServers)
		{
			String virtualServerName = (String) virtualServer.get("message");
			if (!virtualServerName.equals("__asadmin"))
			{
				virtualServerNames.add(virtualServerName);
			}
		}
		return virtualServerNames;
	}

	private static final String VIRTUAL_SERVER = "/configs/config/{config}/http-service/virtual-server/{virtualServer}";

	/**
	 * Obtains the list of all network listeners associated with the list of
	 * provided virtual servers.
	 * 
	 * @param attributes
	 *            The attributes which references the configuration (server or
	 *            cluster configuration)
	 * @param virtualServers
	 *            The {@link List} of all virtual servers whose the listeners
	 *            must be retrieved
	 * @return The list of all listener names associated with the provided list
	 *         of virtual servers
	 */
	private List<String> getNetworkListeners(Map<String, String> attributes, List<String> virtualServers)
	{
		List<String> networkListeners = new ArrayList<String>();
		for (String virtualServer : virtualServers)
		{
			String virtualServerPath = VIRTUAL_SERVER.replace("{config}", attributes.get("configRef")).replace(
					"{virtualServer}", virtualServer);
			Map<String, String> virtualServerAttributes = getClientUtil().getAttributes(virtualServerPath);
			String listenerList = virtualServerAttributes.get("networkListeners");
			String[] listeners = listenerList.split(",");
			for (String listener : listeners)
			{
				networkListeners.add(listener.trim());
			}
		}
		return networkListeners;
	}

	private static final String LISTENER = "/configs/config/{config}/network-config/network-listeners/network-listener/{listener}";

	/**
	 * Obtains the value of a HTTP/HTTPS network listener, as stored in the
	 * GlassFish configuration.
	 * 
	 * @param attributes
	 *            The attributes which references the configuration (server or
	 *            cluster configuration)
	 * @param networkListeners
	 *            The {@link List} of network listeners among which one will be
	 *            chosen
	 * @param secure
	 *            Should a listener with a secure protocol be chosen?
	 * @return The value of the port number stored in the chosen listener
	 *         configuration. This may be parseable as a number, but not
	 *         necessarily so. Sometimes a system property might be returned.
	 */
	private String getActiveHttpPort(Map<String, String> attributes, List<String> networkListeners, boolean secure)
	{
		for (String networkListener : networkListeners)
		{
			String listenerPath = LISTENER.replace("{config}", attributes.get("configRef")).replace("{listener}",
					networkListener);
			Map<String, String> listenerAttributes = getClientUtil().getAttributes(listenerPath);
			boolean enabled = Boolean.parseBoolean(listenerAttributes.get("enabled"));
			if (!enabled)
			{
				continue;
			}
			String port = listenerAttributes.get("port");
			String protocolName = listenerAttributes.get("protocol");
			boolean secureProtocol = isSecureProtocol(attributes, protocolName);
			if (secure && secureProtocol)
			{
				return port;
			}
			else if (!secure && !secureProtocol)
			{
				return port;
			}
		}
		return null;
	}

	private static final String PROTOCOL = "/configs/config/{config}/network-config/protocols/protocol/{protocol}";

	/**
	 * Determines whether the protocol associated with the listener is a secure
	 * protocol or not.
	 * 
	 * @param attributes
	 *            The attributes which references the configuration (server or
	 *            cluster configuration)
	 * @param protocolName
	 *            The name of the protocol
	 * @return A boolean value indicating whether a protocol is secure or not
	 */
	private boolean isSecureProtocol(Map<String, String> attributes, String protocolName)
	{
		String protocolPath = PROTOCOL.replace("{config}", attributes.get("configRef")).replace("{protocol}",
				protocolName);
		Map<String, String> protocolAttributes = getClientUtil().getAttributes(protocolPath);
		boolean isSecure = Boolean.parseBoolean(protocolAttributes.get("securityEnabled"));
		return isSecure;
	}

	private static final String SYSTEM_PROPERTY_REGEX = "\\$\\{(.*)\\}";

	/**
	 * Get the port number of a network listener. Firstly, this method parses
	 * the provided String as a number. If this fails, the provided String is
	 * parsed as a system property stored in the format -
	 * <blockquote>${systemProperty}</blockquote>. The value of the referenced
	 * system property is then read from the GlassFish configuration.
	 * 
	 * @param attributes
	 *            The attributes which references the configuration (server or
	 *            cluster configuration)
	 * @param serverName
	 *            The name of the server instance
	 * @param portNum
	 *            The port number or a system property that stores the port
	 *            number
	 * @return The port number as stored in the network listener configuration
	 *         or in the system property
	 */
	private int getPortValue(Map<String, String> attributes, String serverName, String portNum)
	{
		int portValue = -1;
		try
		{
			portValue = Integer.parseInt(portNum);
		}
		catch (NumberFormatException formatEx)
		{
			Pattern propertyRegex = Pattern.compile(SYSTEM_PROPERTY_REGEX);
			Matcher matcher = propertyRegex.matcher(portNum);
			if (matcher.find())
			{
				String propertyName = matcher.group(1);
				portValue = getSystemProperty(attributes, propertyName);
				portValue = getServerSystemProperty(serverName, propertyName, portValue);
			}
		}
		return portValue;
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
	 * Get the URL of the DAS server
	 * 
	 * @param none
	 * @return URL
	 */    
    private String getDASUrl(){
		return DASUrl;    	
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
		
	    protected GlassFishClientService getGlassFishClient(){
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
			
			// Get the virtual servers and the associated network listeners for the DAS.
			// We'll not verify if the listeners are bound to private IP
			// addresses, or reachable from the Arquillian test client.
			List<String> virtualServers = getVirtualServers(serverAttributes);
			List<String> networkListeners = getNetworkListeners(serverAttributes, virtualServers);
			String httpPortNum = getActiveHttpPort(serverAttributes, networkListeners, false);
			String httpsPortNum = getActiveHttpPort(serverAttributes, networkListeners, true);

			int httpPort = getPortValue(serverAttributes, getTarget(), httpPortNum);
			// A HTTPS listener might not exist in the DAS config.
			// And Arquillian requires a HTTP port for now.
			// So, we'll parse the HTTPS config conditionally.
			int httpsPort = -1;
			if (httpsPortNum != null && !httpsPortNum.equals(""))
			{
				httpsPort = getPortValue(serverAttributes, getTarget(), httpsPortNum);
			}
			
	        addNode(new NodeAddress(GlassFishClient.ADMINSERVER, nodeHost, httpPort, httpsPort));
			
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
			
			// Get the virtual servers and the associated network listeners for the DAS.
			// We'll not verify if the listeners are bound to private IP addresses,
			// or reachable from the Arquillian test client.
			List<String> virtualServers = getVirtualServers(serverAttributes);
			List<String> networkListeners = getNetworkListeners(serverAttributes, virtualServers);
			String httpPortNum = getActiveHttpPort(serverAttributes, networkListeners, false);
			String httpsPortNum = getActiveHttpPort(serverAttributes, networkListeners, true);

			int httpPort = getPortValue(serverAttributes, getTarget(), httpPortNum);
			// A HTTPS listener might not exist in the instance config.
			// And Arquillian requires a HTTP port for now.
			// So, we'll parse the HTTPS config conditionally.
			int httpsPort = -1;
			if (httpsPortNum != null && !httpsPortNum.equals(""))
			{
				httpsPort = getPortValue(serverAttributes, getTarget(), httpsPortNum);
			}
			
	        addNode(new NodeAddress(getTarget(), nodeHost, httpPort, httpsPort));
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
	        
	        // Get the REST resource for the cluster attributes, to reference the config-ref later 
	        Map<String, String> clusterAttributes = getClusterAttributes(getTarget());
	        // Fetch the list of server instances of the cluster
	        Map<String, String>  serverInstances = getServerInstances(getTarget());
	        
			// Get the virtual servers and the associated network listeners for the cluster.
			// GlassFish clusters are homogeneous and the virtual servers and network listeners
			// will be present on every cluster instance; only port numbers for the listener may vary.
			// We'll not verify if the listeners are bound to private IP addresses,
			// or reachable from the Arquillian test client.
			List<String> virtualServers = getVirtualServers(clusterAttributes);
			List<String> networkListeners = getNetworkListeners(clusterAttributes, virtualServers);

			// Obtain a HTTP and a HTTPS port that have been enabled on the
			// virtual server.
			String httpPortNum = getActiveHttpPort(clusterAttributes, networkListeners, false);
			String httpsPortNum = getActiveHttpPort(clusterAttributes, networkListeners, true);
            
	        for (Map.Entry serverInstance : serverInstances.entrySet()) 
	        {
	            String serverName = serverInstance.getKey().toString();
				
	            serverAttributes = getServerAttributes(serverName);				
	            nodeHost = getHostAddress(serverAttributes);
				
				int httpPort = getPortValue(clusterAttributes, serverName, httpPortNum);
				// A HTTPS listener might not exist in the cluster config.
				// And Arquillian requires a HTTP port for now.
				// So, we'll parse the HTTPS config conditionally.
				int httpsPort = -1;
				if (httpsPortNum != null && !httpsPortNum.equals(""))
				{
					httpsPort = getPortValue(clusterAttributes, serverName, httpsPortNum);
				}
				
	            addNode(new NodeAddress(serverName, nodeHost, httpPort, httpsPort));
	        }
			
	        return getNodes();
		}	
	}
	
}
