(ns cheffy.routes
  (:require
   [cheffy.recipes :as recipes]
   [io.pedestal.http.route :as route]
   [io.pedestal.http :as http]))

;; See http://pedestal.io/reference/defining-routes
(def table-routes
  (route/expand-routes
   ;; IMPORTANT: make sure that `:host` matches your hostname, most likely 'localhost'
   ;; - otherwise you will get NOT FOUND in the browser when you try something like
   ;;      http://localhost:3001/recipes
   #{{:app-name :cheffy ::http/scheme :http ::http/host "localhost"}
     ;; now define the routes themselves
     ["/recipes" :get #'recipes/list-recipes-response :route-name :list-recipes]
     ["/recipes" :post #'recipes/upsert-recipe-response :route-name :create-recipe]
     ["/recipes/:recipe-id" :put #'recipes/update-recipe-response :route-name :update-recipe]}))
;; inspect `table-routes` again and notice `:path-params`:
;;     :path-params [:recipe-id]

;; just as an example, here we show the 'terse' syntax - less verbose than in `table-routes`
(comment
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

  .)
