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

(defn create-message []
  (let [{:keys [conversation-id] :as message} (tu/create-entity "/conversations"
                                                                {:to "mike@mailinator.com"
                                                                 :message-body "Hello, Mike!"})]
    (is (uuid? conversation-id))
    message))

(deftest create-messages-test
  (testing "create message for new conversation"
    (reset! conversation-id-store (:conversation-id (create-message))))
  (testing "create message for existing conversation"
    ;; Note: I use :put for creating messages for existing conversations,
    ;; because it fits my framework better - see `crud/upsert` implementation
    ;; For this to work, we also need to implement get-entity operation for conversations
    (let [{:conversation/keys [conversation-id] :as entity} (tu/update-entity (str "/conversations/" @conversation-id-store)
                                                                              {:to "mike@mailinator.com"
                                                                               :message-body "Second message, Mike!"})]
      (is (uuid? conversation-id)))))

(deftest list-messages-test
  (testing "list messages for given conversation id"
    (let [{:keys [conversation-id]} (create-message)
          conversation (tu/get-entity (str "/conversations/" conversation-id))]
      (is (= conversation-id (:conversation/conversation-id conversation)))
      (is (= ["Hello, Mike!"]
             (mapv :message/body
                   (:conversation/messages conversation)))))))
