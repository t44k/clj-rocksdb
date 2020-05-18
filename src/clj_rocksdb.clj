(ns clj-rocksdb
  (:refer-clojure :exclude [get sync])
  (:require
    [byte-streams :as bs])
  (:import
    [java.io
     Closeable]))

(import
  '[org.rocksdb
    WriteBatch
    RocksIterator
    Options
    ReadOptions
    WriteOptions
    CompressionType
    RocksDB])

;;;

(defn- closeable-seq
  "Creates a seq which can be closed, given a latch which can be closed
   and dereferenced to check whether it's already been closed."
  [s close-fn]
  (reify
    Closeable
    (close [this] (close-fn))

    clojure.lang.Sequential
    clojure.lang.ISeq
    clojure.lang.Seqable
    clojure.lang.IPersistentCollection
    (equiv [this x]
      (loop [a this, b x]
        (if (or (empty? a) (empty? b))
          (and (empty? a) (empty? b))
          (if (= (first x) (first b))
            (recur (rest a) (rest b))
            false))))
    (empty [_]
      [])
    (count [_]
      (count s))
    (cons [_ a]
      (cons a s))
    (next [_]
      (next s))
    (more [this]
      (let [rst (next this)]
        (if (empty? rst)
          '()
          rst)))
    (first [_]
      (first s))
    (seq [_]
      (seq s))))

(deftype close-type [iterator]
  Object
  (finalize [_] (.close iterator))
  clojure.lang.IFn
  (invoke [_] (.close iterator)))

(defn- iterator-seq- [^RocksIterator iterator start end reverse? key-decoder key-encoder val-decoder]
  (if start
    (if reverse?
      (.seekForPrev ^RocksIterator iterator (bs/to-byte-array (key-encoder start)))
      (.seek ^RocksIterator iterator (bs/to-byte-array (key-encoder start))))
    (if reverse?
      (.seekToLast ^RocksIterator iterator)
      (.seekToFirst ^RocksIterator iterator)))

  (let [iter-step-fn (if reverse? #(doto % .prev) #(doto % .next))
        iter (fn iter [it]
               (if-not (.isValid it) '()
                       (lazy-seq (cons [(.key it) (.value it)]
                                       (iter (iter-step-fn it))))))
        s (iter iterator)
        end-test-fn (if reverse? neg? pos?)
        s (if end
            (let [end (bs/to-byte-array (key-encoder end))]
              (take-while
               #(not (end-test-fn (bs/compare-bytes (first %) end)))
               s))
            s)]
    (closeable-seq
     (map #(vector
            (key-decoder (first %))
            (val-decoder (second %)))
          s)
     (->close-type iterator))))

;;;

(defprotocol IRocksDB
  (^:private ^RocksDB db-  [_])
  (^:private batch- [_] [_ options])
  (^:private iterator- [_] [_ start end reverse?])
  (^:private get- [_ k])
  (^:private put- [_ k v options])
  (^:private del- [_ k options])
  (^:private snapshot- [_]))

(defrecord Snapshot
  [db
   key-decoder
   key-encoder
   val-decoder
   ^ReadOptions read-options]
  IRocksDB
  (snapshot- [this] this)
  (db- [_] (db- db))
  (get- [_ k]
    (val-decoder (.get (db- db) read-options (bs/to-byte-array (key-encoder k)))))
  (iterator- [_ start end reverse?]
    (iterator-seq-
      (.newIterator (db- db) read-options)
      start
      end
      reverse?
      key-decoder
      key-encoder
      val-decoder))
  Closeable
  (close [_]
    (-> read-options .snapshot .close))
  (finalize [this] (.close this)))

(defrecord Batch
  [^RocksDB db
   ^WriteBatch batch
   key-encoder
   val-encoder
   ^WriteOptions options]
  IRocksDB
  (db- [_] db)
  (batch- [this _] this)
  (put- [_ k v _]
    (.put batch
      (bs/to-byte-array (key-encoder k))
      (bs/to-byte-array (val-encoder v))))
  (del- [_ k _]
    (.delete batch (bs/to-byte-array (key-encoder k))))
  Closeable
  (close [_]
    (.write db (or options (WriteOptions.)) batch)
    (.close batch)))

(defrecord DB
  [^RocksDB db
   key-decoder
   key-encoder
   val-decoder
   val-encoder]
  Closeable
  (close [_] (.close db))
  IRocksDB
  (db- [_]
    db)
  (get- [_ k]
    (let [k (bs/to-byte-array (key-encoder k))]
      (some-> (.get db k)
              val-decoder)))
  (put- [_ k v options]
    (let [k (bs/to-byte-array (key-encoder k))
          v (bs/to-byte-array (val-encoder v))]
      (if options
        (.put db k v options)
        (.put db k v))))
  (del- [_ k options]
    (let [k (bs/to-byte-array (key-encoder k))]
      (if options
        (.delete db k options)
        (.delete db k))))
  (snapshot- [this]
    (->Snapshot
      this
      key-decoder
      key-encoder
      val-decoder
      (doto (ReadOptions.)
        (.setSnapshot (.getSnapshot db)))))
  (batch- [this options]
    (->Batch
      db
      (WriteBatch.)
      key-encoder
      val-encoder
      options))
  (iterator- [_ start end reverse?]
    (iterator-seq-
      (.newIterator db)
      start
      end
      reverse?
      key-decoder
      key-encoder
      val-decoder)))

