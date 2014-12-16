@GrabResolver(name='cloudera-local-snap', root='file:///Users/abayer/.m2/repository')
@GrabResolver(name='cloudera-snap', root='https://repository.cloudera.com/artifactory/libs-snapshot')
@Grab(group='com.cloudera.kitchen', module='package-tools', version='0.5-SNAPSHOT')

import groovy.json.JsonSlurper
import com.cloudera.kitchen.jobdsl.JobDslConstants
import com.cloudera.kitchen.jobdsl.JenkinsDslUtils

def slurper = new JsonSlurper()
def jenkinsJson = slurper.parseText(readFileFromWorkspace("jenkins_metadata.json"))
def crepoJson = slurper.parseText(readFileFromWorkspace("${jenkinsJson['core-prefix']}${jenkinsJson['short-release-base']}.json"))

def phases = []

def pkgGitInfo = JenkinsDslUtils.getPkgGitInfo(crepoJson)

def jobPrefix = JenkinsDslUtils.getJobPrefix(jenkinsJson)

def components = jenkinsJson.components.collectEntries { k, v ->
    def crepoInfo = crepoJson.projects[k]

    if (crepoInfo != null) {
        v['branch'] = crepoInfo['track-branch']
        v['dir'] = crepoInfo['dir']
        def repoName = crepoInfo['remote-project-name'] ?: k

        v['repo'] = "git://github.mtv.cloudera.com/CDH/${repoName}.git".replaceAll("impala", "Impala")
    }

    v['job-name'] = JenkinsDslUtils.componentJobName(jobPrefix, v['display-name'] ?: k.capitalize())
    v['child-jobs'] = jenkinsJson.platforms.collect { JenkinsDslUtils.platformToJob(it, v['job-suffix'] ?: "") }

    if (!v['skipBinaryTarball']) {
        v['child-jobs'] << JobDslConstants.TARBALL_DEPLOY_JOB
    }
    [k, v]
}

def stepNumbers = components.collect { it.value.step }.sort().unique()

phases = stepNumbers.collect { s ->
    components.findAll { it.value.step == s }
}

def gplProjects = components.findAll { it.value.'is-gpl' != null && it.value.'is-gpl' }.collect { it.key }
gplProjects << "gplextras-parcel"

components.each { component, config ->
  def downstreamJobs = jenkinsJson.platforms.collect { p ->
    JenkinsDslUtils.platformToJob(p, config['job-suffix'] ?: "")
  }

    if (!config.skipBinaryTarball) {
        downstreamJobs << JobDslConstants.TARBALL_DEPLOY_JOB
    }

    def mailto = [ "kitchen-build@cloudera.com" ]

    if (config.mailto) {
        mailto << config.mailto
    }
    
    job {
        name config['job-name']
        description "${jobPrefix.toUpperCase()} ${component} Packaging Parent Build"
        logRotator(-1, 15, -1, -1)

        //dissabled(true)

        componentParams(delegate, "${jenkinsJson.'core-prefix'}${jenkinsJson.'release-base'}")

        multiscm {
            cdhGit(delegate, jenkinsJson['cdh-branch'])
            if (config.branch != null) { 
                git(config.repo, "origin/${config.branch}") { gitNode ->
                    gitNode / localBranch << config.branch
                    gitNode / relativeTargetDir << config.dir
                    gitNode / scmName << component
                }
            }
            packageGit(delegate, pkgGitInfo)
        }

        label JobDslConstants.COMPONENT_PARENT_LABEL
        
        jdk JobDslConstants.PACKAGING_JOB_JDK

        triggers {
            scm("*/10 * * * *")
        }

        steps {
          shell(JobDslConstants.SHELL_SCRIPT_CLEAN_CACHES)
          shell(JenkinsDslUtils.constructBuildStep(component, "${jenkinsJson.'core-prefix'}${jenkinsJson['short-release-base']}",
                  config['skipRelNotes'] ? true : false, jenkinsJson.java7 ? true : false,
                  config.makefileAsMetadata ? true : false,
                  config.skipMiscBits ? true : false,
                  config.version ?: "",
                  config['is-gpl'] ? true : false))

            componentChildren(delegate, downstreamJobs, component, jenkinsJson.java7, null)

            conditionalRepoUpdate(delegate, jobPrefix, config["is-gpl"] != null ? true : false)
        }

        publishers {
            //archiveArtifacts("output/**/${component}*/*.tar.gz") // ending comment thingie
            associatedFiles('/mnt/jenkins-staging/binary-staging/${JOB_NAME}-${BUILD_ID}')

            emailSetup(delegate, mailto)
        }

        wrappers {
            timeout(120)
        }
    }
}


