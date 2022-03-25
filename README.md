# sog
Simple Ontology Grep (sog) is a project providing a REST API to search a collection of ontologies for a keyword.  
The keyword lookup currently uses SymSpell with labels extracted from the ontologies, where labels may be `rdfs:label`, `skos:prefLabel`, or `skos:altLabel`.  


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

The application will take a while to load and requires a lot of memory.  
If the JVM's default heap space is too low and the application crashes out before completing startup then amend the *project.clj* map to contain a `:jvm-opts` entry like so:
```clj
(defproject sog "0.1.0"
  :description "Simple Ontology Grep. Greps ontologies."
  ...
  :jvm-opts ["-Xmx6g"]
  ...
  :profiles {:uberjar {:aot :all}})
```
Here we have given 6 gigabytes, but any value supported by your JVM is possible.  
See the [documentation](https://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/jrdocs/refman/optionX.html) for specifying `-Xmx` and `-Xms` values.
## Usage

You may start the program with `lein run -- --conf config.edn`.  
Settings are stored in `config.edn`, but may also be passed in as command-line args (e.g. `lein run -- --port 9090 --tdb-dir "./data/tdb"`) or environment variables (e.g. `TDB_DIR=./data/tdb lein run`).  
Options are parsed in the following order: cli args, env vars, conf file.  

No database or other external service is required to be launched.

### API
The following is an example of the output given for accessing the term search API found at `/lookup/<term>`, here searching for "lung cancer" by performing a GET on `/lookup/lung cancer`:  

```json
[
    {
        "distance": 0.0,
        "meta": {
            "labels": [
                "cancer"
            ],
            "objects": {
                "http://www.geneontology.org/formats/oboInOwl#inSubset": {
                    "label": [
                        "in_subset"
                    ],
                    "objects": [
                        {
                            "iri": "http://purl.obolibrary.org/obo/doid#DO_cancer_slim",
                            "labels": [
                                "DO_cancer_slim"
                            ]
                        },
                        {
                            "iri": "http://purl.obolibrary.org/obo/doid#DO_RAD_slim",
                            "labels": [
                                "DO_RAD_slim"
                            ]
                        },
                        {
                            "iri": "http://purl.obolibrary.org/obo/doid#DO_FlyBase_slim",
                            "labels": [
                                "DO_FlyBase_slim"
                            ]
                        },
                        {
                            "iri": "http://purl.obolibrary.org/obo/doid#DO_CFDE_slim",
                            "labels": [
                                "DO_CFDE_slim"
                            ]
                        },
                        {
                            "iri": "http://purl.obolibrary.org/obo/doid#DO_AGR_slim",
                            "labels": [
                                "DO_AGR_slim"
                            ]
                        },
                        {
                            "iri": "http://purl.obolibrary.org/obo/doid#NCIthesaurus",
                            "labels": [
                                "NCIthesaurus"
                            ]
                        },
                        {
                            "iri": "http://purl.obolibrary.org/obo/doid#DO_GXD_slim",
                            "labels": [
                                "DO_GXD_slim"
                            ]
                        }
                    ]
                }
            },
            "subjects": {}
        },
        "term": "cancer",
        "url": "http://purl.obolibrary.org/obo/DOID_162"
    },
    {
        "distance": 3.0,
        "meta": {
            "labels": [
                "acne"
            ],
            "objects": {},
            "subjects": {}
        },
        "term": "acne",
        "url": "http://purl.obolibrary.org/obo/DOID_6543"
    }
]
```

## Options

- conf: EDN-formatted config file to load options from
- tdb-dir: Directory to create/store Jena TDB datastore
- port: Port to serve the REST API over
- ontology-dir: Directory containing ontology files
- ontologies: EDN-formatted list of ontology files (relative to ontology-dir)
- string-cache-dir: Directory to keep EDN file of term URL/label lookup data, and list of ontologies already loaded into the database
- distance: Maximum edit-distance to index

### Bugs

- Current memory usage is very high
- Ontology terms may not contain tabs

## Details

The purpose of this application is to provide a remote API for providing fuzzy matches for terms in a set of ontologies.  
At present this is powered by the SymSpell algorithm and uses a Damerau-Levenshtein edit distance metric.  
After the application loads the ontologies into Jena TDB (the triple store) it queries the ontologies for all term labels and builds a concept URI/labels map and stores this to disk.  
A SymSpell dictionary is then produced from this and a REST API is exposed on the configured port to take query terms and return a ranked list of potential candidate terms, ranked by difference in length and edit distance.

### Damerau-Levenshtein edit distance

A standard [Levenshtein](https://en.wikipedia.org/wiki/Levenshtein_distance) distance is the minimal number of single-character insertions, deletions, or substitutions needed to go from the source to target string.  

| Source | Target | Distance | Insertions | Deletions | Substitutions
| --- | --- | --- | --- | --- | ---
| Take | Taken | 1 | 1 | 0 | 0
| Bridge | Ridge | 1 | 0 | 1 | 0
| Fool | Cool | 1 | 0 | 0 | 1
| Codiene | Codeine | 2 | 0 | 0 | 2
| Auscultation | Oscalation | 4 | 0 | 1 | 3

The [Damerau-Levenshtein](https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance) distance differs in that it considers transposition of two adjacent characters as a single operation.
This allows for certain misspellings to have a reduced edit distance and be higher ranked than they otherwise would.
In the case of the above table, it would mean "Codiene" would only be a single edit operation away from (the correct) "Codeine" as the "ei" characters are transposed.

### SymSpell

[SymSpell](https://github.com/wolfgarbe/symspell) is a highly optimized spell correction algorithm claiming to be "six orders of magnitude faster" than the classic [norvig](https://norvig.com/spell-correct.html) algorithm.  

Rather than calculating additions/deletions/substitutions of the input term, it instead pre-calculates deletions on the target terms.

### 

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
