(ns test.cheffy.recipes-test
  (:require [cheffy.util.transit :refer [transit-read transit-write]]
            [clojure.test :refer [deftest is testing]]
            [com.stuartsierra.component.repl :as cr]
            [io.pedestal.test :as pt]
            [io.pedestal.http :as http]))


(defn- ok-response
  ([path] (ok-response path nil))
  ([path headers]
   (let [response (-> (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                                       :get path
                                       :headers headers)
                      (update :body transit-read))]
     (is (= 200 (:status response)))
     response)))

(deftest recipes-test
  (testing "list recipes"
    (testing "with auth -- public and drafts"
      (let [{:keys [body]} (ok-response "/recipes"
                                               {"Authorization"  "auth|5fbf7db6271d5e0076903601"})]
        (is (vector? (get body "public")))
        (is (vector? (get body "drafts")))))
    (testing "without auth -- only public "
      (let [{:keys [body]} (ok-response "/recipes")]
        (is (vector? (get body "public")))
        (is (nil? (get body "drafts")))))))
