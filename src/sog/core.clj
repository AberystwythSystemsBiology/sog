(ns sog.core
  (:require [mount.core :as mount]
            [omniconf.core :as cfg]
            [sog.api :refer [WebState]]
            [sog.db :refer [DbState]]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]])
  (:gen-class))

(cfg/define
  {:conf {:description "Location of config file"
          :type :file}
   :tdb-dir {:description "Location of TDB store"
             :type :directory
             :requried true}
   :lucene-dir {:description "Location of Lucene index"
                :type :directory
                :required true}
   :ontology-dir {:description "Directory containing ontology files"
                  :type :directory
                  :requried true}
   :ontologies {:description "List of ontology files to load"
                :type :edn
                :requried true}
   :labels {:description "List of label URIs for predicates to index"
            :type :edn}
   :max-distance {:description "Maximum edit distance for match to search"
                  :type :number
                  :default 3}
   :port {:description "Port for REST API"
          :type :number
          :requried true
          :default 9090}})

(defn -main
  "Init datastore, load (or create) term dictionary, start web interface."
  [& args]
  (cfg/populate-from-cmd args)
  (cfg/populate-from-env)
  (when-let [conf (cfg/get :conf)]
    (cfg/populate-from-file conf))
  (cfg/verify)
  (mount/start))
