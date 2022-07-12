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
                                      :headers {"Authorization" "auth|5fbf7db6271d5e0076903601"}
                                      ;; TODO: check logs and notice that this doesn't work
                                      ;; - :name, :public, :prep-time and :img are all nil!
                                      :body (transit-write {:name "name"
                                                            :public true
                                                            :prep-time 30
                                                            :img "https://github.com/clojure.png"}))]
      (is (uuid? (:recipe-id body))))))
