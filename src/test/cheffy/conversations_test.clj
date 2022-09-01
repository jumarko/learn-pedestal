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

(deftest create-messages-test
  (testing "create message for new conversation"
    (let [{:keys [conversation-id]} (tu/create-entity "/conversations"
                                                      {:to "mike@mailinator.com"
                                                       :message-body "Hello, Mike!"})]
      (is (uuid? conversation-id))
      (reset! conversation-id-store conversation-id)))
  (testing "create message for existing conversation"
    ;; Note: I use :put for creating messages for existing conversations,
    ;; because it fits my framework better - see `crud/upsert` implementation
    ;; For this to work, we also need to implement get-entity operation for conversations
    (let [{:conversation/keys [conversation-id] :as entity} (tu/update-entity (str "/conversations/" @conversation-id-store)
                                                                              {:to "mike@mailinator.com"
                                                                               :message-body "Second message, Mike!"})]
      (is (uuid? conversation-id)))))
