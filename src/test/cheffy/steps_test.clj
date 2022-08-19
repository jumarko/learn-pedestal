(ns cheffy.steps-test
  (:require
   [cheffy.recipes-test :as recipes-test]
   [clojure.test :refer [deftest is testing]]
   [test-utils :as tu]))

(def step-id-store (atom nil))

(deftest steps-test
  (let [recipe-id (recipes-test/create-recipe)]
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
                         :sort-order 2})

      ;; TODO: later when the GET route is available: get again and check that the name was updated
      #_(is (= "Updated step"
             (-> (tu/assert-response-body
                  200 :get (str "/steps/" @step-id-store)
                  :headers tu/default-headers)
                 :body
                 :recipe/display-name))))
    #_(testing "retrieve recipe"
        (let [{:keys [body]} (tu/assert-response-body 200
                                                      :get (str "/recipes/" @step-id-store)
                                                      :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"})]
          (is (uuid? (:recipe/recipe-id body)))))

    #_(testing "delete recipe"
        (tu/assert-response 204
                            :delete (str "/recipes/" @step-id-store)
                            :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"
                                      "Content-Type" "application/transit+json"})

    ;; get again and check that the name was updated
        (tu/assert-response 404
                            :get (str "/recipes/" @step-id-store)
                            :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"}))))
