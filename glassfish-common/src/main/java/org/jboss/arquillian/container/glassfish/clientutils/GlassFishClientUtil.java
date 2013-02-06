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
package org.jboss.arquillian.container.glassfish.clientutils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.CsrfProtectionFilter;
import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.multipart.FormDataMultiPart;

import org.jboss.arquillian.container.glassfish.CommonGlassFishConfiguration;


public class GlassFishClientUtil {
	
   /**
    * Status for a successful GlassFish exit code deployment.
    */
	public static final String SUCCESS = "SUCCESS";
	
	/**
	 * Status for a GlassFish exit code deployment which ended in warning.
	 */
	public static final String WARNING = "WARNING";
	
	private CommonGlassFishConfiguration configuration;
	
	private String adminBaseUrl;
	
	private static final Logger log = Logger.getLogger(GlassFishClientUtil.class.getName());
	
	public GlassFishClientUtil(CommonGlassFishConfiguration configuration, String adminBaseUrl) 
	{
		this.configuration = configuration;
		this.adminBaseUrl = adminBaseUrl;		
	}
	
	public CommonGlassFishConfiguration getConfiguration()
	{
		return configuration;
	}	
	
	public Map<String, String> getAttributes(String additionalResourceUrl) 
	{
		Map responseMap = GETRequest(additionalResourceUrl);
    	Map<String, String> attributes = new HashMap<String, String>();
		
	    Map resultExtraProperties = (Map) responseMap.get("extraProperties");
	    if (resultExtraProperties != null) {	  
	    	attributes = (Map<String, String>) resultExtraProperties.get("entity");
	    }
		
	    return attributes;
	}
	
    public Map<String, String> getChildResources(String additionalResourceUrl) throws ContainerException 
    {
    	Map responseMap = GETRequest(additionalResourceUrl);
    	Map<String, String> childResources = new HashMap<String, String>();
		
	    Map resultExtraProperties = (Map) responseMap.get("extraProperties");
	    if (resultExtraProperties != null) {	  
	    	childResources = (Map<String, String>) resultExtraProperties.get("childResources");
	    }
		
	    return childResources;
    }
	
    public Map GETRequest(String additionalResourceUrl) 
    {
    	ClientResponse response = prepareClient(additionalResourceUrl).get(ClientResponse.class);
    	Map responseMap = getResponseMap(response);
    	
    	return responseMap;
	}
    
    public List<Map> getInstancesList(String additionalResourceUrl) throws ContainerException 
    {
    	Map responseMap = GETRequest(additionalResourceUrl);
    	List<Map> instancesList = new ArrayList();
    
	    Map resultExtraProperties = (Map) responseMap.get("extraProperties");
	    if (resultExtraProperties != null) {	  
	    	instancesList = (List<Map>) resultExtraProperties.get("instanceList");
	    }

	    return instancesList;
    }    
    
    public Map POSTMultiPartRequest(String additionalResourceUrl, FormDataMultiPart form) 
    {
    	ClientResponse response = prepareClient(additionalResourceUrl).type(MediaType.MULTIPART_FORM_DATA_TYPE)
		.post(ClientResponse.class, form);
    	Map responseMap = getResponseMap(response);
    	
    	return responseMap;
	}
	
    /**
     * Basic REST call preparation, with the additional resource url appended
     *
     * @param additionalResourceUrl url portion past the base to use
     * @return the resource builder to execute
     */
    private WebResource.Builder prepareClient(String additionalResourceUrl) 
    {
    	final Client client = Client.create();
        if (configuration.isAuthorisation()) {
            client.addFilter(new HTTPBasicAuthFilter(
													 configuration.getAdminUser(),
													 configuration.getAdminPassword()));
        }
        client.addFilter(new CsrfProtectionFilter());
        return client.resource(this.adminBaseUrl + additionalResourceUrl).accept(MediaType.APPLICATION_XML_TYPE).header("X-GlassFish-3", "junk");
    }
	
