#!/usr/bin/env groovy
import groovy.json.JsonSlurper

def opts = parseArgs(this.args)

println "Replacing ${opts.'old-version'} with ${opts.'new-version'}"
if (!opts.'incremental') {
    println "Bulk update of everything, no build..."
    updateMakefileAndParentPom(opts.'root-dir', opts.'old-version', opts.'new-version')
    replaceCdhVersion(opts.'root-dir' + "/repos", opts.'old-version', opts.'new-version')
    replaceRootVersion(opts.'root-dir' + "/repos", opts.'old-version', opts.'new-version')
} else {
    println "Prepping for incremental updating..."
    def slurper = new JsonSlurper()

    def rootDir = new File(opts.'root-dir')
    def jenkinsJson = slurper.parseText(new File(opts.'root-dir', "jenkins_metadata.json").text)

    def components = []

    jenkinsJson.components.each { k, v ->
        if (v.updateMavenVersion) {
            println "Adding ${k} to list to update"
            def comp = ["component": k,
                        "repo": k ];
            if (v.mavenProps != null && v.mavenProps != "" && v.mavenProps != false) {
                comp.props = v.mavenProps.split(",").toList()
            } else {
                comp.props = [k]
            }
            components << comp
        } else {
            println " - ignorning ${k} since updateMavenVersion not set"
        }

    }

    updateMakefileAndParentPom(opts.'root-dir', opts.'old-version', opts.'new-version', true)

    components.sort { it.component }.each { c ->
        def repoDir = new File(opts.'root-dir' + "/repos/cdh${jenkinsJson.'short-release-base'}", c.repo)
        assert repoDir.isDirectory(), "Repo directory ${repoDir.canonicalPath} does not exist, exitting."
        def alreadyBuilt = false
        def buildDir = new File(opts.'root-dir' + "/build/cdh${jenkinsJson.'short-release-base'}/${c.component}")
        if (buildDir.isDirectory()) {
            buildDir.eachDir { d ->
                if (new File(d, ".maven").exists()) {
                    alreadyBuilt = true
                }
            }
        }

        if (alreadyBuilt) {
            println "Already built component ${c.component} so skipping..."
        } else { 
            println "Updating version in ${repoDir.canonicalPath}..."
            if (c.component.equals("hue")) {
                replaceCdhVersion(repoDir.canonicalPath, opts.'old-version', opts.'new-version', ".git,apps/jobsub/src/jobsubd,data")
                replaceRootVersion(repoDir.canonicalPath, opts.'old-version', opts.'new-version', ".git,apps/jobsub/src/jobsubd,data")
            } else { 
                replaceCdhVersion(repoDir.canonicalPath, opts.'old-version', opts.'new-version')
                replaceRootVersion(repoDir.canonicalPath, opts.'old-version', opts.'new-version')
            }
            
            println " - committing changes"
            runCmd("git", ["add", "-u"], repoDir.canonicalPath)
            runCmd("git", ["commit", "-m", "Bogus commit"], repoDir.canonicalPath)
            
            println " - updating parent POM for new version of ${c.component}"
            updateOneComponentInParentPom(rootDir, opts.'old-version', opts.'new-version', c.props)
            
            println " - deploying modified parent POM"
            runCmd("mvn", ["-N", "deploy"], rootDir.canonicalPath)
            
            println " - building and deploying component ${c.component}"
            runCmd("make", ["${c.component}-maven"], rootDir.canonicalPath, ["DO_MAVEN_DEPLOY=deploy"])
        }
    }

    println "Rebuilding all components with correct dependecy versions"
    runCmd("rm", ["-rf", "build"], rootDir.canonicalPath)

    components.sort { it.component }.each { c ->
        println " - building and deploying component ${c.component}"
        runCmd("make", ["${c.component}-maven"], rootDir.canonicalPath, ["DO_MAVEN_DEPLOY=deploy"])
    }
}


def parseArgs(cliArgs) {
  def cli = new CliBuilder(usage: "bumpMavenVersion.groovy [options]",
          header: "Options")

  cli._(longOpt: 'root-dir', args:1, required: true, "Root directory for cdh.git we're checking and updating")

  cli._(longOpt: 'old-version', args:1, required:true, "CDH Maven version string currently")

  cli._(longOpt: 'new-version', args:1, required:true, "CDH Maven version string we will be changed to")

  cli._(longOpt: 'incremental', "If given, update and build each component individually.")
  
  def options = cli.parse(cliArgs)

  return options
}

def updateOneComponentInParentPom(rootDir, oldVersion, newVersion, props) {
    File pom = new File(rootDir, "pom.xml")
    String pomText = pom.text

    props.each { p ->
        pomText = pomText.replaceFirst(~/\<cdh\.${p}\.version\>(.*?)-cdh${oldVersion}\<\/cdh\.${p}\.version\>/) {
            "<cdh.${p}.version>${it[1]}-cdh${newVersion}</cdh.${p}.version>"
        }
    }
    pom.write(pomText)
}

