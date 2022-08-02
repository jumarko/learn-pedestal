(ns cheffy.components.database
  "Uses datomic to provide persistent storage.
  For setup, see https://docs.datomic.com/cloud/dev-local.html"
  (:require
   [com.stuartsierra.component :as component]

   ;; only for dev-local / client-library
   #_[datomic.client.api :as d]
   #_[datomic.dev-local :as dl] ; (closed source)
   [datomic.api :as d]

   [clojure.java.io :as io]
   [clojure.edn :as edn]))


(defn ident-has-attr?
  "Checks whether there's a db entity with with given id and attribute."
  [db ident attr]
  (contains?
   ;; only for dev-local / client library
   #_(d/pull db {:eid ident :selector '[*]})
   ;; only for peer library - entity id and patter are separate args
   (d/pull db '[*] ident)
   attr))

(defn- load-data [resource-path]
  (-> (io/resource resource-path) slurp edn/read-string))

(defn load-dataset [conn]
  (let [db (d/db conn)
        ;; only for dev-local / datomic client library
        #_#_tx #(d/transact conn {:tx-data %})
        ;; only for Peer library - accepts tx-data directly and returns a future
        tx #(->> % (d/transact conn) deref)
        ]
    (when-not (ident-has-attr? db :account/account-id :db/ident)
      (tx (load-data "schema.edn"))
      (tx (load-data "seed.edn")))))

(defn- start-db [db-config]
  (println "Starting database...")
  (let [db-name-map (select-keys db-config [:db-name])

        ;; client-config for dev-local
        #_#_client-config (select-keys db-config [:server-type :storage-dir :system])

        ;; client-config for peer server: https://docs.datomic.com/on-prem/client/client-getting-started.html#connect-to-a-database
        client-config (select-keys db-config [:server-type :access-key :secret :endpoint :validate-hostnames])

        ;; create the client and connect - only for dev-local and peer server
        #_#_client (d/client client-config)

        db-uri (:db-uri db-config)

        ;; create-database only applies to Peer library (https://docs.datomic.com/on-prem/getting-started/connect-to-a-database.html#create-db)
        ;; and dev-local;
        ;; but not when using the Client library with Peer Server (https://docs.datomic.com/on-prem/client/client-getting-started.html#connect-to-a-database)
        ;; only for dev-local: create the db using the client
        #_#__ (d/create-database client db-name-map)
        ;; only for Peer library: create database using db-uri
        _ (d/create-database db-uri)

        ;; use client to get the connection: only for dev-local and peer-server
        #_#_conn (d/connect client db-name-map)
        ;; use db-uri to connect (when in Peer mode: https://docs.datomic.com/on-prem/getting-started/connect-to-a-database.html)
        conn (d/connect db-uri)
        ]
    (load-dataset conn)
    conn))

(defn- stop-db [db-config]
  (println "Stopping database...")
  ;; only for dev-local
  #_(dl/release-db (select-keys db-config [:system :db-name :meme]))
  ;; only peer library: https://docs.datomic.com/on-prem/clojure/index.html#datomic.api/release
  ;;   Note that Datomic connections do not adhere to an acquire/use/release 
  ;;   pattern.  They are thread-safe, cached, and long lived.  Many 
  ;;   processes (e.g. application servers) will never call release.
  #_(d/release (:db-uri db-config)) ; no need to call this - see the comment

  )

(defrecord Database [config conn]
  component/Lifecycle
  (start [this]
    (assoc this :conn (start-db config)))
  (stop [this]
    (stop-db config)
    (assoc this :conn nil)))

(defn make-db [db-config]
  (map->Database {:config db-config}))