    private Map getResponseMap(ClientResponse response) throws ContainerException 
    {
    	Map responseMap = new HashMap(); String message = "";
        final String xmlDoc = response.getEntity(String.class);
		
    	// Marshalling the XML format response to a java Map
        if (xmlDoc != null && !xmlDoc.isEmpty()) {
        	responseMap = xmlToMap(xmlDoc);
			
        	message = "exit_code: " + responseMap.get("exit_code")
			+ ", message: "+ responseMap.get("message");	    		        	
        } 
		
    	ClientResponse.Status status = ClientResponse.Status.fromStatusCode(response.getStatus());
	    if ( status.getFamily() == javax.ws.rs.core.Response.Status.Family.SUCCESSFUL ) {
			
	       // O.K. the jersey call was successful, what about the GlassFish server response?
           if (responseMap.get("exit_code") == null) {
              throw new GlassFishClientException(message);
           }
           
           else if (WARNING.equals(responseMap.get("exit_code"))) {
              // Warning is not a failure - some warnings in GlassFish are inevitable (i.e. persistence-related: ARQ-606)
              log.warning("Deployment resulted in a warning: " + message);
           }
           
           else if (!SUCCESS.equals(responseMap.get("exit_code"))) {
              // Response is not a warning nor success - it's surely a failure.
              throw new GlassFishClientException(message);
           }
			
	    } else if (status.getReasonPhrase() == "Not Found") {
	    	// the REST resource can not be found (for optional resources it can be O.K.)
	    	message += " [status: " + status.getFamily() + " reason: " + status.getReasonPhrase() +"]";
	        log.warning(message);
	    } else {
	    	message += " [status: " + status.getFamily() + " reason: " + status.getReasonPhrase() +"]";
	    	log.severe(message);
	    	throw new ContainerException(message);
	    }
		
	    return responseMap;
    }
	
    /**
	 * Marshalling a Glassfish Mng API response XML document to a java Map object
	 * 
	 * @param XML document 
	 * @return map containing the XML doc representation in java map format
	 */    	
	public Map xmlToMap(String document) {
		
		if (document == null) { return new HashMap();}
		
        InputStream input = null;
        Map map = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
            input = new ByteArrayInputStream(document.trim().getBytes("UTF-8"));
            XMLStreamReader stream = factory.createXMLStreamReader(input);
            while (stream.hasNext()) {
                int currentEvent = stream.next();
                if ( currentEvent == XMLStreamConstants.START_ELEMENT) {
                    if ("map".equals(stream.getLocalName())) {
                        map = resolveXmlMap(stream);
                    }                	
                }
            }
        } catch (Exception ex) {
        	log.log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } finally {
            try {
                input.close();
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            }
        }
		
		return map;
    }
	
	private Map resolveXmlMap(XMLStreamReader stream) throws XMLStreamException {
		
		boolean endMapFlag = false;
        Map<String, Object> entry = new HashMap<String, Object>();
        String key = null;
        String elementName = null;
		
        while (!endMapFlag) {
			
        	int currentEvent = stream.next();
            if ( currentEvent == XMLStreamConstants.START_ELEMENT) {
				
            	if ("entry".equals(stream.getLocalName())) {
                    key = stream.getAttributeValue(null, "key");
                    String value = stream.getAttributeValue(null, "value");
                    if (value != null) {
                        entry.put(key, value);
                        key = null;
                    }
                } else if ("map".equals(stream.getLocalName())) {
                    Map value = resolveXmlMap(stream);
                    entry.put(key, value);
                } else if ("list".equals(stream.getLocalName())) {
                    List value = resolveXmlList(stream);
                    entry.put(key, value);
                } else {
                    elementName = stream.getLocalName();
                }
            	
			} else if ( currentEvent == XMLStreamConstants.END_ELEMENT ) {
				
				if ("map".equals(stream.getLocalName())) {
					endMapFlag = true;
				}
				elementName = null;
				
			} else {
				
            	String document = stream.getText();
                if (elementName != null) {
                    if ("number".equals(elementName)) {
                        if (document.contains(".")) {
                            entry.put(key, Double.parseDouble(document));
                        } else {
                            entry.put(key, Long.parseLong(document));
                        }
                    } else if ("string".equals(elementName)) {
                        entry.put(key, document);
                    }
                    elementName = null;
                }
            } // end if
            
        } // end while
        return entry;
    }
	
    private List resolveXmlList(XMLStreamReader stream) throws XMLStreamException {
		
        boolean endListFlag = false;
    	List list = new ArrayList();
        String elementName = null;
		
        while (!endListFlag) {
			
        	int currentEvent = stream.next();
            if ( currentEvent == XMLStreamConstants.START_ELEMENT) {
                if ("map".equals(stream.getLocalName())) {
                    list.add(resolveXmlMap(stream));
                } else {
                    elementName = stream.getLocalName();
                }
				
            } else if ( currentEvent == XMLStreamConstants.END_ELEMENT ) {
				
            	if ("list".equals(stream.getLocalName())) {
                    endListFlag = true;
                }
                elementName = null;
				
            } else {
				
            	String document = stream.getText();
                if (elementName != null) {
                    if ("number".equals(elementName)) {
                        if (document.contains(".")) {
                            list.add(Double.parseDouble(document));
                        } else {
                            list.add(Long.parseLong(document));
                        }
                    } else if ("string".equals(elementName)) {
                        list.add(document);
                    }
					elementName = null;
                }
            } // end if
            
        } // end while
        return list;
    }
	
}
