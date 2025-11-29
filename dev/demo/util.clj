(ns demo.util
  (:require [dev.onionpancakes.chassis.core :as c]))

(defn ->html
  [hiccup]
  (c/html hiccup))
