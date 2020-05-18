(defproject kotyo/clj-rocksdb "0.1.7-SNAPSHOT"
  :description "Clojure bindings for facebook's rocksdbjni"
  :url "http://github.com/kotyo/clj-rocksdb"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.rocksdb/rocksdbjni "6.8.1"]
                 [byte-streams "0.2.4"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [com.taoensso/nippy "2.14.0"]]}})
  
