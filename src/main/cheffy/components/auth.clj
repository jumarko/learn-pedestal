(ns cheffy.components.auth
  "Authentication component using AWS Cognito."
  (:require
   [cheffy.cognito :as cognito]
   [cognitect.aws.client.api :as aws]
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

(defn create-cognito-account
  [{:keys [config cognito-idp] :as _auth}
   {:keys [email password] :as _params}]
  (let [{:keys [client-id client-secret]} config]
    (aws/invoke cognito-idp
                {:op :SignUp
                 :request {:ClientId client-id
                           :Username email
                           :Password password
                           :SecretHash (cognito/calculate-secret-hash {:client-id client-id
                                                                       :client-secret client-secret
                                                                       :username email})}})))
