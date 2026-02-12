(ns pomp.rad.datatable.ui.table-search)

(defn render-table-search
  [{:keys [data-url table-id global-table-search]}]
  (let [signal-path (str "datatable." table-id ".globalTableSearch")
        signal-ref (str "$" signal-path)
        bind-key (keyword (str "data-bind:" signal-path))]
    [:input.input.input-sm.w-full.max-w-xs
     (cond-> {:type "search"
              :placeholder "Search table..."
              :autocomplete "off"
              :value (or global-table-search "")
              :data-on:input (str "const _q = ((evt.target && evt.target.value) || '').trim(); "
                                  signal-ref " = (_q.length >= 2 ? _q : '');")}
       true (assoc bind-key true)
       true (assoc :data-on:input__debounce.300ms
                   (str "@post('" data-url "?action=global-search')")))]))
