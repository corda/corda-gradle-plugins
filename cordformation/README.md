Please refer to the 
[documentation](https://docs.r3.com/en/platform/corda/4.8/open-source/generating-a-node.html#use-cordform-and-dockerform-to-create-a-set-of-local-nodes-automatically.)
for creating local nodes.

## Migrating from Corda Gradle plugins 5.0.x

The `cordformation` plugin has been updated to resolve some confusion surrounding its use. It now creates the following
Gradle configurations:
- `cordapp` for any CorDapps you wish to deploy, excluding any CorDapp built by the local project.
- `cordaDriver` for any artifacts that must be added to each node's `drivers/` directory, e.g. database drivers or the
  Corda shell.
- `corda` for the Corda artifact itself.
- `cordaBootstrapper` for Corda's Bootstrapper artifact, i.e. a compatible version of `corda-node-api`. You may also
  wish to include an implementation of SLF4J here for the Bootstrapper to use, e.g. `slf4j-simple`.

The `corda` and `cordaBootstrapper` configurations replace the need for the `cordaRuntime` configuration
when using `cordformation`. Using `cordaRuntime` here was creating the false impression that CorDapps needed
to declare runtime dependencies on either Corda or the Bootstrapper, or both.

There is no need to apply the `net.corda.plugins.cordapp` Gradle plugin along with `cordformation` unless that project
is also building a CorDapp.
