
1. GlassFish has a notion of TARGET, which specifies the target to which
 you are  deploying. We should use this way in relation to ARQ-269 
 solution.
 
 Valid values are:
 	server
	   	Deploys the component to the default Admin Server instance.
	   	This is the default value.
   instance_name
	   	Deploys the component to  a  particular  stand-alone
	   	sever instance.
   cluster_name
	   	Deploys the component to every  server  instance  in
	   	the cluster. (Though Arquillion use only one instance
	   	to run the test case.)
 
 The domain name as a target is not a reasonable deployment 
 senarion in case of testing. 

Using the target we can navigate through the GlassFish REST objects
to resolve the host address and port numbers for the testing node.
Automatic fetch of this data, provides less also related to ARQ-450. 

2. We should introduce new property keys for this new feature.

3. There are some other reasonable set of property keys that 
used by the standard GlassFish utility. Implementing some of them 
provides much more real life like environment for our test.

The most important ones are: name, contextroot, libraries, properties
type, etc. Using the same keys with the same semantic as GlassFish
use them simplifies the learning curve.      