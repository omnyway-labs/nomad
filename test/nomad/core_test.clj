(ns nomad.core-test
  (:require
   [clojure.test :refer :all]
   [nomad.core :as nomad]))

(deftest migrations
  (is (= {:index #{}, :clauses []} (nomad/clear-migrations!)))
  (is (= :ok
         (nomad/register-migration! "init-schema"
                                    (fn []
                                      (println
                                       "CREATE TABLE test1(name VARCHAR(32))")))))
  (is (= :ok 
         (nomad/register-migration! "add-test1-age"
                                    (fn []
                                      (println
                                       "ALTER TABLE test1 ADD COLUMN age INTEGER")))))

  (is (= [2 2]
         [(count (-> @nomad/migrations :clauses))
          (count (-> @nomad/migrations :index))]))

  (is (= :ok 
         (nomad/register-migration! "add-test1-age"
                                    (fn []
                                      (println
                                       "ALTER TABLE test1 ADD COLUMN age INTEGER")))))

  (is (= [2 2]
         [(count (-> @nomad/migrations :clauses))
          (count (-> @nomad/migrations :index))]))

  (is (= ["add-test1-age" "init-schema"]
         (->> @nomad/migrations :clauses (map :tag)))))

