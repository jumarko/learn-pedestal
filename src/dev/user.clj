(ns user
  (:require
   [clojure.edn :as end]
   [io.pedestal.http :as http]
   [io.pedestal.http.route :as route]
   [cheffy.routes :as routes]))

(println "Loading user.clj")

(defonce ^:private system-ref (atom nil))

(defn start-server []
  (let [config (-> (slurp "src/config/development.edn") end/read-string)]
    (->> (merge config
                {::http/routes routes/table-routes
                 ::http/router :map-tree ; :map-tree router is the default one (if you omit `:http/router`)
                 })
         http/create-server
         http/start
         (#(do (println "server started.") %))
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

  ;; let's examine the routes:
  (second (::http/routes @system-ref))
  ;; => {:path "/recipes",
  ;;     :method :get,
  ;;     :app-name :cheffy,
  ;;     :path-re #"/\Qrecipes\E",
  ;;     :path-parts ["recipes"],
  ;;     :host "localhost",
  ;;     :interceptors
  ;;     [{:name nil,
  ;;       :enter #function[io.pedestal.interceptor/eval288/fn--289/fn--290],
  ;;       :leave nil,
  ;;       :error nil}],
  ;;     :route-name :list-recipes,
  ;;     :path-params []}

  ;; try some routes manually - see http://pedestal.io/guides/hello-world#_routes
  (route/try-routing-for routes/table-routes :map-tree "/recipes" :get)
  ;; 1. Unhandled java.lang.NullPointerException
  ;; Cannot invoke "clojure.lang.IFn.invoke(Object, Object)"

  ;; prefix_tree.clj:  324  io.pedestal.http.route.prefix-tree.PrefixTreeRouter/find_route
  ;; route.clj:  441  io.pedestal.http.route/route-context
  ;; route.clj:  440  io.pedestal.http.route/route-context
  ;; route.clj:  459  io.pedestal.http.route/eval11674/fn/fn
  ;; route.clj:  578  io.pedestal.http.route/try-routing-for

  ;; for the above, we would need to use `io.pedestal.http.route.prefix-tree/router`.
  ;; TODO: let's explore this later

  .)
