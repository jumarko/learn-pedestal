(ns cheffy.components.api-server
  (:require
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [cheffy.routes :as routes]))

(defn- start-server [service-map]
  (-> service-map
      (assoc ::http/routes routes/table-routes)
      http/create-server
      http/start))

(defn- stop-server [service]
  (when service
    (http/stop service)))

;; we need to implement the `component/Lifecycle` protocol
;; - let's try with `defrecord`
(defrecord ApiServer [service-map service]
  component/Lifecycle
  (start [this]
    (println "Starting API server...")
    ;; create the service
    (assoc this :service (start-server service-map)))
  (stop [this]
    (println "Stopping API server...")
    (when service
      (stop-server service))
    ;; why don't do `(dissoc this :service)`?
    ;; => you would end up with a plain map, not the ApiServer record anymore!
    (assoc this :service nil)))

(defn make-api-server [service-map]
  (map->ApiServer {:service-map service-map}))

;; ... now try to extend via metadata: https://clojure.org/reference/protocols#_extend_via_metadata
(defn make-api-server [service-map]
  (map->ApiServer {:service-map service-map})
  (with-meta service-map {`start (constantly "Started")}))
