(ns cheffy.conversations
  "Route handlers / interceptors for conversations - see Lesson 43 and following."
  (:require [cheffy.crud :as crud]))


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
        message-id (random-uuid)]
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
               :created-at (java.util.Date.)}]))

(def create-message (crud/upsert id-key params->entity))

(def retrieve-message (crud/retrieve id-key))

