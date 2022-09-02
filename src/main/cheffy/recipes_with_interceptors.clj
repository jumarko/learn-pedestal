(ns cheffy.recipes-with-interceptors
  "This is a different version of `cheffy.recipes` ns
  using interceptors heavily to abstract away common behavior like db queries/transactions.
  See also `cheffy.interceptors/transact-interceptor` and friends.

  Note on debugging: Unfortunately, vars cannot be passed in as interceptor functions
  because there's a validation inside Pedestal that prevents that.
  This really hampers debugging because functions' values are captured at the time the interceptors
  are defined and are thus hard to refresh (impossible with cider debugger?)"
  (:require
   [cheffy.crud :as crud]
   [clojure.set :as set]))

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

(defn- list-recipes-query [request]
  (let [account-id (get-in request [:headers "authorization"])
        base-query '[:find (pull ?e pattern)
                     :in $ pattern]]
    ;; Or clauses: https://docs.datomic.com/on-prem/query/query.html#or-clauses
    ;; `or-join` is needed because the second clause (`and`) uses a different set of variables (`?owner`)
    ;; Note: The fact that ?account-id can be nil complicates this.
    ;; When it's nil, we have to leave out the entire owner filtering clause
    {:query (if account-id
              (conj base-query
                    '?account-id
                    :where '[or-join [?e]
                             [?e :recipe/public? true]
                             (and [?e :recipe/public? false]
                                  [?owner :account/account-id ?account-id]
                                  [?e :recipe/owner ?owner])])
              (conj base-query :where '[?e :recipe/public? true]))
     :args (cond-> [recipe-pattern]
             account-id (conj account-id))}))

(defn- query-result->recipe [[q-result]]
  (-> q-result
      (assoc :favorite-count (count (:account/_favorite_recipes q-result)))
      ;; now dissoc the key that we do not want to expose
      (dissoc :account/_favorite-recipes)))

(defn- list-recipes-response [q-result]
  (let [public-private-recipes (->> q-result
                                    (mapv query-result->recipe)
                                    (group-by :recipe/public?))]
    (set/rename-keys public-private-recipes {true :public false :drafts})))

(def list-recipes (crud/list list-recipes-query
                             list-recipes-response))

(def id-key :recipe/recipe-id)

(defn- params->entity
  [account-id entity-id {:keys [name public prep-time img] :as _params}]
  [{:recipe/recipe-id entity-id
    :recipe/display-name name ; shadowing core function
    :recipe/public? public
    :recipe/prep-time prep-time
    :recipe/image-url img
    ;; owner is actually a ref
    :recipe/owner [:account/account-id account-id]}])

(def upsert-recipe (crud/upsert id-key params->entity))

(def retrieve-recipe (crud/retrieve id-key))

(def delete-recipe (crud/delete id-key))
