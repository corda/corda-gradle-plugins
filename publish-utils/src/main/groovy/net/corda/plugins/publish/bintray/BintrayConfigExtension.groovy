package net.corda.plugins.publish.bintray

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

@SuppressWarnings("unused")
class BintrayConfigExtension {
    private final Property<String> user
    private final Property<String> key
    private final Property<String> repo
    private final Property<String> org
    private final ListProperty<String> licenses
    private final Property<Boolean> gpgSign
    private final Property<String> gpgPassphrase
    private final Property<String> vcsUrl
    private final Property<String> projectUrl
    private final ListProperty<String> publications
    private final Property<Boolean> dryRun
    private final License license
    private final Developer developer

    @Inject
    BintrayConfigExtension(ObjectFactory objects) {
        user = objects.property(String.class)
        key = objects.property(String.class)
        repo = objects.property(String.class)
        org = objects.property(String.class)
        licenses = objects.listProperty(String.class)
        gpgSign = objects.property(Boolean.class).convention(false)
        gpgPassphrase = objects.property(String.class)
        vcsUrl = objects.property(String.class)
        projectUrl = objects.property(String.class)
        publications = objects.listProperty(String.class)
        dryRun = objects.property(Boolean.class).convention(false)
        license = objects.newInstance(License.class)
        developer = objects.newInstance(Developer.class)
    }

    /**
     * Bintray username
     */
    Property<String> getUser() {
        return user
    }

    /**
     * Bintray access key
     */
    Property<String> getKey() {
        return key
    }

    /**
     * Bintray repository
     */
    Property<String> getRepo() {
        return repo
    }

    /**
     * Bintray organisation
     */
    Property<String> getOrg() {
        return org
    }

    /**
     * Licenses for packages uploaded by this configuration
     */
    ListProperty<String> getLicenses() {
        return licenses
    }

    /**
     * Whether to sign packages uploaded by this configuration
     */
    Property<Boolean> getGpgSign() {
        return gpgSign
    }

    /**
     * The passphrase for the key used to sign releases.
     */
    Property<String> getGpgPassphrase() {
        return gpgPassphrase
    }

    /**
     * VCS URL
     */
    Property<String> getVcsUrl() {
        return vcsUrl
    }

    /**
     * Project URL
     */
    Property<String> getProjectUrl() {
        return projectUrl
    }

    /**
     * The publications that will be uploaded as a part of this configuration. These must match both the name on
     * bintray and the gradle module name. ie; it must be "some-package" as a gradle sub-module (root project not
     * supported, this extension is to improve multi-build bintray uploads). The publication must also be called
     * "some-package". Only one publication can be uploaded per module (a bintray plugin restriction(.
     * If any of these conditions are not met your package will not be uploaded.
     */
    ListProperty<String> getPublications() {
        return publications
    }

    boolean isPublishing(String publishName) {
        return publications.get().contains(publishName)
    }

    /**
     * Whether to test the publication without uploading to bintray.
     */
    Property<Boolean> getDryRun() {
        return dryRun
    }

    /**
     * The license this project will use (currently limited to one)
     */
    License getLicense() {
        return license
    }

    void license(Action<? super License> action) {
        action.execute(license)
    }

    /**
     * The developer of this project (currently limited to one)
     */
    Developer getDeveloper() {
        return developer
    }

    void developer(Action<? super Developer> action) {
        action.execute(developer)
    }
}
