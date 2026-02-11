(ns pomp.rad.datatable.ui.cell.boolean
  (:require [pomp.icons :as icons]
            [pomp.rad.datatable.ui.cell.editable :as editable]))

(defn render-editable-cell
  [ctx]
  (let [base (editable/editable-base ctx)
        read-current-value (str "document.getElementById('" (:span-id base) "').dataset.value === 'true'")
        edit-handler (editable/edit-handler base read-current-value)
        change-handler (str (:init-cells base)
                            "$" (:cell-signal-path base) " = evt.target.checked")
        display-content (if (:value base)
                          icons/boolean-true-icon
                          icons/boolean-false-icon)
        edit-input [:input.toggle.toggle-xs.toggle-success
                    {:id (:input-id base)
                     :type "checkbox"
                     :data-on:change change-handler
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
        editing-signal (str signal-base "._editing")
        init-editing (str "$" editing-signal " ||= {}; $" editing-signal "['" row-id "'] ||= {}; ")
        in-flight-check (str "$" editing-signal "?.['" row-id "']?.['" col-key "'] === 'in-flight'")
        toggle-id (str "cell-" table-id "-" row-id "-" col-key)
        click-handler "evt.preventDefault(); evt.stopPropagation();"
        dblclick-handler (str "evt.preventDefault(); evt.stopPropagation(); "
                               init-cells
                               init-editing
                               "$" editing-signal "['" row-id "']['" col-key "'] = 'in-flight'; "
                               "const nextChecked = !evt.target.checked; "
                               "evt.target.checked = nextChecked; "
                               "$" cell-signal-path " = nextChecked; "
                               "@post('" data-url "?action=save');")
        mousedown-handler (editable/boolean-mousedown-handler {:signal-base signal-base
                                                              :editing-signal editing-signal
                                                              :row-idx row-idx
                                                             :col-idx col-idx})]
    [:td (editable/td-attrs {:row-idx row-idx
                           :col-idx col-idx
                            :value value
                            :signal-base signal-base
                            :cell-key cell-key
                            :mousedown-handler mousedown-handler
                            :in-flight-check in-flight-check})
     [:input.toggle.toggle-xs.toggle-success
      {:id toggle-id
        :type "checkbox"
        :checked value
        :data-on:click click-handler
        :data-on:dblclick dblclick-handler}]]))
