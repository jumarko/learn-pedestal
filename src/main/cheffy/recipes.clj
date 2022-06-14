(ns cheffy.recipes
  (:require
   [datomic.client.api :as d]
   [cheffy.interceptors :as interceptors]
   [cheshire.core :as json]
   [ring.util.response :refer [response]]))

(defn list-recipes-response [{:keys [system/database] :as request}]
  #_(prn "DEBUG:: [request] " [request])

  (let [db (:db database)
        ;; For now, a really stupid authentication mechanism will work - just pass account id in the Authorization header
        ;; - see response-for call in dev.clj
        account-id (get-in request [:headers "authorization"])
        ;; see shownotes for the episode: https://www.jacekschae.com/view/courses/learn-pedestal-pro/1366483-recipes/4269943-22-list-recipes
        ;; the output of our query will comply to this pattern
        recipe-pattern [:recipe/recipe-id
                        :recipe/prep-time
                        :recipe/display-name
                        :recipe/image-url
                        :recipe/public?
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
                          :ingredient/sort-order]}]
        ;; notice that `pull` is a required symbol/function;
        ;; instead is a special syntax recognized by datomic
        public-recipes (d/q '[:find (pull ?e pattern)
                              :in $ pattern
                              :where [?e :recipe/public? true]]
                            db recipe-pattern)]
    (cond-> {:public public-recipes}
      account-id (merge
                  {:drafts (d/q '[:find (pull ?e pattern)
                                  :in $ ?account-id pattern
                                  :where
                                  [?owner :account/account-id ?account-id]
                                  [?e :recipe/owner ?owner]
                                  [?e :recipe/public? false]]
                                db account-id recipe-pattern)})
      true (-> json/generate-string response))))


;; TODO juraj: is this really needed?
;; the interceptor seems to work just fine if I keep it in api-server
(def list-recipes [interceptors/db-interceptor #'list-recipes-response])

(defn upsert-recipe-response [request]
  {:status 200
   :body "upsert recipe"})

(defn update-recipe-response [request]
  {:status 200
   :body "update recipe"})

