(ns cheffy.components.database
  "Uses datomic to provide persistent storage.
  For setup, see https://docs.datomic.com/cloud/dev-local.html"
  (:require [com.stuartsierra.component :as component]
            [datomic.client.api :as d]
            ;; this is closed-source
            [datomic.dev-local :as dl]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))


(defn ident-has-attr?
  "Checks whether there's a db entity with with given id and attribute."
  [db ident attr]
  (contains? (d/pull db {:eid ident :selector '[*]})
             attr))

(defn- load-data [resource-path]
  (-> (io/resource resource-path) slurp edn/read-string))

(defn load-dataset [conn]
  (let [db (d/db conn)
        tx #(d/transact conn {:tx-data %})]
    (when-not (ident-has-attr? db :account/account-id :db/ident)
      (tx (load-data "schema.edn"))
      (tx (load-data "seed.edn")))))


(defn- start-db [db-config]
  (println "Starting database...")
  (let [db-name (select-keys db-config [:db-name])
        client (d/client (select-keys db-config [:server-type :storage-dir :system]))
        _ (d/create-database client db-name)
        conn (d/connect client db-name)]
    (load-dataset conn)
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


