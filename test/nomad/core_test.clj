(ns nomad.core-test
  (:require
   [clojure.test :refer :all]
   [nomad.core :as nomad]))

(deftest migrations
  (is (= {:index #{}, :clauses []} (nomad/clear-migrations!)))
  
  (is (= :ok
         (nomad/defmigration verify-defmigration
           :up (fn []
                 (println
                  "CREATE TABLE test1(name VARCHAR(32))")))))

  (is (= :ok
         (nomad/register-migration! :init-schema
                                    {:up (fn []
                                           (println
                                            "CREATE TABLE test1(name VARCHAR(32))"))})))
  (is (= :ok 
         (nomad/register-migration! :add-test1-age
                                    {:up (fn []
                                           (println
                                            "ALTER TABLE test1 ADD COLUMN age INTEGER"))})))

  (is (= [3 3]
         [(count (-> @nomad/migrations :clauses))
          (count (-> @nomad/migrations :index))]))

  (is (= :ok 
         (nomad/register-migration! "add-test1-age"
                                    {:up (fn []
                                           (println
                                            "ALTER TABLE test1 ADD COLUMN age INTEGER"))})))

  (is (= [3 3]
         [(count (-> @nomad/migrations :clauses))
          (count (-> @nomad/migrations :index))]))

  (is (= #{:add-test1-age :init-schema :verify-defmigration}
         (->> @nomad/migrations :clauses (map :tag) set))))

(defrecord MockMigrator [state])
(extend MockMigrator
  nomad/IMigrator
  {:-init (fn [this]
            (reset! (:state this)
                    {:migrations []
                     :applied #{}}))
   :-fini (fn [this] )
   :-load-migrations (fn [this]
                       (-> this
                           :state
                           deref
                           :migrations))
   :-applied? (fn [this tag]
                (-> this
                    :state
                    deref
                    :applied
                    (contains? tag)))
   :-apply! (fn [this tag mfn]
              (mfn)
              (swap! (:state this) update :applied conj tag))})

(deftest migrations-with-meta
  (is (= {:index #{}, :clauses []} (nomad/clear-migrations!)))

  (is (= :ok
         (nomad/defmigration datomic-migration
           :meta {:backend :datomic}
           :up (fn []
                 (println "Do datomic stuff")))))

  (is (= :ok
         (nomad/defmigration other-migration
           :up (fn []
                 (println "Do non-datomic stuff")))))

  (is (= [2 2]
         [(count (-> @nomad/migrations :clauses))
          (count (-> @nomad/migrations :index))]))

  (let [migrator (MockMigrator. (atom nil))]
    (is (= :ok
           (nomad/migrate! migrator
                           (fn [{:keys [meta]}]
                             (not= (:backend meta) :datomic)))))

    (is (nomad/applied? migrator :other-migration))
    (is (not (nomad/applied? migrator :datomic-migration)))

    (is (= :ok
           (nomad/migrate! migrator
                           (fn [{:keys [meta]}]
                             (= (:backend meta) :datomic)))))
    (is (nomad/applied? migrator :datomic-migration)))

  )
