(ns sog.api
  (:require [clojure.java.io :as io]
            [clojure.main :refer [stack-element-str]]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [omniconf.core :as cfg]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [resource-request]]
            [sog.terms :as terms]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]])
  (:gen-class))


(defn wrap-log-exception
  "Wraps request handler with a try/catch, logging exceptions"
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception ex
        (error ex)
        (debug :stack-trace (str/join "\n" (map stack-element-str (.getStackTrace ex))))))))

(defn wrap-log-access
  "Wraps request handler, logging remote address and URI accessed"
  [handler]
  (fn [request]
    (info :http (format "%s: %s" (:remote-addr request) (:uri request)))
    (handler request)))

(defn fuzzy-lookup-handler
  "Handles search requests using simple fuzzy search"
  [request]
  (let [target-term (get-in request [:path-params :term])
        suggestions (terms/fuzzy-lookup-term target-term
                                             (cfg/get :max-distance)
                                             (cfg/get :labels))]
    {:status 200
     :body suggestions}))

(defn raw-lookup-handler
  "Handles search requests sending the raw request string as the fulltext query"
  [request]
  (let [target-term (get-in request [:path-params :term])
        suggestions (terms/raw-lookup-term target-term
                                           (cfg/get :labels))]
    {:status 200
     :body suggestions}))

(defn serve-search-page
  "Serves up a simple web page to view basic data returned by the application"
  [request]
  {:status 200
   :body (io/input-stream (io/resource "public/index.html"))})
  
(def request-handler
  (ring/ring-handler
   (ring/router
    [["/lookup/:term" {:get {:parameters {:path {:term string?}}}
                       :handler fuzzy-lookup-handler}]
     ["/raw/:term" {:get {:parameters {:path {:term string?}}}
                    :handler raw-lookup-handler}]
     ["/*" {:get serve-search-page}]]
    {:conflicts (constantly nil)})
   (ring/create-default-handler)))

(def app
  (-> request-handler
      wrap-params
      wrap-multipart-params
      wrap-log-access
      wrap-log-exception
      wrap-json-body
      wrap-json-response))

(def WebState)

(defn start-web
  "Starts the Ring/Jetty server"
  []
  (jetty/run-jetty #'app {:port (cfg/get :port)
                          :join? false}))

(defn stop-web
  "Stops the Ring/Jetty server"
  []
  (.stop WebState))

(defstate WebState
  :start (start-web)
  :stop (stop-web))
