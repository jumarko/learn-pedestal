(ns user
  (:require [io.pedestal.http :as http]))

(println "Loading user.clj")

(defonce ^:private system-ref (atom nil))

(defn start-server []
  (->> {::http/routes #{}
        ::http/type :jetty
        ;; use 3001 instead of 3000 to avoid conflicts with Backstage and other apps commonly using 3000
        ::http/port 3001
        ::http/join? false}
       http/create-server
       http/start
       (#(do (println "server started.") %))
       (reset! system-ref)))

(defn stop-server []
  (http/stop @system-ref))

(defn restart-server []
  (stop-server)
  (start-server))

(comment
  (restart-server)

  (start-server)

  (stop-server)

  .)
