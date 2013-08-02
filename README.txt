Llama 1.0.0-cdh5.0.0-SNAPSHOT

* Llama Thrift definition

  llama-thrift-api/src/main/thrift/Llama.thrift

* Requirements:

  JDK 1.6+
  Maven 3.0.4+
  Thrift compiler 0.9.9

* Build commands:

  Clean                  : mvn clean
  Compile                : mvn compile
  Run tests              : mvn test
  Install in ~/.m2 cache : mvn install
  Run clover (coverage)  : mvn clean test -Pclover clover2:aggregate clover2:clover (1)
  Create dist TARBALL    : mvn package assembly single -PdistWithMock

  [1: you need a clover license at ${user.home}/.clover.license]
  
* Running Llama Thrift AM server from TARBALL

  Create dist TARBALL, built in llama-dist/target
  
  Expand dist TARBALL

  From expanded root directory, run 'llama'

* Using Llama Thrift Mini AM

  Use the following dependency in your project POM

    <dependency>
      <groupId>com.cloudera.llama</groupId>
      <artifactId>llama-thrift-mini-am</artifactId>
      <version>1.0.0-cdh5.0.0-SNAPSHOT</version>
      <scope>test</scope>
    </dependency>

  If Llama was not built and installed in local ~/.m2 cache, make
  sure the following snapshot repository is in your project POM:

    <repository>
      <id>cdh.snapshots.repo</id>
      <url>https://repository.cloudera.com/content/repositories/snapshots</url>
      <name>Cloudera Snapshots Repository</name>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
      <releases>
        <enabled>false</enabled>
      </releases>
    </repository>


