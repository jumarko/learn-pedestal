(ns cheffy.conversations-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [test-utils :as tu]))

(deftest list-conversations-test
  (testing "list recipes with auth -- public and drafts"
    (let [conversations (tu/list-entities "/conversations")]
      (is (= [3 2]
             (take 2 (mapv #(-> % :conversation/messages count) conversations)))))))

(def conversation-id-store (atom nil))

(defn create-message
  "Create message for a completely new conversation or for existing conversation."
  ([]
   (let [{:keys [conversation-id] :as message}
         (tu/create-entity "/conversations"
                           {:to "mike@mailinator.com"
                            :message-body "Hello, Mike!"})]
     (is (uuid? conversation-id))
     message))
  ([conversation-id]
   (let [{:conversation/keys [conversation-id] :as message}
         (tu/update-entity (str "/conversations/" conversation-id)
                           {:to "mike@mailinator.com"
                            :message-body "Second message, Mike!"})]
     (is (uuid? conversation-id))
     message)))

(deftest create-messages-test
  (testing "create message for new conversation"
    (reset! conversation-id-store (:conversation-id (create-message))))
  (testing "create message for existing conversation" ;; Note: I use :put for creating messages for existing conversations,
    ;; because it fits my framework better - see `crud/upsert` implementation
    ;; For this to work, we also need to implement get-entity operation for conversations
    (create-message @conversation-id-store)))

(deftest list-messages-test
  (testing "list messages for given conversation id"
    (let [{:keys [conversation-id]} (create-message)
          conversation (tu/get-entity (str "/conversations/" conversation-id))]
      (is (= conversation-id (:conversation/conversation-id conversation)))
      (is (= ["Hello, Mike!"]
             (mapv :message/body
                   (:conversation/messages conversation)))))))

(deftest clear-notifications-test
  (let [{:keys [conversation-id]} (create-message)
        _ (create-message conversation-id)
        ;; clear notifications
        _ (tu/assert-response 204
                              :delete
                              (str "/conversations/" conversation-id)
                              ;; we must pass different account id here to clear notifications for Mike (account_2),
                              ;; not for the conversation owner (account_1) - check seed.edn
                              :headers (assoc tu/default-headers
                                              "Authorization" "mike@mailinator.com"))
        ;; and list messages to ...
        {:conversation/keys [messages participants]} (tu/get-entity (str "/conversations/" conversation-id))]
    ;; ... check that all the participants are listed in `:message/read-by`
    (is (= (set participants)
           (set (mapcat :message/read-by messages))))))
