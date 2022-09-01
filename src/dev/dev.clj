(ns dev
  (:require
   [cheffy.server :as server]
   [cheffy.util.transit :refer [transit-read transit-write]]
   [clojure.edn :as end]
   [com.stuartsierra.component.repl :as cr]
   [datomic.client.api :as d]
   ;; use datomic peer library instead of client lib
   [datomic.api :as da]
   [io.pedestal.http :as http]
   [io.pedestal.test :as pt]))

(defn system [_old-system]
  (-> (slurp "src/config/development.edn")
      end/read-string
      server/create-system))

(cr/set-init system)

(defn start-dev []
  (cr/start))

(defn stop-dev []
  (cr/stop))

(defn restart-dev []
  (cr/reset))

(defmacro with-db
  "Helper macro that binds the latest db value  (as per `datomic.api/db`) to special symbol `$db`."
  [& body]
  `(let [conn# (-> cr/system :database :conn)
         ~'$db (da/db conn#)]
     ~@body))


(comment
  (restart-dev)

  (start-dev)

  (stop-dev)

  ;; we can examine they system via `cr/system`
  (keys cr/system)
;; => (:config :api-server :database)

  .)

;;; let's look at :api-server
(comment
  (:api-server cr/system)

  (let [service-map (-> cr/system :api-server :service)
        default-interceptors (-> service-map
                                 http/default-interceptors
                                 ::http/interceptors)]
    (concat
     (butlast default-interceptors)
     #_[my-interceptor]
     [(last default-interceptors)]))
  )

;;; check the database and create the schema and load data
;;; but only if it's not already there
(comment
  (restart-dev)

  ;; inspect the db quickly
  (with-db $db)

  ;; check if accounts exist already
  (with-db (d/pull $db {:eid :account/account-id :selector '[*]}))
  
  ;; => #:db{:id nil}

  ;; ... go to database.clj and add `load-dataset` function
  (restart-dev)
  ;; ... now inspect the db again

  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    (d/q '[:find ?e ?id
           :where [?e :account/account-id ?id]]
         db))
;; => [[79164837199977 "mike@mailinator.com"] [79164837199978 "jade@mailinator.com"] [79164837199976 "auth|5fbf7db6271d5e0076903601"] [79164837199979 "mark@mailinator.com"]]

  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    
    (d/transact conn {:tx-data [{:db/id "rec_123456789"
                                 :recipe/recipe-id (random-uuid)
                                 :recipe/display-name "Dummy recipe for quick testing"
                                 }]}))
  .)


;;; inspect interceptor chain for specific route via `io.pedestal.test/response-for`
(comment

  ;; check the docstring
  pt/response-for

  ;; find the service-fn in the system
  (-> cr/system :api-server :service ::http/service-fn)
  ;; => #function[io.pedestal.http.impl.servlet-interceptor/interceptor-service-fn/fn--24686]

  ;; use this to debug problems with the service
  #_(->> (pt/response-for
        (-> cr/system :api-server :service ::http/service-fn)
        :get
        "/recipes")
       :body
       (spit "debug.txt"))

  (->> (pt/response-for
        (-> cr/system :api-server :service ::http/service-fn)
        :get
        "/recipes"))
  ;; => {:status 200,
  ;;     :body "list recipes",
  ;;     :headers
  ;;     {"Strict-Transport-Security" "max-age=31536000; includeSubdomains",
  ;;      "X-Frame-Options" "DENY",
  ;;      "X-Content-Type-Options" "nosniff",
  ;;      "X-XSS-Protection" "1; mode=block",
  ;;      "X-Download-Options" "noopen",
  ;;      "X-Permitted-Cross-Domain-Policies" "none",
  ;;      "Content-Security-Policy"
  ;;      "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;",
  ;;      "Content-Type" "text/plain"}}

  .)

;; after lesson 22 - pass account-id in Authorization header
(comment

  (restart-dev)

  (-> (pt/response-for
       (-> cr/system :api-server :service ::http/service-fn)
       :get "/recipes"
       ;; account-id is taken from seed.edn
       :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
      :body
      )


  .)

;; Lesson 25: create recipe

(comment
  (restart-dev)

  (-> (pt/response-for
       (-> cr/system :api-server :service ::http/service-fn)
       :post "/recipes"
         ;; account-id is taken from seed.edn
       :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"
                 "Content-Type" "application/transit+json"}
       :body (transit-write {:name "my transit recipe"
                             :public true
                             :prep-time 30
                             :img "https://github.com/clojure.png"})))
  ;; => {:status 201,
  ;;     :body "",
  ;;     :headers
  ;;     {"X-Frame-Options" "DENY",
  ;;      "X-XSS-Protection" "1; mode=block",
  ;;      "X-Download-Options" "noopen",
  ;;      "Location" "/recipes/cd716f87-c59c-4e63-b875-d06d654ce06c",
  ;;      "Strict-Transport-Security" "max-age=31536000; includeSubdomains",
  ;;      "X-Permitted-Cross-Domain-Policies" "none",
  ;;      "X-Content-Type-Options" "nosniff",
  ;;      "Content-Security-Policy"
  ;;      "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"}}
  .)


;; 26: retrieve recipe
(comment
  (restart-dev)

  (def recipe-url
    (get-in (-> (pt/response-for
                 (-> cr/system :api-server :service ::http/service-fn)
                 :post "/recipes"
                 :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"
                           "Content-Type" "application/transit+json"}
                 :body (transit-write {:name "my transit recipe"
                                       :public true
                                       :prep-time 30
                                       :img "https://github.com/clojure.png"})))
            [:headers "Location"]))

  ;; first attempt, just returning a dummy response
  (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                   :get recipe-url
                   :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
;; => {:status 200, :body "7e485d7a-23c1-4519-8043-15dbb38775b7", :headers {"Strict-Transport-Security" "max-age=31536000; includeSubdomains", "X-Frame-Options" "DENY", "X-Content-Type-Options" "nosniff", "X-XSS-Protection" "1; mode=block", "X-Download-Options" "noopen", "X-Permitted-Cross-Domain-Policies" "none", "Content-Security-Policy" "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;", "Content-Type" "text/plain"}}

  ;; proper implementation
  (-> (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                    :get recipe-url
                    :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
      :body
      transit-read)
  ;; => [[#:recipe{:recipe-id #uuid "18f4d0d5-89f9-483c-a926-08ce02eb1453",
  ;;               :prep-time 30,
  ;;               :display-name "my transit recipe",
  ;;               :image-url "https://github.com/clojure.png",
  ;;               :public? true,
  ;;               :owner #:account{:account-id "auth|5fbf7db6271d5e0076903601", :display-name "Auth"}}]]

  .)


;; 27: update recipe
(comment
  (restart-dev)

  ;; create new recipe
  (def recipe-url
    (get-in (-> (pt/response-for
                 (-> cr/system :api-server :service ::http/service-fn)
                 :post "/recipes"
                 :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"
                           "Content-Type" "application/transit+json"}
                 :body (transit-write {:name "my transit recipe"
                                       :public true
                                       :prep-time 30
                                       :img "https://github.com/clojure.png"})))
            [:headers "Location"]))

  (-> (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                       :get recipe-url
                       :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
      :body
      transit-read)
  ;; => [[#:recipe{:recipe-id #uuid "e86fe7c2-1227-42f3-9ea4-18ebf7a3d86d",
  ;;               :prep-time 30,
  ;;               :display-name "my transit recipe",
  ;;               :image-url "https://github.com/clojure.png",
  ;;               :public? true,
  ;;               :owner #:account{:account-id "auth|5fbf7db6271d5e0076903601", :display-name "Auth"}}]]

  ;; update it
  (pt/response-for
   (-> cr/system :api-server :service ::http/service-fn)
   :put recipe-url
   :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"
             "Content-Type" "application/transit+json"}
   :body (transit-write {:name "my UPDATED recipe"
                         :public true
                         :prep-time 45
                         :img "https://github.com/clojure.png"}))
;; => {:status 204, :body "", :headers {"Strict-Transport-Security" "max-age=31536000; includeSubdomains", "X-Frame-Options" "DENY", "X-Content-Type-Options" "nosniff", "X-XSS-Protection" "1; mode=block", "X-Download-Options" "noopen", "X-Permitted-Cross-Domain-Policies" "none", "Content-Security-Policy" "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"}}

  ;; now get it to check if it was updated
  (-> (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                       :get recipe-url
                       :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
      :body
      transit-read)
  ;; => [[#:recipe{:recipe-id #uuid "cb3e63b0-ac88-4be9-9bf5-50a94c6bd47c",
  ;;               :prep-time 45,
  ;;               :display-name "my UPDATED recipe",
  ;;               :image-url "https://github.com/clojure.png",
  ;;               :public? true,
  ;;               :owner #:account{:account-id "auth|5fbf7db6271d5e0076903601", :display-name "Auth"}}]]
  .)


;; 28: delete recipe
(comment
  (restart-dev)

  ;; create new recipe
  (def recipe-url
    (get-in (-> (pt/response-for
                 (-> cr/system :api-server :service ::http/service-fn)
                 :post "/recipes"
                 :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"
                           "Content-Type" "application/transit+json"}
                 :body (transit-write {:name "my transit recipe"
                                       :public true
                                       :prep-time 30
                                       :img "https://github.com/clojure.png"})))
            [:headers "Location"]))

  (-> (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                       :get recipe-url
                       :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
      :body transit-read ffirst :recipe/recipe-id)
;; => #uuid "044630f1-26a3-4c00-8d0e-8c641cd9b96a"

  ;; delete it
  (-> (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                       :delete recipe-url
                       :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
      :body)
;; => ""

  ;; now try to get it again
  (-> (pt/response-for (-> cr/system :api-server :service ::http/service-fn)
                       :get recipe-url
                       :headers {"Authorization"  "auth|5fbf7db6271d5e0076903601"})
      :body
      transit-read)
  ;; Note: this should perhaps return 404 NOT FOUND?
;; => []

  .)



;;; random datomic debugging
(comment
  ;; list all entitities ids having specific attribute
  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    (d/q '[:find ?e
           :where [?e :recipe/image-url]]
         db))
  ;; => [[74766790688882] [74766790688883] [74766790688884] [74766790688885]]

  ;; find entity by internal id: https://stackoverflow.com/questions/42364172/how-can-i-use-datomics-pull-method-to-grab-an-entity-by-its-entity-id
  (def my-db (d/db (-> cr/system :database :conn)))
  (d/pull my-db '[*] 74766790688882)
;; => {:db/id 74766790688882, :recipe/recipe-id #uuid "a3dde84c-4a33-45aa-b0f3-4bf9ac997680", :recipe/owner #:db{:id 74766790688872}, :recipe/display-name "Splitony's Pizza", :recipe/prep-time 45, :recipe/favorite-count 3, :recipe/image-url "https://res.cloudinary.com/schae/image/upload/f_auto,h_400,q_80/v1548183465/cheffy/recipe/pizza.jpg", :recipe/public? false}

  ;; but for the recipe I created from within the test - the data is empty!
  (d/pull my-db '[*] 13194139533326)
;; => #:db{:id 13194139533326, :txInstant #inst "2022-08-01T04:42:12.635-00:00"}


  .)


;;; querying Datomic using datomic.api, not datomic.client.api.
(comment
  (with-db
    (da/q '[:find (pull ?e [*])
            :in $
            :where (or-join [?e ?owner ?account-id]
                            [?e :recipe/public? true]
                            (and [?e :recipe/public? false]
                                 [?owner :account/account-id "auth|5fbf7db6271d5e0076903601"]
                                 [?e :recipe/owner ?owner]))]
          $db))

  (with-db 
    (let [recipe-pattern [:recipe/recipe-id
                          :recipe/prep-time
                          :recipe/display-name
                          :recipe/image-url
                          :recipe/public?
                          ;; Notice the reverse lookup with underscore: https://docs.datomic.com/cloud/query/query-pull.html#reverse-lookup
                          :account/_favorite-recipes
                          {:recipe/owner
                           [:account/account-id
                            :account/display-name]}
                          {:recipe/steps
                           [:step/step-id
                            :step/description
                            :step/sort-order]}
                          {:recipe/ingredients
                           [:ingredient/ingredient-id
                            :ingredient/display-name
                            :ingredient/amount
                            :ingredient/measure
                            :ingredient/sort-order]}]
          account-id "auth|5fbf7db6271d5e0076903601"]
      (filter
       (fn [[r]]
         (not (:recipe/public? r)))
       (da/query
        {:query '[:find (pull ?e pattern)
                  :in $ pattern ?account-id
                  ;; Or clauses: https://docs.datomic.com/on-prem/query/query.html#or-clauses
                  ;; `or-join` is needed because the second clause (`and`) uses a different set of variables
                  :where (or-join [?e]
                                  [?e :recipe/public? true]
                                  (and [?e :recipe/public? false]
                                       [?owner :account/account-id ?account-id]
                                       [?e :recipe/owner ?owner]))]
         :args [$db recipe-pattern account-id]}))))
      .)


;;; Lesson 43: list conversations
(comment

  (with-db
    (da/q '[:find (pull ?c [*])
            :in $ ?account-id
            :where
            [?a :account/account-id ?account-id]
            [?c :conversation/participants ?a]]
          $db "auth|5fbf7db6271d5e0076903601"))
  ;;=>
  [[{:db/id 17592186045438,
   :conversation/conversation-id #uuid "8d4ab926-d5cc-483d-9af0-19627ed468eb",
   :conversation/messages
   [{:db/id 17592186045432,
     :message/message-id #uuid "5ae8cafb-1773-4730-9d8d-a76bd872d110",
     :message/body "First message",
     :message/owner #:db{:id 17592186045418}}
    {:db/id 17592186045433,
     :message/message-id #uuid "1627c35a-d1da-4dd1-88be-aa101a1b5b98",
     :message/body "Second message",
     :message/owner #:db{:id 17592186045419}}
    {:db/id 17592186045434,
     :message/message-id #uuid "5ae8cafb-1773-4731-8d8d-a76bd872d110",
     :message/body "Third message",
     :message/owner #:db{:id 17592186045418}}],
   :conversation/participants [#:db{:id 17592186045418} #:db{:id 17592186045419}]}]
 [{:db/id 17592186045439,
   :conversation/conversation-id #uuid "362d06c7-2702-4273-bcc3-0c04d2753b6f",
   :conversation/messages
   [{:db/id 17592186045435,
     :message/message-id #uuid "0f3ebcf0-3c6f-4258-9074-924d60252973",
     :message/body "First message",
     :message/owner #:db{:id 17592186045418}}
    {:db/id 17592186045436,
     :message/message-id #uuid "dbcb3781-e070-4935-8b3f-afc48453bb20",
     :message/body "Second message",
     :message/owner #:db{:id 17592186045420}}],
   :conversation/participants [#:db{:id 17592186045418} #:db{:id 17592186045420}]}]]

  ;; or alternatively using pull syntax
  (with-db 
    (da/pull $db
             '[{:conversation/_participants [*]}]
             [:account/account-id "auth|5fbf7db6271d5e0076903601"]))
  ;;=>
#:conversation{:_participants
               [{:db/id 17592186045438,
                 :conversation/conversation-id #uuid "8d4ab926-d5cc-483d-9af0-19627ed468eb",
                 :conversation/messages
                 [{:db/id 17592186045432,
                   :message/message-id #uuid "5ae8cafb-1773-4730-9d8d-a76bd872d110",
                   :message/body "First message",
                   :message/owner #:db{:id 17592186045418}}
                  {:db/id 17592186045433,
                   :message/message-id #uuid "1627c35a-d1da-4dd1-88be-aa101a1b5b98",
                   :message/body "Second message",
                   :message/owner #:db{:id 17592186045419}}
                  {:db/id 17592186045434,
                   :message/message-id #uuid "5ae8cafb-1773-4731-8d8d-a76bd872d110",
                   :message/body "Third message",
                   :message/owner #:db{:id 17592186045418}}],
                 :conversation/participants [#:db{:id 17592186045418} #:db{:id 17592186045419}]}
                {:db/id 17592186045439,
                 :conversation/conversation-id #uuid "362d06c7-2702-4273-bcc3-0c04d2753b6f",
                 :conversation/messages
                 [{:db/id 17592186045435,
                   :message/message-id #uuid "0f3ebcf0-3c6f-4258-9074-924d60252973",
                   :message/body "First message",
                   :message/owner #:db{:id 17592186045418}}
                  {:db/id 17592186045436,
                   :message/message-id #uuid "dbcb3781-e070-4935-8b3f-afc48453bb20",
                   :message/body "Second message",
                   :message/owner #:db{:id 17592186045420}}],
                 :conversation/participants [#:db{:id 17592186045418} #:db{:id 17592186045420}]}]}


;; let's use the conversation-pattern from the lesson
(def conversation-pattern [:conversation/conversation-id
                           {:conversation/messages
                            [:message/message-id
                             :message/body
                             {:message/owner
                              [:account/account-id
                               :account/display-name]}]}])

(with-db
  (da/q '[:find (pull ?c pattern)
          :in $ pattern ?account-id
          :where
          [?a :account/account-id ?account-id]
          [?c :conversation/participants ?a]]
        $db conversation-pattern "auth|5fbf7db6271d5e0076903601"))
;;=>
[[#:conversation{:conversation-id #uuid "8d4ab926-d5cc-483d-9af0-19627ed468eb",
                 :messages
                 [#:message{:message-id #uuid "5ae8cafb-1773-4730-9d8d-a76bd872d110",
                            :body "First message",
                            :owner
                            #:account{:account-id "auth|5fbf7db6271d5e0076903601",
                                      :display-name "Auth"}}
                  #:message{:message-id #uuid "1627c35a-d1da-4dd1-88be-aa101a1b5b98",
                            :body "Second message",
                            :owner
                            #:account{:account-id "mike@mailinator.com", :display-name "Mike"}}
                  #:message{:message-id #uuid "5ae8cafb-1773-4731-8d8d-a76bd872d110",
                            :body "Third message",
                            :owner
                            #:account{:account-id "auth|5fbf7db6271d5e0076903601",
                                      :display-name "Auth"}}]}]
 [#:conversation{:conversation-id #uuid "362d06c7-2702-4273-bcc3-0c04d2753b6f",
                 :messages
                 [#:message{:message-id #uuid "0f3ebcf0-3c6f-4258-9074-924d60252973",
                            :body "First message",
                            :owner
                            #:account{:account-id "auth|5fbf7db6271d5e0076903601",
                                      :display-name "Auth"}}
                  #:message{:message-id #uuid "dbcb3781-e070-4935-8b3f-afc48453bb20",
                            :body "Second message",
                            :owner
                            #:account{:account-id "jade@mailinator.com", :display-name "Jade"}}]}]]

  ..)
