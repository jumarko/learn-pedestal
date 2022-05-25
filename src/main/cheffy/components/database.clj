(ns cheffy.components.database
  "Uses datomic to provide persistent storage.
  For setup, see https://docs.datomic.com/cloud/dev-local.html"
  (:require [com.stuartsierra.component :as component]
            [datomic.client.api :as d]
            ;; this is closed-source
            [datomic.dev-local :as dl]))


(defn- start-db [db-config]
  (println "Starting database...")
  (let [db-name (select-keys db-config [:db-name])
        client (d/client (select-keys db-config [:server-type :storage-dir :system]))
        _ (d/create-database client db-name)
        conn (d/connect client db-name)]
    conn))

(defn- stop-db [db-config]
  (println "Stopping database...")
  (dl/release-db (select-keys db-config [:system :db-name :meme])))

(defrecord Database [config conn]
  component/Lifecycle
  (start [this]
    (assoc this :conn (start-db config)))
  (stop [this]
    (stop-db config)
    (assoc this :conn nil)))

(defn make-db [db-config]
  (map->Database {:config db-config}))

