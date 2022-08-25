(ns cheffy.ingredients
  "Route handlers / interceptors for ingredients - see Lesson 42:
  https://www.jacekschae.com/view/courses/learn-pedestal-pro/1409479-ingredients-and-ingredients/4435320-42-ingredient.
  See also `seed.edn` file to explore ingredients data."
  (:require [cheffy.crud :as crud]))

(def id-key :ingredient/ingredient-id)

(defn- params->entity
  [_account-id entity-id {:keys [recipe-id name amount measure sort-order] :as _params}]
  {:recipe/recipe-id recipe-id
   :recipe/ingredients [{:ingredient/ingredient-id entity-id
                         :ingredient/display-name name
                         :ingredient/amount amount
                         :ingredient/measure measure
                         :ingredient/sort-order sort-order}]})

(def upsert-ingredient (crud/upsert id-key params->entity))

(def retrieve-ingredient (crud/retrieve id-key))

(def delete-ingredient (crud/delete id-key))

