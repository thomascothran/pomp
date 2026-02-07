(ns pomp.rad.datatable.ui.cell.text
  (:require [pomp.rad.datatable.ui.cell.editable :as editable]))

(defn render-editable-cell
  [ctx]
  (let [base (editable/editable-base ctx)
        read-current-value (str "document.getElementById('" (:span-id base) "').dataset.value")
        edit-handler (editable/edit-handler base read-current-value)
        edit-input [:input.input.input-xs.input-ghost.flex-1.min-w-0.bg-base-200
                    {:id (:input-id base)
                     :data-on:input (:input-handler base)
                     :data-on:keydown (:keydown-handler base)
                     :data-on:blur (:blur-handler base)}]
        mousedown-handler (editable/editable-mousedown-handler base)]
    (editable/render-editable-cell (assoc base
                                        :edit-handler edit-handler
                                        :display-content (:value base)
                                        :edit-input edit-input
                                        :mousedown-handler mousedown-handler))))