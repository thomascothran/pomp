(ns scratch.app-skeleton
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
      [:div {:class "drawer"}
       [:input {:id "app-skeleton-drawer"
                :type "checkbox"
                :class "drawer-toggle"}]
       [:div {:class "drawer-content"}
        (navbar/navbar
         {:attrs {:class "bg-base-100 border-b border-base-300"}
          :left-group
          [:div {:class "flex items-center gap-2"}
           [:label {:for "app-skeleton-drawer"
                    :class "btn btn-ghost btn-square drawer-button"
                    :aria-label "Open sidebar menu"}
            [:svg {:xmlns "http://www.w3.org/2000/svg"
                   :viewBox "0 0 24 24"
                   :fill "none"
                   :stroke "currentColor"
                   :stroke-width "1.5"
                   :class "size-6"}
              [:path {:stroke-linecap "round"
                      :stroke-linejoin "round"
                      :d "M3.75 5.25h16.5m-16.5 4.5h16.5m-16.5 4.5h16.5m-16.5 4.5h16.5"}]]]
           [:a {:class "btn btn-ghost text-xl"} "App Skeleton"]]
          :right-group (theme-picker/theme-picker {})})
        [:main {:class "min-h-[calc(100vh-4rem)] p-6"}
         [:h1 {:class "text-2xl font-semibold"} "App Skeleton"]
         [:p {:class "text-base-content/70 mt-2"}
          "Navbar and sidebar shell for scratch exploration."]]]
       [:div {:class "drawer-side"}
        [:label {:for "app-skeleton-drawer"
                 :aria-label "Close sidebar menu"
                 :class "drawer-overlay"}]
        [:aside {:class "w-64 min-h-full border-r border-base-300 bg-base-100 p-4"}
         [:ul {:class "menu w-full gap-1"}
          [:li [:a {:href "#"} "datatable"]]
          [:li [:a {:href "#"} "autocomplete"]]
          [:li [:a {:href "#"} "forms"]]
          [:li [:a {:href "#"} "detail cards"]]]]]]))})
