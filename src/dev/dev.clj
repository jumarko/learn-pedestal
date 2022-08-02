(ns dev
  (:require
   [cheffy.server :as server]
   [cheffy.util.transit :refer [transit-read transit-write]]
   [clojure.edn :as end]
   [com.stuartsierra.component.repl :as cr]
   [datomic.client.api :as d]
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
  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    db)

  ;; check if accounts exist already
  (let [conn (-> cr/system :database :conn)
        db (d/db conn)]
    (d/pull db {:eid :account/account-id :selector '[*]}))
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
