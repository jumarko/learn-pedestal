(ns cheffy.interceptors
  (:require [datomic.client.api :as d]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.log :as log]))

(def conn-path [:request :system/database :conn])
(def db-path [:request :system/database :db])

(defn connection [ctx]
  (get-in ctx conn-path))

(defn database [ctx]
  (get-in ctx db-path))

(defn- inject-db [ctx]
  ;; grab the connection injected by the system interceptor (see `cheffy.components.api-server/cheffy-interceptors`),
  ;; create the db and add it to the context
  (let [db (d/db (connection ctx))]
    (println "Adding current db value to the request context")
    (assoc-in ctx db-path db)))

(def db-interceptor
  (interceptor/interceptor
   {:name ::db-interceptor
    :enter inject-db}))

(defn- transact!
  [{:keys [tx-data] :as ctx}]
  (log/debug :transact tx-data)
  #_(def my-ctx ctx)
  (when-let [result (some->> tx-data (d/transact (connection ctx)))] 
    (log/trace :tx-result result)
    (assoc ctx :tx-result result)))

(defn- query!
  [{:keys [q-data] :as ctx}]
  (log/debug :q-data q-data :database (database ctx))
  (when-let [result (and q-data (d/q (doto (update q-data :args #(into [(database ctx)] %))
                                       prn)))]
    ;; add the database as the first of `:args`
    (log/trace :q-result result)
    (assoc ctx :q-result result)))

(def transact-interceptor
  (interceptor/interceptor
   {:name ::transact-interceptor
    :enter transact!}))

(def query-interceptor
  (interceptor/interceptor
   {:name ::query-interceptor
    :enter query!}))

(comment
  ;; get recipe by id
  (let [db (d/db (get-in com.stuartsierra.component.repl/system [:database :conn]))]
    (-> {:q-data
         {:query '[:find (pull ?e pattern)
                   :in $ ?recipe-id pattern
                   :where [?e :recipe/recipe-id ?recipe-id]],
          :args [#uuid "a3dde84c-4a33-45aa-b0f3-4bf9ac997680" ; this is from seed.edn
                 [:recipe/recipe-id :recipe/prep-time :recipe/display-name :recipe/image-url :recipe/public? :account/_favorite-recipes #:recipe{:owner [:account/account-id :account/display-name]} #:recipe{:steps [:step/step-id :step/description :step/sort-order]} #:recipe{:ingredients [:ingredient/ingredient-id :ingredient/display-name :ingredient/amount :ingredient/measure :ingredient/sort-order]}]]}}
        (assoc-in db-path db)
        query!
        :q-result
        ffirst)) ; notice we need to use ffirst - why there are two vectors??
;; => {:recipe/recipe-id #uuid "a3dde84c-4a33-45aa-b0f3-4bf9ac997680", :recipe/prep-time 45, :recipe/display-name "Splitony's Pizza", :recipe/image-url "https://res.cloudinary.com/schae/image/upload/f_auto,h_400,q_80/v1548183465/cheffy/recipe/pizza.jpg", :recipe/public? false, :recipe/owner #:account{:account-id "auth|5fbf7db6271d5e0076903601", :display-name "Auth"}, :account/_favorite-recipes [#:db{:id 79164837199976} #:db{:id 79164837199977} #:db{:id 79164837199978}]}


  .)
