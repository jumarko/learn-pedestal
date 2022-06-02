(ns dev
  (:require
   [clojure.edn :as end]
   [cheffy.server :as server]
   [com.stuartsierra.component.repl :as cr]))

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

  .)
