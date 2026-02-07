(ns pomp.element.navbar
  (:require [pomp.daisy.impl]
            [pomp.element :as element]))

(defn- with-base-class
  [attrs base-class]
  (let [attrs (or attrs {})]
    (update attrs :class (fn [class-name]
                           (if class-name
                             (str base-class " " class-name)
                             base-class)))))

(defn- group-children
  [group]
  (cond
    (nil? group) nil
    (vector? group) [group]
    (sequential? group) (seq group)
    :else [group]))

(defn- group-element
  [group attrs constructor base-class]
  (when-let [children (seq (group-children group))]
    (apply constructor (with-base-class attrs base-class) children)))

(defn navbar
  [{:keys [left-group middle-group right-group
           attrs left-attrs middle-attrs right-attrs]}]
  (let [left (group-element left-group left-attrs element/navbar-start "navbar-start")
        middle (group-element middle-group middle-attrs element/navbar-center "navbar-center")
        right (group-element right-group right-attrs element/navbar-end "navbar-end")
        children (remove nil? [left middle right])]
    (apply element/navbar (with-base-class attrs "navbar") children)))
