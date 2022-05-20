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

(def table-routes
  (route/expand-routes
   #{{:app-name :cheffy :schema :http :host "learnpedestal.com"}
     ;; now define the routes themselves
     ["/recipes" :get list-recipes :route-name :list-recipes]
     ["/recipes" :post upsert-recipe :route-name :create-recipe]
     ["/recipes/:recipe-id" :put update-recipe :route-name :update-recipe]
     }))
;; inspect `table-routes` again and notice `:path-params`:
;;     :path-params [:recipe-id]


(defn start-server []
  (->> {::http/routes #{}
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

  .)
