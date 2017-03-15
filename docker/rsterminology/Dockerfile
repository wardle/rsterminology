
from openjdk:7


COPY ../rsterminology-server/target/rsterminology-server-1.1-SNAPSHOT.jar
COPY config.yml .
COPY snomedCT_InternationalRF1_*.zip snomedCT_International.zip

#Import SNOMED-CT
#needs a sct file
# http://download.nlm.nih.gov/umls/kss/IHTSDO20170131/SnomedCT_InternationalRF2_Production_20170131T120000.zip
RUN java -jar rsterminology-server.jar --config run.yml --importrf1 snomedCT_International.zip
RUN rm snomedCT_International.zip

#Build the parent cache
RUN java -jar rsterminology-server.jar --config run.yml --buildparentcache

#Create the search index

RUN java -jar rsterminology-server.jar --config run.yml --createindex /var/rsdb/sct_lucene

EXPOSE 8080

CMD java -jar rsterminology-server.jar --config run.yml --server
