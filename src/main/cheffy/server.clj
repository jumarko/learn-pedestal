(ns cheffy.server
  (:require
   [cheffy.components.api-server :as api-server]
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]))

(defn create-system [config]
  (component/system-map
   :config config
   :api-server (api-server/make-api-server (:service-map config))))

;; define main method to be able to start our server
(defn -main [config-file]
  (let [config (-> config-file slurp edn/read-string)]
    ;; TODO: why don't they  use `component/start-system`
    (component/start (create-system config))))
