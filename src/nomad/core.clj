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

(defn remove-and-conj [pred-fn c v]
  (into (empty c)
        (conj (remove pred-fn c) v)))

(defn filter-by-tag [tag x] (= tag (:tag x)))

(defn register-migration! [tag specs]
  (let [clause (assoc specs :tag tag)]
    (swap! migrations
           #(-> %
                (update-in [:clauses]
                           (if (contains? (:index @migrations) tag)
                             (do
                               (log/warnf "Redefining migration handler for tag %s"
                                          (pr-str tag))
                               (partial remove-and-conj (partial filter-by-tag tag)))
                             conj)
                           clause)
                (update-in [:index] conj tag)))
    :ok))

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
  [tag & specs]
  `(register-migration! (name '~tag) specs))

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
