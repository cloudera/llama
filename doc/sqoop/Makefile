
# When we copy the build artifacts into the deployment directory
# for packaging, where do they go?
DEPLOY_TARGET=../../repos/hadoop/src/contrib/sqoop/doc/

all: man webpage

man: sqoop.1

webpage: hadoop-sqoop.html

sqoop.1: Sqoop-manpage.txt
	asciidoc -b docbook -d manpage Sqoop-manpage.txt
	xmlto man Sqoop-manpage.xml

hadoop-sqoop.html: Sqoop-web.txt
	asciidoc Sqoop-web.txt

clean:
	-rm Sqoop-manpage.xml
	-rm hadoop-sqoop.html
	-rm sqoop.1

# copy build artifacts into the hadoop subrepo
deploy: all
	cp sqoop.1 $(DEPLOY_TARGET)/sqoop.1
	cp Sqoop-web.html $(DEPLOY_TARGET)/SqoopUserGuide.html

.PHONY: all man webpage clean deploy

