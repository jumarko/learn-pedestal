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
      (let [{:step/keys [step-id]} (tu/get-entity (str "/steps/" @step-id-store))]
        (is (uuid? step-id))))

    (testing "delete step"
      (tu/delete-entity (str "/steps/" @step-id-store)))))

(comment

  ;; this will only return OK if you do not run the last test "delete step"
  (tu/get-entity (str "/steps/" @step-id-store))
  ;;=>
  {:db/id 17592186045821,
   :step/step-id #uuid "a2fab962-e974-42b5-bf30-df848584c560",
   :step/description "Updated step",
   :step/sort-order 2}

  ;; this is a step entity from seed.edn
  (tu/get-entity (str "/steps/" "867ed4bf-4628-48f4-944d-e6b7786bfa92" ))
  {:db/id 17592186045422,
   :step/step-id #uuid "867ed4bf-4628-48f4-944d-e6b7786bfa92",
   :step/description "First step",
   :step/sort-order 1}
  )
