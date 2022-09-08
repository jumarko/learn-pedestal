(ns cheffy.components.auth
  "Authentication component using AWS Cognito."
  (:require
   [cheffy.cognito :as cognito]
   [com.stuartsierra.component :as component]))

(defrecord Auth [config cognito-idp]
  component/Lifecycle
  (start [this]
    (println "Starting Auth...")
    (assoc this :cognito-idp (cognito/make-client)))
  (stop [this]
    (println "Stopping Auth...")
    (assoc this :cognito-idp nil)))

(defn make-auth-service [auth-config]
  (map->Auth {:config auth-config}))
