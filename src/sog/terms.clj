(ns sog.terms
  (:import [java.io
            FileInputStream
            File
            FileNotFoundException
            PushbackReader]
           [io.github.mightguy.spellcheck.symspell.impl
            InMemoryDataHolder
            SymSpellCheck]
           [io.github.mightguy.spellcheck.symspell.common
            SpellCheckSettings
            Murmur3HashFunction
            WeightedDamerauLevenshteinDistance
            QwertyDistance
            DictionaryItem
            Verbosity])
  (:require [omniconf.core :as cfg]
            [sog.db :refer [make-query query-db DbState]]
            [mount.core :refer [defstate]]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:gen-class))

(def TermState)

(defn term-file
  [file-name]
  (io/file (cfg/get :string-cache-dir)
           file-name))

(defn mk-sc-settings
  []
  (-> (SpellCheckSettings/builder)
      (.maxEditDistance (cfg/get :distance))
      (.keySplitRegex "\t+")
      (.build)))

(defn mk-sc-holder
  [sc-settings]
  (InMemoryDataHolder. sc-settings (Murmur3HashFunction.)))

(defn mk-sc-distance
  [sc-settings]
  (WeightedDamerauLevenshteinDistance.
   (.getDeletionWeight sc-settings)
   (.getInsertionWeight sc-settings)
   (.getReplaceWeight sc-settings)
   (.getTranspositionWeight sc-settings)
   (QwertyDistance.)))

(defn mk-sc-checker
  [sc-settings sc-holder sc-distance]
  (SymSpellCheck. sc-holder sc-distance sc-settings))

(defn make-dict-from-terms
  [terms]
  (let [sc-settings (mk-sc-settings)
        sc-holder (mk-sc-holder sc-settings)
        sc-distance (mk-sc-distance sc-settings)
        sc-checker (mk-sc-checker sc-settings sc-holder sc-distance)]
    (doall
     (->> terms
          (map (fn [[term labels]]
                 (->> labels
                      (map (fn [label]
                             (let [item (DictionaryItem. label 1.0 1.0)]
                               (.addItem sc-holder item)
                               {label term})))
                      (reduce merge))))
          (reduce merge)
          (vector sc-holder sc-checker)))))

(defn describe-query
  [url]
  (make-query {}
    "{"
      (format "{ <%s> rdfs:label ?label }" url)
      "UNION"
      (format "{ <%s> skos:prefLabel ?label }" url)
      "UNION"
      (format "{ <%s> skos:altLabel ?label }" url)
    "}"
    "UNION"
    "{"
      (format "<%s> ?p ?o ." url)
      "{ ?p rdfs:label ?pLabel }"
      "UNION"
      "{ ?p skos:prefLabel ?pLabel }"
      "UNION"
      "{ ?p skos:altLabel ?pLabel } ."

      "{ ?o rdfs:label ?oLabel }"
      "UNION"
      "{ ?o skos:prefLabel ?oLabel }"
      "UNION"
      "{ ?o skos:altLabel ?oLabel }"
    "}"
    "UNION"
    "{"
      (format "?s ?p <%s> ." url)
      "{ ?p rdfs:label ?pLabel }"
      "UNION"
      "{ ?p skos:prefLabel ?pLabel }"
      "UNION"
      "{ ?p skos:altLabel ?pLabel } ."

      "{ ?s rdfs:label ?sLabel }"
      "UNION"
      "{ ?s skos:prefLabel ?sLabel }"
      "UNION"
      "{ ?s skos:altLabel ?sLabel }"
    "}"))

(defn description-type
  [dict]
  (cond
    (get dict "?s") :subject
    (get dict "?o") :object
    :else :label))

(defn reduce-labels
  [entries]
  (reduce 
   (fn [items item]
     (merge-with #(set (concat %1 %2))
                 (if (get item "?s")
                   {(get item "?s")
                    [(get item "?sLabel")]}
                   {})
                 (if (get item "?p")
                   {(get item "?p")
                    [(get item "?pLabel")]}
                   {})
                 (if (get item "?o")
                   {(get item "?o")
                    [(get item "?oLabel")]}
                   {})
                 items))
          {} entries))

(defn unique-stmts
  [stmts stmt-key]
  (set (map (fn [stmt] 
              [(get stmt "?p")
               (get stmt stmt-key)])
            stmts)))

(defn describe-item
  [url]
  (let [stmts (query-db DbState (describe-query url))
        by-type (group-by description-type stmts)
        item-labels (map #(get %1 "?label") (:label by-type))
        annotation-labels (reduce-labels stmts)
        subj-stmts (unique-stmts (:subject by-type) "?s")
        obj-stmts (unique-stmts (:object by-type) "?o")]
    {:labels item-labels
     :subjects (into {} (map (fn [[pred subjs]]
                      [pred
                       {:label (get annotation-labels pred)
                        :subjects (map (fn [[pred subj]]
                                    {:iri subj
                                     :labels (get annotation-labels subj)})
                                       subjs)}])
                    (group-by first subj-stmts)))
     :objects (into {} (map (fn [[pred objs]]
                      [pred
                       {:label (get annotation-labels pred)
                        :objects (map (fn [[pred obj]]
                                    {:iri obj
                                     :labels (get annotation-labels obj)})
                                       objs)}])
                    (group-by first obj-stmts)))}))

(def term-query
  (make-query {}
    "SELECT * WHERE {"
    "  { ?iri rdfs:label ?label . } "
    "    UNION"
    "  { ?iri skos:prefLabel ?label . }"
    "    UNION"
    "  { ?iri skos:altLabel ?label . }"
    "}"))
      
(defn get-term-labels
  []
  (let [results (query-db DbState term-query)]
    (reduce (fn [terms {iri "?iri" label "?label"}]
              (merge-with into terms {iri [(.toLowerCase label)]}))
            {}
            results)))

(defn read-term-file
  [file-name]
  (with-open [r (PushbackReader. (io/reader file-name))]
    (edn/read r)))

(defn write-term-file!
  [file-name term-dict]
  (with-open [w (io/writer file-name)]
    (.write w (pr-str term-dict))))

(defn load-or-make-dict
  [infile]
  (try
    (let [terms (read-term-file infile)
          [sc-holder sc-checker term-dict] (make-dict-from-terms terms)]
      {:sc-holder sc-holder
       :sc-checker sc-checker
       :term-dict term-dict})
    (catch FileNotFoundException e
        (let [terms (get-term-labels)
              [sc-holder sc-checker term-dict] (make-dict-from-terms terms)]
          (write-term-file! infile terms)
          {:sc-holder sc-holder
           :sc-checker sc-checker
           :term-dict term-dict}))))

(defn enhance-suggestion
  [suggest-item]
  (let [{:keys [term distance]} (bean suggest-item)
        url (get (:term-dict TermState) term)
        meta (describe-item url)]
    {:term term
     :distance distance
     :url url
     :meta meta}))

(defn lookup-term
  [term max-dist]
  (let [spell-suggestions (.lookup (:sc-checker TermState)
                                   term
                                   Verbosity/ALL
                                   max-dist)
        enhanced-suggestions (map enhance-suggestion spell-suggestions)]
    enhanced-suggestions))

(defn start-terms
  []
  (let [{:keys [sc-holder sc-checker term-dict]}
        (load-or-make-dict (term-file "terms.edn"))]
    {:sc-checker sc-checker
     :term-dict term-dict}))

(defn stop-terms
  []
  {})

(defstate TermState
  :start (start-terms)
  :stop (stop-terms))
