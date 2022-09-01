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


