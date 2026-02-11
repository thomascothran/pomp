(ns pomp.rad.datatable.ui.cell.editable
  (:require [pomp.icons :as icons]))

(defn cell-selection-class
  [signal-base cell-key in-flight-check]
  (str "{'bg-info/20': $" signal-base ".cellSelection && $" signal-base ".cellSelection.includes('" cell-key "')"
       (when in-flight-check
         (str ", 'bg-warning/20': " in-flight-check))
       "}"))

(defn editable-mousedown-handler
  [{:keys [signal-base row-idx col-idx]}]
  (let [cell-key (str row-idx "-" col-idx)]
    (str "window.pompCellMouseDown(evt, $" signal-base ", {"
         "cellSelectionKey: '" cell-key "', "
         "row: " row-idx ", col: " col-idx
         "});")))

(defn td-attrs
  [{:keys [row-idx col-idx value signal-base cell-key mousedown-handler in-flight-check]}]
  {:data-row row-idx
   :data-col col-idx
   :data-value (str value)
   :data-class (cell-selection-class signal-base cell-key in-flight-check)
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
        editing-signal (str signal-base "._editing")
        init-editing (str "$" editing-signal " ||= {}; $" editing-signal "['" row-id "'] ||= {}; ")
        submit-flag (str signal-base ".submitInProgress")
        enum-blur-lock (str signal-base ".enumBlurLock")
        input-id (str "editInput-" row-id "-" col-key)
        span-id (str "cell-" table-id "-" row-id "-" col-key)
        editing-check (str "$" editing-signal "?.['" row-id "']?.['" col-key "'] === 'active'")
        in-flight-check (str "$" editing-signal "?.['" row-id "']?.['" col-key "'] === 'in-flight'")
        cancel-edit (str init-editing
                         "$" editing-signal "['" row-id "']['" col-key "'] = false; "
                         init-cells
                         "$" cell-signal-path " = null")
        optimistic-display-update (str "const displayEl = document.getElementById('" span-id "'); "
                                       "const nextValue = $" cell-signal-path "; "
                                       "if (displayEl) { "
                                       "displayEl.dataset.value = String(nextValue ?? ''); "
                                       "displayEl.textContent = String(nextValue ?? ''); "
                                       "} ")
        save-handler (str "evt.stopPropagation(); "
                          init-editing
                          "$" editing-signal "['" row-id "']['" col-key "'] = 'in-flight'; "
                          optimistic-display-update
                          "$" submit-flag " = true; "
                          "@post('" data-url "?action=save');")
        blur-handler (str "if ($" submit-flag ") { $" submit-flag " = false; return; } "
                          cancel-edit)
        keydown-handler (str "if (evt.key === 'Enter') { evt.stopPropagation(); "
                             init-editing
                             "$" editing-signal "['" row-id "']['" col-key "'] = 'in-flight'; "
                             optimistic-display-update
                             "$" submit-flag " = true; "
                             "@post('" data-url "?action=save'); } "
                             "if (evt.key === 'Escape') { $" submit-flag " = true; " cancel-edit " }")
        enum-mousedown-handler (str "evt.stopPropagation(); $" enum-blur-lock " = Date.now()")
        enum-keydown-handler (str "if (evt.key === 'Escape' || evt.key === 'Esc') { evt.stopPropagation(); $" submit-flag " = true; " cancel-edit " } "
                                  "if (evt.key === 'ArrowDown' || evt.key === 'ArrowUp' || evt.key === 'Enter' || evt.key === ' ') { $" enum-blur-lock " = Date.now(); }")
        enum-blur-handler (str "setTimeout(() => { "
                               "const now = Date.now(); "
                               "if ($" enum-blur-lock " && (now - $" enum-blur-lock " < 200)) { return; } "
                               "if (" editing-check ") { "
                               cancel-edit
                               " } }, 0)")
        input-handler (str init-cells "$" cell-signal-path " = evt.target.value")
        enum-change-handler (str "evt.stopPropagation(); "
                                 input-handler
                                 "; "
                                 init-editing
                                 "$" editing-signal "['" row-id "']['" col-key "'] = 'in-flight'; "
                                 optimistic-display-update
                                 "@post('" data-url "?action=save');")]
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
     :init-editing init-editing
     :editing-signal editing-signal
     :submit-flag submit-flag
     :enum-blur-lock enum-blur-lock
     :input-id input-id
     :span-id span-id
     :editing-check editing-check
     :in-flight-check in-flight-check
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
  [{:keys [signal-base editing-signal row-id col-key init-cells init-editing cell-signal-path input-id]} read-current-value]
  (str "evt.stopPropagation(); "
       "$" signal-base ".cellSelection = []; " "$" signal-base ".cellSelection = null; "
       init-editing
       "$" editing-signal "['" row-id "']['" col-key "'] = 'active'; "
       init-cells
       "const currentValue = " read-current-value "; "
       "$" cell-signal-path " = currentValue; "
       "const input = document.getElementById('" input-id "'); "
       "if (input) { if (input.type === 'checkbox') { input.checked = currentValue; } else { input.value = (currentValue ?? ''); } } "
       "setTimeout(() => document.getElementById('" input-id "')?.focus(), 0)"))

(defn render-editable-cell
  [{:keys [row-idx col-idx value signal-base cell-key editing-check in-flight-check span-id edit-handler display-content edit-input col-type save-handler mousedown-handler]}]
  [:td (td-attrs {:row-idx row-idx
                  :col-idx col-idx
                  :value value
                  :signal-base signal-base
                  :cell-key cell-key
                  :mousedown-handler mousedown-handler
                  :in-flight-check in-flight-check})
   [:div.relative
    [:div.flex.items-center.gap-1.group
     {:data-class (str "{'invisible': " editing-check "}")}
     [:span.flex-1 {:id span-id
                    :data-value (str value)
                    :data-pomp-editable-ready "true"
                    :data-on:dblclick edit-handler}
      display-content]
     [:span.pointer-events-none.opacity-0.group-hover:opacity-100.transition-opacity.duration-150
      icons/edit-pencil-icon]]

    [:div.absolute.inset-0.flex.items-center.gap-1.bg-base-100
     {:data-show editing-check}
     edit-input
     (when (not= col-type :enum)
       [:button.btn.btn-ghost.btn-xs.p-0
        {:data-on:mousedown save-handler
         :title "Save"}
        icons/save-icon])]]])
