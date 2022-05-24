(ns cheffy.routes
  (:require [io.pedestal.http.route :as route]))

;; add some more routes
(defn list-recipes [request]
  {:status 200
   :body "list recipes"})

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



;; just as an example, here we show the 'terse' syntax - less verbose than in `table-routes`
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

