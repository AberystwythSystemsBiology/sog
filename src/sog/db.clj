(ns sog.db
  (:import [org.apache.jena.tdb TDBFactory]
           [org.apache.jena.sparql.core Var]
           [org.apache.jena.query QueryFactory
                                  QueryExecutionFactory
                                  Dataset
                                  ReadWrite])
  (:require [clojure.edn :as edn]
            [mount.core :refer [defstate]]
            [omniconf.core :as cfg]
            [clojure.set :refer [difference]]
            [clojure.java.io :refer [file]]))


;; Initialisation requires self-reference for DbState
(def DbState)

(defn ontology-cache-file
  []
  (file (cfg/get :string-cache-dir)
        "ontologies.edn"))

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
      ~@(map to-prefix prefixes)
      "SELECT * WHERE {"
      ~@chonks
      "}")))

(defn query-db
  [db query-str]
  (with-dataset db ReadWrite/READ
    (let [model (.getDefaultModel db)
          query (QueryFactory/create query-str)
          query-exec (QueryExecutionFactory/create query model)
          results (doall (iterator-seq (.execSelect query-exec)))
          mapped-results (map result->map results)]
      mapped-results)))

(defn get-loaded-ontologies!
  []
  (if (.exists (ontology-cache-file))
    (edn/read-string (slurp (ontology-cache-file)))
    (with-open [w (clojure.java.io/writer (ontology-cache-file))]
      (.write w (pr-str []))
      #{})))

(defn set-loaded-ontologies!
  [ontologies]
  (with-open [w (clojure.java.io/writer (ontology-cache-file))]
    (.write w (pr-str ontologies)))
  ontologies)

(defn resolve-full-ont-file
  [file-name]
  (file (cfg/get :ontology-dir)
        file-name))

(defn load-ontology!
  [db ontology-file]
  (with-dataset db ReadWrite/WRITE
    (with-open [r (clojure.java.io/input-stream
                   (resolve-full-ont-file ontology-file))]
      (let [model (.getDefaultModel db)]
        (.read model r "")))))

(defn load-ontologies!
  [db]
  (let [wanted-ontologies (set (cfg/get :ontologies))
        loaded-ontologies (set (get-loaded-ontologies!))
        unloaded-ontologies (difference wanted-ontologies
                                        loaded-ontologies)]
    (doall
     (reduce (fn [loaded-onts ontology-file]
               (do (load-ontology! db ontology-file)
                   (set-loaded-ontologies! (conj loaded-onts ontology-file))))
             loaded-ontologies
             unloaded-ontologies))))

(defn start-db
  []
  (let [datastore (TDBFactory/createDataset
                   (.getAbsolutePath (cfg/get :tdb-dir)))]
    (load-ontologies! datastore)
    datastore))

(defn stop-db
  []
  (.close DbState)
  (TDBFactory/release DbState))

(defstate DbState
  :start (start-db)
  :stop (stop-db))
