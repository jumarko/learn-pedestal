(ns cheffy.components.api-server
  (:require
   [cheffy.routes :as routes]
   [cheffy.interceptors :as interceptors]
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.interceptor :as interceptor]))

(defn dev? [{:keys [env] :as _service-map}]
  (= :dev env))

;; let's define an interceptor that injects our system into the context
(defn inject-system [system]
  (interceptor/interceptor
   {:name ::inject-system
    :enter (fn [ctx]
             ;; let's just add our whole system to the :request key
             (update ctx :request merge system))}))

(defn cheffy-interceptors
  "Injects interceptors into the service properly so they are put
  just before the last one in the default set of interceptors.
  The last one should always be the router.
  Adds `http/dev-interceptors` if we are running in the dev env."
  [service-map interceptors]
  (let [default-interceptors (-> service-map
                                 http/default-interceptors
                                 ::http/interceptors)
        all-interceptors (vec
                          (concat
                           (butlast default-interceptors)
                           interceptors
                           ;; the last interceptor should be `:io.pedestal.http.route/router`
                           [(last default-interceptors)]))]
    (cond-> (assoc service-map  ::http/interceptors all-interceptors)
        (dev? service-map) (http/dev-interceptors))))

(defn- start-server [service-map database]
  (println "Starting API server...")
  (-> service-map
      (assoc ::http/routes routes/table-routes)
      ;; here we add our interceptor to the interceptor chain to include the db component
      (cheffy-interceptors [(inject-system {:system/database database})
                            interceptors/db-interceptor])
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
    (assoc this :service (start-server service-map database)))
  (stop [this] (when service (stop-server service))
    ;; why don't do `(dissoc this :service)`?
    ;; => you would end up with a plain map, not the ApiServer record anymore!
    (assoc this :service nil)))


(defn make-api-server [service-map]
  (map->ApiServer {:service-map service-map}))
