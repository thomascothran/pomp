(ns pomp.rad.datatable.filter-menu
  (:require [clojure.string :as str]))

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
  (let [removed-keys (clojure.set/difference (set (keys old-signals)) (set (keys new-signals)))]
    (merge new-signals (into {} (map (fn [k] [k nil]) removed-keys)))))

(defn render
  [{:keys [col-key col-label current-filter-op current-filter-value col-idx total-cols data-url]}]
  (let [col-name (name col-key)
        current-op (or current-filter-op "contains")
        has-filter? (or (not (str/blank? current-filter-value)) (= current-op "is-empty"))
        use-dropdown-end? (>= col-idx (/ total-cols 2))]
    [:div {:class (str "dropdown" (when use-dropdown-end? " dropdown-end"))}
     [:div.btn.btn-ghost.btn-xs.px-1
      {:tabindex "0"
       :role "button"
       :class (if has-filter? "text-primary" "opacity-50 hover:opacity-100")}
      funnel-icon]
     [:div.dropdown-content.z-50.bg-base-100.shadow-lg.rounded-box.p-4.w-64
      {:tabindex "0"}
      [:form.flex.flex-col.gap-3
       {:data-on:submit__prevent
        (str "@get('" data-url "?filterCol=" col-name
             "&filterOp=' + evt.target.elements['filterOp'].value + "
             "'&filterVal=' + evt.target.elements['filterVal'].value)")}
       [:div.text-sm.font-semibold (str "Filter " col-label)]
       [:select.select.select-sm.select-bordered.w-full
        {:name "filterOp"}
        (for [{:keys [value label]} filter-operations]
          [:option {:value value :selected (= current-op value)} label])]
       [:input.input.input-sm.input-bordered.w-full
        {:type "text"
         :name "filterVal"
         :placeholder "Value..."
         :value (or current-filter-value "")}]
       [:button.btn.btn-sm.btn-primary.w-full {:type "submit"} "Apply"]]]]))
