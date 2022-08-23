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

;;; GET step - Note: this isn't covered in the course
;;; but it was convenient  (e.g. in the update tests).
(defn find-step-on-request
  [ctx]
  (let [step-id (parse-uuid (get-in ctx [:request :path-params :step-id]))]
    (assoc ctx :q-data {:query '[:find (pull ?e [*])
                                 :in $ ?step-id
                                 :where [?e :step/step-id ?step-id]]
                        ;; Notice that we do not add `db` as arg although it's required by datomic
                        ;; - it is supplied by `query-interceptor`
                        :args [step-id]})))

(defn find-step-on-response
  [{:keys [q-result] :as ctx}]
  (if (empty? q-result)
    (assoc ctx :response (response/not-found (str "step not found: " (-> ctx :q-data :args first))))
    (assoc ctx :response (response/response (ffirst q-result)))))

(def find-step-interceptor (around ::find-step find-step-on-request find-step-on-response))

(def retrieve-step
  [find-step-interceptor
   interceptors/query-interceptor])

(defn retract-step-on-request [{:keys [request] :as ctx}]
  (let [step-id (parse-uuid (get-in request [:path-params :step-id]))]
    (assoc ctx :tx-data [[:db/retractEntity [:step/step-id step-id]]])))

(defn retract-step-on-response [ctx]
  (assoc ctx :response (response/status 204)))

(def retract-step-interceptor (around ::retract-step retract-step-on-request retract-step-on-response))

(def delete-step
  [retract-step-interceptor
   interceptors/transact-interceptor])

