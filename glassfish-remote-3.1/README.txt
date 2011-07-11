Implementing the GlassFish compatible way of deployment using the "target"

1. GlassFish has a notion of TARGET, which specifies the target to which you 
are deploying. I recommend the attached implementation to use as a solution in 
relation to ARQ-269. 
 
Valid values of the target are:
 
 -  server:  Deploys the component to the default Admin Server instance. This is the default value.

 -  instance_name: Deploys the component to a particular stand-alone sever instance, which may 
 be on the same hots or can be on a different one as the DAS server.

 -  cluster_name: Deploys the component to every server instance in the cluster. They can be 
 on the same or on several other hosts as the DAS server. (Though Arquillion use only one 
 instance to run the test case.)
 
Note: The domain name as a target (which is valid for GlasFish) is not a reasonable deployment 
senarion in case of testing.

Taking the target we can navigate through the GlassFish REST objects to resolve the host 
address and port numbers for the testing node. Automatic fetch of these data, provides a more 
easily usable solution. 

2. We should introduce a new property key for this new feature.

3. There are some other reasonable set of property keys that used by the standard GlassFish 
utility (asadmin). Implementing some of them provides much more real life like environment for 
our tests. The most important ones are: name, contextroot, libraries, properties type, etc. Using 
the same keys with the same semantic as GlassFish use them, simplifies the learning curve.

4. I also recommend to simplify the existing property keys used by the remote GlassFish container 
to make them more consistent and simple to use. 

The solution provided by this implementation may also relates to ARQ-450 and ARQ-323.