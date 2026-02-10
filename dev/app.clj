(ns app
  (:require [pomp.element.navbar :as navbar]
            [pomp.icons :as icons]
            [pomp.element.theme-picker :as theme-picker]))

(def ^:private default-sidebar
  [:aside {:class "w-64 min-h-full border-r border-base-300 bg-base-100 p-4"}
   [:ul {:class "menu w-full gap-1"}
    [:li
     [:details {:open true}
      [:summary "Data Views"]
      [:ul
       [:li [:a {:href "#"} "datatable"]]
       [:li [:a {:href "#"} "detail cards"]]
       [:li [:a {:href "#"} "kanban"]]]]]
    [:li
     [:details
      [:summary "Inputs"]
      [:ul
       [:li [:a {:href "#"} "autocomplete"]]
       [:li
        [:details
         [:summary "Forms"]
         [:ul
          [:li [:a {:href "#"} "signup form"]]
          [:li [:a {:href "#"} "settings form"]]]]]
       [:li [:a {:href "#"} "file upload"]]]]]
    [:li
     [:details
      [:summary "Navigation"]
      [:ul
       [:li [:a {:href "#"} "tabs"]]
       [:li [:a {:href "#"} "breadcrumbs"]]]]]]])

(defn with-app-layout
  ([content]
   (with-app-layout {} content))
  ([{:keys [drawer-id nav-title sidebar main-attrs]
     :or {drawer-id "app-drawer"
          nav-title "Pomp"
          sidebar default-sidebar
          main-attrs {:class "min-h-[calc(100vh-4rem)] p-6"}}}
    content]
   [:html {:data-theme "light"}
    [:head
     [:link {:href "/assets/output.css"
             :rel "stylesheet"}]
     [:script {:type "module"
               :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"}]]
    [:body {:class "min-h-screen m-0 bg-base-200"}
     [:div {:class "drawer"}
      [:input {:id drawer-id
               :type "checkbox"
               :class "drawer-toggle"}]
      [:div {:class "drawer-content"}
       (navbar/navbar
        {:attrs {:class "bg-base-100 border-b border-base-300"}
         :left-group
         [:div {:class "flex items-center gap-2"}
            [:label {:for drawer-id
                     :class "btn btn-ghost btn-square drawer-button"
                     :aria-label "Open sidebar menu"}
             icons/menu-icon]
           [:a {:href "#"
                :class "btn btn-ghost text-xl"} nav-title]]
         :right-group (theme-picker/theme-picker {})})
       (into [:main main-attrs] [content])]
       [:div {:class "drawer-side"}
        [:label {:for drawer-id
                 :aria-label "Close sidebar menu"
                 :class "drawer-overlay"}]
        sidebar]]]]))
