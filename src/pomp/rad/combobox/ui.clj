(ns pomp.rad.combobox.ui
  (:require [clojure.data.json :as json]))

(defn default-render-input
  [{:keys [ids signal-paths signal-refs data-url min-chars debounce-key]}]
  (let [query-path (:query signal-paths)
        results-open-path (:results-open signal-paths)
        loading-options-path (:loading-options signal-paths)
        query-ref (:query signal-refs)
        query-alt-ref (:query-alt signal-refs)
        selected-value-ref (:selected-value signal-refs)
        selected-label-ref (:selected-label signal-refs)
        results-open-ref (:results-open signal-refs)
        indicator-key (str "data-indicator:" loading-options-path)
        bind-key (str "data-bind:" query-path)
        query-setter (str query-ref " = _q; "
                          (when query-alt-ref
                            (str query-alt-ref " = _q; ")))]
    [:div {:class "relative"}
     [:input (cond-> {:id (:input ids)
                      :type "text"
                      :class "input input-bordered w-full pr-10"
                      :placeholder "Type to search..."
                      :autocomplete "off"
                      :role "combobox"
                      :aria-autocomplete "list"
                      :aria-haspopup "listbox"
                      :aria-controls (:listbox ids)
                      :data-combobox-query-path query-path
                      :data-combobox-results-open-path results-open-path
                      :data-combobox-loading-options-path loading-options-path
                      :data-on:click "if (evt.target && evt.target.select) { evt.target.select(); }"
                      :data-on:input (str results-open-ref " = ((((evt.target && evt.target.value) || '')).trim().length >= " min-chars ");")
                      :data-on:keydown (str "if (evt.key === 'Enter') { evt.preventDefault(); const _raw = ((((evt.target && evt.target.value) || ''))).trim(); "
                                            "const _selected = (" selected-label-ref " || ''); "
                                            "const _q = (_selected && _raw.startsWith(_selected) && _raw.length > _selected.length) ? _raw.slice(_selected.length) : _raw; "
                                            "if (evt.target) { evt.target.value = _q; } "
                                            query-setter
                                            selected-value-ref " = _q; "
                                            selected-label-ref " = _q; "
                                            results-open-ref " = false; } "
                                            "if (evt.key === 'Escape') { " results-open-ref " = false; }")
                      :data-on:blur (str "setTimeout(() => { " results-open-ref " = false; }, 120);")
                      :data-attr:aria-expanded (str results-open-ref " ? 'true' : 'false'")}
               true
               (assoc bind-key true)
               true
               (assoc debounce-key (str "@get('" data-url "')"))
               true
               (assoc indicator-key true))]
     [:div {:class "pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3"
            :data-show (:loading-options signal-refs)}
      [:span {:class "loading loading-spinner loading-sm"}]]]))

(defn item-click-expression
  [{:keys [signal-refs]} {:keys [label value]}]
  (str (:query signal-refs) " = " (json/write-str label) "; "
       (when-let [query-alt-ref (:query-alt signal-refs)]
         (str query-alt-ref " = " (json/write-str label) "; "))
       (:selected-value signal-refs) " = " (json/write-str value) "; "
       (:selected-label signal-refs) " = " (json/write-str label) "; "
       (:results-open signal-refs) " = false;"))

(defn default-render-results
  [{:keys [ids signal-refs items]}]
  [:div {:id (:panel ids)
         :class "absolute left-0 top-full z-20 w-full"
         :data-show (:results-open signal-refs)}
   [:div {:class "mt-2 rounded-box border border-base-300 bg-base-100 p-1 shadow-lg"}
    [:ul {:id (:listbox ids)
          :class "menu w-full"
          :role "listbox"
          :aria-live "polite"}
     (for [item items]
       [:li {:key (str (:label item) "-" (:value item))}
        [:button {:type "button"
                  :class "btn btn-ghost justify-start"
                  :role "option"
                  :data-on:click (item-click-expression {:signal-refs signal-refs} item)}
         (:label item)]])]]])
