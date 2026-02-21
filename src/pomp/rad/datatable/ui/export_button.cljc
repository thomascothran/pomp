(ns pomp.rad.datatable.ui.export-button
  (:require [pomp.rad.datatable.ui.primitives :as primitives]))

(defn render
  [{:keys [table-id data-url]}]
  [:button.btn.btn-ghost.btn-sm.px-2
   {:type "button"
    :title "Export CSV"
    :aria-label "Export CSV"
    :data-on:click (str "@get('" data-url "?action=export')")}
   primitives/export-icon])
