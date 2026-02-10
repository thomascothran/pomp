(ns pomp.rad.datatable.ui.cell.boolean
  (:require [pomp.icons :as icons]
            [pomp.rad.datatable.ui.cell.editable :as editable]))

(defn render-editable-cell
  [ctx]
  (let [base (editable/editable-base ctx)
        read-current-value (str "document.getElementById('" (:span-id base) "').dataset.value === 'true'")
        edit-handler (editable/edit-handler base read-current-value)
        display-content (if (:value base)
                          icons/boolean-true-icon
                          icons/boolean-false-icon)
        edit-input [:input.toggle.toggle-xs.toggle-success
                    {:id (:input-id base)
                     :type "checkbox"
                     :data-on:keydown (:keydown-handler base)
                     :data-on:blur (:blur-handler base)}]
        mousedown-handler (editable/editable-mousedown-handler base)]
    (editable/render-editable-cell (assoc base
                                        :edit-handler edit-handler
                                        :display-content display-content
                                        :edit-input edit-input
                                        :mousedown-handler mousedown-handler))))

(defn render-boolean-cell
  [{:keys [value row-id col table-id data-url row-idx col-idx] :as ctx}]
  (let [col-key (name (:key col))
        cell-key (str row-idx "-" col-idx)
        signal-base (str "datatable." table-id)
        cell-base (str signal-base ".cells")
        cell-signal-path (str cell-base "['" row-id "']['" col-key "']")
        init-cells (str "$" cell-base " ||= {}; $" cell-base "['" row-id "'] ||= {}; ")
        editing-signal (str signal-base ".editing")
        cancel-edit (str "$" editing-signal " = {rowId: null, colKey: null}; "
                         init-cells
                         "$" cell-signal-path " = null")
        toggle-id (str "cell-" table-id "-" row-id "-" col-key)
        change-handler (str "evt.stopPropagation(); "
                            init-cells
                            "$" editing-signal " = {rowId: '" row-id "', colKey: '" col-key "'}; "
                            "$" cell-signal-path " = evt.target.checked; "
                            "@post('" data-url "?action=save'); "
                            cancel-edit)
        mousedown-handler (editable/boolean-mousedown-handler {:signal-base signal-base
                                                            :editing-signal editing-signal
                                                            :row-idx row-idx
                                                            :col-idx col-idx})]
    [:td (editable/td-attrs {:row-idx row-idx
                           :col-idx col-idx
                           :value value
                           :signal-base signal-base
                           :cell-key cell-key
                           :mousedown-handler mousedown-handler})
     [:input.toggle.toggle-xs.toggle-success
      {:id toggle-id
       :type "checkbox"
       :checked value
       :data-on:change change-handler}]]))
