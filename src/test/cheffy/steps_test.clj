(ns cheffy.steps-test
  (:require
   [cheffy.recipes-test :as recipes-test]
   [clojure.test :refer [deftest is testing]]
   [test-utils :as tu]))

(def step-id-store (atom nil))

(deftest steps-test
  (let [recipe-id (recipes-test/create-recipe)]
    (testing "create step"
    ;; first create a recipe so we can reference it
      (let [{:keys [step-id]} (tu/create-entity "/steps"
                                                {:recipe-id recipe-id
                                                 :description "My first step"
                                                 :sort-order 1})]
        (is (uuid? step-id))
        (reset! step-id-store step-id)))

    (testing "update step"
      (tu/update-entity (str "/steps/" @step-id-store)
                        {:recipe-id recipe-id
                         :description "Updated step"
                         :sort-order 2}))

    (testing "retrieve step"
      (let [{:recipe/keys [recipe-id]} (tu/get-entity (str "/recipes/" @step-id-store))]
        (is (uuid? recipe-id))))

    (testing "delete step"
      (tu/delete-entity (str "/steps/" @step-id-store)))))
