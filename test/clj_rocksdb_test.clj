(ns clj-rocksdb-test
  (:require
    [clojure.test :refer :all]
    [clj-rocksdb :as r]
    [byte-streams :as bs]
    [taoensso.nippy :as nippy]
    [clojure.edn :as edn])
  (:import
   [java.util
     UUID]
    [java.io
     File]))

(defn delete-files-recursively [fname & [silently]]
  (letfn [(delete-f [file]
            (when (.isDirectory file)
              (doseq [child-file (.listFiles file)]
                (delete-f child-file)))
            (clojure.java.io/delete-file file silently))]
    (delete-f (clojure.java.io/file fname))))

(def ^:dynamic db nil)

(defn db-fixture [f]
  (let [db-dir (str "/tmp/clj-rocks-testdb-" (UUID/randomUUID) "/")]
    (with-redefs [db (r/create-db
                      db-dir
                      {:key-encoder nippy/freeze
                       :key-decoder nippy/thaw
                       :val-decoder nippy/thaw
                       :val-encoder nippy/freeze})]
      (f)
      (.close db)
      (delete-files-recursively db-dir))))

(use-fixtures :each db-fixture)

(deftest test-basic-operations
  (r/put db :a :b) 
  (is (= :b
         (r/get db :a)
         (r/get db :a ::foo)))
  (is (= [[:a :b]]
         (r/iterator db)
         (r/iterator db :a)
         (r/iterator db :a :a)
         (r/iterator db :a :c)))
  (is (= []
         (r/iterator db :b)
         (r/iterator db :b :d)))
  (r/delete db :a)
  (is (= nil (r/get db :a)))
  (is (= ::foo (r/get db :a ::foo)))

  (r/put db :a :b :z :y)
  (is (= :b (r/get db :a)))
  (is (= :y (r/get db :z)))

  (is (= [[:a :b] [:z :y]]
         (r/iterator db)))
  (is (= [[:a :b]]
         (r/iterator db :a :x)
         (r/iterator db nil :x)))
  (is (= [[:z :y]]
         (r/iterator db :b)
         (r/iterator db :b :z)))

  (is (= [:a :z] (r/bounds db)))

  (r/compact db)

  (with-open [snapshot (r/snapshot db)]
    (r/delete db :a :z)
    (is (= nil (r/get db :a)))
    (is (= :b (r/get snapshot :a))))

  (r/compact db)

  (r/delete db :a :b :z :y)

  (r/put db :j :k :l :m)
  (is (= :k (r/get db :j)))
  (is (= :m (r/get db :l)))

  (r/batch db)
  (is (= :k (r/get db :j)))
  (is (= :m (r/get db :l)))
  (r/batch db {:put [:r :s :t :u]})
  (is (= :s (r/get db :r)))
  (is (= :u (r/get db :t)))
  (r/batch db {:delete [:r :t]})
  (is (= nil (r/get db :r)))
  (is (= nil (r/get db :t)))

  (r/batch db {:put [:a :b :c :d]
               :delete [:j :l]})
  (is (= :b (r/get db :a)))
  (is (= :d (r/get db :c)))
  (is (= nil (r/get db :j)))
  (is (= nil (r/get db :l)))
  (is (thrown? AssertionError (r/batch db {:put [:a]}))))
