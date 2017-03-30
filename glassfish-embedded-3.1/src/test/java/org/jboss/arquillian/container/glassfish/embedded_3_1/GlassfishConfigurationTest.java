package org.jboss.arquillian.container.glassfish.embedded_3_1;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class GlassfishConfigurationTest {

    @Test
    public void testValidInstallRoot() throws Exception {
        String installRoot = "./src/test/resources/gfconfigs/installRoot";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setInstallRoot(installRoot);
        config.validate();
    }

    @Test(expected = RuntimeException.class)
    public void testInstallRootWithoutDirectories() throws Exception {
        String installRoot = "./src/test/resources/gfconfigs/";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setInstallRoot(installRoot);
        config.validate();
    }

    @Test
    public void testValidInstanceRoot() throws Exception {
        String instanceRoot = "./src/test/resources/gfconfigs/instanceRoot";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setInstanceRoot(instanceRoot);
        config.validate();
    }

    @Test(expected = RuntimeException.class)
    public void testInstanceRootWithoutDirectories() throws Exception {
        String instanceRoot = "./src/test/resources/gfconfigs/emptydir";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setInstanceRoot(instanceRoot);
        config.validate();
    }

    @Test
    public void testInstanceRootWithoutConfigXml() throws Exception {
        String instanceRoot = "./src/test/resources/gfconfigs/instanceRootNoConfigxml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setInstanceRoot(instanceRoot);
        config.validate();
    }

    @Test
    public void testValidConfigXmlPath() throws Exception {
        String configXml = "./src/test/resources/gfconfigs/configxml/test-domain.xml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setConfigurationXml(configXml);
        config.validate();

        assertTrue(config.getConfigurationXml().startsWith("file:"));
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidConfigXmlPath() throws Exception {
        String configXml = "./src/test/resources/gfconfigs/emptydir/test-domain.xml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setConfigurationXml(configXml);
        config.validate();
    }

    @Test
    public void testInstanceRootAndConfigXml() throws Exception {
        String instanceRoot = "./src/test/resources/gfconfigs/instanceRoot";
        String configXml = "./src/test/resources/gfconfigs/configxml/test-domain.xml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setInstanceRoot(instanceRoot);
        config.setConfigurationXml(configXml);
        config.validate();

        assertTrue(config.getConfigurationXml().startsWith("file:"));
    }

    @Test
    public void testValidSunResourcesXmlPath() throws Exception {
        String sunResourcesXml = "./src/test/resources/gfconfigs/sunresourcesxml/sun-resources.xml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setSunResourcesXml(sunResourcesXml);
        config.validate();
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidSunResourcesXmlPath() throws Exception {
        String sunResourcesXml = "./src/test/resources/gfconfigs/emptydir/sun-resources.xml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setSunResourcesXml(sunResourcesXml);
        config.validate();
    }

    @Test
    public void testValidResourcesXmlPath() throws Exception {
        String resourcesXml = "./src/test/resources/gfconfigs/resourcesxml/glassfish-resources.xml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setResourcesXml(resourcesXml);
        config.validate();
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidResourcesXmlPath() throws Exception {
        String resourcesXml = "./src/test/resources/gfconfigs/emptydir/glassfish-resources.xml";

        GlassFishConfiguration config = new GlassFishConfiguration();
        config.setResourcesXml(resourcesXml);
        config.validate();
    }
}
