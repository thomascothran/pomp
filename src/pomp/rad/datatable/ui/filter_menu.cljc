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

(def default-filter-operations
  "Default filter operations by column type.
   Each type maps to a vector of {:value :label} operation specs."
  {:string [{:value "contains" :label "contains"}
            {:value "equals" :label "equals"}
            {:value "starts-with" :label "starts with"}
            {:value "ends-with" :label "ends with"}
            {:value "is-empty" :label "is empty"}
            {:value "is-not-empty" :label "is not empty"}
            {:value "is-any-of" :label "is any of"}]
   :number [{:value "equals" :label "equals"}
            {:value "not-equals" :label "not equals"}
            {:value "greater-than" :label "greater than"}
            {:value "greater-than-or-equal" :label "greater than or equal"}
            {:value "less-than" :label "less than"}
            {:value "less-than-or-equal" :label "less than or equal"}
            {:value "is-empty" :label "is empty"}
            {:value "is-not-empty" :label "is not empty"}]
   :boolean [{:value "is" :label "is"}
             {:value "is-not" :label "is not"}
             {:value "is-empty" :label "is empty"}
             {:value "is-not-empty" :label "is not empty"}]
   :date [{:value "is" :label "is"}
          {:value "is-not" :label "is not"}
          {:value "after" :label "after"}
          {:value "on-or-after" :label "on or after"}
          {:value "before" :label "before"}
          {:value "on-or-before" :label "on or before"}
          {:value "is-empty" :label "is empty"}
          {:value "is-not-empty" :label "is not empty"}]
   :enum [{:value "is" :label "is"}
          {:value "is-not" :label "is not"}
          {:value "is-any-of" :label "is any of"}
          {:value "is-empty" :label "is empty"}
          {:value "is-not-empty" :label "is not empty"}]})

(defn- humanize
  "Converts kebab-case string to human-readable label.
   e.g., \"starts-with\" -> \"starts with\""
  [s]
  (str/replace s "-" " "))

(defn normalize-operations
  "Converts operation specs to canonical {:value :label} format.
   Accepts a vector of strings or maps (or mixed).
   Strings are converted to {:value s :label (humanize s)}.
   Maps are passed through unchanged."
  [ops]
  (mapv (fn [op]
          (if (string? op)
            {:value op :label (humanize op)}
            op))
        (or ops [])))

(defn operations-for-column
  "Returns the filter operations for a column based on precedence:
   1. col-filter-ops (column-level override) if provided
   2. table-filter-ops for col-type (table-level override) if provided
   3. default-filter-operations for col-type
   4. Falls back to :string operations for unknown types.
   
   All results are normalized to [{:value :label} ...] format."
  [col-type col-filter-ops table-filter-ops]
  (let [;; Normalize :text to :string
        normalized-type (if (= col-type :text) :string col-type)
        ;; Determine which ops to use based on precedence
        ops (cond
              ;; Column-level override takes highest precedence
              (seq col-filter-ops)
              col-filter-ops
              ;; Table-level override for this type
              (seq (get table-filter-ops normalized-type))
              (get table-filter-ops normalized-type)
              ;; Default operations for this type
              (get default-filter-operations normalized-type)
              (get default-filter-operations normalized-type)
              ;; Fallback to string operations for unknown types
              :else
              (get default-filter-operations :string))]
    (normalize-operations ops)))

(defn render
  [{:keys [col-key col-label col-type col-filter-ops table-filter-ops
           current-filter-op current-filter-value table-id data-url]}]
  (let [col-name (name col-key)
        popover-id (str "filter-" col-name)
        anchor-name (str "--filter-" col-name)
        ;; Convert col-type keyword to string for signal
        filter-type-str (name (or col-type :string))
        ;; Get operations for this column based on type and any overrides
        ops (operations-for-column (or col-type :string) col-filter-ops table-filter-ops)
        current-op (or current-filter-op (:value (first ops)))
        current-label (or (->> ops
                               (filter #(= (:value %) current-op))
                               first
                               :label)
                          (:label (first ops)))
        has-filter? (or (not (str/blank? current-filter-value))
                        (= current-op "is-empty")
                        (= current-op "is-not-empty"))
        ;; Signal path for this column's filters
        signal-path (str "$datatable." table-id ".filters." col-name)]
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
             signal-path " = [{type: '" filter-type-str "', op: evt.target.elements['filterOp'].value, value: evt.target.elements['filterVal'].value}]; "
              "@post('" data-url "')")}
       [:div.text-sm.font-semibold (str "Filter " col-label)]
       [:input {:type "hidden" :name "filterOp" :value current-op}]
       [:details.dropdown.w-full
        [:summary.select.select-sm.w-full.flex.items-center
         {:style {:padding-right "2rem"}}
         [:span current-label]]
        [:ul.dropdown-content.menu.bg-base-100.rounded-field.w-full.shadow-lg.mt-1.text-xs.py-1
         {:style {:position "absolute" :z-index 9999}}
         (for [{:keys [value label]} ops]
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
                              signal-path " = null; "
                               "@post('" data-url "')")}
         "Clear"]
        [:button.btn.btn-sm.btn-primary.flex-1
         {:type "button"
          :data-on:click (str "document.getElementById('" popover-id "').hidePopover(); "
                              signal-path " = [{type: '" filter-type-str "', op: evt.target.closest('form').elements['filterOp'].value, value: encodeURIComponent(evt.target.closest('form').elements['filterVal'].value)}]; "
                               "@post('" data-url "')")}
         "Apply"]]]])))


