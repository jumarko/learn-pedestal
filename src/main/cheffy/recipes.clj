(ns cheffy.recipes
  (:require
   [datomic.client.api :as d]
   [cheffy.interceptors :as interceptors]
   [cheshire.core :as json]
   [ring.util.response :as response]
   [io.pedestal.http :as http]))

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

(defn- create-recipe! [conn
                      account-id
                      {:keys [name public prep-time img] :as _params}]
  ;; TODO: change UUID later to something better indexed by Datomic
  (let [recipe-id (random-uuid)]
    (d/transact conn {:tx-data [{:recipe/recipe-id recipe-id
                                 :recipe/display-name name ; shadowing core function
                                 :recipe/public? public
                                 :recipe/prep-time prep-time
                                 :recipe/image-url img
                                 ;; owner is actually a ref
                                 :recipe/owner [:account/account-id account-id]}]})
    recipe-id))


(defn create-recipe-response
  ;; TODO: why do we use `transit-params` and not just `params`
  ;; - this makes the code transport specific
  [{:keys [headers transit-params system/database]}]
  (let [account-id (get headers "authorization")
        recipe-id (create-recipe! (:conn database) account-id transit-params)]
    (response/created (str "/recipes/" recipe-id))))

(defn- retrieve-recipe [db recipe-id]
  (let [recipe (d/q '[:find (pull ?e pattern)
                              :in $ ?recipe-id pattern
                              :where [?e :recipe/recipe-id ?recipe-id]]
                            db recipe-id recipe-pattern)]
    recipe))

(defn retrieve-recipe-response
  ;; Notice `path-params`
  [{:keys [path-params system/database] :as _request}]
  (let [db (:db database)
        recipe-id (parse-uuid (:recipe-id path-params))]
    (response/response (retrieve-recipe db recipe-id))))

;; TODO juraj: NOT USED - is this really needed?
;; the interceptor seems to work just fine if I keep it in api-server
;; similar for `create-recipe`
(def list-recipes [interceptors/db-interceptor
                   http/transit-body
                   #'list-recipes-response])

(defn- update-recipe! [conn
                      account-id
                      recipe-id
                      {:keys [name public prep-time img] :as _params}]
  (d/transact conn {:tx-data [{:recipe/recipe-id recipe-id
                               :recipe/display-name name ; shadowing core function
                               :recipe/public? public
                               :recipe/prep-time prep-time
                               :recipe/image-url img
                               ;; owner is actually a ref
                               :recipe/owner [:account/account-id account-id]}]}))

(defn update-recipe-response
  [{:keys [headers path-params transit-params system/database] :as _request}]
  (let [db (:conn database)
        account-id (get headers "authorization")
        recipe-id (parse-uuid (:recipe-id path-params))]
    (update-recipe! db account-id recipe-id transit-params)
    {:status 204}))

(defn delete-recipe! [conn recipe-id]
  (d/transact conn {:tx-data [[:db/retractEntity [:recipe/recipe-id recipe-id]]]}))

(defn delete-recipe-response
  [{:keys [path-params system/database] :as _request}]
  (let [db (:conn database)
        ;; notice we don't do any authorization
        recipe-id (parse-uuid (:recipe-id path-params))]
    (delete-recipe! db recipe-id)
    {:status 204}))

;; TODO: refactor update-recipe and create-recipe to use common code
(defn upsert-recipe-response [request]
  {:status 200
   :body "upsert recipe"})


