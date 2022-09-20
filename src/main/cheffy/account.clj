(ns cheffy.account
  (:require
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(defn on-request [{:keys [request] :as ctx}]
  (let [{:keys [system/auth transit-params]} request
        {:keys [email password]} transit-params
        account-result (auth/create-cognito-account auth
                                                    {:email email
                                                     :password password})]
    (assoc ctx :tx-data [{:account/account-id (:UserSub account-result)
                          :account/display-name email}])))

(defn on-response [ctx]
  (let [account-id (-> ctx :tx-data first :account/account-id)]
    (assoc ctx :response (response/response {:account-id account-id}))))

(def sign-up  [(around ::sign-up-interceptor on-request on-response)
               interceptors/transact-interceptor])
