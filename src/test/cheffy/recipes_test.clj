(ns cheffy.recipes-test
  (:require
   [cheffy.util.transit :refer [transit-write]]
   [clojure.test :refer [deftest is testing]]
   [test-utils :as tu]))

;; a bit nasty but does its work - this is set by 'create recipe' test
;; and used by the subsequent tests
;; It also has an advantage that you can run a single test such as 'retrieve recipe'
;; manually without preparing the recipe first
(def recipe-id-store (atom nil))

(defn create-recipe
  "Creates a new recipe and returns it's body."
  []
  (let [recipe-id (:recipe-id (tu/create-entity "/recipes" {:name "name"
                                                            :public true
                                                            :prep-time 30
                                                            :img "https://github.com/clojure.png"}))]
    (is (uuid? recipe-id))
    recipe-id))

(deftest recipes-test
  (testing "list recipes"
    (testing "with auth -- public and drafts"
      (let [{:keys [body]} (tu/assert-response-body 200
                                                    :get "/recipes"
                                                    :headers tu/default-headers)]
        (is (vector? (get body "public")))
        (is (vector? (get body "drafts")))))
    (testing "without auth -- only public "
      (let [{:keys [body]} (tu/assert-response-body 200 :get "/recipes")]
        (is (vector? (get body "public")))
        (is (nil? (get body "drafts"))))))
  (testing "create recipe"
    (let [recipe-id (create-recipe)]
      (reset! recipe-id-store recipe-id)))
  (testing "retrieve recipe"
    (let [{:recipe/keys [recipe-id]} (tu/get-entity (str "/recipes/" @recipe-id-store))]
      (is (uuid? recipe-id))))
  (testing "update recipe"
    (is (= "updated name"
           (:recipe/display-name
            (tu/update-entity (str "/recipes/" @recipe-id-store)
                              {:name "updated name"
                               :public true
                               :prep-time 30
                               :img "https://github.com/clojure.png"})))))
  (testing "delete recipe"
    (tu/delete-entity (str "/recipes/" @recipe-id-store))))

