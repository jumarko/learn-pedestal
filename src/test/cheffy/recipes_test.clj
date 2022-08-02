(ns test.cheffy.recipes-test
  (:require [cheffy.util.transit :refer [transit-read transit-write]]
            [clojure.test :refer [deftest is testing]]
            [com.stuartsierra.component.repl :as cr]
            [io.pedestal.test :as pt]
            [io.pedestal.http :as http]))


(defn- ok-response
  [expected-status method path & options]
  (let [response (-> (apply pt/response-for
                            (-> cr/system :api-server :service ::http/service-fn)
                            method path
                            options)
                     (update :body transit-read))]
    (is (= expected-status (:status response)))
    response))

;; a bit nasty but does its work - this is set by 'create recipe' test
;; and used by the subsequent tests
;; It also has an advantage that you can run a single test such as 'retrieve recipe'
;; manually without preparing the recipe first
(def recipe-id-store (atom nil))

(deftest recipes-test
  (testing "list recipes"
    (testing "with auth -- public and drafts"
      (let [{:keys [body]} (ok-response 200 :get "/recipes"
                                        :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"})]
        (is (vector? (get body "public")))
        (is (vector? (get body "drafts")))))
    (testing "without auth -- only public "
      (let [{:keys [body]} (ok-response 200 :get "/recipes")]
        (is (vector? (get body "public")))
        (is (nil? (get body "drafts"))))))
  (testing "create recipe"
    (let [{:keys [body]} (ok-response 201 :post "/recipes"
                                      :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"
                                                "Content-Type" "application/transit+json"}
                                      :body (transit-write {:name "name"
                                                            :public true
                                                            :prep-time 30
                                                            :img "https://github.com/clojure.png"}))
          recipe-id (:recipe-id body)]
      (is (uuid? recipe-id))
      (reset! recipe-id-store recipe-id)))
  ;; TODO: fix this - it fails with EOFException again,
  ;; maybe because the 'create recipe' step doesn't work properly
  ;; the db query simply returns an empty result
  (testing "retrieve recipe"
    (let [{:keys [body]} (ok-response 200 :get (str "/recipes/" @recipe-id-store)
                                      :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"})]
      (is (uuid? (:recipe/recipe-id body))))))


