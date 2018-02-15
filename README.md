# Gradle Plugins for Corda and Cordapps

The projects at this level of the project are gradle plugins for cordapps and are published to Maven Local with
the rest of the Corda libraries.

From the beginning of development for Corda 4.0 these plugins have been split from the main Corda repository. 
Any changes to gradle plugins pre-4.0 should be on a release branch within the Corda repo. Any changes after 3.0
belong in this repository. 

## Version number


To modify the version number edit constants.properties in root dir. 

The version number should track the Corda version number it is built for. Eg; Corda `4.0` should be built as `4.0.x`.

## Getting started

To bootstrap this repository you can install the publishing plugins with;

    cd publish-utils
    ../gradlew -u install

## Installing locally

To install locally for testing a new build against Corda you can run the following from the project root;

    ./gradlew install
