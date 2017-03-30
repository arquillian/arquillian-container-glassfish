package org.jboss.arquillian.container.glassfish.managed_3_1;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;

import javax.servlet.annotation.WebServlet;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public abstract class GlassFishManagedDeploymentTestTemplate {

    @ArquillianResource
    private URL deploymentUrl;

    @Test
    public void shouldBeAbleToDeployEnterpriseArchive() throws Exception {
        final String servletPath =
            greeterImplementationBasedOnDerbyEnabled().getAnnotation(WebServlet.class).urlPatterns()[0];

        final URLConnection response = new URL(deploymentUrl.toString() + servletPath.substring(1)).openConnection();

        BufferedReader in = new BufferedReader(new InputStreamReader(response.getInputStream()));
        final String result = in.readLine();

        assertThat(result, equalTo("Hello"));
    }

    static Class<?> greeterImplementationBasedOnDerbyEnabled() {
        if (Boolean.valueOf(System.getProperty("enableDerby"))) {
            return GreeterServletWithDerby.class;
        }
        return GreeterServlet.class;
    }
}
