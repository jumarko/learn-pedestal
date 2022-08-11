(ns cheffy.steps
  (:require
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(defn step-on-request [{:keys [request] :as ctx}]
  ctx
  )

(defn step-on-response [{:keys [request] :as ctx}]
  (prn "step-on-response")
  (assoc ctx :response (response/response {:msg "ok"})))

(def step-interceptor (around ::step step-on-request step-on-response))

(def create-step
  [step-interceptor
   interceptors/transact-interceptor])
