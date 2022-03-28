# sog
Simple Ontology Grep (sog) is a project providing a REST API to search a collection of ontologies for a keyword.  
The keyword lookup currently uses Lucene with labels extracted from the ontologies, where labels are by default `rdfs:label`, `skos:prefLabel`, or `skos:altLabel`.  


![sog](https://user-images.githubusercontent.com/8222624/160463111-2c3fc133-d3e3-4d90-bf08-9a7f24530971.jpg)

## Installation

Clone the repo, and run `lein deps` to get started.  
## Quick-start

The fastest way to get up and running with sog is the following:
* `git clone 'https://github.com/AberystwythSystemsBiology/sog.git'`
* `cd sog`
* `mkdir -p data/{cache,ont,tdb}`
* Copy your ontology files (in RDF/XML format) into the *./data/ont* directory
* Edit the `:ontologies` entry in *config.edn* to list your ontology files (e.g. `:ontologies ["doid.owl" "icd10.owl"]`)
* `lein run -- --conf config.edn`

The application will take a while to load.
Jena works quite efficiently but if it requires more memory then the *project.clj* may be modified to provide more.  
Add a `:jvm-opts` entry like so:
```clj
(defproject sog "0.9.0"
  :description "Simple Ontology Grep. Greps ontologies."
  ...
  :jvm-opts ["-Xmx6g"]
  ...
  :profiles {:uberjar {:aot :all}})
```
Here we have given 6 gigabytes, but any value supported by your JVM is possible.  
See the [documentation](https://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/jrdocs/refman/optionX.html) for specifying `-Xmx` and `-Xms` values.  
Please note this is not the ideal way to specify this value, but if you are not familiar with Leiningen and Clojure then I recommend you do it this way.  
Should I push a change that modifies the `project.clj` then you may need to temporarily undo your changes to pull the new version.  
I may add better instructions for this in the future.  

## Usage

You may start the program with `lein run -- --conf config.edn`.  
Settings are stored in `config.edn`, but may also be passed in as command-line args (e.g. `lein run -- --port 9090 --tdb-dir "./data/tdb"`) or environment variables (e.g. `TDB_DIR=./data/tdb lein run`).  
Options are parsed in the following order: cli args, env vars, conf file.  

No database or other external service is required to be launched.

### API
The following is an example of the output given for accessing the term search API found at `/lookup/<term>`, here searching for "lung cancer" by performing a GET on `/lookup/lung cancer`:  

```json
{
[
    {
        "labels": [
            "lung cancer"
        ],
        "objects": {
            "http://www.geneontology.org/formats/oboInOwl#inSubset": {
                "labels": [
                    "in_subset"
                ],
                "subjects": {
                    "http://purl.obolibrary.org/obo/doid#DO_CFDE_slim": {
                        "labels": [
                            "DO_CFDE_slim"
                        ]
                    },
                    "http://purl.obolibrary.org/obo/doid#DO_cancer_slim": {
                        "labels": [
                            "DO_cancer_slim"
                        ]
                    },
                    "http://purl.obolibrary.org/obo/doid#TopNodes_DOcancerslim": {
                        "labels": [
                            "TopNodes_DOcancerslim"
                        ]
                    }
                }
            }
        },
        "subjects": {},
        "uri": "http://purl.obolibrary.org/obo/DOID_1324"
    },
    {
        "labels": [
            "lung carcinoma",
            "cancer of lung"
        ],
        "objects": {
            "http://www.geneontology.org/formats/oboInOwl#inSubset": {
                "labels": [
                    "in_subset"
                ],
                "subjects": {
                    "http://purl.obolibrary.org/obo/doid#DO_cancer_slim": {
                        "labels": [
                            "DO_cancer_slim"
                        ]
                    },
                    "http://purl.obolibrary.org/obo/doid#NCIthesaurus": {
                        "labels": [
                            "NCIthesaurus"
                        ]
                    }
                }
            }
        },
        "subjects": {},
        "uri": "http://purl.obolibrary.org/obo/DOID_3905"
    }
}
```

The top-level array contains each match.  
Inside these match objects are four sections:  

* **"uri"**: The concept's URI
* **"labels"**: All of the concept's labels
* **"subjects"**: All triples where this concept is the subject
* **"objects"**: All triples where this concept is the object

Note that "all triples" here actually means all triples that take the simple form `<uri> <uri> <uri>`, so excludes blank nodes and literals.  
It also requires that both the predicate and the other node have at least one label.  

Inside the "subjects" and "objects" maps are keys of the predicate URI for the triple, and the values are a map of the predicate's label and the matching node's URI and label.  
To take an example, if the following triples were loaded into the datastore:  

```turtle
:ObjectA rdfs:label "Object A" ;
         foaf:knows :ObjectB ;
         foaf:knows :MysteriousObject ;
         example:predicate :ObjectB .
:ObjectB rdfs:label "Object B ;
         skos:prefLabel "ObbyBobject B" .
:MysteriousObject foaf:knows :ObjectA ;
                  foaf:knows :ObjectB .
```

Assuming the following:  
* rdfs and foaf are loaded into the triplestore
* The base URI for these triples is `http://example.com/ontology#`
* The `example:predicate` concept has no labels

Then the results should look roughly as follows:  

```json
[
    {
        "uri": "http://example.com/ontology#ObjectA",
        "labels": [
            "Object A"
        ],
        "objects": {},
        "subjects": {
            "http://xmlns.com/foaf/0.1/knows": {
                "labels": [
                    "knows"
                ],
                "objects": {
                    "http://example.com/ontology#ObjectB": {
                        "labels": [
                            "Object B",
                            "ObbyBobject B"
                        ]
                    }
                }
            }
        
        }
    },
    {
        "uri": "http://example.com/ontology#ObjectB",
        "labels": [
            "Object B",
            "ObbyBobject B"
        ],
        "objects": {
          "http://xmlns.com/foaf/0.1/knows": {
                "labels": [
                    "knows"
                ],
                "subjects": {
                    "http://example.com/ontology#ObjectA": {
                        "labels": [
                            "Object A"
                        ]
                    }
                }
            }
        },
        "subjects": {}
    }
]

```
The `:MysteriousObject` appears nowhere because it has no labels, and the `example:predicate` relation does not appear under Subject A because it also has no label.  


## Options

- **conf**: EDN-formatted config file to load options from
- **tdb-dir**: Directory to create/store Jena TDB datastore
- **port**: Port to serve the REST API over
- **ontology-dir**: Directory containing ontology files
- **ontologies**: EDN-formatted list of ontology files (relative to ontology-dir)
- **lucene-dir**: Directory to create Lucene indexes in
- **labels**: List of label URIs to index for full text search

### Bugs

* Term is directly parsed as Lucene search term. This may have unintended consequences.
* Ordering relevance of terms is currently not ideal
## Details

The purpose of this application is to provide a remote API for providing fuzzy matches for terms in a set of ontologies.  
At present this is powered by the Apache Lucene full text search functionality provided by Apache Jena.  

Upon starting, the application checks its triplestore to see which ontologies are currently loaded then loads any that are specified in the config file but not present in the triple store.  
As the TDB triple store is wrapped with a Lucene Datastore, text indexes are generated when the ontologies are loaded in.  

Once the application has finished loading ontologies, it provides a REST API (default port: 9090) with a single valid route: `/lookup/<term>`  
Accessing this path with an HTTP GET request will perform a full-text search via Apache Jena for any concepts that vaguely contain this term in one of their labels.  

The query also looks up additional information about the concepts to provide a rough summary of the concept.  
At present this only includes the concept's relations to other concepts, and only when both the predicate and other concept have at least one label each.  

The SPARQL query that achives this is as follows:  

```sparql
SELECT * WHERE {
  {
    SELECT DISTINCT ?uri WHERE {
      ?uri text:query ? .
    } LIMIT 100
  }
  {
    { ?uri <http://www.w3.org/2000/01/rdf-schema#label> ?label } UNION { ?uri <http://www.w3.org/2004/02/skos/core#prefLabel> ?label } UNION { ?uri <http://www.w3.org/2004/02/skos/core#altLabel> ?label } .
  }
  UNION
  {
    ?uri ?p ?o .
    { ?p <http://www.w3.org/2000/01/rdf-schema#label> ?pLabel } UNION { ?p <http://www.w3.org/2004/02/skos/core#prefLabel> ?pLabel } UNION { ?p <http://www.w3.org/2004/02/skos/core#altLabel> ?pLabel } .
    { ?o <http://www.w3.org/2000/01/rdf-schema#label> ?oLabel } UNION { ?o <http://www.w3.org/2004/02/skos/core#prefLabel> ?oLabel } UNION { ?o <http://www.w3.org/2004/02/skos/core#altLabel> ?oLabel } .
  }
  UNION
  {
    ?s ?p ?uri .
    { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?sLabel } UNION { ?s <http://www.w3.org/2004/02/skos/core#prefLabel> ?sLabel } UNION { ?s <http://www.w3.org/2004/02/skos/core#altLabel> ?sLabel } .
    { ?p <http://www.w3.org/2000/01/rdf-schema#label> ?pLabel } UNION { ?p <http://www.w3.org/2004/02/skos/core#prefLabel> ?pLabel } UNION { ?p <http://www.w3.org/2004/02/skos/core#altLabel> ?pLabel } .
  }
} LIMIT 5000
```

This is a prepared statement and as such the lone `?` in the `text:query` triple is substituted for the query term provided over REST.  
Additionally the UNION query fragments depend upon the label predicates specified in config file.
## Security

Please run [nvd-clojure](https://github.com/rm-hull/nvd-clojure) against the project before deciding to use it.  
Install instructions may be found in the official repository, after which you should run the following while in the root of this repository:  
`clojure -J-Dclojure.main.report=stderr -Tnvd nvd.task/check :classpath '"'"$(lein with-profile -user classpath)"'"'`
To locate which direct dependency is pulling in a vulnerable indirect dependency, run `lein deps :tree` to see the dependency tree.  

## License

Copyright © 2020 Rob Bolton

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
