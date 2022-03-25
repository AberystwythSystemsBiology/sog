(defproject sog "0.1.0"
  :description "Simple Ontology Grep. Greps ontologies."
  :url "https://github.com/AberystwythSystemsBiology/sog"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[com.grammarly/omniconf "0.4.3"]
                 [javax.servlet/servlet-api "2.5"]
                 [metosin/reitit "0.5.17"]
                 [mount "0.1.16"]
                 [org.apache.jena/apache-jena-libs "4.4.0" :extension "pom"]
                 [org.apache.jena/jena-text "4.4.0"]
                 [ring/ring-core "1.9.5"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 [ring/ring-json "0.5.1"]
                 [org.clojure/clojure "1.10.3"]]
  :jvm-opts ["-Xmx4g"]
  :main ^:skip-aot sog.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
