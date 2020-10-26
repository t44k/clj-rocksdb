(defproject org.clojars.vaugusto92/clj-ttldb "0.1.0"
  :description "Clojure bindings for the facebook's rocksdbjni TtlDB."
  :main clj-ttldb
  :url "http://github.com/vaugusto92/clj-ttldb"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.rocksdb/rocksdbjni "6.8.1"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]]}})
  
