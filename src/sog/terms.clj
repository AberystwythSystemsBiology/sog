(ns sog.terms
  (:import [org.apache.jena.query
            ReadWrite
            ParameterizedSparqlString])
  (:require [sog.db :refer [make-query query-ds DbState]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))


(defn mk-label-clause
  [template labels]
  (str/join " UNION " (map #(format template %) labels)))

(defn mk-fulltext-qstr
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

(defn prepare-fulltext-query
  [term labels]
  (doto (ParameterizedSparqlString.)
    (.setNsPrefix "rdfs" "http://www.w3.org/2000/01/rdf-schema#")
    (.setNsPrefix "text" "http://jena.apache.org/text#")
    (.setCommandText (mk-fulltext-qstr labels))
    (.setLiteral 0 (str term "~"))))

(defn fulltext-query
  [term labels]
  (let [res (query-ds DbState (.toString (prepare-fulltext-query term labels)))]
    res))

(defn assoc-set-in
  [m ks v]
  (assoc-in m ks (conj (get-in m ks #{}) v)))

(defn fold-row
  [uris row]
  (let [uri (get row "?uri")
        p (get row "?p")
        pl (get row "?pLabel")
        s (get row "?s")
        sl (get row "?sLabel")
        o (get row "?o")
        ol (get row "?oLabel")
        entry (get uris uri {:labels #{} :subjects {} :objects {}})]
    (assoc
     uris
     uri
     (cond
       s (-> entry
             (assoc-set-in [:subjects p :labels] pl)
             (assoc-set-in [:subjects p :objects s :labels] sl))
       o (-> entry
             (assoc-set-in [:objects p :labels] pl)
             (assoc-set-in [:objects p :subjects o :labels] ol))
       :else (merge-with conj entry {:labels (get row "?label")})))))

(defn lookup-term
  [term labels]
  (let [suggestions (fulltext-query term labels)]
    (reduce fold-row {} suggestions)))

