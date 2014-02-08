#!/usr/bin/env groovy

// This is only meant to run on the child repos - gotta rig something else for cdh.git proper.

def opts = parseArgs(this.args)

updateMakefileAndParentPom(opts.'root-dir', opts.'old-version', opts.'new-version')
replaceCdhVersion(opts.'root-dir' + "/repos", opts.'old-version', opts.'new-version')
replaceRootVersion(opts.'root-dir' + "/repos", opts.'old-version', opts.'new-version')

def parseArgs(cliArgs) {
  def cli = new CliBuilder(usage: "bumpMavenVersion.groovy [options]",
          header: "Options")

  cli._(longOpt: 'root-dir', args:1, required: true, "Root directory for cdh.git we're checking and updating")

  cli._(longOpt: 'old-version', args:1, required:true, "CDH Maven version string currently")

  cli._(longOpt: 'new-version', args:1, required:true, "CDH Maven version string we will be changed to")

  def options = cli.parse(cliArgs)

  return options
}

def updateMakefileAndParentPom(rootDir, oldVersion, newVersion) {
    File makeFile = new File(rootDir, "Makefile")
    String mfText = makeFile.text
    mfText = mfText.replaceAll("CDH_VERSION_STRING ?= cdh${oldVersion}",
            "CDH_VERSION_STRING ?= cdh${newVersion}")
    makeFile.write(mfText)

    File pom = new File(rootDir, "pom.xml")
    String pomText = pom.text
    pomText = pomText.replaceFirst("<version>${oldVersion}</version>", "<version>${newVersion}</version>")
    pomText = pomText.replaceFirst("<cdh.parent.version>${oldVersion}</cdh.parent.version>",
            "<cdh.parent.version>${newVersion}</cdh.parent.version>")
    pomText = pomText.replaceFirst("<cdh.cdh-parcel.version>${oldVersion}</cdh.cdh-parcel.version>",
            "<cdh.cdh-parcel.version>${newVersion}</cdh.cdh-parcel.version>")
    pomText = pomText.replaceAll("cdh${oldVersion}", "cdh${newVersion}")
    pom.write(pomText)
}

def replaceRootVersion(rootDir, oldVersion, newVersion) {
    def filesToUpdate = new AntBuilder().fileset(dir: rootDir,
            includes: "**/*",
            excludes: "**/.git,impala/**/*,hue/apps/jobsub/src/jobsubd,hue/data") {
        and(contains(text:"cdh-root"),
                filename(name:"**/*pom.xml*"))
    }*.files

    filesToUpdate.each { File f ->
        String fileText = f.text
        fileText = fileText.replaceFirst("<version>${oldVersion}</version>", "<version>${newVersion}</version>")
        f.write(fileText)
    }
}

def replaceCdhVersion(rootDir, oldVersion, newVersion) {
    def filesToUpdate = new AntBuilder().fileset(dir: rootDir,
            includes: "**/*",
            excludes: "**/.git,impala/**/*,hue/apps/jobsub/src/jobsubd,hue/data") {
        contains text:"cdh${oldVersion}"
    }*.files

    filesToUpdate.each { f ->
        String fileText = f.text
        fileText = fileText.replaceAll("cdh${oldVersion}", "cdh${newVersion}")
        f.write(fileText)
    }
}

