(ns dev
  (:require
   [clojure.edn :as end]
   [cheffy.server :as server]
   [com.stuartsierra.component.repl :as cr]
   [datomic.client.api :as d]
   [io.pedestal.http :as http]
   [io.pedestal.test :as pt]))

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

  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    
    (d/transact conn {:tx-data [{:db/id "rec_123456789"
                                 :recipe/recipe-id (random-uuid)
                                 :recipe/display-name "Dummy recipe for quick testing"
                                 }]}))

  .)


;;; inspect interceptor chain for specific route via `io.pedestal.test/response-for`
(comment
  (restart-dev)

  ;; check the docstring
  pt/response-for

  ;; find the service-fn in the system
  (-> cr/system :api-server :service ::http/service-fn)
  ;; => #function[io.pedestal.http.impl.servlet-interceptor/interceptor-service-fn/fn--24686]

  ;; use this to debug problems with the service
  #_(->> (pt/response-for
        (-> cr/system :api-server :service ::http/service-fn)
        :get
        "/recipes")
       :body
       (spit "debug.txt"))

  (->> (pt/response-for
        (-> cr/system :api-server :service ::http/service-fn)
        :get
        "/recipes"))
  ;; => {:status 200,
  ;;     :body "list recipes",
  ;;     :headers
  ;;     {"Strict-Transport-Security" "max-age=31536000; includeSubdomains",
  ;;      "X-Frame-Options" "DENY",
  ;;      "X-Content-Type-Options" "nosniff",
  ;;      "X-XSS-Protection" "1; mode=block",
  ;;      "X-Download-Options" "noopen",
  ;;      "X-Permitted-Cross-Domain-Policies" "none",
  ;;      "Content-Security-Policy"
  ;;      "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;",
  ;;      "Content-Type" "text/plain"}}

  .)

