(ns cheffy.recipes
  (:require
   [datomic.client.api :as d]
   [cheffy.interceptors :as interceptors]
   [cheshire.core :as json]))

(defn list-recipes-response [{:keys [system/database] :as request}]
  #_(prn "DEBUG:: [request] " [request])
  {:status 200
   :body "list recipes"})

(defn list-recipes-response [{:keys [system/database] :as request}]
  #_(prn "DEBUG:: [request] " [request])
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string
          (d/q '[:find ?e ?id
                 :where [?e :recipe/recipe-id ?id]]
               (:db database)))})

;; TODO juraj: is this really needed?
;; the interceptor seems to work just fine if I keep it in api-server
(def list-recipes [interceptors/db-interceptor list-recipes-response])

(defn upsert-recipe-response [request]
  {:status 200
   :body "upsert recipe"})

(defn update-recipe-response [request]
  {:status 200
   :body "update recipe"})