if (jenkinsJson.'c5-parcel') {
  def downstreamParcelJobs = jenkinsJson.platforms.collect { p ->
    JenkinsDslUtils.platformToJob(p, "", true, true)
  }

// Parcel job
  job {
    name JenkinsDslUtils.componentJobName(jobPrefix, "Parcel")

    logRotator(-1, 15, -1, -1)

    //dissabled(true)

    componentParams(delegate, "${jenkinsJson.'core-prefix'}${jenkinsJson.'release-base'}")

    multiscm {
      cdhGit(delegate, jenkinsJson['cdh-branch'])
      packageGit(delegate, pkgGitInfo)
    }

    label JobDslConstants.COMPONENT_PARENT_LABEL

    jdk JobDslConstants.PACKAGING_JOB_JDK

    triggers {
      cron("0 23 * * *")
    }

    steps {
      shell(JobDslConstants.SHELL_SCRIPT_CLEAN_CACHES)
      shell(JenkinsDslUtils.parcelBuildStep("${jenkinsJson['core-prefix']}${jenkinsJson['short-release-base']}"))

      componentChildren(delegate, downstreamParcelJobs, "cdh-parcel", jenkinsJson.java7,
              "origin/${crepoJson.projects.'parcel-build'.'track-branch'}")

      conditionalRepoUpdate(delegate, jobPrefix, false)
    }

    publishers {
        //archiveArtifacts("output/**/cdh-parcel*/*.tar.gz") // ending comment thingie
      associatedFiles('/mnt/jenkins-staging/binary-staging/${JOB_NAME}-${BUILD_ID}')

      emailSetup(delegate, ["kitchen-build@cloudera.com"])
    }

    wrappers {
      timeout(120)
    }
  }

// GPL Parcel job
  job {
    name JenkinsDslUtils.componentJobName(jobPrefix, "GPLExtras-Parcel")

    logRotator(-1, 15, -1, -1)

    //dissabled(true)

    componentParams(delegate, "${jenkinsJson.'core-prefix'}${jenkinsJson.'release-base'}")

    multiscm {
      cdhGit(delegate, jenkinsJson['cdh-branch'])
      packageGit(delegate, pkgGitInfo)
    }

    label JobDslConstants.COMPONENT_PARENT_LABEL

    jdk JobDslConstants.PACKAGING_JOB_JDK

    steps {
      shell(JobDslConstants.SHELL_SCRIPT_CLEAN_CACHES)
      shell(JenkinsDslUtils.parcelBuildStep("${jenkinsJson['core-prefix']}${jenkinsJson['short-release-base']}", true))

      componentChildren(delegate, downstreamParcelJobs, "gplextras-parcel", jenkinsJson.java7,
                        "origin/${crepoJson.projects.'parcel-build'.'track-branch'}".replaceAll("cdh", "gplextras"))

      conditionalRepoUpdate(delegate, jobPrefix, true)
    }

    publishers {
        //archiveArtifacts("output/**/gplextras-parcel*/*.tar.gz") // ending comment thingie
      associatedFiles('/mnt/jenkins-staging/binary-staging/${JOB_NAME}-${BUILD_ID}')

      emailSetup(delegate, ["kitchen-build@cloudera.com"])
    }

    wrappers {
      timeout(120)
    }
  }
} else {
  // C4 parcel style
  def downstreamJobs = jenkinsJson.platforms.collect { p ->
    JenkinsDslUtils.platformToJob(p, "")
  }

  job {
    name JenkinsDslUtils.componentJobName(jobPrefix, "Parcel")

    logRotator(-1, 15, -1, -1)

    //dissabled(true)

    c4ParcelParams(delegate, "${jenkinsJson['core-prefix']}${jenkinsJson.'release-base'}", jenkinsJson.'repo-category', jenkinsJson.'parcel-type')

    multiscm {
      cdhGit(delegate, jenkinsJson['cdh-branch'])
      packageGit(delegate, pkgGitInfo)
    }

    label JobDslConstants.COMPONENT_PARENT_LABEL

    jdk JobDslConstants.PACKAGING_JOB_JDK

    triggers {
      cron("0 23 * * *")
    }

    steps {
      shell(JobDslConstants.SHELL_SCRIPT_CLEAN_CACHES)
      shell(JenkinsDslUtils.c4ParcelBuildStep("${jenkinsJson['core-prefix']}${jenkinsJson['short-release-base']}", jenkinsJson.'parcel-type'))

      componentChildren(delegate, downstreamJobs, "${jenkinsJson.'parcel-type'.toLowerCase()}-parcel",
              jenkinsJson.java7, false)

      shell(JenkinsDslUtils.c4ParcelPostBuildStep(jenkinsJson.'parcel-type', jenkinsJson.'repo-category'))
    }

    publishers {
        //archiveArtifacts("output/**/${jenkinsJson.'parcel-type'.toLowerCase()}-parcel*/*.tar.gz") // ending comment thingie
      associatedFiles('/mnt/jenkins-staging/binary-staging/${JOB_NAME}-${BUILD_ID}')

      emailSetup(delegate, ["kitchen-build@cloudera.com"])
    }

    wrappers {
      timeout(120)
    }

  }
}

