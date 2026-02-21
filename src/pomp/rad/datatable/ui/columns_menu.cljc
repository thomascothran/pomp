(ns pomp.rad.datatable.ui.columns-menu
  (:require [pomp.rad.datatable.ui.primitives :as primitives]))

(defn render
  [{:keys [cols columns-state table-id data-url]}]
  (let [popover-id "columns-menu"
        anchor-name "--columns-menu"]
    [:div
     [:button.btn.btn-ghost.btn-sm.px-2
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      primitives/columns-icon]
     [:div.bg-base-100.shadow-lg.rounded-box
      {:popover "auto"
       :id popover-id
       :style {:position-anchor anchor-name
               :position "absolute"
               :top "anchor(bottom)"
               :left "anchor(right)"
               :translate "-100% 0"}}
      [:ul.menu.menu-sm.w-48
       (for [{:keys [key label]} cols]
         (let [col-name (name key)
               signal-path (str "datatable." table-id ".columns." col-name ".visible")
               visible? (get-in columns-state [(keyword col-name) :visible] true)]
           [:li
            [:label.flex.items-center.gap-2
             [:input.checkbox.checkbox-xs
              {:type "checkbox"
               :checked visible?
               :data-bind signal-path}]
             label]]))]
      [:div.p-2.pt-0
        [:button.btn.btn-sm.btn-primary.w-full
         {:data-on:click (str "document.getElementById('" popover-id "').hidePopover(); @post('" data-url "')")}
         "Apply"]]]]))
