(ns cheffy.components.api-server
  (:require
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [cheffy.routes :as routes]))

(defn- start-server [service-map]
  (println "Starting API server...")
  (-> service-map
      (assoc ::http/routes routes/table-routes)
      http/create-server
      http/start))

(defn- stop-server [service]
  (println "Stopping API server...")
  (when service
    (http/stop service)))

;; we need to implement the `component/Lifecycle` protocol

;; ... we could try to extend it via metadata: https://clojure.org/reference/protocols#_extend_via_metadata
(comment
  (defn make-api-server [service-map]
    (with-meta service-map {`start (constantly "Started")}))
  .)

;; ... but let's try with `defrecord` for now:
;; service-map is our "parameters"
;; service is our "runtime"
;; database is our "dependencies"
(defrecord ApiServer [service-map service database]
  component/Lifecycle
  (start [this]
    (assoc this :service (start-server service-map)))
  (stop [this]
    (when service (stop-server service))
    ;; why don't do `(dissoc this :service)`?
    ;; => you would end up with a plain map, not the ApiServer record anymore!
    (assoc this :service nil)))


(defn make-api-server [service-map]
  (map->ApiServer {:service-map service-map}))
