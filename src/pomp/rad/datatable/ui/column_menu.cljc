(ns pomp.rad.datatable.ui.column-menu
  (:require [pomp.rad.datatable.ui.primitives :as primitives]))

(defn- group-action-handler
  [hide-popover data-url action]
  (str hide-popover "@post('" data-url "?" action "')"))

(defn render
  [{:keys [col-key col-label data-url table-id groupable? group-by]}]
  (let [col-name (name col-key)
        popover-id (str "col-menu-" col-name)
        anchor-name (str "--col-menu-" col-name)
        hide-popover (str "document.getElementById('" popover-id "').hidePopover(); ")
        grouped? (seq group-by)
        group-col-key (first group-by)
        sort-disabled? (and grouped? (not= col-key group-col-key))]
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
        [:a.flex.items-center.gap-2
         (cond-> {}
           (not sort-disabled?)
           (assoc :data-on:click (str hide-popover "@post('" data-url "?sortCol=" col-name "&sortDir=asc')"))
           sort-disabled?
           (assoc :aria-disabled "true" :class "opacity-50 pointer-events-none"))
         primitives/arrow-up-icon "Sort ascending"]]
       [:li
        [:a.flex.items-center.gap-2
         (cond-> {}
           (not sort-disabled?)
           (assoc :data-on:click (str hide-popover "@post('" data-url "?sortCol=" col-name "&sortDir=desc')"))
           sort-disabled?
           (assoc :aria-disabled "true" :class "opacity-50 pointer-events-none"))
         primitives/arrow-down-icon "Sort descending"]]
       (when groupable?
         [:li
          [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "@post('" data-url "?groupBy=" col-name "')")}
           primitives/list-icon "Group by " col-label]])
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "$datatable." table-id ".columns." col-name ".visible = false; @post('" data-url "')")}
         primitives/eye-slash-icon "Hide column"]]]])))

(defn render-group-column
  [{:keys [data-url group-col-key]}]
  (let [popover-id "col-menu-group"
        anchor-name "--col-menu-group"
        hide-popover (str "document.getElementById('" popover-id "').hidePopover(); ")
        group-col-name (when group-col-key (name group-col-key))
        sort-enabled? (some? group-col-name)]
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
        [:a.flex.items-center.gap-2
         (cond-> {}
           sort-enabled?
           (assoc :data-on:click (str hide-popover "@post('" data-url "?sortCol=" group-col-name "&sortDir=asc')"))
           (not sort-enabled?)
           (assoc :aria-disabled "true" :class "opacity-50 pointer-events-none"))
         primitives/arrow-up-icon "Sort ascending"]]
       [:li
        [:a.flex.items-center.gap-2
         (cond-> {}
           sort-enabled?
           (assoc :data-on:click (str hide-popover "@post('" data-url "?sortCol=" group-col-name "&sortDir=desc')"))
           (not sort-enabled?)
           (assoc :aria-disabled "true" :class "opacity-50 pointer-events-none"))
         primitives/arrow-down-icon "Sort descending"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (group-action-handler hide-popover data-url "ungroup=true")}
         "Ungroup"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (group-action-handler hide-popover data-url "clearGroups=true")}
         "Clear all groups"]]]])))
