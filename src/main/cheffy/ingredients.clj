(ns cheffy.ingredients
  "Route handlers / interceptors for ingredients - see Lesson 42:
  https://www.jacekschae.com/view/courses/learn-pedestal-pro/1409479-ingredients-and-ingredients/4435320-42-ingredient.
  See also `seed.edn` file to explore ingredients data."
  (:require
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(defn ingredient-on-request [{:keys [request] :as ctx}]
  (let [{:keys [recipe-id name amount measure sort-order]} (:transit-params request)
        ingredient-id (or (some-> (get-in request [:path-params :ingredient-id]) parse-uuid)
                          (random-uuid))]
    (assoc ctx
           :tx-data [{:recipe/recipe-id recipe-id
                      :recipe/ingredients [{:ingredient/ingredient-id ingredient-id
                                            :ingredient/display-name name
                                            :ingredient/amount amount
                                            :ingredient/measure measure
                                            :ingredient/sort-order sort-order}]}]
           :update? (when (get-in request [:path-params :ingredient-id])
                      ingredient-id))))

(defn ingredient-on-response [{:keys [tx-data] :as ctx}]
  (let [{:recipe/keys [ingredients]} (first tx-data)
        ingredient-id (-> ingredients first :ingredient/ingredient-id)]
    (assoc ctx :response
           (if (:update? ctx)
             {:status 204}
             (response/created (str "/ingredients/" ingredient-id)
                               {:ingredient-id ingredient-id})))))

(def ingredient-interceptor (around ::ingredient ingredient-on-request ingredient-on-response))

(def upsert-ingredient
  [ingredient-interceptor
   interceptors/transact-interceptor])

;;; GET ingredient - Note: this isn't covered in the course
;;; but it was convenient  (e.g. in the update tests).
(defn find-ingredient-on-request
  [ctx]
  (let [ingredient-id (parse-uuid (get-in ctx [:request :path-params :ingredient-id]))]
    (assoc ctx :q-data {:query '[:find (pull ?e [*])
                                 :in $ ?ingredient-id
                                 :where [?e :ingredient/ingredient-id ?ingredient-id]]
                        ;; Notice that we do not add `db` as arg although it's required by datomic
                        ;; - it is supplied by `query-interceptor`
                        :args [ingredient-id]})))

(defn find-ingredient-on-response
  [{:keys [q-result] :as ctx}]
  (if (empty? q-result)
    (assoc ctx :response (response/not-found (str "ingredient not found: " (-> ctx :q-data :args first))))
    (assoc ctx :response (response/response (ffirst q-result)))))

(def find-ingredient-interceptor (around ::find-ingredient find-ingredient-on-request find-ingredient-on-response))

(def retrieve-ingredient
  [find-ingredient-interceptor
   interceptors/query-interceptor])

(defn retract-ingredient-on-request [{:keys [request] :as ctx}]
  (let [ingredient-id (parse-uuid (get-in request [:path-params :ingredient-id]))]
    (assoc ctx :tx-data [[:db/retractEntity [:ingredient/ingredient-id ingredient-id]]])))

(defn retract-ingredient-on-response [ctx]
  (assoc ctx :response (response/status 204)))

(def retract-ingredient-interceptor (around ::retract-ingredient retract-ingredient-on-request retract-ingredient-on-response))

(def delete-ingredient
  [retract-ingredient-interceptor
   interceptors/transact-interceptor])

