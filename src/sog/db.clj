(ns sog.db
  (:import [java.nio.file FileSystems]
           [org.apache.jena.tdb TDBFactory]
           [org.apache.jena.sparql.core Var]
           [org.apache.jena.query QueryFactory
            QueryExecutionFactory
            Dataset
            ReadWrite]
           [org.apache.jena.graph NodeFactory]
           [org.apache.jena.query.text
            EntityDefinition
            TextDatasetFactory
            TextIndexConfig]
           [org.apache.lucene.store Directory NIOFSDirectory])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :refer [difference]]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [omniconf.core :as cfg]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]))


;; Initialisation requires self-reference for DbState
(def DbState)

(defn as-path
  "Coerce args into a Path as parts, using default FileSystems"
  [& paths]
  (.getPath (FileSystems/getDefault)
            (first paths)
            (into-array String (rest paths))))

(defmacro with-dataset
  "Given a dataset, perform ~body on it and wrap in a transaction, catching exceptions and safely ending transaction on failure"
  [dataset readwrite body]
  `(try
    (do
      (.begin ~dataset ~readwrite)
      (let [result# ~body]
        (when (= ~readwrite ReadWrite/WRITE)
          (.commit ~dataset))
        (.end ~dataset)
        result#))
    (catch Exception e#
      (try (.end ~dataset)
           (catch Exception e2# (throw e#))))))

(defn node->value
  "Unwrap a Node and give its raw value"
  [node]
  (cond
    (.isLiteral node) (.getValue (.getLiteral node))
    (.isURI node) (.getURI node)
     :else (str node)))

(defn result->map
  "Convert a SPARQL result row into a clojure map"
  [result]
  (let [binding (.getBinding result)
        vars (.vars binding)]
    (into {} (map #(vector (str %1) (node->value (Var/lookup binding %1)))
                  (iterator-seq vars)))))

(defn query-ds
  "Given a query string and dataset, perform the query and eagerly return full results"
  [query-str ds]
  (with-dataset ds ReadWrite/READ
    (do (debug :sparql \newline query-str)
        (let [model (.getNamedModel ds "urn:x-arq:UnionGraph")
              query (QueryFactory/create query-str)
              query-exec (QueryExecutionFactory/create query model)
              results (doall (iterator-seq (.execSelect query-exec)))
              mapped-results (map result->map results)]
          mapped-results))))

(defn resolve-full-ont-file
  "Given an ontology file name, resolve relative to the configured ontology dir location"
  [file-name]
  (io/file (cfg/get :ontology-dir)
           file-name))

(defn load-ontology!
  "Load the given named graph into the datastore from the specified file"
  [ds graph-name file-name]
  (with-dataset ds ReadWrite/WRITE
    (with-open [r (io/input-stream (resolve-full-ont-file file-name))]
      (let [model (.getNamedModel ds graph-name)]
        (info :load (format "Loading <%s> from \"%s\""
                            graph-name
                            (resolve-full-ont-file file-name)))
        (.read model r nil)))))

(def q-loaded-owls
  "Query string to find all loaded ontologies"
  (str/join
   "\n"
   ["PREFIX owl: <http://www.w3.org/2002/07/owl#>"
    "SELECT ?uri WHERE {"
      "?uri a owl:Ontology"
    "}"]))

(defn get-loaded-ontologies
  "Find all ontologies loaded into a given datastore"
  [ds]
  (set (map #(get % "?uri") (query-ds q-loaded-owls ds))))

(defn load-ontologies!
  "Given a datastore and map of {graph name: file name}, check for any graphs not yet loaded and load them into the datastore"
  [ds ontologies]
  (let [loaded-onts (get-loaded-ontologies ds)
        left-to-load (reduce dissoc ontologies loaded-onts)]
    (info :ontologies {:loaded loaded-onts :left-to-load left-to-load})
    (doseq [[graph-name file-name] left-to-load]
      (load-ontology! ds graph-name file-name))
    (info "Finished loading ontologies")
    left-to-load))

(def node-factory (NodeFactory.))

(defn uri-to-node
  "Create a URI node"
  [uri]
  (NodeFactory/createURI uri))

(defn mk-fulltext-ent-def
  "Make EntityDefinition to index given predicates on parsed triples"
  [fields]
  (let [ent-def (EntityDefinition. "uri" "text" "graph" (uri-to-node (first fields)))]
    (doseq [field (map uri-to-node (rest fields))]
      (.set ent-def "text" field))
    ent-def))

(defn wrap-ds-with-lucene
  "Given a real datastore, an index directory, and sequence of labels, wrap the datastore with a Lucene fulltext datastore and return it"
  [ds lucene-dir labels]
  (let [index-dir (NIOFSDirectory. (as-path lucene-dir))
        ent-def (mk-fulltext-ent-def labels)
        text-index-config (TextIndexConfig. ent-def)]
    (TextDatasetFactory/createLucene ds index-dir text-index-config)))

(defn mk-lucene-ds
  "Given a TDB dir, index dir, and sequence of labels, create a fulltext capable TDB datastore"
  [tdb-dir lucene-dir labels]
  (let [tdb-ds (-> tdb-dir
                   io/as-file
                   .getAbsolutePath
                   TDBFactory/createDataset)]
    (wrap-ds-with-lucene tdb-ds lucene-dir labels)))

(defn start-db
  "Connect to the datastore and load any ontologies needed"
  []
  (let [datastore (mk-lucene-ds (cfg/get :tdb-dir)
                                (.getAbsolutePath (cfg/get :lucene-dir))
                                (cfg/get :labels))
        ontologies (cfg/get :ontologies)]
    (load-ontologies! datastore ontologies)
    datastore))

;; TODO: Datastore does not appear to properly release TDB, perhaps because we are only closing Lucene wrapper?
(defn stop-db
  "Release the datastore"
  []
  (.close DbState)
  (TDBFactory/release DbState))

(defstate DbState
  :start (start-db)
  :stop (stop-db))
