(ns dev
  (:require
   [clojure.edn :as end]
   [cheffy.server :as server]
   [com.stuartsierra.component.repl :as cr]
   [io.pedestal.http :as http]
   [datomic.client.api :as d]))

(defn system [_old-system]
  (-> (slurp "src/config/development.edn")
      end/read-string
      server/create-system))

(cr/set-init system)

(defn start-dev []
  (cr/start))

(defn stop-dev []
  (cr/stop))

(defn restart-dev []
  (cr/reset))

(comment
  (restart-dev)

  (start-dev)

  (stop-dev)

  ;; we can examine they system via `cr/system`
  (keys cr/system)
;; => (:config :api-server :database)

  .)

;;; let's look at :api-server
(comment
  (:api-server cr/system)

  (let [service-map (-> cr/system :api-server :service)
        default-interceptors (-> service-map
                                 http/default-interceptors
                                 ::http/interceptors)]
    (concat
     (butlast default-interceptors)
     #_[my-interceptor]
     [(last default-interceptors)]))
  )

;;; check the database and create the schema and load data
;;; but only if it's not already there
(comment
  (restart-dev)

  ;; inspect the db quickly
  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    db)

  ;; check if accounts exist already
  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    (d/pull db {:eid :account/account-id :selector '[*]}))
  ;; => #:db{:id nil}

  ;; ... go to database.clj and add `load-dataset` function
  (restart-dev)
  ;; ... now inspect the db again

  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    (d/q '[:find ?e ?id
           :where [?e :account/account-id ?id]]
         db))
;; => [[79164837199977 "mike@mailinator.com"] [79164837199978 "jade@mailinator.com"] [79164837199976 "auth|5fbf7db6271d5e0076903601"] [79164837199979 "mark@mailinator.com"]]

  .)



