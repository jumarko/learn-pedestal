(ns user
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]))

(println "Loading user.clj")

(defonce ^:private system-ref (atom nil))

(defn list-recipes [request]
  {:status 200
   :body "list recipes"})

(def table-routes
  (route/expand-routes
   #{{:app-name :cheffy :schema :http :host "learnpedestal.com"}
     ;; now define the routes themselves
     ["/recipes" :get list-recipes :route-name :list-recipes]}))
;; if you inspect `table-routes` you will see this
;; ({:path "/recipes",
;;   :method :get,
;;   :app-name :cheffy,
;;   :path-re #"/\Qrecipes\E",
;;   :path-parts ["recipes"],
;;   :host "learnpedestal.com",
;;   :interceptors
;;   [{:name nil,
;;     :enter #function[io.pedestal.interceptor/eval288/fn--289/fn--290],
;;     :leave nil,
;;     :error nil}],
;;   :route-name :list-recipes,
;;   :path-params []})

;; add some more routes
(defn upsert-recipe [request]
  {:status 200
   :body "upsert recipe"})

(defn update-recipe [request]
  {:status 200
   :body "update recipe"})

;; See http://pedestal.io/reference/defining-routes
(def table-routes
  (route/expand-routes
   #{{:app-name :cheffy :schema :http :host "learnpedestal.com"}
     ;; now define the routes themselves
     ["/recipes" :get list-recipes :route-name :list-recipes]
     ["/recipes" :post upsert-recipe :route-name :create-recipe]
     ["/recipes/:recipe-id" :put update-recipe :route-name :update-recipe]}))
;; inspect `table-routes` again and notice `:path-params`:
;;     :path-params [:recipe-id]

;; now try the 'terse' syntax - less verbose than in `table-routes`

(def terse-routes
  (route/expand-routes
   [[:cheffy :http "learnpedestal.com"]
    ;; notice how handlers must be fully-qualified symbols
    ["/recipes" {:get `list-recipes
                 :post `upsert-recipe}
     ;; Note: subpath specified via the nested vector
     ;; here we need to specify a custom route name `:update-recipe` to avoid conflicts (upsert-twice is used twice)??
     ;; (it didn't fail for me)
     ["/:recipe-id" {:put `upsert-recipe}]]]))

(defn start-server []
  (->> {::http/routes table-routes
        ::http/router :map-tree ; :map-tree router is the default one (if you omit `:http/router`)
        ::http/type :jetty
        ;; use 3001 instead of 3000 to avoid conflicts with Backstage and other apps commonly using 3000
        ::http/port 3001
        ::http/join? false}
       http/create-server
       http/start
       (#(do (println "server started.") %))
       (reset! system-ref)))

(defn stop-server []
  (http/stop @system-ref))

(defn restart-server []
  (stop-server)
  (start-server))

(comment
  (restart-server)

  (start-server)

  (stop-server)

  ;; try some routes manually - see http://pedestal.io/guides/hello-world#_routes
  (route/try-routing-for table-routes :prefix-tree "/recipes" :get)
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
