(ns cheffy.account
  (:require
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.chain :as chain]
   [io.pedestal.interceptor.helpers :refer [before]]
   [ring.util.response :as response]))

(defn on-request [{:keys [request] :as ctx}]
  (let [{:keys [system/auth transit-params]} request
        {:keys [email password]} transit-params
        account-result (auth/create-cognito-account auth
                                                    {:email email
                                                     :password password})]
    (if (contains? account-result :cognitect.anomalies/category)
      (response/bad-request {:type (:__type account-result)
                             :message (:message account-result)
                             :data {:account-id email}})
      (-> ctx
          ;; add transact-interceptor dynamically only if the response is OK.
          ;; `chain/enqueue` adds the interceptor to the end of context's execution queue
          (chain/enqueue interceptors/transact-interceptor)
          (assoc :tx-data [{:account/account-id (:UserSub account-result)
                            :account/display-name email}])))))

(def sign-up  (before on-request))
