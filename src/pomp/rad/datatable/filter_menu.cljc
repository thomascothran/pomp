(ns pomp.rad.datatable.filter-menu
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(def funnel-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "1.5"
         :stroke "currentColor"
         :class "w-4 h-4"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 0 1-.659 1.591l-5.432 5.432a2.25 2.25 0 0 0-.659 1.591v2.927a2.25 2.25 0 0 1-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 0 0-.659-1.591L3.659 7.409A2.25 2.25 0 0 1 3 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0 1 12 3Z"}]])

(def filter-operations
  [{:value "contains" :label "contains"}
   {:value "not-contains" :label "does not contain"}
   {:value "equals" :label "equals"}
   {:value "not-equals" :label "does not equal"}
   {:value "starts-with" :label "starts with"}
   {:value "ends-with" :label "ends with"}
   {:value "is-empty" :label "is empty"}])

(defn next-state
  [signals query-params]
  (let [{:keys [filter-col filter-op filter-val clear-filters?]}
        {:filter-col (get query-params "filterCol")
         :filter-op (get query-params "filterOp")
         :filter-val (get query-params "filterVal")
         :clear-filters? (some? (get query-params "clearFilters"))}]
    (cond
      clear-filters? {}
      (nil? filter-col) signals
      (and (str/blank? filter-val) (not= filter-op "is-empty")) (dissoc signals (keyword filter-col))
      :else (assoc signals (keyword filter-col) {:type "text" :op (or filter-op "contains") :value filter-val}))))

(defn compute-patch
  [old-signals new-signals]
  (let [removed-keys (set/difference (set (keys old-signals)) (set (keys new-signals)))]
    (merge new-signals (into {} (map (fn [k] [k nil]) removed-keys)))))

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
      funnel-icon]
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
                              "@get('" data-url "?filterCol=" col-name "&filterVal=')")}
         "Clear"]
        [:button.btn.btn-sm.btn-primary.flex-1
         {:type "button"
          :data-on:click (str "document.getElementById('" popover-id "').hidePopover(); "
                              "@get('" data-url "?filterCol=" col-name
                              "&filterOp=' + evt.target.closest('form').elements['filterOp'].value + "
                              "'&filterVal=' + encodeURIComponent(evt.target.closest('form').elements['filterVal'].value))")}
         "Apply"]]]])))
