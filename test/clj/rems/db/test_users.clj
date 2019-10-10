(ns ^:integration rems.db.test-users
  (:require [clojure.test :refer :all]
            [rems.db.roles :as roles]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-users
  ;; TODO: enforce that userid and eppn must be same?
  (users/add-user! "user1" {:eppn "whatever"
                            :some-attr "some value"})

  (testing "get-user-attributes"
    (is (= {:eppn "whatever"
            :some-attr "some value"}
           (users/get-user-attributes "user1"))))

  (testing "get-all-users"
    (is (= [{:eppn "whatever"
             :some-attr "some value"}]
           (users/get-all-users))))

  (testing "get-users-with-role"
    (roles/add-role! "user1" :owner)
    (is (= ["user1"] (users/get-users-with-role :owner)))
    (is (= [] (users/get-users-with-role :reporter)))))

(deftest test-nonexistent-user
  (is (= {:userid "nonexistent"
          :name nil
          :email nil}
         (users/get-user "nonexistent"))))
