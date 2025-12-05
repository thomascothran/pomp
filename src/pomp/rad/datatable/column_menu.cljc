(ns pomp.rad.datatable.column-menu)

(def dots-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M12 6.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5ZM12 12.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5ZM12 18.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5Z"}]])

(def arrow-up-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M4.5 10.5 12 3m0 0 7.5 7.5M12 3v18"}]])

(def arrow-down-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3"}]])

(def list-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0ZM3.75 12h.007v.008H3.75V12Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm-.375 5.25h.007v.008H3.75v-.008Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z"}]])

(defn render
  [{:keys [col-key col-label data-url]}]
  (let [col-name (name col-key)
        popover-id (str "col-menu-" col-name)
        anchor-name (str "--col-menu-" col-name)]
    (list
     [:button.btn.btn-ghost.btn-xs.px-1.opacity-50.hover:opacity-100
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      dots-icon]
     [:div.dropdown-content.bg-base-100.rounded-box.shadow-lg.p-2
      {:popover "auto"
       :id popover-id
       :style {:position-anchor anchor-name
               :position "absolute"
               :top "anchor(bottom)"
               :left "anchor(right)"
               :translate "-100% 0"
               :font-weight "normal"}}
      [:ul.menu.menu-sm.w-44
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str "@get('" data-url "?sortCol=" col-name "&sortDir=asc')")}
         arrow-up-icon "Sort ascending"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str "@get('" data-url "?sortCol=" col-name "&sortDir=desc')")}
         arrow-down-icon "Sort descending"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str "@get('" data-url "?groupBy=" col-name "')")}
         list-icon "Group by " col-label]]]])))

(defn render-group-column
  [{:keys [data-url]}]
  (let [popover-id "col-menu-group"
        anchor-name "--col-menu-group"]
    (list
     [:button.btn.btn-ghost.btn-xs.px-1.opacity-50.hover:opacity-100
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      dots-icon]
     [:div.dropdown-content.bg-base-100.rounded-box.shadow-lg.p-2
      {:popover "auto"
       :id popover-id
       :style {:position-anchor anchor-name
               :position "absolute"
               :top "anchor(bottom)"
               :left "anchor(right)"
               :translate "-100% 0"
               :font-weight "normal"}}
      [:ul.menu.menu-sm.w-44
       [:li
        [:a {:data-on:click (str "@get('" data-url "?ungroup=true')")}
         "Remove grouping"]]]])))
