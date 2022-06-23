(ns cheffy.util.transit
  "Helper functions for (de-)serializaing transit encoded data.
  See https://github.com/cognitect/transit-clj"
  (:require
   [cognitect.transit :as transit])
  (:import (java.io ByteArrayInputStream
                    ByteArrayOutputStream)))

;;; transit-write and transit-read functions introduced in Lesson 25:
;;; https://www.jacekschae.com/view/courses/learn-pedestal-pro/1366483-recipes/4306647-25-transit

(defn transit-write
  "Serialize given object to transit encoded in JSON."
  [obj]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) obj)
    (.toString out)))
(println (transit-write {:name "Juraj" :age 36}))
;; prints: ["^ ","~:name","Juraj","~:age",36]

(defn transit-read
  "Deserialized string representing json-encoded transit data
  intoa proper Clojure object."
  [json-string]
  (let [in (ByteArrayInputStream. (.getBytes json-string))]
    (transit/read (transit/reader in :json))))

(-> {:name "Juraj" :age 36}
    transit-write
    transit-read)
;; => {:name "Juraj", :age 36}
