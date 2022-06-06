(ns dev
  (:require
   [clojure.edn :as end]
   [cheffy.server :as server]
   [com.stuartsierra.component.repl :as cr]
   [io.pedestal.http :as http]))

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

  ;; let's look at :api-server
  (:api-server cr/system)

  (let [service-map (-> cr/system :api-server :service)
        default-interceptors (-> service-map
                                 http/default-interceptors
                                 ::http/interceptors)]
    (concat
     (butlast default-interceptors)
     #_[my-interceptor]
     [(last default-interceptors)]))

  .)
