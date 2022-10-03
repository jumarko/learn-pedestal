(ns cheffy.components.auth
  "Authentication component using AWS Cognito."
  (:require
   [cheffy.cognito :as cognito]
   [cognitect.aws.client.api :as aws]
   [com.stuartsierra.component :as component]
   [clojure.string :as str]))

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
  (let [{:keys [client-id client-secret]} config
        result (aws/invoke cognito-idp
                           {:op :SignUp
                            :request {:ClientId client-id
                                      :Username email
                                      :Password password
                                      :SecretHash (cognito/calculate-secret-hash {:client-id client-id
                                                                                  :client-secret client-secret
                                                                                  :username email})}})]
    (when (contains? result :cognitect.anomalies/category)
      (throw (ex-info "Create cognito account failed" result)))))

(defn confirm-account
  [{:keys [config cognito-idp] :as _auth}
   {:keys [email confirmation-code] :as _params}]
  (let [{:keys [client-id client-secret]} config
        ;; result will be `{}` if all is ok
        result (aws/invoke cognito-idp
                           {:op :ConfirmSignUp
                            :request {:ClientId client-id
                                      :Username email
                                      :ConfirmationCode confirmation-code
                                      :SecretHash (cognito/calculate-secret-hash {:client-id client-id
                                                                                  :client-secret client-secret
                                                                                  :username email})}})]
    (when (contains? result :cognitect.anomalies/category)
      (throw (ex-info "Create cognito account failed" result)))))

(comment

  (def my-cognito (cognito/make-client))

  ;; try to search for some 'confirm' operation
  (keep (fn [[k v]]
          (when (-> k name str/lower-case (str/includes? "confirm"))
            k))
          (aws/ops my-cognito))
;; => (:AdminConfirmSignUp :ConfirmSignUp :ConfirmForgotPassword :ResendConfirmationCode :ConfirmDevice)

  ;; ConfirmSignUp looks like the thing we want to do
  (aws/doc my-cognito :ConfirmSignUp)
  ;;=> needs :ConfirmationCode (see your email: https://mail.google.com/mail/u/0/?zx=vb7xkb7gywyc#inbox/FMfcgzGqQmMRxrxQkPCTsrFnnFtMHNfn)

  (let [client-id "60c14ln0t3saa1jg2e0q13oj2v"
        ;; see https://us-east-1.console.aws.amazon.com/cognito/v2/idp/user-pools/us-east-1_naCTUkfCg/app-integration/clients/60c14ln0t3saa1jg2e0q13oj2v?region=us-east-1
        client-secret "xxx"
        username "jumarko+cheffy@gmail.com"
        ;; code must be string
        confirmation-code "843367"]
    (confirm-account {:config {:client-id client-id
                               :client-secret client-secret}
                      :cognito-idp my-cognito}
                     {:email username
                      :confirmation-code confirmation-code}))
  

  .)
