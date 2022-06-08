(ns cheffy.interceptors
  (:require [datomic.client.api :as d]
            [io.pedestal.interceptor :as interceptor]))

(def db-interceptor
  (interceptor/interceptor
   {:name ::db-interceptor
    :enter (fn [ctx]
             ;; grab the connection injected by the system interceptor (see `cheffy.components.api-server/cheffy-interceptors`),
             ;; create the db and add it to the context
             (let [conn (get-in ctx [:request :system/database :conn])
                   db (d/db conn)]
               (assoc-in ctx [:request :system/database :db] db)))}))
