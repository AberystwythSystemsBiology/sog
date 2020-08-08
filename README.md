# sog
Simple Ontology Grep (sog) is a project providing a REST API to search a collection of ontologies for a keyword.  
The keyword lookup currently uses SymSpell with labels extracted from the ontologies, where labels may be `rdfs:label`, `skos:prefLabel`, or `skos:altLabel`.  


## Installation

Clone the repo, and run `lein deps` to get started.  
The [@Lundez](https://github.com/Lundez/JavaSymSpell) SymSpell library is not available on Maven, so you must first clone, build, and install it.  
Once built, install the jar to your local maven repo:
```
mvn install:install-file 
   -Dfile=symspell.jar \
   -DgroupId=org.lundez \
   -DartifactId=symspell \
   -Dversion=1.0-SNAPSHOT \
   -Dpackaging=jar \
   -DgeneratePom=true`
```

This is now accessible to leiningen and, by extension, SOG.
## Usage

You may start the program with `lein run -- --conf config.edn`.  
Settings are stored in `config.edn`, but may also be passed in as command-line args (e.g. `lein run -- --port 9090 --tdb-dir "./data/tdb"`) or environment variables (e.g. `TDB_DIR=./data/tdb lein run`).  
Options are parsed in the following order: cli args, env vars, conf file.  


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
