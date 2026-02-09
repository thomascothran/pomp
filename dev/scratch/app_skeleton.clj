(ns scratch.app-skeleton
  (:require [app :as app]
            [dev.onionpancakes.chassis.core :as c]))

(defn handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body
     (c/html
      (app/with-app-layout
       {:drawer-id "app-skeleton-drawer"
        :nav-title "App Skeleton"}
       [:div
        [:h1 {:class "text-2xl font-semibold"} "App Skeleton"]
        [:p {:class "text-base-content/70 mt-2"}
         "Navbar and sidebar shell for scratch exploration."]]))})
