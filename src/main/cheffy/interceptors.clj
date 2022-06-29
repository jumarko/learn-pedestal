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
  (when-let [result (and q-data (d/q (update q-data :args #(into [(database ctx)] %))))]
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

  ;; DOesn't work for some reason
  ;;1. Unhandled clojure.lang.ExceptionInfo
  ;; :db.error/invalid-data-source Nil or missing data source. Did you forget to pass a database
  ;; argument?
  ;; {:cognitect.anomalies/category :cognitect.anomalies/incorrect,
  ;;  :cognitect.anomalies/message
  ;;  "Nil or missing data source. Did you forget to pass a database argument?",
  ;;  :input nil,
  ;;  :db/error :db.error/invalid-data-source}
  #_(let [db (d/db (get-in com.stuartsierra.component.repl/system [:database :conn]))]
    (query! {:q-data {:query '[:find (pull ?e pattern)
                               :in $ ?recipe-id pattern
                               :where [?e :recipe/recipe-id ?recipe-id]],
                      :args [db

                             #uuid "df3fc518-ae36-4fb0-a63a-58636bd99a26"
                             [:recipe/recipe-id :recipe/prep-time :recipe/display-name :recipe/image-url :recipe/public? :account/_favorite-recipes #:recipe{:owner [:account/account-id :account/display-name]} #:recipe{:steps [:step/step-id :step/description :step/sort-order]} #:recipe{:ingredients [:ingredient/ingredient-id :ingredient/display-name :ingredient/amount :ingredient/measure :ingredient/sort-order]}]]}}))

  .)
