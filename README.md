# clj-rocksdb
This is a self-contained wrapper around [RocksDB](https://rocksdb.org), which provides all the necessary binaries via [RocksDB Java API](https://github.com/facebook/rocksdb/tree/master/java/src/main/java/org/rocksdb).

The source code and the documentation is hevaily based on [Factual/clj-leveldb](https://github.com/Factual/clj-leveldb) library.

### basic usage

```clj
[kotyo/clj-rocksdb "0.1.5"]
```

To create or access a database, use `clj-rocksdb/create-db`:
The returned database object can be used with `clj-rocksdb/get`, `put`, `delete`, `batch`, and `iterator`.
RocksDB by default stores the keys and values in byte arrays. We may want to define custom encoders and decoders. This can be done in `create-db`:

Notice that the value returned is a byte-array.  This is because byte arrays are the native storage format for RocksDB, and we haven't defined custom encoders and decoders.  This can be done in `create-db`:

```clj
clj-rocksdb> (def db (create-db "/tmp/leveldb" 
                       {:key-encoder taoensso.nippy/freeze :key-decoder taoensso.nippy/thaw 
                        :val-encoder taoensso.nippy/freeze :val-decoder taoensso.nippy/thaw}))
#'clj-rocksdb/db
clj-rocksdb> (put db "a" "b")
nil
clj-rocksdb> (get db "a")
"b"
```

Both `put` and `delete` can take multiple values, which will be written in batch:

```clj
clj-rocksdb> (put db "a" "b" "c" "d" "e" "f")
nil
clj-rocksdb> (delete db "a" "c" "e")
nil
```

If you need to batch a collection of puts and deletes, use `batch`:

```clj
clj-rocksdb> (batch db {:put ["a" "b" "c" "d"] :delete ["j" "k" "l"]})
```

We can also get a sequence of all key/value pairs, either in the entire database or within a given range using `iterator`:

```clj
clj-rocksdb> (put db "a" "b" "c" "d" "e" "f")
nil
clj-rocksdb> (iterator db)
(["a" "b"] ["c" "d"] ["e" "f"])
clj-rocksdb> (iterator db "c" nil)
(["c" "d"] ["e" "f"])
clj-rocksdb> (iterator db nil "c")
(["a" "b"] ["c" "d"])
```

Syncing writes to disk can be forced via `sync`, and compaction can be forced via `compact`.


### license

done by kotyo
EPL-v1.0

### original license

Copyright © 2013 Factual, Inc.

Distributed under the Eclipse Public License, the same as Clojure.
