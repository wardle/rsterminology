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

## Getting started

### Download the source-code from github.

```
    git clone https://github.com/wardle/rsterminology.git
```
    
### Compile using maven.

```
cd rsterminology
mvn package
```    

Note: this step requires unit tests to work with appropriate runtime configuration. Have a look at the run.yml file within rsterminology-server.

    jdbc:
      rsdb:
        url: jdbc:postgresql:rsdb
        driver: org.postgresql.Driver
    
    cayenne:
      datasource: rsdb
      createSchema: false


For example, you may need to change your database url.
  
### Locate and run the executable jar file

```
cd rsterminology-server/target
java -jar rsterminology-server-1.0-SNAPSHOT.jar
```

This should show the help for the rsterminology server software. This will look something like this:
    
```
    Option                    Description                           
    ------                    -----------                           
    --browser                 Browse and search SNOMED-CT           
                                interactively.                      
    --buildparentcache        Rebuilds the concept parent cache. Use
                                after updating concepts from a new  
                                release.                            
    --config <yaml_location>  Specifies YAML config location, which 
                                can be a file path or a URL.        
    --createindex             Builds a new lucene index.            
    --help                    Prints this message.                  
    --importrf1               Import concepts, descriptions and     
                                relationships for SNOMED-CT in RF1  
                                format.                             
    --server                  Starts Jetty server
```

### Create your own configuration file

You should use the supplied run.yml file from within rsterminology-server as a starting point. Essentially, you will need to create a database and include the URL in the JDBC configuration within the YML file.

### Import SNOMED-CT

Download a SNOMED-CT release. These releases consist of files representing SNOMED-CT concepts, relationships and descriptions. The order of imports is important. Always import concepts first before importing relationships or descriptions. 

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --importrf1 sctXXX....
```

### Build the parent cache

In order to optimise the runtime operation of SNOMED-CT, there is a database-backed cache of the recursive parent concepts for all concepts. Build this cache using the following command:

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --buildparentcache
```

### Create the search index

In order to support fast free-text searching, you need to create a free-text index. Choose a reasonable location (e.g. I use /var/rsdb/sct_lucene/)

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --createindex /var/rsdb/sct_lucene
```

### And run your server

You can now run a fully-functional SNOMED-CT server providing a very fast (usually <10ms) optimised SNOMED-CT terminology server.

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --server
```


Mark Wardle
12th July 2016