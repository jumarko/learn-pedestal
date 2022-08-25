(ns cheffy.steps
  "Route handlers / interceptors for recipe steps."
  (:require [cheffy.crud :as crud]))

(def id-key :step/step-id)

(defn- params->entity
  [entity-id {:keys [recipe-id description sort-order] :as _params}]
  {:recipe/recipe-id recipe-id
   :recipe/steps [{:step/step-id entity-id
                   :step/description description
                   :step/sort-order sort-order}]})

(def upsert-step (crud/upsert id-key params->entity))

(def retrieve-step (crud/retrieve id-key))

(def delete-step (crud/delete id-key))


