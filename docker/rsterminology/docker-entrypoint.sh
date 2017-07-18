#!/bin/sh

#wait for postgres to start
while ! timeout 1 bash -c 'cat < /dev/null > /dev/tcp/pg/5432' >/dev/null 2>/dev/null; do sleep 0.1; done
find SnomedCT_RF1Release_* -type f -exec java -jar rsterminology-server.jar --config config.yml --import-rf1 {} ';'
#import all the snowmed files

java -jar rsterminology-server.jar --config config.yml  --build-parent-cache
java -jar rsterminology-server.jar --config config.yml --build-index /var/rsdb/sct_lucene
java -jar rsterminology-server.jar --config config.yml --server
