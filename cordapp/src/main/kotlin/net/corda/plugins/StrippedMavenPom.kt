package net.corda.plugins;

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal
import org.gradle.api.publish.maven.internal.publication.MavenPomDistributionManagementInternal
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity

class StrippedMavenPom(
    private val pom: MavenPomInternal,
    private val project: Project,
    private val projectDepCalculator: (Project) -> Set<Pair<String, String>>
): MavenPomInternal {

    override fun withXml(action: Action<in XmlProvider>?) {
        pom.withXml(action)
    }
    override fun getContributors(): MutableList<MavenPomContributor>? {
        return pom.contributors
    }
    override fun issueManagement(action: Action<in MavenPomIssueManagement>?) {
        pom.issueManagement(action)
    }

    override fun getName(): Property<String>? {
        return pom.name
    }

    override fun getCiManagement(): MavenPomCiManagement? {
        return pom.ciManagement
    }

    override fun licenses(action: Action<in MavenPomLicenseSpec>?) {
        pom.licenses(action)
    }

    override fun distributionManagement(action: Action<in MavenPomDistributionManagement>?) {
        pom.distributionManagement(action)
    }

    override fun getOrganization(): MavenPomOrganization? {
        return pom.organization
    }

    override fun getPackaging(): String? {
        return pom.packaging
    }

    override fun getDescription(): Property<String>? {
        return pom.description
    }

    override fun getInceptionYear(): Property<String>? {
        return pom.inceptionYear
    }

    override fun ciManagement(action: Action<in MavenPomCiManagement>?) {
        pom.ciManagement(action)
    }

    override fun getUrl(): Property<String>? {
        return pom.url
    }

    override fun developers(action: Action<in MavenPomDeveloperSpec>?) {
        pom.developers(action)
    }

    override fun getXmlAction(): Action<XmlProvider>? {
        return pom.xmlAction
    }

    override fun getIssueManagement(): MavenPomIssueManagement? {
        return pom.issueManagement
    }

    override fun getMailingLists(): MutableList<MavenPomMailingList>? {
        return pom.mailingLists
    }

    override fun getRuntimeDependencyManagement(): MutableSet<MavenDependency>? {
        //for now, just return empty set, but in future if we do allow cordapps to have dependencies outside of corda
        //something along the lines of:
        // val depsToExclude: Set<Pair<String, String>> = projectDepCalculator(project)
        // return pom.runtimeDependencyManagement.filterNot { toExcludableDependency(it) in depsToExclude}.toHashSet()
        return HashSet()
    }

    override fun getRuntimeDependencies(): MutableSet<MavenDependencyInternal>? {
        return HashSet()
    }

    override fun getApiDependencyManagement(): MutableSet<MavenDependency>? {
        return HashSet()
    }

    override fun getApiDependencies(): MutableSet<MavenDependencyInternal>? {
        return HashSet()
    }

    override fun getDevelopers(): MutableList<MavenPomDeveloper>? {
        return pom.developers
    }

    override fun getProjectIdentity(): MavenProjectIdentity? {
        return pom.projectIdentity
    }

    override fun mailingLists(action: Action<in MavenPomMailingListSpec>?) {
        pom.mailingLists(action)
    }

    override fun organization(action: Action<in MavenPomOrganization>?) {
        pom.organization(action)
    }

    override fun getLicenses(): MutableList<MavenPomLicense>? {
        return pom.licenses
    }

    override fun getDistributionManagement(): MavenPomDistributionManagementInternal? {
        return pom.distributionManagement
    }

    override fun scm(action: Action<in MavenPomScm>?) {
        pom.scm(action)
    }

    override fun setPackaging(packaging: String?) {
        pom.packaging = packaging
    }

    override fun contributors(action: Action<in MavenPomContributorSpec>?) {
        pom.contributors(action)
    }

    override fun getScm(): MavenPomScm? {
        return pom.scm
    }

    private fun toExcludableDependency(md: MavenDependency): Pair<String, String> {
        return md.groupId to md.artifactId
    }


}
