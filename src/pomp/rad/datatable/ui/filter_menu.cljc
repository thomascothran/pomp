(ns pomp.rad.datatable.ui.filter-menu
  (:require [clojure.string :as str]
            [pomp.rad.datatable.ui.primitives :as primitives]))

(def filter-operations
  [{:value "contains" :label "contains"}
   {:value "not-contains" :label "does not contain"}
   {:value "equals" :label "equals"}
   {:value "not-equals" :label "does not equal"}
   {:value "starts-with" :label "starts with"}
   {:value "ends-with" :label "ends with"}
   {:value "is-empty" :label "is empty"}])

(defn render
  [{:keys [col-key col-label current-filter-op current-filter-value data-url]}]
  (let [col-name (name col-key)
        popover-id (str "filter-" col-name)
        anchor-name (str "--filter-" col-name)
        current-op (or current-filter-op "contains")
        current-label (or (->> filter-operations
                               (filter #(= (:value %) current-op))
                               first
                               :label)
                          "contains")
        has-filter? (or (not (str/blank? current-filter-value)) (= current-op "is-empty"))]
    (list
     [:button.btn.btn-ghost.btn-xs.px-1
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}
       :class (if has-filter? "text-primary" "opacity-50 hover:opacity-100")}
      primitives/funnel-icon]
     [:div.bg-base-100.shadow-lg.rounded-box.p-4.w-64
      {:popover "auto"
       :id popover-id
       :style {:position-anchor anchor-name
               :position "absolute"
               :top "anchor(bottom)"
               :left "anchor(right)"
               :translate "-100% 0"
               :overflow "visible"}}
      [:form.flex.flex-col.gap-3
       {:data-on:submit__prevent
        (str "document.getElementById('" popover-id "').hidePopover(); "
             "@get('" data-url "?filterCol=" col-name
             "&filterOp=' + evt.target.elements['filterOp'].value + "
             "'&filterVal=' + evt.target.elements['filterVal'].value)")}
       [:div.text-sm.font-semibold (str "Filter " col-label)]
       [:input {:type "hidden" :name "filterOp" :value current-op}]
       [:details.dropdown.w-full
        [:summary.select.select-sm.w-full.flex.items-center
         {:style {:padding-right "2rem"}}
         [:span current-label]]
        [:ul.dropdown-content.menu.bg-base-100.rounded-field.w-full.shadow-lg.mt-1.text-xs.py-1
         {:style {:position "absolute" :z-index 9999}}
         (for [{:keys [value label]} filter-operations]
           [:li {:data-on:click (str "evt.target.closest('form').elements['filterOp'].value = '" value "'; "
                                     "evt.target.closest('details').querySelector('summary span').textContent = '" label "'; "
                                     "evt.target.closest('details').removeAttribute('open')")}
            [:a.py-1 {:class (when (= current-op value) "active")} label]])]]
       [:input.input.input-sm.w-full
        {:type "text"
         :name "filterVal"
         :placeholder "Value..."
         :value (or current-filter-value "")}]
       [:div.flex.gap-2
        [:button.btn.btn-sm.btn-ghost.flex-1
         {:type "button"
          :disabled (not has-filter?)
          :data-on:click (str "document.getElementById('" popover-id "').hidePopover(); "
                              "@get('" data-url "?filterCol=" col-name "&clearColFilters=1')")}
         "Clear"]
        [:button.btn.btn-sm.btn-primary.flex-1
         {:type "button"
          :data-on:click (str "document.getElementById('" popover-id "').hidePopover(); "
                              "@get('" data-url "?filterCol=" col-name
                              "&filterOp=' + evt.target.closest('form').elements['filterOp'].value + "
                              "'&filterVal=' + encodeURIComponent(evt.target.closest('form').elements['filterVal'].value))")}
         "Apply"]]]])))
