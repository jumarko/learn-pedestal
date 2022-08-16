(ns cheffy.steps
  "Route handlers / interceptors for recipe steps."
  (:require
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(defn step-on-request [{:keys [request] :as ctx}]
  (let [{:keys [recipe-id description sort-order]} (:transit-params request)
        ;; TODO: get value from request for UPDATE operation
        step-id (random-uuid)]
    (assoc ctx :tx-data [{:recipe/recipe-id recipe-id
                          :recipe/steps [{:step/step-id step-id
                                          :step/description description
                                          :step/sort-order sort-order}]}])))

(defn step-on-response [{:keys [tx-data] :as ctx}]
  (let [{:recipe/keys [recipe-id steps]} (first tx-data)
        step-id (-> steps first :step/step-id)]
    ;; TODO: shouldn't we return a link to '/steps/<step-id>' instead?
    (assoc ctx :response (response/created (str "/recipes/" recipe-id)
                                           {:step-id step-id}))))

(def step-interceptor (around ::step step-on-request step-on-response))

(def create-step
  [step-interceptor
   interceptors/transact-interceptor])
