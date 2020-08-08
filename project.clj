(defproject sog "0.1.0"
  :description "Simple Ontology Grep. Greps ontologies."
  :url "https://github.com/AberystwythSystemsBiology/sog"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[com.grammarly/omniconf "0.3.2"]                 
                 [javax.servlet/servlet-api "2.5"]
                 [metosin/reitit "0.3.10"]
                 [mount "0.1.16"]
                 [org.apache.jena/apache-jena-libs "3.10.0" :extension "pom"]
                 [org.lundez/symspell "1.0-SNAPSHOT"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [org.clojure/clojure "1.10.0"]]
  :main ^:skip-aot sog.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
