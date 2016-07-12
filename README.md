# rsterminology
An open-source SNOMED-CT implementation.

This is a conversion of a legacy WebObjects-based SNOMED-CT web service and administration portal. It is currently running and passing unit tests to query and explore the SNOMED-CT hierarchy.

It is built using Bootique, Apache Cayenne and LinkRest together with Apache Lucene. Initially, it uses an old version of Lucene as most of the code has simply migrated as-is. 

The current version can now:

1. Import data in RF1 (Release format 1) format into a backend database.
2. Build an optimised concept parent cache.
3. Build a backend lucene index to support fast free-text search.

The current development targets are:

1. Upgrade Lucene to a latest version [complex]
2. Add support to filter based on immediate IS-A relationships (direct parent concepts) [simple]
3. Add support for RF2 file format and RefSets. The legacy application supported RF1 (Release format 1) as it was written in 2009 but RF2 support is required. [complex]

I plan to release step-by-step instructions in the next few days on how to build and run this application as well as the steps required to bootstrap a working SNOMED-CT server.

Mark Wardle
12th July 2016