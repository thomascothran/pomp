(ns pomp.daisy.impl
  (:require [pomp.element :as pe]))


(defmethod pe/default-attrs
  ::pe/button
  [_]
  {:class ["btn"]})

(defmethod pe/default-attrs
  ::pe/table
  [_]
  {:class ["table"]})

(defmethod pe/default-attrs
  ::pe/list
  [_]
  {:class "list"})

(defmethod pe/html-element
  ::pe/list
  [_]
  :ul)

(defmethod pe/html-element
  ::pe/list-row
  [_]
  :li)

(defmethod pe/default-attrs
  ::pe/list-row
  [_]
  {:class "list-row"})

(defmethod pe/html-element
  ::pe/menu
  [_]
  :ul)

(defmethod pe/default-attrs
  ::pe/menu
  [_]
  {:class "menu"})

(defmethod pe/html-element
  ::pe/menu-title
  [_]
  :li)

(defmethod pe/default-attrs
  ::pe/menu-list
  [_]
  {:class "menu-list"})

(defmethod pe/html-element
  ::pe/link
  [_]
  :a)

(defmethod pe/default-attrs
  ::pe/link
  [_]
  {:class "link"})

(defmethod pe/html-element
  ::pe/navbar
  [_]
  :div)

(defmethod pe/default-attrs
  ::pe/navbar
  [_]
  {:class "navbar"})

(defmethod pe/html-element
  ::pe/navbar-start
  [_]
  :div)

(defmethod pe/default-attrs
  ::pe/navbar-start
  [_]
  {:class "navbar-start"})

(defmethod pe/html-element
  ::pe/navbar-end
  [_]
  :div)

(defmethod pe/default-attrs
  ::pe/navbar-end
  [_]
  {:class "navbar-end"})

(defmethod pe/default-attrs
  ::pe/checkbox
  [_]
  {:type "checkbox"
   :class "checkbox"})

(defmethod pe/html-element
  ::pe/checkbox
  [_]
  :input)

(defmethod pe/html-element
  ::pe/input
  [_]
  :input)

(defmethod pe/default-attrs
  ::pe/input
  [_]
  {:type "text"
   :class "input"})

(defmethod pe/html-element
  ::pe/label-text
  [_]
  :span)

(defmethod pe/default-attrs
  ::pe/label-text
  [_]
  {:class "label"})
