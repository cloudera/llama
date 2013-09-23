#!/usr/bin/env groovy

@Grab(group='org.apache.commons', module='commons-vfs2', version='2.0')
@Grab(group='commons-httpclient', module='commons-httpclient', version='3.1')
@Grab(group='jdom', module='jdom', version='1.0')
@Grab(group='commons-collections', module='commons-collections', version='3.1')
@Grab(group='commons-net', module='commons-net', version='1.4.1')
@Grab(group='commons-logging', module='commons-logging', version='1.0.4')
@Grab ( 'org.testng:testng:6.8.1' )


import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileSelectInfo;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Listeners;
import org.testng.Assert;
import org.testng.ITestResult;

import org.testng.reporters.JUnitReportReporter;

@Listeners([ DynamicJUnitReportReporter.class ])
class HarmonizationTest {
  String name;
  Map versions;

  @DataProvider(name = "jars")
  public static Object[][] createData ( ) {
    def jarMap = [:];
    String sourceTar = System.getenv("HARMONY_MAP_TAR_URL");
    FileSystemManager fsManager = VFS.getManager();
    FileObject file = fsManager.resolveFile( "tgz:$sourceTar" );

    file.findFiles(new AllFileSelector() { public boolean includeFile(final FileSelectInfo fileInfo) {
                                         def parser = (fileInfo.getFile().getName().getBaseName() =~ /(.*?)-([0-9]+.*).jar/);
                                         if (parser.matches()) {
                                           def component = parser[0][1];
                                           def version = parser[0][2];

                                           // filter out SNAPSHOTS e.g: 20130921.084903-106
                                           version = version.replaceFirst(/-201[0-9]{5}\.[0-9]{6}-[0-9]{1,3}/, "-SNAPSHOT"); 

                                           // remove classifier from the version and add it to the component name
                                           [ "sources", "javadoc", "tests", "smoketests", "job", 
                                             "jar-with-dependencies", "withouthadoop" ].each {
                                             if (version =~ "-${it}\$") {
                                               version = version.replaceFirst("-${it}\$", '');
                                               component = "$component-$it"
                                             }
                                           }

                                           // special-case MR1 artifacts
                                           version = version.replaceFirst(/-mr1-/, '-');

                                           if (jarMap[component] == null) {
                                             jarMap[component] = [:];
                                           }
                                           if (jarMap[component][version] == null) {
                                             jarMap[component][version] = [];
                                           }
                                           jarMap[component][version].add(fileInfo.getFile().getName().getPath());
                                         }
                                         return false;
                                       }
                                     });

    Object[][] result = new Object[jarMap.size()][2];
    jarMap.eachWithIndex { ent, idx -> result[idx][0] = ent.getKey(); result[idx][1] = ent.getValue() }
    return result;
  }

  @Factory(dataProvider = "jars")
  public HarmonizationTest(String name, Map versions) {
    this.name = name;
    this.versions = versions;
  }

  @Test
  public void unique() {
    Assert.assertEquals(this.versions.size(), 1, "Found the following ${this.versions.size()} different versions:\n${this.versions.toString().tr(',', '\n').replaceAll(']', ']\n\n')}");
  }
 
  public String toString() {
    return this.name;
  }
}

class DynamicJUnitReportReporter extends JUnitReportReporter {
  @Override
  protected String getFileName(Class cls) {
    return "DYNAMIC-TEST-" + cls.getName() + ".xml";
  }

  @Override
  protected String getTestName(ITestResult tr) {
    return tr.getInstanceName();
    // return tr.getMethod().getMethodName();
  }
}
