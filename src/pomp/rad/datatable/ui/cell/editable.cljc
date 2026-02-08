(ns pomp.rad.datatable.ui.cell.editable)

(defn cell-selection-class
  [signal-base cell-key]
  (str "{'bg-info/20': $" signal-base ".cellSelection && $" signal-base ".cellSelection.includes('" cell-key "')}"))

(defn editable-mousedown-handler
  [{:keys [signal-base editing-signal editing-check cell-base row-idx col-idx]}]
  (str "if (evt.target.closest('input, button, select, textarea')) return; "
       "if ($" editing-signal "?.rowId) { "
       "if (!(" editing-check ")) { "
       "const editingRow = $" editing-signal ".rowId; "
       "const editingCol = $" editing-signal ".colKey; "
       "$" editing-signal " = {rowId: null, colKey: null}; "
       "$" cell-base " ||= {}; "
       "if ($" cell-base "[editingRow]) { $" cell-base "[editingRow][editingCol] = null; } "
       "} else { return; } "
       "} "
       "$" signal-base "._cellSelectDragging = true; "
       "$" signal-base "._cellSelectStart = {row: " row-idx ", col: " col-idx "}; "))

(defn boolean-mousedown-handler
  [{:keys [signal-base editing-signal row-idx col-idx]}]
  (str "if (evt.target.closest('input, button, select, textarea')) return; "
       "if ($" editing-signal "?.rowId) return; "
       "$" signal-base "._cellSelectDragging = true; "
       "$" signal-base "._cellSelectStart = {row: " row-idx ", col: " col-idx "}; "))

(defn td-attrs
  [{:keys [row-idx col-idx value signal-base cell-key mousedown-handler]}]
  {:data-row row-idx
   :data-col col-idx
   :data-value (str value)
   :data-class (cell-selection-class signal-base cell-key)
   :data-on:mousedown mousedown-handler})

(defn editable-base
  [{:keys [value row-id col table-id data-url row-idx col-idx]}]
  (let [col-key (name (:key col))
        col-type (:type col)
        cell-key (str row-idx "-" col-idx)
        signal-base (str "datatable." table-id)
        cell-base (str signal-base ".cells")
        cell-signal-path (str cell-base "['" row-id "']['" col-key "']")
        init-cells (str "$" cell-base " ||= {}; $" cell-base "['" row-id "'] ||= {}; ")
        editing-signal (str signal-base ".editing")
        submit-flag (str signal-base ".submitInProgress")
        enum-blur-lock (str signal-base ".enumBlurLock")
        input-id (str "editInput-" row-id "-" col-key)
        span-id (str "cell-" table-id "-" row-id "-" col-key)
        editing-check (str "$" editing-signal "?.rowId === '" row-id "' && $" editing-signal "?.colKey === '" col-key "'")
        cancel-edit (str "$" editing-signal " = {rowId: null, colKey: null}; "
                         init-cells
                         "$" cell-signal-path " = null")
        save-handler (str "evt.stopPropagation(); "
                          "$" submit-flag " = true; "
                          "@post('" data-url "?action=save'); "
                          cancel-edit)
        blur-handler (str "if ($" submit-flag ") { $" submit-flag " = false; return; } "
                          cancel-edit)
        keydown-handler (str "if (evt.key === 'Enter') { evt.stopPropagation(); "
                             "$" submit-flag " = true; "
                             "@post('" data-url "?action=save'); "
                             cancel-edit " } "
                             "if (evt.key === 'Escape') { $" submit-flag " = true; " cancel-edit " }")
        enum-mousedown-handler (str "evt.stopPropagation(); $" enum-blur-lock " = Date.now()")
        enum-keydown-handler (str "if (evt.key === 'Escape' || evt.key === 'Esc') { evt.stopPropagation(); $" submit-flag " = true; " cancel-edit " } "
                                  "if (evt.key === 'ArrowDown' || evt.key === 'ArrowUp' || evt.key === 'Enter' || evt.key === ' ') { $" enum-blur-lock " = Date.now(); }")
        enum-blur-handler (str "setTimeout(() => { "
                               "const now = Date.now(); "
                               "if ($" enum-blur-lock " && (now - $" enum-blur-lock " < 200)) { return; } "
                               "if ($" editing-signal "?.rowId === '" row-id "' && $" editing-signal "?.colKey === '" col-key "') { "
                               cancel-edit
                               " } }, 0)")
        input-handler (str init-cells "$" cell-signal-path " = evt.target.value")
        enum-change-handler (str "evt.stopPropagation(); "
                                 "$" submit-flag " = false; "
                                 input-handler
                                 "; @post('" data-url "?action=save'); "
                                 cancel-edit)]
    {:value value
     :row-id row-id
     :col col
     :col-key col-key
     :col-type col-type
     :cell-key cell-key
     :signal-base signal-base
     :cell-base cell-base
     :cell-signal-path cell-signal-path
     :init-cells init-cells
     :editing-signal editing-signal
     :submit-flag submit-flag
     :enum-blur-lock enum-blur-lock
     :input-id input-id
     :span-id span-id
     :editing-check editing-check
     :cancel-edit cancel-edit
     :save-handler save-handler
     :blur-handler blur-handler
     :keydown-handler keydown-handler
     :enum-mousedown-handler enum-mousedown-handler
     :enum-keydown-handler enum-keydown-handler
     :enum-blur-handler enum-blur-handler
     :input-handler input-handler
     :enum-change-handler enum-change-handler
     :row-idx row-idx
     :col-idx col-idx}))

