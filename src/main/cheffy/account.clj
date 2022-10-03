(ns cheffy.account
  (:require
   [cheffy.components.auth :as auth]
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(defn on-signup-request [{:keys [request] :as ctx}]
  (let [{:keys [system/auth transit-params]} request
        {:keys [email password]} transit-params
        account-result (auth/create-cognito-account auth
                                                    {:email email
                                                     :password password})]
    (assoc ctx :tx-data [{:account/account-id (:UserSub account-result)
                          :account/display-name email}])))

(defn on-signup-response [ctx]
  (let [account-id (-> ctx :tx-data first :account/account-id)]
    (assoc ctx :response (response/response {:account-id account-id}))))

(def sign-up  [(around ::sign-up-interceptor on-signup-request on-signup-response)
               interceptors/transact-interceptor])

(defn on-confirm-request [{:keys [request] :as ctx}]
  (let [{:keys [system/auth transit-params]} request]
    (auth/confirm-account auth transit-params)
    ctx))

(defn on-confirm-response [ctx]
  (assoc ctx :response (response/status 204)))

(def confirm [(around ::confirm-account-interceptor on-confirm-request on-confirm-response)])
