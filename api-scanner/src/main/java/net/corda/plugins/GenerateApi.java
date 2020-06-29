package net.corda.plugins;

/**
 * This task provides backwards compatibility with earlier versions of
 * the Corda Gradle plugins. Projects should migrate to using the new
 * {@link net.corda.plugins.apiscanner.GenerateApi} task instead.
 */
@Deprecated
public class GenerateApi extends net.corda.plugins.apiscanner.GenerateApi {
}
