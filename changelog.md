# Changelog

## Version 4

### Version 4.0.16

 * `api-scanner`: Write each annotation on a new line and remove its package name; remove `@Deprecated` annotations from the generated output.
 * `api-scanner`: Remove the `@JvmDefault` annotation from the generated output.

### Version 4.0.15

 * `cordformation`: added Jolokia fix such that when the Jolokia JVM agent jar file is missing the Node process will still start (and give a warning)
 * `cordformation`: root nodes directory is deleted before running

### Version 4.0.14

* `cordform-common`,`cordapp`: Replace deprecated `kotlin-stdlib-jre8` with `kotlin-stdlib-jdk8`.

### Version 4.0.13

* `cordformation`: No longer copies the CorDapp jars to the nodes' `cordapps` directories. This will instead be done by
  the network bootstrapper.

### Version 4.0.12

* `cordformation`: Correctly copying CorDapp configs to cordapps/config

### Version 4.0.11

* `quasar-utils`: Add quasar-core to cordaRuntime. This ensures that it is excluded from "fat jar" CorDapps [CORDA-1301].

### Version 4.0.10

* `cordformation`: Fixes a crash in Dockerform. 
* `cordformation`: NodeRunner delegates Corda jolokia logging to slf4j.

### Version 4.0.9

* `cordformation`: Fix WebServer config file missing `security` section [CORDA-1231] 
* `api-scanner`: Remove the `@JvmStatic` annotation from the generated output.

### Version 4.0.8

* `publish-utils`: Revert "Setting the `name` property no longer triggers configuration." because it breaks publishing to Artifactory.

### Version 4.0.7

* `api-scanner`: Handle `@Inherited` annotations from library modules.
* `publish-utils`: Setting the `name` property no longer triggers configuration. This is now applied after the project has been evaluated.
* `publish-utils`: Add `publishJavadoc` property so that publishing Javadoc can be optional.

### Version 4.0.6

* `api-scanner`: Field annotations are now written out in alphanumerical order. The `@JvmField` annotation is no longer written at all.

### Version 4.0.5

* `cordformation`: Fix webserver startup when headless.

### Version 4.0.4

* `api-scanner`: Class/interface/method annotations are now written out in alphanumerical order.
* `api-scanner`: Handle methods with vararg parameters correctly.

### Version 4.0.3

* `cordformation`: Fix parsing of unknown node configuration properties.

### Version 4.0.2

* `cordformation`: Add `NetworkParameters` contract implementation whitelist.

### Version 4.0.1

* `cordformation`: Use updated rpc users config format.

### Version 4.0.0

* Split repository from [Corda](https://github.com/corda/corda) repository.

## Pre-version 4

All pre-4 changes were untagged in the [Corda](https://github.com/corda/corda) repository.
