(ns cheffy.ingredients-test
  (:require
   [cheffy.recipes-test :as recipes-test]
   [clojure.test :refer [deftest is testing]]
   [test-utils :as tu]))

(def ingredient-id-store (atom nil))

(deftest ingredients-test
  (let [recipe-id (recipes-test/create-recipe)]
    (testing "create ingredient"
    ;; first create a recipe so we can reference it
      (let [{:keys [ingredient-id]} (tu/create-entity "/ingredients"
                                                {:recipe-id recipe-id
                                                 :name "ingredient name"
                                                 :amount 500
                                                 :measure "something"
                                                 :sort-order 1})]
        (is (uuid? ingredient-id))
        (reset! ingredient-id-store ingredient-id)))

    (testing "update ingredient"
      (tu/update-entity (str "/ingredients/" @ingredient-id-store)
                        {:recipe-id recipe-id
                         :name "updated name"
                         :amount 500
                         :measure "something"
                         :sort-order 1}))

    (testing "retrieve ingredient"
      (let [{:ingredient/keys [ingredient-id amount display-name]} (tu/get-entity (str "/ingredients/" @ingredient-id-store))]
        (is (uuid? ingredient-id))
        (is (= "updated name" display-name))
        (is (= 500 amount))))

    (testing "delete ingredient"
      (tu/delete-entity (str "/ingredients/" @ingredient-id-store)))))
