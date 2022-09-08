(ns cheffy.server
  (:require
   [cheffy.components.api-server :as api-server]
   [cheffy.components.database :as database]
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]
   [cheffy.components.auth :as auth]))

(defn create-system [config]
  (component/system-map
   :config config
   :api-server (component/using (api-server/make-api-server (:service-map config))
                                [:database])
   :auth (auth/make-auth-service (:auth config))
   :database (database/make-db (:database config))))

;; define main method to be able to start our server
(defn -main [config-file]
  (let [config (-> config-file slurp edn/read-string)]
    ;; TODO: why don't they  use `component/start-system`
    (component/start (create-system config))))
