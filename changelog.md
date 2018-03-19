# Changelog

## Version 4

### Version 4.0.9

`api-scanner`: Remove the `@JvmStatic` annotation from the generated output.
`cordformation`: Removed default dependency on jolokia, to provide HTTP JMX monitoring, explicitly depend on jolokia.
`node-runner`: To expose HTTP JMX monitoring, pass the flag --expose-jolokia to runnodes, as the default is off. 
### Version 4.0.8

`publish-utils`: Revert "Setting the `name` property no longer triggers configuration." because it breaks publishing to Artifactory.

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
