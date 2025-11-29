(ns demo.autocomplete
  "Demonstrates an autocomplete text field with:

  1. Dynamic server replies matching the text
  2. Debouncing"
  (:require [demo.util :refer [->html]]))

(defn autocomplete-handler
  "Renders the autocomplete"
  [req]
  {:status 200
   :body "todo"})

(comment
  (autocomplete-handler {}))

(defn options-handler
  "Renders the options. As the user types,
  the text is handled here, and the autocomplete
  options are patched via datastar."
  [req]
  {:status 500
   :body "todo"})

(defn make-routes
  [_]
  [["/autocomplete" autocomplete-handler]
   ["/autocomplete/options" options-handler]])
