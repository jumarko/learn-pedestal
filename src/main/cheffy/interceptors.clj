(ns cheffy.interceptors
  (:require [datomic.client.api :as d]
            [io.pedestal.interceptor :as interceptor]))

(defn- inject-db [ctx]
  ;; grab the connection injected by the system interceptor (see `cheffy.components.api-server/cheffy-interceptors`),
  ;; create the db and add it to the context
  (let [conn (get-in ctx [:request :system/database :conn])
        db (d/db conn)]
    (println "Adding current db value to the request context")
    (assoc-in ctx [:request :system/database :db] db)))

(def db-interceptor
  (interceptor/interceptor
   {:name ::db-interceptor
    :enter inject-db}))