(def ^:private option-setters
  {:create-if-missing? #(.setCreateIfMissing ^Options %1 %2)
   :error-if-exists?   #(.setErrorIfExists ^Options %1 %2)
   :write-buffer-size  #(.setDbWriteBufferSize ^Options %1 %2)
   :block-size         #(.setArenablockSize ^Options %1 %2)
   :max-open-files     #(.setMaxOpenFiles ^Options %1 %2)
   :cache-size         #(.setBlockCacheSize ^Options %1 %2)
   :comparator         #(.setComparator ^Options %1 %2)
   :paranoid-checks?   #(.setParanoidChecks ^Options %1 %2)
   :compress?          #(.setCompressionType ^Options %1 (if % CompressionType/SNAPPY_COMPRESSION CompressionType/NO_COMPRESSION))
   :logger             #(.setLogger ^Options %1 %2)})

(defn create-db
  "Creates a closeable database object, which takes a directory and zero or more options.

   The key and val encoder/decoders are functions for transforming to and from byte-arrays."
  [directory
   {:keys [key-decoder
           key-encoder
           val-decoder
           val-encoder
           create-if-missing?
           error-if-exists?
           write-buffer-size
           block-size
           max-open-files
           cache-size
           comparator
           compress?
           paranoid-checks?
           block-restart-interval
           logger]
    :or {key-decoder identity
         key-encoder identity
         val-decoder identity
         val-encoder identity
         compress? true
         cache-size 32
         block-size (* 16 1024)
         write-buffer-size (* 32 1024 1024)
         create-if-missing? true
         error-if-exists? false}
    :as options}]
  (->DB
    (RocksDB/open 
     (let [opts (Options.)]
        (doseq [[k v] options]
          (when (contains? option-setters k)
            ((option-setters k) opts v)))
        opts
      directory))
    key-decoder
    key-encoder
    val-decoder
    val-encoder))

(defn destroy-db
  "Destroys the database at the specified `directory`."
  [directory]
  (RocksDB/destroyDB
    directory
    (Options.)))

;;;

(defn get
  "Returns the value of `key` for the given database or snapshot. If the key doesn't exist, returns
   `default-value` or nil."
  ([db key]
   (get db key nil))
  ([db key default-value]
   (let [v (get- db key)]
     (if (nil? v)
       default-value
       v))))

(defn snapshot
  "Returns a snapshot of the database that can be used with `get` and `iterator`. This implements
   java.io.Closeable, and can leak space in the database if not closed."
  [db]
  (snapshot- db))

(defn iterator
  "Returns a closeable sequence of map entries (accessed with `key` and `val`) that is the inclusive
   range from `start `to `end`.  If exhausted, the sequence is automatically closed."
  ([db]
   (iterator db nil nil))
  ([db start]
   (iterator db start nil))
  ([db start end]
   (iterator- db start end false)))

(defn reverse-iterator
  "Returns a closeable sequence of map entries (accessed with `key` and `val`) that is the inclusive
   range from `start `to `end` in reverse order.  If exhausted, the sequence is automatically closed."
  ([db]
   (reverse-iterator db nil nil))
  ([db start]
   (reverse-iterator db start nil))
  ([db start end]
   (iterator- db start end true)))

(defn put
  "Puts one or more key/value pairs into the given `db`."
  ([db])
     
  ([db key val]
   (put- db key val nil))
  ([db key val & key-vals]
   (with-open [^Batch batch (batch- db nil)]
     (put- batch key val nil)
     (doseq [[k v] (partition 2 key-vals)]
       (put- batch k v nil)))))

(defn delete
  "Deletes one or more keys in the given `db`."
  ([db])
     
  ([db key]
   (del- db key nil))
  ([db key & keys]
   (with-open [^Batch batch (batch- db nil)]
     (del- batch key nil)
     (doseq [k keys]
       (del- batch k nil)))))

(defn sync
  "Forces the database to fsync."
  [db]
  (with-open [^Batch batch (batch- db (doto (WriteOptions.) (.setSync true)))]))
    

(defn stats
  "Returns statistics for the database."
  [db property]
  (.getProperty (db- db) "rocksdb.stats"))

(defn bounds
  "Returns a tuple of the lower and upper keys in the database or snapshot."
  [db]
  (let [key-decoder (:key-decoder db)]
    (with-open [^RocksIterator iterator (condp instance? db
                                          DB (.newIterator (db- db))
                                          Snapshot (.newIterator (db- db) (:read-options db)))]
      (doto iterator .seekToFirst)
      (when (.isValid iterator)
        [(-> (doto iterator .seekToFirst) .key key-decoder)
         (-> (doto iterator .seekToLast) .key key-decoder)]))))

(defn compact
  "Forces compaction of database over the given range. If `start` or `end` are nil, they default to
   the full range of the database."
  ([db]
   (compact db nil nil))
  ([db start]
   (compact db start nil))
  ([db start end]
   (let [encoder (:key-encoder db)
         [start' end'] (bounds db)
         start (or start start')
         end (or end end')]
     (when (and start end)
       (.compactRange (db- db)
         (bs/to-byte-array (encoder start))
         (bs/to-byte-array (encoder end)))))))

(defn batch
  "Batch a collection of put and/or delete operations into the supplied `db`.
   Takes a map of the form `{:put [key1 value1 key2 value2] :delete [key3 key4]}`.
   If `:put` key is provided, it must contain an even-length sequence of alternating keys and values."
  ([db])
  ([db {puts :put deletes :delete}]
   (assert (even? (count puts)) ":put option requires even number of keys and values.")
   (with-open [^Batch batch (batch- db nil)]
     (doseq [[k v] (partition 2 puts)]
       (put- batch k v nil))
     (doseq [k deletes]
       (del- batch k nil)))))
