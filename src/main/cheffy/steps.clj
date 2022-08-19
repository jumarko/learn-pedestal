(ns cheffy.steps
  "Route handlers / interceptors for recipe steps."
  (:require
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(defn step-on-request [{:keys [request] :as ctx}]
  (let [{:keys [recipe-id description sort-order]} (:transit-params request)
        step-id (or (some-> (get-in request [:path-params :step-id]) parse-uuid)
                    (random-uuid))]
    (assoc ctx
           :tx-data [{:recipe/recipe-id recipe-id
                      :recipe/steps [{:step/step-id step-id
                                      :step/description description
                                      :step/sort-order sort-order}]}]
           :update? (when (get-in request [:path-params :step-id])
                      step-id))))

(defn step-on-response [{:keys [tx-data] :as ctx}]
  (let [{:recipe/keys [steps]} (first tx-data)
        step-id (-> steps first :step/step-id)]
    (assoc ctx :response
           (if (:update? ctx)
             {:status 204}
             (response/created (str "/steps/" step-id)
                               {:step-id step-id})))))

(def step-interceptor (around ::step step-on-request step-on-response))

(def upsert-step
  [step-interceptor
   interceptors/transact-interceptor])
