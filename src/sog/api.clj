(ns sog.api
  (:require [sog.terms :as terms]
            [omniconf.core :as cfg]
            [mount.core :refer [defstate]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]])
  (:gen-class))


(defn lookup-handler
  [request]
  (let [target-term (get-in request [:path-params :term])
        suggestions (terms/lookup-term target-term (cfg/get :distance))]
    {:status 200
     :body suggestions}))
    
  
(def request-handler
  (ring/ring-handler
   (ring/router
    ["/lookup/:term" {:get {:parameters {:path {:term string?}}}
                      :handler lookup-handler}])))

(def app
  (-> request-handler
      wrap-params
      wrap-multipart-params
      wrap-json-body
      wrap-json-response))

(def WebState)

(defn start-web
  []
  (jetty/run-jetty #'app {:port (cfg/get :port)
                          :join? false}))

(defn stop-web
  []
  (.stop WebState))

(defstate WebState
  :start (start-web)
  :stop (stop-web))

