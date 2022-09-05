(ns cheffy.conversations
  "Route handlers / interceptors for conversations - see Lesson 43 and following."
  (:require
   [cheffy.crud :as crud]
   [cheffy.interceptors :as interceptors]
   [io.pedestal.interceptor.helpers :refer [around]]
   [ring.util.response :as response]))

(def conversation-pattern [:conversation/conversation-id
                           {:conversation/messages
                            [:message/message-id
                             :message/body
                             {:message/owner
                              [:account/account-id
                               :account/display-name]}]}])

(defn- list-conversations-query [request]
  (let [account-id (get-in request [:headers "authorization"])]
    {:query '[:find (pull ?c pattern)
              :in $ pattern ?account-id
              :where
              [?a :account/account-id ?account-id]
              [?c :conversation/participants ?a]]
     :args [conversation-pattern account-id]}))

(defn- list-conversations-response [q-result]
  (mapv first q-result))

(def list-conversations (crud/list list-conversations-query list-conversations-response))

(def id-key :conversation/conversation-id)

(defn- params->entity
  [account-id entity-id {:keys [to message-body] :as _params}]
  (let [from-ref [:account/account-id account-id]
        to-ref [:account/account-id to]
        message-id (random-uuid)
        now (java.util.Date.)]
    [#:conversation{:conversation-id entity-id
                    :participants [from-ref to-ref]
                    ;; Note: we really need to call `str` on message-id, otherwise we get a nasty error:
                    ;; Caused by: java.lang.IllegalArgumentException: :db.error/not-an-entity Unable to resolve entity: 46742725-2949-4dfa-a8d0-aedd5afaaa4d in datom [-9223301668109598085 :conversation/messages #uuid "46742725-2949-4dfa-a8d0-aedd5afaaa4d"]
                    :messages [(str message-id)]}
     #:message{:db/id (str message-id)
               :message-id message-id
               :owner from-ref
               ;; TODO: what does read-by really mean?
               :read-by [from-ref]
               :body message-body
               :created-at now}]))

(def create-message (crud/upsert id-key params->entity))

(def retrieve-message (crud/retrieve id-key))


;;; DELETE is special - it does not remove conversation or messages,
;;; instead it 'clears conversation', that is marks all the messages as read by given account id

;; Notice that this operation breaks the usual pattern when we delegate datomic interactions
;; to the interceptors namespace
;; This is unfortunate so maybe we should make the interceptors more generic?
(defn- find-unread-messages [ctx {:keys [account-id conversation-id] :as _query-args}]
  (->> (interceptors/query!
        (assoc ctx
               :q-data {:query '[:find ?m
                                 :in $ ?account-id ?conversation-id
                                 :where
                                 [?a :account/account-id ?account-id]
                                 [?c :conversation/conversation-id ?conversation-id]
                                 [?c conversation/participants ?a]
                                 [?c :conversation/messages ?m]]
                        :args [account-id conversation-id]}))
       :q-result
       (mapv first)))

(defn- clear-notifications-on-request [{:keys [request] :as ctx}]
  (let [conversation-id (parse-uuid (get-in request [:path-params :conversation-id]))
        account-id (crud/get-account-id ctx)
        unread (find-unread-messages ctx {:account-id account-id
                                          :conversation-id conversation-id})]
    (cond-> ctx
      (not-empty unread) (assoc :tx-data (for [um unread]
                                           [:db/add um :message/read-by [:account/account-id account-id]])))))

(defn- clear-notifications-on-response [ctx]
  (assoc ctx :response (response/status 204)))

(def clear-notifications
  [(around clear-notifications-on-request clear-notifications-on-response)
   interceptors/transact-interceptor])


(mapv first #{[17592186046180] [17592186046182]})
