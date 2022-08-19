(ns test-utils
  (:require
   [cheffy.util.transit :refer [transit-read transit-write]]
   [clojure.test :refer [is]]
   [com.stuartsierra.component.repl :as cr]
   [io.pedestal.test :as pt]
   [io.pedestal.http :as http]))

(defn assert-response
  [expected-status method path & options]
  (let [response (apply pt/response-for
                        (-> cr/system :api-server :service ::http/service-fn)
                        method
                        path
                        options)]
    (is (= expected-status (:status response)))
    response))

(defn assert-response-body
  ;; for destructuring, see https://clojure.org/guides/destructuring#_keyword_arguments
  [expected-status method path & options]
  (let [response (apply assert-response expected-status method path options)]
    (if (= 204 expected-status)
      response
      (update response :body transit-read))))

(def default-headers {"Authorization" "auth|5fbf7db6271d5e0076903601"
                      "Content-Type" "application/transit+json"})

(defn create-entity
  "Creates a new entity at given path and returns response body.
  Automatically adds Authorization header using one of the seed accounts.
  Assumes response status 201."
  [path entity-data]
  (get (assert-response-body 201
                             :post path
                             :headers default-headers
                             :body (transit-write entity-data))
       :body))

(defn update-entity
  "Updates an existing entity at given path and returns the response.
  Asserts that the response status is 204."
  [path entity-data]
  (get (assert-response-body 204
                             :put path
                             :headers default-headers
                             :body (transit-write entity-data))
       :body))
