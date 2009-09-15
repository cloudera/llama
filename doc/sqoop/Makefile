
# When we copy the build artifacts into the deployment directory
# for packaging, where do they go?
DEPLOY_TARGET=../../repos/hadoop/build/contrib/sqoop/doc/

# Where do source .txt files come from?
SOURCES=../../repos/hadoop/src/contrib/sqoop/doc/

all: man webpage

man: sqoop.1.gz

webpage: hadoop-sqoop.html

sqoop.1.gz: Sqoop-manpage.txt troubleshooting.txt $(SOURCES)/*.txt
	asciidoc --unsafe -b docbook -d manpage Sqoop-manpage.txt
	xmlto man Sqoop-manpage.xml
	-rm sqoop.1.gz
	gzip sqoop.1

hadoop-sqoop.html: Sqoop-web.txt troubleshooting.txt $(SOURCES)/*.txt
	asciidoc --unsafe Sqoop-web.txt

clean:
	-rm Sqoop-manpage.xml
	-rm Sqoop-web.html
	-rm hadoop-sqoop.html
	-rm sqoop.1.gz

# copy build artifacts into the hadoop subrepo
deploy: all
	mkdir -p $(DEPLOY_TARGET)
	cp sqoop.1.gz $(DEPLOY_TARGET)/sqoop.1.gz
	cp Sqoop-web.html $(DEPLOY_TARGET)/SqoopUserGuide.html

.PHONY: all man webpage clean deploy

