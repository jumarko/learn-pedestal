(ns user
  (:require
   [clojure.edn :as end]
   [io.pedestal.http :as http]
   [io.pedestal.http.route :as route]
   [cheffy.routes :as routes]
   [cheffy.server :as server]
   [com.stuartsierra.component :as component]))

(println "Loading user.clj")

(defonce ^:private system-ref (atom nil))

(defn start-server []
  (let [config (-> (slurp "src/config/development.edn") end/read-string)]
    (->> config
        server/create-system
        component/start
        (reset! system-ref))))

(defn stop-server []
  (some-> @system-ref http/stop))

(defn restart-server []
  (stop-server)
  (start-server))

(comment
  (restart-server)

  (start-server)

  (stop-server)

  (keys (:api-server @system-ref))
  ;; => (:service-map :service)

  ;; let's examine the routes:
  (-> @system-ref :api-server :service ::http/routes first)
  ;; => {:path "/recipes/:recipe-id",
  ;;     :method :put,
  ;;     :path-constraints {:recipe-id "([^/]+)"},
  ;;     :app-name :cheffy,
  ;;     :path-re #"/\Qrecipes\E/([^/]+)",
  ;;     :path-parts ["recipes" :recipe-id],
  ;;     :host "localhost",
  ;;     :interceptors
  ;;     [{:name nil,
  ;;       :enter #function[io.pedestal.interceptor/eval288/fn--289/fn--290],
  ;;       :leave nil,
  ;;       :error nil}],
  ;;     :route-name :update-recipe,
  ;;     :path-params [:recipe-id]}


  ;; try some routes manually - see http://pedestal.io/guides/hello-world#_routes
  (route/try-routing-for routes/table-routes :map-tree "/recipes" :get)
  ;; 1. Unhandled java.lang.NullPointerException
  ;; Cannot invoke "clojure.lang.IFn.invoke(Object, Object)"
  ;;; ???

  .)
