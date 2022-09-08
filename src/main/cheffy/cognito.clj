(ns cheffy.cognito
  "For interaction with AWS cognito via aws-api.
  See:
  - aws-api: https://github.com/cognitect-labs/aws-api
  - Cognito app client: https://us-east-1.console.aws.amazon.com/cognito/v2/idp/user-pools/us-east-1_naCTUkfCg/app-integration/clients/60c14ln0t3saa1jg2e0q13oj2v?region=us-east-1

  For the aws client to work properly, you have to configure aws cli.
  If you use multiple profiles, then you need to specify :credentials-provider too.

  To calculate proper SecretHash value, see
  https://docs.aws.amazon.com/cognito/latest/developerguide/signing-up-users-in-your-app.html#cognito-user-pools-computing-secret-hash
  "
  (:require
   [clojure.string :as str]
   [cognitect.aws.client.api :as aws]
   [cognitect.aws.credentials :as credentials]
   [cheffy.util.crypto :as crypto]))

;; this is what I have in my ~/.aws/config
;;   [profile jumar]
;;   region = us-east-1
(def my-aws-profile "jumar")

;; Check also `cognitect.aws.region/default-region-provider`
;; Returns a chain-region-provider with, in order:
;;  environment-region-provider
;;  system-property-region-provider
;;  profile-region-provider
;;  instance-region-provider

(def cognito (aws/client {:api :cognito-idp
                          :credentials-provider (credentials/profile-credentials-provider my-aws-profile)
                          ;; specify region explicitly just in case
                          ;; if you don't do this, then :region-provider is used,
                          ;; which by default is `cognitect.aws.region/default-region-provider` (see above)
                          ;; - for some reason, it wasn't working properly for me (probably using eu-west-1 or something else)
                          :region "us-east-1"}))

;; enable spec checking on invoke calls - those will throw a spec error if the request is invalid
(aws/validate-requests cognito true)


