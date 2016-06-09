# rsterminology
An open-source SNOMED-CT implementation.

This is a conversion of a legacy WebObjects-based SNOMED-CT web service and administration portal. It is currently running and passing unit tests to query and explore the SNOMED-CT hierarchy.

It is built using Bootique, Apache Cayenne and LinkRest together with Apache Lucene. Initially, it uses an old version of Lucene as most of the code has simply migrated as-is. 
At the moment, it is reliant on an existing database backend created by the legacy software but an early goal will be to make this self-sufficient so it can initialise its database itself.
It already can rebuild a backend lucene index and build a concept parent cache.

Immediate development plans:

1. Add command-line functionality to support importing concept, description and relationship data in different release formats. The legacy application supported RF1 (Release format 1) as it was written in 2009 but RF2 support is required including adding support for RefSets.
2. Add support for choosing where to create the lucene index file.
3. Upgrade Lucene to a latest version.

Mark Wardle