// Parent POM job

job {
    name JenkinsDslUtils.componentJobName(jobPrefix, "Parent-POM")
    
    logRotator(-1, 15, -1, -1)
    
    //dissabled(true)

    scm { 
        cdhGit(delegate, jenkinsJson['cdh-branch'], "(?!.*pom\\.xml.*)")
    }

    label JobDslConstants.COMPONENT_PARENT_LABEL
    
    jdk JobDslConstants.PACKAGING_JOB_JDK
    
    triggers {
        scm("*/10 * * * *")
    }

    steps {
        maven('clean deploy -N', 'pom.xml') { n ->
            def mvnVer = n / mavenName
            mvnVer.value = "Maven 3.0"
        }
    }
}

// Update Repos job

job {
    name JenkinsDslUtils.componentJobName(jobPrefix, "Update-Repos")
    
    logRotator(-1, 15, -1, -1)
    
    //dissabled(true)

    scm { 
        cdhGit(delegate, jenkinsJson['cdh-branch'])
    }

    label JobDslConstants.FULL_AND_REPO_JOBS_LABEL
    
    jdk JobDslConstants.PACKAGING_JOB_JDK

    parameters {
      stringParam("PARENT_BUILD_ID", "", "Build ID of parent job whose artifacts will be added to the repo.")
      stringParam("IS_GPL", "false", "Whether this is updating GPL bits")
    }

    repoThrottle(delegate, jobPrefix)

    steps {
        shell(JenkinsDslUtils.constructRepoUpdateStep(jenkinsJson['repo-category'],
                jenkinsJson['gpl-repo-category'],
                jenkinsJson['c5-parcel'],
                "${jenkinsJson['gpl-prefix']}${jenkinsJson['release-base']}",
                jenkinsJson.platforms))
    }
}

// Repo promotion job
job {
  name jobPrefix.toUpperCase() + "-Promote-Repository"

  logRotator(-1, 15, -1, -1)

  scm {
    cdhGit(delegate, jenkinsJson['cdh-branch'])
  }

  label JobDslConstants.FULL_AND_REPO_JOBS_LABEL

  jdk JobDslConstants.PACKAGING_JOB_JDK

  parameters {
    stringParam("REPO_PARENT", "", "Parent directory of the original repo (under repos.jenkins.cloudera.com")
    stringParam("CATEGORY", "", "The category to promote to, i.e., cdh5.1.1-nightly")
    stringParam("REPO_BUILD_ID", "", "The repository build ID to promote")
  }

  steps {
      shell(JenkinsDslUtils.boilerPlatePromoteStep(jenkinsJson['core-prefix'], jobPrefix.toLowerCase()))
  }
}

