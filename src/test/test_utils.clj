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
      (if (and (= 404 (:status response)) (not= 404 expected-status))
        (throw (ex-info "Entity not found." {:path path :response-body (:body response)}))
        (update response :body transit-read)))))

(def default-headers {"Authorization" "auth|5fbf7db6271d5e0076903601"
                      "Content-Type" "application/transit+json"})

(defn get-entity
  "Gets an entity resource at given path, asserts 200 response and returns body."
  [path]
  (get (assert-response-body 200 :get path :headers default-headers)
       :body))

(defn list-entities
  "Gets all the entities located at given path, asserts 200 response and returns body."
  [path]
  (get-entity path))

(defn create-entity
  "Creates a new entity at given path and returns response body.
  Automatically adds Authorization header using one of the seed accounts.
  Assumes response status 201."
  [path entity-data]
  (get (assert-response-body 201 :post path
                             :headers default-headers
                             :body (transit-write entity-data))
       :body))

(defn update-entity
  "Updates an existing entity at given path and returns the response.
  Asserts that the response status is 204.
  Then calls GET on the `path` to verify the resource and returns the body
  so the caller can perform more verifications"
  [path entity-data]
  (assert-response-body 204 :put path
                        :headers default-headers
                        :body (transit-write entity-data))
  (get-entity path))

(defn delete-entity
  "Deletes the resource/entity at given path (assuming it contains the id).
  Assumes response status 204."
  [path]
  (assert-response 204 :delete path :headers default-headers)
  ;; try to get again to check it was indeed deleted
  (assert-response 404 :get path :headers default-headers))
