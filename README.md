# Gradle Plugins for Cordapps

The projects at this level of the project are gradle plugins for cordapps and are published to Maven Local with
the rest of the Corda libraries.

Version number
--------------

To modify the version number edit constants.properties in root dir

Installing
----------

If you need to bootstrap the corda repository you can install these plugins with

.. code-block:: text

    cd publish-utils
    ../../gradlew -u install
    cd ../
    ../gradlew install