// Full build job

job {
  name JenkinsDslUtils.componentJobName(jobPrefix, "Full-Build")
  logRotator(-1, 15, -1, -1)

  //dissabled(true)

  scm {
    cdhGit(delegate, jenkinsJson['cdh-branch'])
  }

  label JobDslConstants.FULL_AND_REPO_JOBS_LABEL

  jdk JobDslConstants.PACKAGING_JOB_JDK

  triggers {
    cron("13 1 * * 1,3,6")
  }

  repoThrottle(delegate, jobPrefix)

  steps {
    shell(JenkinsDslUtils.firstFullBuildStep("${jenkinsJson['core-prefix']}${jenkinsJson.'release-base'}"))
    parentCall(delegate, JenkinsDslUtils.componentJobName(jobPrefix, "Parent-POM"), false)
    phases.each { p ->
      parentCall(delegate, p.collect { it.value['job-name'] }.join(", "), jenkinsJson.java7)
    }
    if (jenkinsJson.'c5-parcel') {
        parentCall(delegate, ["Parcel", "GPLExtras-Parcel"].collect { JenkinsDslUtils.componentJobName(jobPrefix, it) }.join(","))
    }
    shell(JenkinsDslUtils.repoGenFullBuildStep(jenkinsJson['repo-category'], jenkinsJson['c5-parcel'],
            false, jenkinsJson.platforms, gplProjects, false, "${jenkinsJson['core-prefix']}${jenkinsJson.'release-base'}", jenkinsJson['base-repo']))
    shell(JenkinsDslUtils.repoGenFullBuildStep(jenkinsJson['gpl-repo-category'], jenkinsJson['c5-parcel'],
            true, jenkinsJson.platforms, gplProjects, true, "${jenkinsJson['gpl-prefix']}${jenkinsJson.'release-base'}", jenkinsJson['base-repo']))
    if (jenkinsJson.'update-static') {
      shell(JenkinsDslUtils.updateStaticRepoFullBuildStep(jenkinsJson['repo-category']))
      downstreamParameterized {
        trigger(jobPrefix.toUpperCase() + "-Promote-Repository", "ALWAYS", false) {
          predefinedProps(['REPO_BUILD_ID': '${JOB_NAME}-${BUILD_ID}',
                           'REPO_PARENT': "${jenkinsJson['repo-category']}-repos",
                           'CATEGORY': jenkinsJson['repo-category']])

        }
      }
    }

    if (!jenkinsJson.'c5-parcel') {
      parentCall(delegate, JenkinsDslUtils.componentJobName(jobPrefix, "Parcel"))
    }

    if (jenkinsJson['update-nightly']) {
      conditionalSteps {
        condition {
          status('Success', 'Success')
        }
        runner("Fail")
        downstreamParameterized {
          trigger(jobPrefix.toUpperCase() + "-Packaging-Update-Nightly", "ALWAYS", false) {
                        predefinedProp('PARENT_BUILD_ID', '${JOB_NAME}-${BUILD_ID}')
          }
        }
      }
    }
  }

}

def parentCall(Object delegate, String components, boolean useParams = true) {
    return delegate.downstreamParameterized {
        trigger(components, "ALWAYS", false,
                ["buildStepFailure": "FAILURE",
                 "failure": "FAILURE"]) {
            if (useParams) { 
                predefinedProps(['FULL_PARENT_BUILD_ID': '${JOB_NAME}-${BUILD_ID}',
                                 'CHILD_BUILD': 'true'])
            }
        }
    }
}

def emailSetup(Object delegate, List<String> recipients) {
    return delegate.extendedEmail(recipients.join(", ")) {
        trigger(triggerName: 'Failure', sendToDevelopers: false, sendToRequester: false, includeCulprits: false,
                sendToRecipientList: true)
        trigger(triggerName: 'StillFailing', sendToDevelopers: false, sendToRequester: false, includeCulprits: false,
                sendToRecipientList: true)
        trigger(triggerName: 'Fixed', sendToDevelopers: false, sendToRequester: false, includeCulprits: false,
                sendToRecipientList: true)
    }
}