;; play with aws-api
(comment

  (filterv (fn [[op details]] (str/includes? (str/lower-case (name op)) "signup"))
           (aws/ops cognito))
  ;;=>
  ;; [[:SignUp
  ;; {:name "SignUp",
  ;;  :documentation
  ;;  "<p>Registers the user in the specified user pool and creates a user name, password, and user attributes.</p> <note> <p>This action might generate an SMS text message. Starting June 1, 2021, US telecom carriers require you to register an origination phone number before you can send SMS messages to US phone numbers. If you use SMS text messages in Amazon Cognito, you must register a phone number with <a href=\"https://console.aws.amazon.com/pinpoint/home/\">Amazon Pinpoint</a>. Amazon Cognito uses the registered number automatically. Otherwise, Amazon Cognito users who must receive SMS messages might not be able to sign up, activate their accounts, or sign in.</p> <p>If you have never used SMS text messages with Amazon Cognito or any other Amazon Web Service, Amazon Simple Notification Service might place your account in the SMS sandbox. In <i> <a href=\"https://docs.aws.amazon.com/sns/latest/dg/sns-sms-sandbox.html\">sandbox mode</a> </i>, you can send messages only to verified phone numbers. After you test your app while in the sandbox environment, you can move out of the sandbox and into production. For more information, see <a href=\"https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools-sms-userpool-settings.html\"> SMS message settings for Amazon Cognito user pools</a> in the <i>Amazon Cognito Developer Guide</i>.</p> </note>",
  ;;  :request
  ;;  {:UserContextData {:IpAddress string, :EncodedData string},
  ;;   :SecretHash string,
  ;;   :ValidationData [:seq-of {:Name string, :Value string}],
  ;;   :AnalyticsMetadata {:AnalyticsEndpointId string},
  ;;   :ClientId string,
  ;;   :Password string,
  ;;   :ClientMetadata [:map-of string string],
  ;;   :UserAttributes [:seq-of {:Name string, :Value string}],
  ;;   :Username string},
  ;;  :required [:ClientId :Username :Password],
  ;;  :response
  ;;  {:UserConfirmed boolean,
  ;;   :CodeDeliveryDetails
  ;;   {:Destination string, :DeliveryMedium [:one-of ["SMS" "EMAIL"]], :AttributeName string},
  ;;   :UserSub string}}]
  ;; ...

  ;; get the docs printed in the repl
  (aws/doc cognito :SignUp)
  ;; ...
  ;; Required
  ;; [:ClientId :Username :Password]
  ;; ...

  (-> (aws/invoke cognito
               {:op :SignUp
                ;; look here to find the client information: https://us-east-1.console.aws.amazon.com/cognito/v2/idp/user-pools/us-east-1_naCTUkfCg/app-integration/clients/60c14ln0t3saa1jg2e0q13oj2v?region=us-east-1
                :request {:ClientId "60c14ln0t3saa1jg2e0q13oj2v"
                          :Username"jumar" ; this is aws username
                          :Password "xxx"}})
      ;; Note: you will find basically the same information in the error message returned directly by the invoke operation
      meta
      :http-response
      :body
      slurp
      )
;; => "{\"__type\":\"ResourceNotFoundException\",\"message\":\"User pool client 60c14ln0t3saa1jg2e0q13oj2v does not exist.\"}"

  ;; why it says my pool doesn't exist?
  ;; is it because of wrong region or something?
  ;; Note that I have created it in us-east-1 which is also the default region
  ;; of the 'jumar' profile set in ~/.aws/config
  ;; See also Region Lookup: https://github.com/cognitect-labs/aws-api#region-lookup

  ;;=> solved by passing :region "us-east-1" when constructing the client

  ;; now try again
  (aws/invoke cognito
              {:op :SignUp
               ;; look here to find the client information: https://us-east-1.console.aws.amazon.com/cognito/v2/idp/user-pools/us-east-1_naCTUkfCg/app-integration/clients/60c14ln0t3saa1jg2e0q13oj2v?region=us-east-1
               :request {:ClientId "60c14ln0t3saa1jg2e0q13oj2v"
                         :Username"jumar" ; this is aws username
                         :Password "xxx"}})
  ;; => {:__type "NotAuthorizedException",
  ;;     :message "Client 60c14ln0t3saa1jg2e0q13oj2v is configured for secret but secret was not received",
  ;;     :cognitect.anomalies/category :cognitect.anomalies/incorrect}

  ;; that was an expected error - I'll have to pass :SecretHash which we'll do in the next lesson


  .)

;;; pass :SecretHash
(defn calculate-secret-hash
  "See https://docs.aws.amazon.com/cognito/latest/developerguide/signing-up-users-in-your-app.html#cognito-user-pools-computing-secret-hash"
  [{:keys [client-id client-secret username]}]
  ;; notice that we are sining a concatenation username + client-id
  (crypto/hmac-sha256-base64 client-secret (str username client-id)))

(comment
  (calculate-secret-hash {:client-id "abc"
                          :client-secret "xxx"
                          :username "jumarko@example.com"})
;; => "5ux0qdUfS0vc3w2hpZzIhskFDmFJhbLBxvsFJM4X1Us="

  (let [client-id "60c14ln0t3saa1jg2e0q13oj2v"
        client-secret "XXX"]
    (aws/invoke cognito
                {:op :SignUp
                 ;; look here to find the client information: https://us-east-1.console.aws.amazon.com/cognito/v2/idp/user-pools/us-east-1_naCTUkfCg/app-integration/clients/60c14ln0t3saa1jg2e0q13oj2v?region=us-east-1
                 :request {:ClientId client-id
                           :Username"jumarko@gmail.com" ; this is aws username
                           :Password "Pas$w0rd"
                           :SecretHash (calculate-secret-hash {:client-id client-id
                                                               :client-secret client-secret
                                                               :username "jumarko@gmail.com"})}}))

  .)
