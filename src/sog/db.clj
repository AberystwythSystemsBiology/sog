(ns sog.db
  (:import [java.nio.file FileSystems]
           [org.apache.jena.tdb TDBFactory]
           [org.apache.jena.sparql.core Var]
           [org.apache.jena.query QueryFactory
            QueryExecutionFactory
            Dataset
            ReadWrite
            ParameterizedSparqlString]
           [org.apache.jena.graph NodeFactory]
           [org.apache.jena.query.text
            EntityDefinition
            TextDatasetFactory
            TextIndexConfig]
           [org.apache.lucene.store Directory NIOFSDirectory])
  (:require [clojure.edn :as edn]
            [mount.core :refer [defstate]]
            [omniconf.core :as cfg]
            [clojure.set :refer [difference]]
            [clojure.java.io :as io]
            [clojure.string :as str]))


;; Initialisation requires self-reference for DbState
(def DbState)

(defn as-path
  [& paths]
  (.getPath (FileSystems/getDefault)
            (first paths)
            (into-array String (rest paths))))

(defmacro with-dataset
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
  [node]
  (cond
    (.isLiteral node) (.getValue (.getLiteral node))
    (.isURI node) (.getURI node)
     :else (str node)))

(defn result->map
  [result]
  (let [binding (.getBinding result)
        vars (.vars binding)]
    (into {} (map #(vector (str %1) (node->value (Var/lookup binding %1)))
                  (iterator-seq vars)))))

(defn to-prefix
  [[prefix url]]
  (format "PREFIX %s: <%s>" prefix url))

(defn make-query
  [prefixes
   & chonks]
  (clojure.string/join "\n"
    `("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
      "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"
      "PREFIX text: <http://jena.apache.org/text#>"
      ~@(map to-prefix prefixes)
      "SELECT * WHERE {"
      ~@chonks
      "} LIMIT 100")))

(defn prepare-fulltext-query
  [term]
  (doto (ParameterizedSparqlString.)
    (.setNsPrefix "rdfs" "http://www.w3.org/2000/01/rdf-schema#")
    (.setNsPrefix "text" "http://jena.apache.org/text#")
    (.setCommandText "SELECT * WHERE { ?uri text:query ?; rdfs:label ?label } LIMIT 100")
    (.setLiteral 0 (str term "~"))))

(defn query-ds
  [ds query-str]
  (with-dataset ds ReadWrite/READ
    (let [model (.getNamedModel ds "urn:x-arq:UnionGraph")
          query (QueryFactory/create query-str)
          query-exec (QueryExecutionFactory/create query model)
          results (doall (iterator-seq (.execSelect query-exec)))
          mapped-results (map result->map results)]
      mapped-results)))

(defn resolve-full-ont-file
  [file-name]
  (io/file (cfg/get :ontology-dir)
           file-name))

(defn load-ontology!
  [ds graph-name file-name]
  (with-dataset ds ReadWrite/WRITE
    (with-open [r (io/input-stream (resolve-full-ont-file file-name))]
      (let [model (.getNamedModel ds graph-name)]
        (.read model r nil)))))

(def q-loaded-owls
  (str/join
   "\n"
   ["PREFIX owl: <http://www.w3.org/2002/07/owl#>"
    "SELECT ?uri WHERE {"
      "?uri a owl:Ontology"
    "}"]))

(defn get-loaded-ontologies
  [ds]
  (set (map #(get % "?uri") (query-ds ds q-loaded-owls))))

(defn load-ontologies!
  [ds ontologies]
  (let [loaded-onts (get-loaded-ontologies ds)
        left-to-load (reduce dissoc ontologies loaded-onts)]
    (doseq [[graph-name file-name] left-to-load]
      (load-ontology! ds graph-name file-name))
    left-to-load))

(def node-factory (NodeFactory.))

(defn uri-to-node
  [uri]
  (NodeFactory/createURI uri))

(defn mk-fulltext-ent-def
  [fields]
  (let [ent-def (EntityDefinition. "uri" "text" "graph" (uri-to-node (first fields)))]
    (doseq [field (map uri-to-node (rest fields))]
      (.set ent-def "text" field))
    ent-def))

(defn wrap-ds-with-lucene
  [ds lucene-dir labels]
  (let [index-dir (NIOFSDirectory. (as-path lucene-dir))
        ent-def (mk-fulltext-ent-def labels)
        text-index-config (TextIndexConfig. ent-def)]
    (TextDatasetFactory/createLucene ds index-dir text-index-config)))

(defn mk-lucene-ds
  [tdb-dir lucene-dir labels]
  (let [tdb-ds (-> tdb-dir
                   io/as-file
                   .getAbsolutePath
                   TDBFactory/createDataset)]
    (wrap-ds-with-lucene tdb-ds lucene-dir labels)))

(defn start-db
  []
  (let [datastore (mk-lucene-ds (cfg/get :tdb-dir)
                                (.getAbsolutePath (cfg/get :lucene-dir))
                                (cfg/get :labels))
        ontologies (cfg/get :ontologies)]
    (load-ontologies! datastore ontologies)
    datastore))

(defn stop-db
  []
  (.close DbState)
  (TDBFactory/release DbState))

(defstate DbState
  :start (start-db)
  :stop (stop-db))