def cdhGit(Object delegate, String cdhBranch, String exclRegion = ".*") {
    return delegate.git(JobDslConstants.CDH_GIT_REPO_BASE + "cdh.git", "origin/${cdhBranch}") { gitNode ->
        gitNode / scmName << "cdh"
        gitNode / excludedRegions << exclRegion
    }
}

def packageGit(Object delegate, pkgGitInfo) {
    return delegate.git(pkgGitInfo.repo, "origin/${pkgGitInfo.branch}") { gitNode ->
        gitNode / localBranch << pkgGitInfo.branch
        gitNode / relativeTargetDir << pkgGitInfo.dir
        gitNode / scmName << "cdh-package"
        gitNode / excludedRegions << ".*"
    }
}

def repoThrottle(Object delegate, String shortRel) {
    return delegate.throttleConcurrentBuilds {
        maxPerNode 0
        maxTotal 0
        categories(["${shortRel.toUpperCase()}-repo-update"])
    }
}

def componentParams(Object delegate, String release) {
    return delegate.parameters {
        choiceParam('CHILD_BUILD', ["false", "true"], "When called directly or as a result of polling for changes, "
                    + "this will be no, and the existing repository for this release will be re-used. If this is "
                    + "called automatically from the full all-components build, this will be yes, and all repository "
                    + "management will be handled in the parent job.")
        stringParam("FULL_PARENT_BUILD_ID", "", "When called directly or as a result of polling for changes, this "
                    + "will be empty and ignored. If called from the full all-components build, this will be set "
                    + "the build id of the parent build, and used for staging information.")
        stringParam("RELEASE_CODENAME", release, "Release codename - generally will not need to be overridden.")
    }
}

def c4ParcelParams(Object delegate, String release, String repoCategory, String parcelType) {
  return delegate.parameters {
    choiceParam('CHILD_BUILD', ["false", "true"], "When called directly or as a result of polling for changes, "
            + "this will be no, and the existing repository for this release will be re-used. If this is "
            + "called automatically from the full all-components build, this will be yes, and all repository "
            + "management will be handled in the parent job.")
    stringParam("FULL_PARENT_BUILD_ID", "", "When called directly or as a result of polling for changes, this "
            + "will be empty and ignored. If called from the full all-components build, this will be set "
            + "the build id of the parent build, and used for staging information.")
    stringParam("RELEASE_CODENAME", release, "Release codename - generally will not need to be overridden.")
    stringParam("PKG_ARCHIVE", "http://repos.jenkins.cloudera.com/${repoCategory}/", "The repo where to pull the packages from to build the parcel.")
    stringParam("PKG_${parcelType}_VERSION", 1, "Just the version number of the release - i.e., 1 for Search, 4.7.0 for CDH")
  }
}

def componentChildren(Object delegate, List<String> jobs, String pkg, boolean java7 = true,
                      String parcelBranch = null) {
  def params = ['PARENT_BUILD_ID': '${JOB_NAME}-${BUILD_ID}',
                'PACKAGES': pkg,
                'CDH_RELEASE': '${RELEASE_CODENAME}',
                'JAVA7_BUILD': java7 ? "true" : ""]

  if (parcelBranch != null) {
    params['PARCEL_BUILD_BRANCH'] = parcelBranch
  }

  return delegate.downstreamParameterized {
    trigger(jobs.join(", "), "ALWAYS", false,
            ["buildStepFailure": "FAILURE",
             "failure": "FAILURE"]) {
      predefinedProps(params)
    }
  }
}

def conditionalRepoUpdate(Object delegate, String jobPrefix, boolean isGpl = false) {
    return delegate.conditionalSteps {
        condition {
            stringsMatch('${ENV,var="CHILD_BUILD"}', "false", false)
        }
        runner("Fail")
        downstreamParameterized {
            trigger(JenkinsDslUtils.componentJobName(jobPrefix, "Update-Repos"), "ALWAYS", false) { 
                predefinedProps(['PARENT_BUILD_ID': '${JOB_NAME}-${BUILD_ID}',
                                 'IS_GPL': "${isGpl}"])
            }
        }
    } 
}
