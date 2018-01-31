(ns
    ^{:doc "A minimal database schema migration library.

  KEY FORMS

  `defmigration` - creates a named migration handler

  `migrate!` - migrate a target database by applying all the migration
  handlers previously defined using `defmigration`

  USAGE

    (require '[nomad.core :as nomad :refer [defmigration]])

    (defmigration initial-schema
      :dependencies [other-migration some-other-migration]
      :up   (fn []
              ;; perform db specific schema ops
              )
      :down (fn []
              ;; db schema ops to rollback this migration
             ))
"}
    nomad.core
  (:require
   [taoensso.timbre :as log]
   [com.stuartsierra.dependency :as dep]))

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

(defn as-keyword [v]
  (if (keyword? v) v (keyword (name v))))

(defn normalize-dependency-names [clause]
  (if (contains? clause :dependencies)
    (update clause :dependencies (fn [d] (vec (map as-keyword d))))
    clause))

(defn register-migration! [tag specs]
  (let [tag (as-keyword tag)
        clause (assoc specs :tag tag)]
    (swap! migrations
           #(-> %
                (update-in [:clauses]
                           (if (contains? (:index @migrations) tag)
                             (do
                               (log/warnf "Redefining migration handler for tag %s"
                                          (pr-str tag))
                               (partial remove-and-conj (partial filter-by-tag tag)))
                             conj)
                           (normalize-dependency-names clause))
                (update-in [:index] conj tag)))
    :ok))

(defn clear-migrations! []
  (reset! migrations {:index #{} :clauses []}))
;; (clear-migrations!)

(defmacro defmigration
  "Register a migration handler providing functions labeled `:up` for
  migrating forward and `:down` for rolling back, e.g.,

    (defmigration init-schema
      :dependencies [other-migration some-other-migration]
      :up           (fn []
                      (jdbc/do-commands
                       \"CREATE TABLE test1(name VARCHAR(32))\"))
      :down         (fn []
                      (jdbc/do-commands
                       \"DROP TABLE test1\")))"
  [tag & specs]
  `(register-migration! '~tag
                        ~(normalize-dependency-names (apply hash-map specs))))

(defn init [migrator]
  (-init migrator))

(defn fini [migrator]
  (-fini migrator))

(defn load-applied-migrations [migrator]
  (-load-migrations migrator))

(defn applied? [migrator tag]
  (-applied? migrator tag))

(defn apply! [migrator tag migration-fn]
  (-apply! migrator tag migration-fn))

(defn sort-tags [clauses]
  (let [dependencies (->> (for [{:keys [tag dependencies]} clauses]
                            (if (empty? dependencies)
                              [[tag :none]]
                              (for [dependency dependencies]
                                [tag dependency])))
                          (mapcat identity))]
    (->> (reduce (fn [graph dependency]
                   (apply dep/depend graph dependency))
                 (dep/graph) dependencies)
         dep/topo-sort
         (filter #(not= % :none)))))

(defn pending-migrations []
  (->> (sort-tags (:clauses @migrations))
       (map
        (fn [tag]
          (or (->> (:clauses @migrations)
                   (filter #(= (:tag %) tag))
                   first)
              (as-> (format "Missing tag %s in registered migrations" tag) errmsg
                (do (log/error errmsg)
                    (throw (Exception. errmsg)))))))))

(defn apply-migrations! [migrator migration-filter]
  (let [applied-migrations (set (load-applied-migrations migrator))
        migration-filter (or migration-filter (constantly true))]
    (doseq [{:as clause :keys [tag up]} (pending-migrations)]
      (when (and (migration-filter clause)
                 (not (contains? applied-migrations tag)))
        (log/infof "Applying migration %s" tag)
        (apply! migrator tag up)))))

(defn migrate!
  "Apply previously defined migration handlers using the specified
  `migrator`."
  ([migrator]
   (migrate! migrator nil))
  ([migrator migration-filter]
   (init migrator)
   (apply-migrations! migrator migration-filter)
   (fini migrator)
   :ok))
