(ns cheffy.conversations-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [test-utils :as tu]))

(deftest list-conversations-test
  (testing "list recipes with auth -- public and drafts"
    (let [conversations (tu/list-entities "/conversations")]
      (is (= [3 2]
             (mapv #(-> % :conversation/messages count) conversations))))))
