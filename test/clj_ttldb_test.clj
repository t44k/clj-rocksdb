(ns clj-ttldb-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [clj-ttldb :as rdb]
            [clojure.java.io]))

(defn- delete-files-recursively [fname & [silently]]
  (letfn [(delete-f [file]
            (when (.isDirectory file)
              (doseq [child-file (.listFiles file)]
                (delete-f child-file)))
            (clojure.java.io/delete-file file silently))]
    (delete-f (clojure.java.io/file fname))))

(def ^:dynamic db nil)

;; Sets the directory for database creation within the project's 
;; folder.
(defn db-dir []
  (let [path (-> (java.io.File. "") .getAbsolutePath)]
    (str path "/clj-ttldb-testdb")))

;; Freeze and thaw methods for the tests.
(defn freeze [x] (byte-array (.getBytes (name x))))
(defn thaw [x] (keyword (String. x)))

;; Database fixture for the tests.
(defn db-fixture [f]
  (let [db-dir (db-dir)]
    (with-redefs [db (rdb/create-db
                      db-dir
                      {:create-if-missing? true
                       :key-encoder freeze
                       :val-encoder freeze
                       :key-decoder thaw
                       :val-decoder thaw}
                      1000000
                      false)]
      (f)
      (.close db)
      (delete-files-recursively db-dir))))

(use-fixtures :each db-fixture)

(deftest basic-operations-test
  (testing "Put and get operations."
    (rdb/put db :a :b) 
    (is (= :b (rdb/get db :a)))
    (is (= :b (rdb/get db :a ::foo))))
  
  (testing "Delete and get operations."
    (rdb/delete db :a)
    (is (= nil (rdb/get db :a)))
    (is (= ::foo (rdb/get db :a ::foo))))

  (testing "Put and get operations."
    (rdb/put db :a :b :z :y)
    (is (= :b (rdb/get db :a)))
    (is (= :y (rdb/get db :z))))

  (testing "Bounds method."
    (is (= [:a :z] (rdb/bounds db))))

  ;; Compaction
  (rdb/compact db)

  (testing "Snapshot creation."
    (with-open [snapshot (rdb/snapshot db)]
      (rdb/delete db :a :z)
      (is (= nil (rdb/get db :a)))
      (is (= :b (rdb/get snapshot :a)))))

  (testing "Compaction, delete and get operations."
    (rdb/compact db)
    (rdb/delete db :a :b :z :y)
    (rdb/put db :j :k :l :m)
    (is (= :k (rdb/get db :j))))

  (testing "Batch and get operations."
    (rdb/batch db)
    (is (= :k (rdb/get db :j)))
    (is (= :m (rdb/get db :l)))

    (rdb/batch db {:put [:r :s :t :u]})
    (is (= :s (rdb/get db :r)))
    (is (= :u (rdb/get db :t)))

    (rdb/batch db {:delete [:r :t]})
    (is (= nil (rdb/get db :r)))
    (is (= nil (rdb/get db :t)))

    (rdb/batch db {:put [:a :b :c :d]
                  :delete [:j :l]})
    (is (= :b (rdb/get db :a)))
    (is (= :d (rdb/get db :c)))
    (is (= nil (rdb/get db :j)))
    (is (= nil (rdb/get db :l)))
    (is (thrown? AssertionError (rdb/batch db {:put [:a]})))))