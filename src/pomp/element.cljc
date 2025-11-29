(ns pomp.element
  (:refer-clojure :exclude [list])
  (:require [clojure.string :as str]))

(def elements
  [::table ::thead ::tbody ::tr ::td ::th ::tfoot
   ::div ::span ::p ::h1 ::h2 ::h3 ::h4 ::h5 ::h6
   ::button ::dialog
   ;; non-html elements
   ::list ::list-row
   ::menu ::menu-title ::menu-dropdown ::menu-dropdown-toggle
   ::link
   ;; forms
   ::checkbox ::input ::label ::label-text])

(doseq [el elements]
  (derive el ::element))

(defmulti merge-attrs
  (fn [el attr-name _attr-value _attrs]
    [el attr-name]))

(defmethod merge-attrs
  :default
  [_el attr-name attr-value attrs]
  (assoc attrs attr-name attr-value))

(defmulti merge-class
  (fn [c1 c2]
    [(type c1) (type c2)]))

(defmethod merge-class
  #?(:clj [String String]
     :cljs [js/String js/String])
  [c1 c2]
  (str c1 " " c2))

(defmethod merge-class
  #?(:clj [clojure.lang.APersistentVector
           clojure.lang.APersistentVector]
     :cljs [cljs.core/PersistentVector
            cljs.core/PersistentVector])
  [c1 c2]
  (str/join " " (into c1 c2)))

(defmethod merge-class
  #?(:clj [String clojure.lang.APersistentVector]
     :cljs [js/String cljs.core/PersistentVector])
  [c1 c2]
  (str c1 " " (str/join " " c2)))

(defmethod merge-class
  #?(:clj [clojure.lang.APersistentVector String]
     :cljs [js/String cljs.core/PersistentVector])
  [c1 c2]
  (str (str/join " " c1) " " c2))

(defmethod merge-attrs
  [::element :class]
  [_el _attr-name attr-value attrs]
  (if-let [existing-classes (get attrs :class)]
    (assoc attrs :class (merge-class existing-classes attr-value))
    (assoc attrs :class attr-value)))

(defmulti default-attrs
  (fn [el] el))

(defmethod default-attrs
  :default
  [_el])

(defmulti html-element
  (fn [el-name] el-name))

(defmethod html-element
  :default
  [el-name]
  (keyword (name el-name)))

(defn- process-properties
  ([el attrs] (process-properties el attrs attrs))
  ([el attrs1 attrs2]
   (reduce-kv (fn [acc attr-name attr-value]
                (merge-attrs el attr-name attr-value acc))
              attrs1
              attrs2)))

(defn make-element
  [el]
  (fn [attrs & children]
    [(html-element el)
     (process-properties el (process-properties
                             el
                             (default-attrs el)
                             attrs))
     children]))

(def button
  (make-element ::button))

(comment
  (button {:class "btn-large"} "hi"))

(def div
  (make-element ::div))

(def span
  (make-element ::span))

(def p
  (make-element ::p))

(def h1
  (make-element ::h1))

(def h2
  (make-element ::h2))

(def h3
  (make-element ::h3))

(def h4
  (make-element ::h4))

(def h5
  (make-element ::h5))

(def h6
  (make-element ::h6))

(def table
  (make-element ::table))

(def thead
  (make-element ::thead))

(def tfoot
  (make-element ::tfoot))

(def tr
  (make-element ::tr))

(def th
  (make-element ::th))

(def tbody
  (make-element ::tbody))

(def list
  (make-element ::list))

(def list-row
  (make-element ::list-row))

(def menu
  (make-element ::menu))

(def menu-title
  (make-element ::menu-title))

(def link
  (make-element ::link))

(def navbar
  (make-element ::navbar))

(def navbar-start
  (make-element ::navbar-start))

(def navbar-center
  (make-element ::navbar-center))

(def navbar-end
  (make-element ::navbar-end))

;; Forms
(def checkbox
  (make-element ::checkbox))

(def input
  (make-element ::input))

(def label
  (make-element ::label))

(def label-text
  (make-element ::label-text))

(def dialog
  (make-element ::dialog))
