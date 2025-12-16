(ns pomp.rad.datatable.ui.column-menu
  (:require [pomp.rad.datatable.ui.primitives :as primitives]))

(defn render
  [{:keys [col-key col-label data-url table-id]}]
  (let [col-name (name col-key)
        popover-id (str "col-menu-" col-name)
        anchor-name (str "--col-menu-" col-name)
        hide-popover (str "document.getElementById('" popover-id "').hidePopover(); ")]
    (list
     [:button.btn.btn-ghost.btn-xs.px-1.opacity-50.hover:opacity-100
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      primitives/dots-icon]
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
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "@get('" data-url "?sortCol=" col-name "&sortDir=asc')")}
         primitives/arrow-up-icon "Sort ascending"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "@get('" data-url "?sortCol=" col-name "&sortDir=desc')")}
         primitives/arrow-down-icon "Sort descending"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "@get('" data-url "?groupBy=" col-name "')")}
         primitives/list-icon "Group by " col-label]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "$" table-id ".columns." col-name ".visible = false; @get('" data-url "')")}
         primitives/eye-slash-icon "Hide column"]]]])))

(defn render-group-column
  [{:keys [data-url]}]
  (let [popover-id "col-menu-group"
        anchor-name "--col-menu-group"]
    (list
     [:button.btn.btn-ghost.btn-xs.px-1.opacity-50.hover:opacity-100
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      primitives/dots-icon]
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
        [:a {:data-on:click (str "document.getElementById('" popover-id "').hidePopover(); @get('" data-url "?ungroup=true')")}
         "Remove grouping"]]]])))
