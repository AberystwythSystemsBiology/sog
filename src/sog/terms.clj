(ns sog.terms
  (:import [org.apache.jena.query
            ReadWrite
            ParameterizedSparqlString])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [sog.db :refer [query-ds DbState]]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]])
  (:gen-class))


(defn mk-label-clause
  "With a query template and sequence of values, return a string of the template with each value inserted stringed together with UNIONs"
  [template labels]
  (str/join " UNION " (map #(format template %) labels)))

(defn mk-fulltext-qstr
  "Return a SPARQL query template for the given labels. Returned query string has unbound text:query ready for prepared statement insertion"
  [labels]
  (str
   "SELECT * WHERE {
  {
    SELECT DISTINCT ?uri WHERE {
      ?uri text:query ? .
    } LIMIT 100
  }
  {
    " (mk-label-clause "{ ?uri <%s> ?label }" labels) " .
  }
  UNION
  {
    ?uri ?p ?o .
    " (mk-label-clause "{ ?p <%s> ?pLabel }" labels) " .
    " (mk-label-clause "{ ?o <%s> ?oLabel }" labels) " .
  }
  UNION
  {
    ?s ?p ?uri .
    " (mk-label-clause "{ ?s <%s> ?sLabel }" labels) " .
    " (mk-label-clause "{ ?p <%s> ?pLabel }" labels) " .
  }
} LIMIT 5000"))

(defn term-to-query-str
  "Given a term and max edit distance, produce a lucene query string where each word in the query is required and may be up to max-distance different (e.g. (term-to-query-str \"heart disease\" 3) returns \"+heart~3 +disease~3\")"
  [term max-distance]
  (->> (str/split term #"\s+")
       (map #(format "+%s~%d" % max-distance))
       (str/join " ")))

(defn prepare-fulltext-query
  "Given a lucene fulltext search term string and sequence of labels, return a final query string ready for execution"
  [term-str labels]
  (doto (ParameterizedSparqlString.)
    (.setNsPrefix "rdfs" "http://www.w3.org/2000/01/rdf-schema#")
    (.setNsPrefix "text" "http://jena.apache.org/text#")
    (.setCommandText (mk-fulltext-qstr labels))
    (.setLiteral 0 term-str)))

(defn fuzzy-fulltext-query
  "Perform a fulltext query using default basic fuzzy query"
  [term max-distance labels]
  (info :query :fuzzy {:distance max-distance :labels labels} term)
  (-> term
      (term-to-query-str max-distance)
      (prepare-fulltext-query labels)
      .toString
      (query-ds DbState)))

(defn raw-fulltext-query
  "Perform a fulltext query passing the term directly to lucene for more advanced query patterns"
  [term labels]
  (info :query :raw {:labels labels} term)
  (-> term
      (prepare-fulltext-query labels)
      .toString
      (query-ds DbState)))

(defn assoc-set-in
  "Works like assoc-in, but instead of replacing the original value treat the original value as a set and insert the new value into it"
  [m ks v]
  (assoc-in m ks (conj (get-in m ks #{}) v)))

(defn empty-result-entry
  "Create a blank concept result map for the given URI"
  [uri]
  {:uri uri :labels #{} :subjects {} :objects {}})

(defn fold-row
  "Given a concept map and row, insert the row data based on its contents (label, subject, or object)"
  [entry row]
  (let [uri (get row "?uri")
        p (get row "?p")
        pl (get row "?pLabel")
        s (get row "?s")
        sl (get row "?sLabel")
        o (get row "?o")
        ol (get row "?oLabel")]
    (cond
      s (-> entry
            (assoc-set-in [:subjects p :labels] pl)
            (assoc-set-in [:subjects p :objects s :labels] sl))
      o (-> entry
            (assoc-set-in [:objects p :labels] pl)
            (assoc-set-in [:objects p :subjects o :labels] ol))
      :else (merge-with conj entry {:labels (get row "?label")}))))

(defn reduce-term-batch
  "Given a term's batch of rows, reduce these into a complete concept result map"
  [batch]
  (let [uri (get (first batch) "?uri")]
    (reduce fold-row (empty-result-entry uri) batch)))

(defn fuzzy-lookup-term
  "Perform fuzzy term lookup with given term, maximum edit distance, and labels"
  [term max-distance labels]
  (let [suggestions (fuzzy-fulltext-query term max-distance labels)
        concept-batches (partition-by #(get % "?uri") suggestions)
        concept-maps (map reduce-term-batch concept-batches)]
    concept-maps))

(defn raw-lookup-term
  "Perform fulltext term lookup with given lucene term, maximum edit distance, and labels"
  [term labels]
  (let [suggestions (raw-fulltext-query term labels)
        concept-batches (partition-by #(get % "?uri") suggestions)
        concept-maps (map reduce-term-batch concept-batches)]
    concept-maps))
