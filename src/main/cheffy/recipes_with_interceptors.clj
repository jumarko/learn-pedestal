(ns cheffy.recipes-with-interceptors
  "This is a different version of `cheffy.recipes` ns
  using interceptors heavily to abstract away common behavior like db queries/transactions.
  See also `cheffy.interceptors/transact-interceptor` and friends.

  Note on debugging: Unfortunately, vars cannot be passed in as interceptor functions
  because there's a validation inside Pedestal that prevents that.
  This really hampers debugging because functions' values are captured at the time the interceptors
  are defined and are thus hard to refresh (impossible with cider debugger?)"
  (:require
   ;; only for dev-local / client-library
   #_[datomic.client.api :as d]
   [datomic.api :as d]
   [cheffy.crud :as crud]
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

(def list-recipes [interceptors/db-interceptor
                   http/transit-body
                   list-recipes-response])

(def id-key :recipe/recipe-id)

(defn- params->entity
  [account-id entity-id {:keys [name public prep-time img] :as _params}]
  {:recipe/recipe-id entity-id
   :recipe/display-name name ; shadowing core function
   :recipe/public? public
   :recipe/prep-time prep-time
   :recipe/image-url img
   ;; owner is actually a ref
   :recipe/owner [:account/account-id account-id]})

(def upsert-recipe (crud/upsert id-key params->entity))

(def retrieve-recipe (crud/retrieve id-key))

(def delete-recipe (crud/delete id-key))