(defn edit-handler
  [{:keys [signal-base editing-signal row-id col-key init-cells cell-signal-path input-id]} read-current-value]
  (str "evt.stopPropagation(); "
       "$" signal-base ".cellSelection = []; " "$" signal-base ".cellSelection = null; "
       "$" editing-signal " = {rowId: '" row-id "', colKey: '" col-key "'}; "
       init-cells
       "const currentValue = " read-current-value "; "
       "$" cell-signal-path " = currentValue; "
       "const input = document.getElementById('" input-id "'); "
       "if (input) { if (input.type === 'checkbox') { input.checked = currentValue; } else { input.value = (currentValue ?? ''); } } "
       "setTimeout(() => document.getElementById('" input-id "')?.focus(), 0)"))

(defn render-editable-cell
  [{:keys [row-idx col-idx value signal-base cell-key editing-check span-id edit-handler display-content edit-input col-type save-handler mousedown-handler]}]
  [:td (td-attrs {:row-idx row-idx
                  :col-idx col-idx
                  :value value
                  :signal-base signal-base
                  :cell-key cell-key
                  :mousedown-handler mousedown-handler})
   [:div.relative
    [:div.flex.items-center.gap-1.group
     {:data-class (str "{'invisible': " editing-check "}")}
     [:span.flex-1 {:id span-id
                    :data-value (str value)
                    :data-pomp-editable-ready "true"
                    :data-on:dblclick edit-handler}
      display-content]
     [:span.pointer-events-none.opacity-0.group-hover:opacity-100.transition-opacity.duration-150
      [:svg.w-3.h-3 {:class "text-base-content/50"
                     :xmlns "http://www.w3.org/2000/svg"
                     :fill "none"
                     :viewBox "0 0 24 24"
                     :stroke-width "1.5"
                     :stroke "currentColor"}
       [:path {:stroke-linecap "round"
               :stroke-linejoin "round"
               :d "m16.862 3.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L10.582 15.07a4.5 4.5 0 0 1-1.897 1.13L6 17l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Zm0 0L19.5 6.125"}]]]]

    [:div.absolute.inset-0.flex.items-center.gap-1.bg-base-100
     {:data-show editing-check}
     edit-input
     (when (not= col-type :enum)
       [:button.btn.btn-ghost.btn-xs.p-0
        {:data-on:mousedown save-handler
         :title "Save"}
        [:svg.w-4.h-4.text-success {:xmlns "http://www.w3.org/2000/svg"
                                    :fill "none"
                                    :viewBox "0 0 24 24"
                                    :stroke-width "1.5"
                                    :stroke "currentColor"}
         [:path {:stroke-linecap "round"
                 :stroke-linejoin "round"
                 :d "m4.5 12.75 6 6 9-13.5"}]]])]]])
