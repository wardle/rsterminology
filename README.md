This repository has been archived. It has been replaced by a new terminology service/library [hermes](https://github.com/wardle/hermes).

-----

# rsterminology
An open-source SNOMED-CT implementation.  Copyright (C) Dr. Mark Wardle 2007-2016 and Eldrix Ltd 2013-2016

## Licence
 rsterminology is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    rsterminology is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>.

## About
This is a conversion of a legacy WebObjects-based SNOMED-CT web service and administration portal. It is currently running and passing unit tests to query and explore the SNOMED-CT hierarchy.

It is built using Bootique, Apache Cayenne and LinkRest together with Apache Lucene.

For more information, please see [my blog](http://wardle.org/clinical-informatics/2017/04/29/rsterminology-part2.html). There is a live demonstration system that shows a small part of the functionality [here](https://msdata.org.uk/apps/WebObjects/SnomedBrowser.woa/). 


The current version can now:

1. Import data in RF1 (Release format 1) format into a backend database.
2. Build an optimised concept parent cache.
3. Build a backend lucene index to support fast free-text search.

The SNOMED-CT international releases are now made in RF2 file format. At the time of writing RF1 file format files are still available and the DM&D are only available in RF1 format. Future versions might need to add support for RF2 file format and RefSets. The legacy application supported RF1 (Release format 1) as it was written in 2009 but RF2 support will almost certainly be required in the future. There are currently tools available to turn files in RF2 format into RF1 but it would be nice to handle them natively. 

## Getting started

A runnable jar file is available so that you can experiment without having to compile the source code. If you download this from the RELEASES tab on github, you can skip directly to running the runnable jar section.

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


For example, you may need to change your database url. Alternatively, you can run maven without running unit tests.

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
    --build-parent-cache        Rebuilds the concept parent cache. Use
                                after updating concepts from a new
                                release.
    --config <yaml_location>  Specifies YAML config location, which
                                can be a file path or a URL.
    --build-index             Builds a new lucene index.
    --help                    Prints this message.
    --import-rf1               Import concepts, descriptions and
                                relationships for SNOMED-CT in RF1
                                format.
    --server                  Starts Jetty server
```

### Create your own configuration file

You should use the supplied run.yml file from within rsterminology-server as a starting point. Essentially, you will need to create a database and include the URL in the JDBC configuration within the YML file.

### Import SNOMED-CT

Download a SNOMED-CT release. These releases consist of files representing SNOMED-CT concepts, relationships and descriptions. The order of imports is important. Always import concepts first before importing relationships or descriptions.

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --import-rf1 sctXXX....
```

### Build the parent cache

In order to optimise the runtime operation of SNOMED-CT, there is a database-backed cache of the recursive parent concepts for all concepts. Build this cache using the following command:

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --build-parent-cache
```

### Create the search index

In order to support fast free-text searching, you need to create a free-text index. Choose a reasonable location (e.g. I use /var/rsdb/sct_lucene/)

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --build-index /var/rsdb/sct_lucene
```

### And run your server

You can now run a fully-functional SNOMED-CT server providing a very fast (usually <10ms) optimised SNOMED-CT terminology server.

```
java -jar rsterminology-server-1.0-SNAPSHOT.jar --config run.yml --server
```

## Guide to the web-service

You may now use the web-service from your code.

### Obtaining information about a SNOMED-CT concept

```
HTTP GET http://localhost:8080/concept/24700007
```

will return JSON:

```
{
  "data": [
    {
      "id": 24700007,
      "conceptId": 24700007,
      "conceptStatusCode": 0,
      "ctvId": "F20..",
      "fullySpecifiedName": "Multiple sclerosis (disorder)",
      "isPrimitive": 1,
      "snomedId": "DA-25010"
    }
  ],
  "total": 1
}
```

### Searching for a concept

```
HTTP GET http://localhost:8080/concept/search?s=multiple scler&rootIds=64572001
```

will search for the term "multiple scler" using a root concept of 64572001 (which is the concept representing a Disease). You can specify multiple roots using a list of identifiers delimited by commas.

Example result:
```
{
  "data": [
    {
      "conceptId": 24700007,
      "term": "Multiple sclerosis",
      "preferredTerm": "Multiple sclerosis"
    },
  ]
}
```

### Get synonyms for a search term

You may wish to search clinic letters for a specific term. Find the synonyms for the entered search term using this service
```
HTTP GET http://localhost:8080/concept/synonyms?s=heart attack
```
or
```
HTTP GET http://localhost:8080/concept/synonyms?s=mi
```

will result in:

```
{
  "data": [
    "MI - Myocardial infarction",
    "Myocardial infarct",
    "Myocardial infarction",
    "Myocardial infarction, NOS",
    "Infarction of heart, NOS",
    "Cardiac infarction, NOS",
    "Heart attack, NOS",
    "Infarction of heart",
    "Cardiac infarction",
    "Heart attack",
    "Myocardial infarction (disorder)",
    "MR - Mitral regurgitation",
    "Mitral insufficiency",
    "Mitral valve regurgitation (disorder)",
    "Mitral valve regurgitation",
    "Mitral valve regurgitation, NOS",
    "Mitral valve incompetence, NOS",
    "Mitral valve insufficiency, NOS",
    "Mitral regurgitation, NOS",
    "Mitral valve incompetence",
    "Mitral valve insufficiency",
    "Mitral regurgitation",
    "AMI - Acute myocardial infarction",
    "Acute myocardial infarction (disorder)",
    "Acute myocardial infarction",
    "Acute myocardial infarction, NOS",
    "Aborted myocardial infarction",
    "MI - Myocardial infarction aborted",
    "Coronary thrombosis not resulting in myocardial infarction",
    "Coronary thrombosis not resulting in myocardial infarction (disorder)",
    "Silent myocardial infarction",
    "MI - Silent myocardial infarction",
    "Silent myocardial infarction (disorder)",
    "Attack - heart",
    "MI - acute myocardial infarction",
    "MI - Mitral incompetence"
  ]
}
```

which allows you to fetch documents matching the synonyms for MI (which could represent myocardial infarction or mitral incompetence).

There are additional service features - documentation forthcoming.

Mark Wardle
31st July 2016
