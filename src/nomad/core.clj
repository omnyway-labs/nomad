(ns
    ^{:doc "A minimal database schema migration library.

  KEY FORMS

  `defmigration` - creates a named migration handler

  `migrate!` - migrate a target database by applying all the migration
  handlers previously defined using `defmigration`

  USAGE

    (require '[nomad.core :as nomad :refer [defmigration]])

    (defmigration initial-schema
      :up   (fn []
              ;; perform db specific schema ops
              )
      :down (fn []
              ;; db schema ops to rollback this migration
             ))
"}
    nomad.core
  (:require
   [taoensso.timbre :as log]))

(defprotocol IMigrator
  (-init [this])
  (-fini [this])
  (-load-migrations [this])
  (-applied? [this tag])
  (-apply! [this tag migration-fn]))

(defonce migrations (atom {:index #{} :clauses []}))

(defn register-migration!
  ([tag]
   (register-migration! tag (constantly nil)))
  ([tag up-fn]
   (register-migration! tag up-fn (constantly nil)))
  ([tag up-fn down-fn]
   (let [clause {:tag tag :up up-fn :down down-fn}]
     (if (contains? (:index @migrations) tag)
       (throw
        (ex-info (str "migration exists for tag " (pr-str tag)) {:tag tag}))
       (swap! migrations
              #(-> %
                   (update-in [:index] conj tag)
                   (update-in [:clauses] conj clause))))
     :ok)))

(defn clear-migrations! []
  (reset! migrations {:index #{} :clauses []}))
;; (clear-migrations!)

(defmacro defmigration
  "Register a migration handler providing functions labeled `:up` for
  migrating forward and `:down` for rolling back, e.g.,

    (defmigration init-schema
      :up   (fn []
              (jdbc/do-commands
               \"CREATE TABLE test1(name VARCHAR(32))\"))
      :down (fn []
              (jdbc/do-commands
               \"DROP TABLE test1\")))"
  [tag & {:keys [up down]}]
  `(register-migration! (name '~tag) ~up ~down))

(defn init [migrator]
  (-init migrator))

(defn fini [migrator]
  (-fini migrator))

(defn load-migrations [migrator]
  (-load-migrations migrator))

(defn applied? [migrator tag]
  (-applied? migrator tag))

(defn apply! [migrator tag migration-fn]
  (-apply! migrator tag migration-fn))

(defn apply-migrations! [migrator]
  (let [existing-migrations (set (load-migrations migrator))]
    (doseq [{:as clause :keys [tag up]} (-> @migrations :clauses)]
      (when-not (contains? existing-migrations tag)
        (log/infof "Applying migration %s" tag)
        (apply! migrator tag up)))))

(defn migrate!
  "Apply previously defined migration handlers using the specified
  `migrator`."
  [migrator]
  (init migrator)
  (apply-migrations! migrator)
  (fini migrator)
  :ok)
