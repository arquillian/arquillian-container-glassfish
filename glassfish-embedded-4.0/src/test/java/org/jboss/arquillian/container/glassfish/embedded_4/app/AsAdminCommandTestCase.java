package org.jboss.arquillian.container.glassfish.embedded_4.app;

import javax.annotation.Resource;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import junit.framework.Assert;
import org.glassfish.embeddable.CommandResult;
import org.glassfish.embeddable.CommandResult.ExitStatus;
import org.glassfish.embeddable.CommandRunner;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that Glassfish 'asadmin' commands can be executed against an 
 * Embedded Glassfish instance using the CommandRunner resource.
 * The CommandRunner is exercised by performing commands to create a Glassfish 
 * JDBC connection pool and a JDBC resource. 
 * The JDBC connection pool is tested by using the CommandRunner to perform a ping.
 * The JDBC resource is verified by performing a JNDI lookup.
 * 
 * @author magnus.smith
 */
@RunWith(Arquillian.class)
public class AsAdminCommandTestCase {

    @Deployment
    public static WebArchive createDeployment() throws Exception {
        return ShrinkWrap.create(WebArchive.class).addClasses(
                NoInterfaceEJB.class,
                NameProvider.class).addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");

    }
    @Resource(mappedName = "org.glassfish.embeddable.CommandRunner")
    private CommandRunner commandRunner;

    @Test
    public void shouldBeAbleToIssueAsAdminCommand() throws Exception {
        Assert.assertNotNull(
                "Verify that the asadmin CommandRunner resource is available",
                commandRunner);

        CommandResult result = commandRunner.run(
                "create-jdbc-connection-pool",
                "--datasourceclassname=org.apache.derby.jdbc.EmbeddedXADataSource",
                "--restype=javax.sql.XADataSource",
                "--property=portNumber=1527:password=APP:user=APP"
                + ":serverName=localhost:databaseName=my_database"
                + ":connectionAttributes=create\\=true",
                "my_derby_pool");

        Assert.assertEquals("Verify 'create-jdbc-connection-pool' asadmin command",
                ExitStatus.SUCCESS,
                result.getExitStatus());

        
        result = commandRunner.run(
                "create-jdbc-resource",
                "--connectionpoolid",
                "my_derby_pool",
                "jdbc/my_database");

        Assert.assertEquals("Verify 'create-jdbc-resource' asadmin command",
                ExitStatus.SUCCESS,
                result.getExitStatus());


        result = commandRunner.run("ping-connection-pool", "my_derby_pool");

        Assert.assertEquals("Verify asadmin command 'ping-connection-pool'",
                ExitStatus.SUCCESS,
                result.getExitStatus());

        Context ctx = new InitialContext();
        DataSource myDatabase = (DataSource) ctx.lookup("jdbc/my_database");

        Assert.assertNotNull(myDatabase);

    }
}
