(ns pomp.rad.datatable.ui.export-button)

(defn render
  [{:keys [table-id data-url]}]
  [:button.btn.btn-ghost.btn-sm.px-2
   {:type "button"
    :data-on:click (str "@get('" data-url "?action=export')")}
   "Export CSV"])
