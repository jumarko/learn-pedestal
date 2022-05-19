(ns user
  (:require [io.pedestal.http :as http]))

(println "Loading user.clj")

(defonce ^:private system-ref (atom nil))

(defn start-server []
  (->> {::http/routes #{}
        ::http/type :jetty
        ::http/port 3000
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
  (start-server)
  (restart-server)
  .)
