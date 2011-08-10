package org.jboss.arquillian.container.glassfish.remote_3_1;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class CDIJarTestCase {

    @Inject
    private SimpleBean foo;

    @Deployment
    public static JavaArchive deploy() {
        return ShrinkWrap.create(JavaArchive.class, "foo.jar").addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(SimpleBean.class, CDIJarTestCase.class);
    }

    @Test
    public void test() {
        Assert.assertNotNull(foo);
    }
}

