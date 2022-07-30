(ns cheffy.recipes-with-interceptors
  "This is a different version of `cheffy.recipes` ns
  using interceptors heavily to abstract away common behavior like db queries/transactions.
  See also `cheffy.interceptors/transact-interceptor` and friends.

  Note on debugging: Unfortunately, vars cannot be passed in as interceptor functions
  because there's a validation inside Pedestal that prevents that.
  This really hampers debugging because functions' values are captured at the time the interceptors
  are defined and are thus hard to refresh (impossible with cider debugger?)"
  (:require
   [datomic.client.api :as d]
   [cheffy.interceptors :as interceptors]
   [cheshire.core :as json]
   [ring.util.response :as response]
   [io.pedestal.http :as http]
   [io.pedestal.interceptor :as interceptor]
   ;; Note: there's also `middleware` function but that works on request and response respectively
   ;; - it doesn't have access to the whole context
   [io.pedestal.interceptor.helpers :refer [around]]))

;; see shownotes for the episode: https://www.jacekschae.com/view/courses/learn-pedestal-pro/1366483-recipes/4269943-22-list-recipes
;; the output of our query will comply to this pattern
(def recipe-pattern [:recipe/recipe-id
                     :recipe/prep-time
                     :recipe/display-name
                     :recipe/image-url
                     :recipe/public?
                     ;; Notice the reverse lookup with underscore: https://docs.datomic.com/cloud/query/query-pull.html#reverse-lookup
                     :account/_favorite-recipes
                     {:recipe/owner
                      [:account/account-id
                       :account/display-name]}
                     {:recipe/steps
                      [:step/step-id
                       :step/description
                       :step/sort-order]}
                     {:recipe/ingredients
                      [:ingredient/ingredient-id
                       :ingredient/display-name
                       :ingredient/amount
                       :ingredient/measure
                       :ingredient/sort-order]}])

(defn- query-result->recipe [[q-result]]
  (-> q-result
      (assoc :favorite-count (count (:account/_favorite_recipes q-result)))
      ;; now dissoc the key that we do not want to expose
      (dissoc :account/_favorite-recipes)))

(defn- query-recipes [db account-id]
  (let [;; notice that `pull` is a required symbol/function;
        ;; instead is a special syntax recognized by datomic
        public-recipes (d/q '[:find (pull ?e pattern)
                              :in $ pattern
                              :where [?e :recipe/public? true]]

                            db recipe-pattern)]
    (cond-> {:public (mapv query-result->recipe public-recipes)}
      account-id (merge
                  {:drafts (mapv query-result->recipe
                                 (d/q '[:find (pull ?e pattern)
                                        :in $ ?account-id pattern
                                        :where
                                        [?owner :account/account-id ?account-id]
                                        [?e :recipe/owner ?owner]
                                        [?e :recipe/public? false]]
                                      db account-id recipe-pattern))}))))

(defn list-recipes-response [{:keys [system/database] :as request}]
  #_(prn "DEBUG:: [request] " [request])
  (let [db (:db database)
        ;; For now, a really stupid authentication mechanism will work - just pass account id in the Authorization header
        ;; - see response-for call in dev.clj
        account-id (get-in request [:headers "authorization"])
        recipes (query-recipes db account-id)]
    (-> recipes json/generate-string response/response)))

(def list-recipes [interceptors/db-interceptor
                   http/transit-body
                   list-recipes-response])


;;; CREATE & UPDATE

;; the common interceptor used by both create and update operations.
;; They differ only in recipe-id handling and the actual response 
(defn recipe-on-request
  [{:keys [request] :as ctx}]
  (let [account-id (get-in request [:headers "authorization"])
        ;; parse path param (for update) or assign random id (for create)
        recipe-id (or (some-> (get-in request [:path-params :recipe-id])
                              parse-uuid)
                      (random-uuid))
        {:keys [name public prep-time img]} (:transit-params request)]
    (assoc ctx
           :tx-data [{:recipe/recipe-id recipe-id
                      :recipe/display-name name ; shadowing core function
                      :recipe/public? public
                      :recipe/prep-time prep-time
                      :recipe/image-url img
                      ;; owner is actually a ref
                      :recipe/owner [:account/account-id account-id]}]
           :recipe-id (when-not (get-in request [:path-params :recipe-id])
                        ;; add only for create operation
                        recipe-id))))

(defn recipe-on-response
  [ctx]
  (assoc ctx :response (if-let [id (:recipe-id ctx)]
                         (response/created (str "/recipes/" id)
                                           {:recipe-id id})
                         {:status 200})))


(def recipe-interceptor (around ::recipe recipe-on-request recipe-on-response))

(def create-recipe
  [recipe-interceptor
   interceptors/transact-interceptor])

(def update-recipe
  [recipe-interceptor
   interceptors/transact-interceptor])


;;; GET
(defn find-recipe-on-request
  [ctx]
  (prn "DEBUG:: find-recipe-on-request " find-recipe-on-request)
  (let [recipe-id (parse-uuid (get-in ctx [:request :path-params :recipe-id]))]
    (assoc ctx :q-data {:query '[:find (pull ?e pattern)
                                 :in $ ?recipe-id pattern
                                 :where [?e :recipe/recipe-id ?recipe-id]]
                        ;; Notice that we do not add `db` as arg although it's required by datomic
                        ;; - it is supplied by `query-interceptor`
                        :args [recipe-id recipe-pattern]})))

(defn find-recipe-on-response
  [{:keys [q-result] :as ctx}]
  (prn "DEBUG:: find-recipe-on-response " q-result)
  (if (empty? q-result)
    (response/not-found (str "recipe not found: " (-> ctx :q-data :args first)))
    (response/response (query-result->recipe (first q-result)))))

(def find-recipe-interceptor (around ::find-recipe find-recipe-on-request find-recipe-on-response))

(def retrieve-recipe
  [find-recipe-interceptor
   interceptors/query-interceptor])


;;; DELETE
(defn retract-recipe-on-request
  [ctx]
  (let [recipe-id (parse-uuid (get-in ctx [:request :path-params :recipe-id]))]
    (assoc ctx :tx-data [[:db/retractEntity [:recipe/recipe-id recipe-id]]])
    recipe-id))

(defn retract-recipe-on-response
  [ctx]
  (assoc ctx :response {:status 204}))

(def retract-recipe-interceptor
  #_(around retract-recipe-on-request retract-recipe-on-response)
  ;; verbose form - this is what `around` expands to:
  (interceptor/interceptor
   {:name ::retract-recipe
    :enter retract-recipe-on-request
    :leave retract-recipe-on-response}))

(def delete-recipe
  [retract-recipe-interceptor
   ;; notice that I can add this one as the second interceptor because I want it to be applied
   ;; after I added :tx-data to the context in `retract-recipe-response`
   interceptors/transact-interceptor])

