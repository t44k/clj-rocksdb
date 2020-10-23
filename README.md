# clj-ttldb
A wrapper around [RocksDB](https://rocksdb.org) with Time to live (TTL) support based on the [TtlDB](https://github.com/facebook/rocksdb/blob/master/java/src/main/java/org/rocksdb/TtlDB.java) class, provided by [RocksDB Java API](https://github.com/facebook/rocksdb/tree/master/java/src/main/java/org/rocksdb).

The source code and the documentation is based on [rotyo/clj-rocksdb](https://github.com/kotyo/clj-rocksdb) library.

## Basic Usage

```clj
[org.clojars.vaugusto92/clj-ttldb "0.0.1-SNAPSHOT"]
```

To create or access a database, use `clj-ttldb/create-db`:
The returned database object can be used with `clj-ttldb/get`, `put`, `delete`, `batch`, and `iterator`.
RocksDB by default stores the keys and values in byte arrays. We may want to define custom encoders and decoders. This can be done in `create-db`:

Notice that the value returned is a byte-array.  This is because byte arrays are the native storage format for RocksDB, and we haven't defined custom encoders and decoders.  This can be done in `create-db`:

```clj
;; A map with options (at least :create-if-missing?)
;; {:key-decoder
;;  :key-encoder
;;  :val-decoder
;;  :val-encoder
;;  :create-if-missing?
;;  :error-if-exists?
;;  :write-buffer-size
;;  :block-size
;;  :max-open-files
;;  :cache-size
;;  :comparator
;;  :compress?
;;  :paranoid-checks?
;;  :block-restart-interval
;;  :logger}
clj-ttldb> (def options {:create-if-missing? true})

;; Time to live in seconds
clj-ttldb> (def ttl 1000)

;; open the database with write option
clj-ttldb> (def readonly? false) 

clj-ttldb> (def db (create-db "/tmp/rocksdb" 
                              options
                              ttl
                              readonly?))
#'clj-ttldb/db
clj-ttldb> (put db "a" "b")
nil
clj-ttldb> (get db "a")
"b"
```

Both `put` and `delete` can take multiple values, which will be written in batch:

```clj
clj-ttldb> (put db "a" "b" "c" "d" "e" "f")
nil
clj-ttldb> (delete db "a" "c" "e")
nil
```

If you need to batch a collection of puts and deletes, use `batch`:

```clj
clj-ttldb> (batch db {:put ["a" "b" "c" "d"] :delete ["j" "k" "l"]})
```

We can also get a sequence of all key/value pairs, either in the entire database or within a given range using `iterator`:

```clj
clj-ttldb> (put db "a" "b" "c" "d" "e" "f")
nil
clj-ttldb> (iterator db)
(["a" "b"] ["c" "d"] ["e" "f"])
clj-ttldb> (iterator db "c" nil)
(["c" "d"] ["e" "f"])
clj-ttldb> (iterator db nil "c")
(["a" "b"] ["c" "d"])
```

Syncing writes to disk can be forced via `sync`, and compaction can be forced via `compact`.


## License

EPL-v1.0
Distributed under the Eclipse Public License, the same as Clojure.
