(ns cheffy.steps-test
  (:require
   [cheffy.recipes-test :as recipes-test]
   [cheffy.util.transit :refer [transit-write]]
   [clojure.test :refer [deftest is testing]]
   [test-utils :as tu]))

(def step-id-store (atom nil))

(deftest steps-test
  #_(testing "list recipes"
      (testing "with auth -- public and drafts"
        (let [{:keys [body]} (tu/assert-response-body 200
                                                      :get "/recipes"
                                                      :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"})]
          (is (vector? (get body "public")))
          (is (vector? (get body "drafts")))))
      (testing "without auth -- only public "
        (let [{:keys [body]} (tu/assert-response-body 200 :get "/recipes")]
          (is (vector? (get body "public")))
          (is (nil? (get body "drafts"))))))
  (testing "create step"
    ;; first create a recipe so we can reference it
    (let [recipe-id (recipes-test/create-recipe)
          {:keys [body]}
          (tu/assert-response-body 201
                                   :post "/steps"
                                   :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"
                                             "Content-Type" "application/transit+json"}
                                   :body (transit-write {:recipe-id recipe-id
                                                         :description "My first recipe"
                                                         :sort-order 1 ; TODO: not sure what this means
                                                         }))
          step-id (:step-id body)]
      (is (uuid? step-id))
      (reset! step-id-store step-id)))
  #_(testing "retrieve recipe"
      (let [{:keys [body]} (tu/assert-response-body 200
                                                    :get (str "/recipes/" @step-id-store)
                                                    :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"})]
        (is (uuid? (:recipe/recipe-id body)))))
  #_(testing "update recipe"
      (tu/assert-response 200
                          :put (str "/recipes/" @step-id-store)
                          :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"
                                    "Content-Type" "application/transit+json"}
                          :body (transit-write {:name "updated name"
                                                :public true
                                                :prep-time 30
                                                :img "https://github.com/clojure.png"}))
    ;; get again and check that the name was updated
      (is (= "updated name"
             (-> (tu/assert-response-body
                  200 :get (str "/recipes/" @step-id-store)
                  :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"})
                 :body
                 :recipe/display-name))))
  #_(testing "delete recipe"
      (tu/assert-response 204
                          :delete (str "/recipes/" @step-id-store)
                          :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"
                                    "Content-Type" "application/transit+json"})

    ;; get again and check that the name was updated
      (tu/assert-response 404
                          :get (str "/recipes/" @step-id-store)
                          :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"})))




