
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

.PHONY: all man webpage clean
