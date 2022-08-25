(ns cheffy.crud
  "Generic CRUD handlers reusable for different entities like recipes, steps, ingredients, etc.

  There are 3 main public functions representing the 4 operations:
  - `upsert` - for Create (POST) and Update (PUT)
  - `retrieve` - for GET
  - `delete` - for DELETE
  All of them take at least `id-key` that is a namespaced keyword representing the Datomic entity ID,
  e.g. `:recipe/recipe-id`, `:ingredient/ingredient-id`, etc.
  The sipmle (non-namespaced) version of `id-key` is used to lookup the ID in request's `:path-params`."
  (:require
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(defn simple-id [id-key]
  (keyword (name id-key)))

;;; CREATE + UPDATE
(defn- entity-on-request [id-key params->entity-fn]
  (fn [{:keys [request] :as ctx}]
    (let [entity-id (or (some-> (get-in request [:path-params (simple-id id-key)]) parse-uuid)
                        (random-uuid))
          entity (params->entity-fn entity-id (:transit-params request))]
      (assoc ctx
             :tx-data [entity]
             :entity-id entity-id
             :update? (boolean (get-in request [:path-params (simple-id id-key)]))))))

(defn- entity-on-response [id-key]
  (fn [{:keys [entity-id] :as ctx}]
    (assoc ctx :response
           (if (:update? ctx)
             {:status 204}
             (response/created (str "/" (namespace id-key) "s/" ; to get e.g. "/recipes/"
                                    entity-id)
                               {(simple-id id-key) entity-id})))))

(defn upsert
  "Takes id-key and a function to convert body parameters (:transit-params)
  to a Datomic entity data suitable for passing as `:tx-data`
  (the entity will be wrapped in a vector and passed as `:tx-data` without further modifications)."
  [id-key params->entity-fn]
  [(around (entity-on-request id-key params->entity-fn) (entity-on-response id-key))
   interceptors/transact-interceptor])

;;; GET
(defn- find-on-request [id-key]
  (fn [ctx]
    (let [entity-id (parse-uuid (get-in ctx [:request :path-params (simple-id id-key)]))]
      (assoc ctx :q-data {:query '[:find (pull ?e [*])
                                   :in $ ?id-key ?entity-id
                                   :where [?e ?id-key ?entity-id]]
                          ;; Notice that we do not add `db` as arg although it's required by datomic
                          ;; - it is supplied by `query-interceptor`
                          :args [id-key entity-id]}))))

(defn- find-on-response [{:keys [q-result] :as ctx}]
  (if (empty? q-result)
    (assoc ctx :response (response/not-found (str "Entity not found: " (-> ctx :q-data :args first))))
    (assoc ctx :response (response/response (ffirst q-result)))))

(defn retrieve [id-key]
  [(around (find-on-request id-key) find-on-response)
   interceptors/query-interceptor])

;;; DELETE
(defn- retract-on-request [id-key]
  (fn [{:keys [request] :as ctx}]
    (let [entity-id (parse-uuid (get-in request [:path-params (simple-id id-key)]))]
      (assoc ctx :tx-data [[:db/retractEntity [id-key entity-id]]]))))

(defn- retract-on-response [ctx]
  (assoc ctx :response (response/status 204)))

(defn delete [id-key]
  [(around (retract-on-request id-key) retract-on-response)
   interceptors/transact-interceptor])

;;; TODO: add `crud` macro that will generate all the route handlers
;;; instead of manually defining all 4 of them as defs?
