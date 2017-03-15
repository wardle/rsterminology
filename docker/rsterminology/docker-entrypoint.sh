#!/bin/sh

while ! timeout 1 bash -c 'cat < /dev/null > /dev/tcp/pg/5432' >/dev/null 2>/dev/null; do sleep 0.1; done
find SnomedCT_RF1Release_* -type f -exec java -jar rsterminology-server.jar --config config.yml --import-rf1 {} ';'
#find SnomedCT_RF1Release_* -type f -exec java -jar rsterminology-server.jar --config config.yml --import-rf1 SnomedCT_RF1Release_GB1000000_20161001/Terminology/Content/sct1_Concepts_National_GB1000000_20161001.txt

java -jar rsterminology-server.jar --config config.yml  --build-parent-cache
java -jar rsterminology-server.jar --config config.yml --build-index /var/rsdb/sct_lucene
java -jar rsterminology-server.jar --config config.yml --server
