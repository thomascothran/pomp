(ns scratch.navbar
  (:require [dev.onionpancakes.chassis.core :as c]
            [pomp.element.navbar :as navbar]
            [pomp.element.theme-picker :as theme-picker]))

(defn page
  [& children]
  [:html {:data-theme "light"}
   [:head
    [:link {:href "/assets/output.css"
            :rel "stylesheet"}]
    [:script {:type "module"
              :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"}]]
   [:body {:class "min-h-screen m-0 bg-base-200"}
    children]])

(defn handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body
      (c/html
       (page
         (navbar/navbar
          {:attrs {:class "bg-base-100"}
           :left-group [:a {:class "btn btn-ghost text-xl"} "Pomp"]
           :middle-group [:div {:class "form-control"}
                          [:input {:class "input input-bordered input-sm"
                                   :type "text"
                                   :placeholder "Search"}]]
           :right-group (theme-picker/theme-picker {})})))})
