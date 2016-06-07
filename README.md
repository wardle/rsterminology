# rsterminology
An open-source SNOMED-CT implementation.

This is a conversion of a legacy WebObjects-based SNOMED-CT web service and administration portal. It is currently running and passing unit tests to query and explore the SNOMED-CT hierarchy.

It is built using Bootique, Apache Cayenne and LinkRest together with Apache Lucene. Initially, it uses an old version of Lucene as most of the code has simply migrated as-is. 
At the moment, it is reliant on an existing database and lucene index backend created by the legacy software but an early goal will be to make this self-sufficient so it can initialise its database itself.

Immediate development plans:

1. Add command-line functionality to support importing concept, description and relationship data in different release formats.
2. Improve building of free-text search indexing using Apache Lucene.3
3. Upgrade Lucene to a latest version.

Mark Wardle