## Overview

Nomad is the OmnyPay schema migration library

## Usage

Define migration handlers as follows:

```clojure
(require '[nomad.core :as nomad :refer [defmigration]])
(require '[clojure.java.jdbc :as jdbc])

(defmigration initial-schema
  :up   (fn []
          (jdbc/do-commands
           "CREATE TABLE test(name VARCHAR(32))"))
  :down (fn []
          (jdbc/do-commands
           "DROP TABLE test")))

(defmigration add-age-column
  :up   (fn []
          (jdbc/do-commands
           "ALTER TABLE test ADD COLUMN age INTEGER"))
  :down (fn []
          (jdbc/do-commands
           "ALTER TABLE test DROP COLUMN age")))

```

Create a `migrator` using one of the supported nomad migrators (currently H2 and Postgres).

```clojure
(require '[nomad.migrator.h2 :as h2])

;; create a migrator
(def migrator (h2/connect {:db "mem:test;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE"}))

;; apply migrations
(nomad/migrate! migrator)
16-08-06 19:11:19 luna INFO [nomad.core:89] - Applying migration init-schema
16-08-06 19:11:19 luna INFO [nomad.core:89] - Applying migration add-test1-age
```

## References

See [nomad-migrators](https://github.com/omnypay/nomad-migrators/tree/master/src/nomad/migrator) for examples of how to create migrators for different databases.
