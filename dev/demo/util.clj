(ns demo.util
  (:require [dev.onionpancakes.chassis.core :as c]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [starfederation.datastar.clojure.api :as d*]))

(defn ->html
  [hiccup]
  (c/html hiccup))

(defn page
  [& children]
  [:html {:data-theme "dracula"}
   [:head
    [:link {:href "/assets/output.css"
            :rel "stylesheet"}]
    [:script {:type "module"
              :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"}]
    (when-let [script (some-> (io/resource "public/js/datatable.js") slurp)]
      [:script (c/raw script)])]
   [:body.min-h-screen.m-0
    children]])

(comment
  (page [:div [:h1 "Hello"] [:p "World"]])
  (println (page [:div#app [:h1 "Test Page"]])))

(defn get-signals
  [req]
  (if (get-in req [:headers "datastar-request"])
    (some-> (d*/get-signals req)
            (json/read-str {:key-fn keyword}))
    req))
