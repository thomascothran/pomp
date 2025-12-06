(ns pomp.rad.datatable.columns-menu)

(def columns-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M9 4.5v15m6-15v15m-10.875 0h15.75c.621 0 1.125-.504 1.125-1.125V5.625c0-.621-.504-1.125-1.125-1.125H4.125C3.504 4.5 3 5.004 3 5.625v12.75c0 .621.504 1.125 1.125 1.125Z"}]])

(defn render
  [{:keys [cols columns-state table-id data-url]}]
  (let [popover-id "columns-menu"
        anchor-name "--columns-menu"]
    [:div.ml-auto
     [:button.btn.btn-ghost.btn-sm.px-2
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      columns-icon]
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
               signal-path (str table-id ".columns." col-name ".visible")
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
        {:data-on:click (str "document.getElementById('" popover-id "').hidePopover(); @get('" data-url "')")}
        "Apply"]]]]))
