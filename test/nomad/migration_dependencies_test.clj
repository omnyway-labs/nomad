(ns nomad.migration-dependencies-test
  (:require  [clojure.test :refer :all]
             [nomad.core :as nomad]))

(deftest migrations-with-dependencies
  (testing "All dependent migrations"
    (is (= {:index #{}, :clauses []} (nomad/clear-migrations!)))

    ;; mix up migration orders

    (is (= :ok
           (nomad/register-migration! "add-test1-age-string"
                                      {:up (fn []
                                             (println
                                              "ALTER TABLE test1 ADD COLUMN age STRING"))
                                       :dependencies ["init-test1"]})))
    (is (= :ok
           (nomad/register-migration! "init-test1"
                                      {:up (fn []
                                             (println
                                              "CREATE TABLE test1
                                             (id VARCHAR,
                                              name VARCHAR(32))"))})))
    (is (= :ok
           (nomad/register-migration! "rename-test1.age-to-age-years"
                                      {:up (fn []
                                             (println
                                              "ALTER TABLE test1 RENAME age TO age_years"))
                                       :dependencies ["alter-test1.age-to-integer"]})))
    (is (= :ok
           (nomad/register-migration! "alter-test1.age-to-integer"
                                      {:up (fn []
                                             (println
                                              "ALTER TABLE test1 ALTER age TYPE INTEGER"))
                                       :dependencies ["add-test1-age-string"]})))

    (is (= ["init-test1" "add-test1-age-string" "alter-test1.age-to-integer" "rename-test1.age-to-age-years"]
           (map :tag (nomad/pending-migrations)))))

  (testing "Dependent and independent migrations"
    (is (= {:index #{}, :clauses []} (nomad/clear-migrations!)))

    (is (= :ok
           (nomad/register-migration! "init-test3"
                                      {:up (fn []
                                             (println
                                              "CREATE TABLE test3
                                             (id VARCHAR,
                                              token VARCHAR)"))})))

    (is (= :ok
           (nomad/register-migration! "alter-test2.id"
                                      {:up (fn []
                                             (println
                                              "ALTER TABLE test2 ALTER id TYPE VARCHAR(36)"))
                                       :dependencies ["init-test2"]})))
    (is (= :ok
           (nomad/register-migration! "init-test2"
                                      {:up (fn []
                                             (println
                                              "CREATE TABLE test2
                                             (id VARCHAR,
                                              name VARCHAR(32))"))})))
    (is (= :ok
           (nomad/register-migration! "init-test4"
                                      {:up (fn []
                                             (println
                                              "CREATE TABLE test3
                                             (c1 INTEGER,
                                              c2 INTEGER)"))})))

    (let [ordered-migration-tags (map :tag (nomad/pending-migrations))]
      (is (= #{"init-test3" "init-test2" "alter-test2.id" "init-test4"}
             (set ordered-migration-tags)))

      (is (< (.indexOf ordered-migration-tags "init-test2")
             (.indexOf ordered-migration-tags "alter-test2.id"))))))
