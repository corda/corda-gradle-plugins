# Gradle Plugins for Corda and Cordapps

The projects at this level of the project are gradle plugins for cordapps and are published to Maven Local with
the rest of the Corda libraries.

From the beginning of development for Corda 4.0 these plugins have been split from the main Corda repository. 
Any changes to gradle plugins pre-4.0 should be on a release branch within the Corda repo. Any changes after 3.0
belong in this repository. 

## Version number

To modify the version number edit the root build.gradle.

The version number should track the Corda version number it is built for. Eg; Corda `4.0` should be built as `4.0.x`.

## Getting started

You will need JVM 8 installed and on the path to run and install these plugins.

## Installing locally

To install locally for testing a new build against Corda you can run the following from the project root;

    ./gradlew install

## Release process

The version number of the "bleeding edge" in `master` is always a `-SNAPSHOT` version. To create a new release, a _maintainer_ must create a new branch off the latest commit in this release, remove the `-SNAPSHOT` from the version number, create a tag and then run the publish to artifactory task for the created tag in TeamCity. The version number in `master` is then advanced to the next `-SNAPSHOT` number.
DRRR
