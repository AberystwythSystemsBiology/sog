(ns sog.core
  (:require [omniconf.core :as cfg]
            [mount.core :as mount]
            [sog.api :refer [WebState]]
            [sog.db :refer [DbState]]
            [sog.terms :refer [TermState]])
  (:gen-class))

(cfg/define
  {:conf {:description "Location of config file"
          :type :file}
   :tdb-dir {:description "Location of TDB store"
             :type :directory
             :requried true}
   :port {:description "Port for REST API"
          :type :number
          :requried true
          :default 9090}
   :ontology-dir {:description "Directory containing ontology files"
                  :type :directory
                  :requried true}
   :ontologies {:description "List of ontology files to load"
                :type :edn
                :requried true}
   :string-cache-dir {:description "Directory to store URI/label maps"
                      :type :directory
                      :requried true}
   :distance {:description "Max edit-distance to search & index"
              :type :number
              :default 5}})

(defn -main
  "Init datastore, load (or create) term dictionary, start web interface."
  [& args]
  (cfg/populate-from-cmd args)
  (cfg/populate-from-env)
  (when-let [conf (cfg/get :conf)]
    (cfg/populate-from-file conf))
  (cfg/verify)
  (mount/start))
