(ns pomp.rad.datatable.ui.cell.enum
  (:require [pomp.rad.datatable.ui.cell.editable :as editable]))

(defn render-editable-cell
  [ctx]
  (let [base (editable/editable-base ctx)
        col (:col base)
        read-current-value (str "document.getElementById('" (:span-id base) "').dataset.value")
        edit-handler (editable/edit-handler base read-current-value)
        edit-input [:select.select.select-xs.select-ghost.flex-1.min-w-0.bg-base-200
                    {:id (:input-id base)
                     :data-on:mousedown (:enum-mousedown-handler base)
                     :data-on:change (:enum-change-handler base)
                     :data-on:keydown (:enum-keydown-handler base)
                     :data-on:blur (:enum-blur-handler base)}
                    (for [opt (:options col)]
                      [:option {:value opt} opt])]
        mousedown-handler (editable/editable-mousedown-handler base)]
    (editable/render-editable-cell (assoc base
                                        :edit-handler edit-handler
                                        :display-content (:value base)
                                        :edit-input edit-input
                                        :mousedown-handler mousedown-handler))))