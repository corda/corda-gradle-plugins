package net.corda.plugins;

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal
import org.gradle.api.publish.maven.internal.publication.MavenPomDistributionManagementInternal
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity

open class DelegatedPom(
    private val pom: MavenPomInternal
) {
    
     fun withXml(action: Action<in XmlProvider>?) {
        pom.withXml(action)
    }
     fun getContributors(): MutableList<MavenPomContributor>? {
        return pom.contributors
    }
     fun issueManagement(action: Action<in MavenPomIssueManagement>?) {
        pom.issueManagement(action)
    }

     fun getName(): Property<String>? {
        return pom.name
    }

     fun getCiManagement(): MavenPomCiManagement? {
        return pom.ciManagement
    }

     fun licenses(action: Action<in MavenPomLicenseSpec>?) {
        pom.licenses(action)
    }

     fun distributionManagement(action: Action<in MavenPomDistributionManagement>?) {
        pom.distributionManagement(action)
    }

     fun getOrganization(): MavenPomOrganization? {
        return pom.organization
    }

     fun getPackaging(): String? {
        return pom.packaging
    }

     fun getDescription(): Property<String>? {
        return pom.description
    }

     fun getInceptionYear(): Property<String>? {
        return pom.inceptionYear
    }

     fun ciManagement(action: Action<in MavenPomCiManagement>?) {
        pom.ciManagement(action)
    }

     fun getUrl(): Property<String>? {
        return pom.url
    }

     fun developers(action: Action<in MavenPomDeveloperSpec>?) {
        pom.developers(action)
    }

     fun getXmlAction(): Action<XmlProvider>? {
        return pom.xmlAction
    }

     fun getIssueManagement(): MavenPomIssueManagement? {
        return pom.issueManagement
    }

     fun getMailingLists(): MutableList<MavenPomMailingList>? {
        return pom.mailingLists
    }

     fun getRuntimeDependencyManagement(): MutableSet<MavenDependency>? {
        //for now, just return empty set, but in future if we do allow cordapps to have dependencies outside of corda
        //something along the lines of:
        // val depsToExclude: Set<Pair<String, String>> = projectDepCalculator(project)
        // return pom.runtimeDependencyManagement.filterNot { toExcludableDependency(it) in depsToExclude}.toHashSet()
        return HashSet()
    }

     fun getRuntimeDependencies(): MutableSet<MavenDependencyInternal>? {
        return HashSet()
    }

     fun getApiDependencyManagement(): MutableSet<MavenDependency>? {
        return HashSet()
    }

     fun getApiDependencies(): MutableSet<MavenDependencyInternal>? {
        return HashSet()
    }

     fun getDevelopers(): MutableList<MavenPomDeveloper>? {
        return pom.developers
    }

     fun getProjectIdentity(): MavenProjectIdentity? {
        return pom.projectIdentity
    }

     fun mailingLists(action: Action<in MavenPomMailingListSpec>?) {
        pom.mailingLists(action)
    }

     fun organization(action: Action<in MavenPomOrganization>?) {
        pom.organization(action)
    }

     fun getLicenses(): MutableList<MavenPomLicense>? {
        return pom.licenses
    }

     fun getDistributionManagement(): MavenPomDistributionManagementInternal? {
        return pom.distributionManagement
    }

     fun scm(action: Action<in MavenPomScm>?) {
        pom.scm(action)
    }

     fun setPackaging(packaging: String?) {
        pom.packaging = packaging
    }

     fun contributors(action: Action<in MavenPomContributorSpec>?) {
        pom.contributors(action)
    }

     fun getScm(): MavenPomScm? {
        return pom.scm
    }


}