def updateMakefileAndParentPom(rootDir, oldVersion, newVersion, boolean justParent=false) {
    File makeFile = new File(rootDir, "Makefile")
    String mfText = makeFile.text
    mfText = mfText.replaceAll("cdh${oldVersion}",
            "cdh${newVersion}")
    def snapLessOld = oldVersion.replaceAll("-SNAPSHOT", "")
    def snapLessNew = newVersion.replaceAll("-SNAPSHOT", "")
    // Just in case, also replace any non-SNAPSHOT occurences
    if (oldVersion.contains("SNAPSHOT")) {
        mfText = mfText.replaceAll("cdh${snapLessOld}", "cdh${snapLessNew}")
    }
    if (newVersion.contains("SNAPSHOT")) {
        mfText = mfText.replaceAll(/CDH_VERSION_STRING \?\= .*/, "CDH_VERSION_STRING ?= cdh\\\$(LONG_VERSION)-SNAPSHOT")
    } else {
        mfText = mfText.replaceAll(/CDH_VERSION_STRING \?\= .*/, "CDH_VERSION_STRING ?= cdh\\\$(LONG_VERSION)")
    }

    if (!snapLessOld.equals(snapLessNew)) {
        mfText = mfText.replaceAll("LONG_VERSION ?= ${snapLessOld}", "LONG_VERSION ?= ${snapLessNew}")
    }
    
    makeFile.write(mfText)

    File pom = new File(rootDir, "pom.xml")
    String pomText = pom.text
    pomText = pomText.replaceFirst("<version>${oldVersion}</version>", "<version>${newVersion}</version>")
    pomText = pomText.replaceFirst("<cdh.parent.version>${oldVersion}</cdh.parent.version>",
            "<cdh.parent.version>${newVersion}</cdh.parent.version>")
    pomText = pomText.replaceFirst("<cdh.cdh-parcel.version>${oldVersion}</cdh.cdh-parcel.version>",
            "<cdh.cdh-parcel.version>${newVersion}</cdh.cdh-parcel.version>")
    if (!justParent) { 
        pomText = pomText.replaceAll("cdh${oldVersion}", "cdh${newVersion}")
    }
    pom.write(pomText)

    if (!snapLessOld.equals(snapLessNew)) {
        File cdhMk = new File (rootDir, "cdh5.mk")
        
        String cdhText = cdhMk.text
        cdhText = cdhText.replaceAll(/GPLEXTRAS_PARCEL_BASE_VERSION\=.*/, "GPLEXTRAS_PARCEL_BASE_VERSION=${snapLessNew}")
        cdhText = cdhText.replaceAll(/CDH_PARCEL_BASE_VERSION\=.*/, "CDH_PARCEL_BASE_VERSION=${snapLessNew}")
        cdhMk.write(cdhText)
    }
}


def replaceRootVersion(rootDir, oldVersion, newVersion) {
    replaceRootVersion(rootDir, oldVersion, newVersion, "**/.git,impala/**/*,**/hue/apps/jobsub/src/jobsubd,**/hue/data")
}

def replaceRootVersion(rootDir, oldVersion, newVersion, excludes) {
    def filesToUpdate = new AntBuilder().fileScanner {
        fileset(dir: rootDir,
                includes: "**/*pom.xml*",
                excludes: excludes) {
            contains(text:"cdh-root")
        }
    }

    filesToUpdate.each { File f ->
        println "Replacing root version in ${f.canonicalPath}"
        String fileText = f.text
        fileText = fileText.replaceFirst("<version>${oldVersion}</version>", "<version>${newVersion}</version>")
        f.write(fileText)
    }
}

def replaceCdhVersion(rootDir, oldVersion, newVersion) {
    replaceCdhVersion(rootDir, oldVersion, newVersion, "**/.git,impala/**/*,**/hue/apps/jobsub/src/jobsubd,**/hue/data")
}

def replaceCdhVersion(rootDir, oldVersion, newVersion, excludes) {
    def filesToUpdate = new AntBuilder().fileScanner {
        fileset(dir: rootDir,
                includes: "**/*",
                excludes: excludes) {
            contains text:"cdh${oldVersion}"
        }
    }

    filesToUpdate.each { File f ->
        println "Replacing CDH version in ${f.canonicalPath}"
        String fileText = f.text
        fileText = fileText.replaceAll("cdh${oldVersion}", "cdh${newVersion}")
        f.write(fileText)
    }
}


def runCmd(exe, cmdv, rootDir, cmdEnv=[], ignoreFailure=false) {
    def cmdAnt = new AntBuilder()
    def attempts = 0
    def finished = false

    while (!finished) {
      attempts += 1

      cmdAnt.exec(dir:rootDir,
              resultproperty:'cmdExit',
              executable:exe) {
        redirector(outputproperty:'cmdOut', errorproperty:'cmdErr', alwayslog:true)
        cmdv.each { a ->
          arg(value:a)
        }
        cmdEnv.each { c ->
          def cmdSp = c.tokenize('=')
          env(key:cmdSp[0], value:cmdSp[1])
        }
      }
      if (ignoreFailure) {
        finished = true
      } else if (cmdAnt.project.properties.cmdExit != '0') {
        if (attempts < 6 && cmdAnt.project.properties.cmdErr =~ /Input\/output error/) {
          println "Transient nfs error in command execution - retrying."
          sleep 5000
        } else {
          finished = true
          assert cmdAnt.project.properties.cmdExit == '0', "Error executing \"${exe} ${cmdv}\" with env ${cmdEnv}: ${cmdAnt.project.properties.cmdErr}"
        }
      } else {
        finished = true
      }
    }
    return cmdAnt.project.properties.cmdOut.trim()
  }
