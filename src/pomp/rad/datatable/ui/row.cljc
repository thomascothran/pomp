(ns pomp.rad.datatable.ui.row
  "Row rendering functions for the datatable.

   These are the default implementations that can be overridden
   by passing `:render-row` to `pomp.rad.datatable.core/render` or
   `pomp.datatable/make-handler`.

   Functions:
   - `render-row`           - Renders a complete table row (default for :render-row)
   - `render-cell`          - Renders a single data cell
   - `render-selection-cell` - Renders the selection checkbox cell

   Example custom render-row:

     (defn my-render-row [{:keys [cols row selectable? row-id table-id]}]
       [:tr.my-custom-class {:data-id row-id}
        (when selectable?
          (row/render-selection-cell
            {:signal-path (str \"datatable.\" table-id \".selections.\" row-id)}))
        (for [col cols]
          (row/render-cell {:value (get row (:key col)) :row row :col col}))])")

(defn- escape-js-string
  "Escapes a string for use in JavaScript."
  [s]
  (-> (str s)
      (clojure.string/replace "\\" "\\\\")
      (clojure.string/replace "'" "\\'")
      (clojure.string/replace "\n" "\\n")
      (clojure.string/replace "\r" "\\r")))

(defn render-boolean-cell
  "Renders a boolean cell as a toggle switch that auto-saves on change.
   
   Unlike other editable cells, booleans don't need pencil/save buttons -
   they show a toggle that saves immediately when clicked.
   
   ctx contains:
   - :value    - The boolean value
   - :row-id   - The row's unique identifier
   - :col      - The column definition
   - :table-id - Table element ID
   - :data-url - URL for save POST requests
   - :row-idx  - Row index (0-based, for cell selection)
   - :col-idx  - Column index (0-based, for cell selection)"
  [{:keys [value row-id col table-id data-url row-idx col-idx]}]
  (let [col-key (name (:key col))
        cell-key (str row-idx "-" col-idx)
        signal-base (str "datatable." table-id)
        cell-base (str signal-base ".cells")
        cell-signal-path (str cell-base "['" row-id "']['" col-key "']")
        init-cells (str "$" cell-base " ||= {}; $" cell-base "['" row-id "'] ||= {}; ")
        editing-signal (str signal-base ".editing")
        ;; Unique ID for the toggle (for patching after save)
        toggle-id (str "cell-" table-id "-" row-id "-" col-key)
        ;; On change: set editing signal, set cell value, and post immediately
        change-handler (str "evt.stopPropagation(); "
                            init-cells
                            "$" editing-signal " = {rowId: '" row-id "', colKey: '" col-key "'}; "
                            "$" cell-signal-path " = evt.target.checked; "
                            "@post('" data-url "?action=save')")]
    [:td {:data-row row-idx
          :data-col col-idx
          :data-value (str value)
          :data-class (str "{'bg-info/20': $" signal-base ".cellSelection['" cell-key "']}")
          :data-on:mousedown (str "if ($" editing-signal "?.rowId) return; "
                                  "$" signal-base ".cellSelectDragging = true; "
                                  "$" signal-base ".cellSelectStart = {row: " row-idx ", col: " col-idx "}; "
                                  "$" signal-base ".cellSelection = {'" cell-key "': true}")}
     [:input.toggle.toggle-xs.toggle-success
      {:id toggle-id
       :type "checkbox"
       :checked value
       :data-on:change change-handler}]]))

(defn render-editable-cell
  "Renders an editable data cell with inline editing support.

   Uses a pencil icon to enter edit mode and a checkmark icon to save,
   avoiding conflicts with cell selection (which uses click/drag).

   ctx contains:
   - :value    - The cell value to display
   - :row-id   - The row's unique identifier (for signal path)
   - :col      - The column definition {:key :label :editable :type :options :min :max ...}
   - :table-id - Table element ID (for signals)
   - :data-url - URL for save POST requests
   - :row-idx  - Row index (0-based, for cell selection)
   - :col-idx  - Column index (0-based, for cell selection)

   Type-aware inputs (based on :type in col):
   - :string/:text/nil - Text input (default)
   - :enum             - Select dropdown (requires :options list of strings)
   - :number           - Number input (optional :min/:max)
   - :boolean          - Toggle switch

   Signal paths used:
   - datatable.<table-id>.editing = {rowId: '...', colKey: '...'} - tracks which cell is being edited
   - datatable.<table-id>.cells.<row-id>.<col-key> - holds the current edit value
   - datatable.<table-id>.submitInProgress - prevents double-submit on blur after Enter"
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
        ;; Unique ID for this input (to focus it when entering edit mode)
        input-id (str "editInput-" row-id "-" col-key)
        ;; Unique ID for the display span (for patching after save)
        span-id (str "cell-" table-id "-" row-id "-" col-key)
        ;; Condition to check if this cell is being edited
        editing-check (str "$" editing-signal "?.rowId === '" row-id "' && $" editing-signal "?.colKey === '" col-key "'")
        ;; For booleans, we need to read the data-value and convert to boolean
        ;; For other types, we read it as a string
        read-current-value (if (= col-type :boolean)
                             (str "document.getElementById('" span-id "').dataset.value === 'true'")
                             (str "document.getElementById('" span-id "').dataset.value"))
        ;; Click pencil: clear cell selection, enter edit mode, set initial value from data-value, and focus input
        edit-handler (str "evt.stopPropagation(); "
                          "$" signal-base ".cellSelection = {}; "
                          "$" editing-signal " = {rowId: '" row-id "', colKey: '" col-key "'}; "
                          init-cells
                          "$" cell-signal-path " = " read-current-value "; "
                          "setTimeout(() => document.getElementById('" input-id "')?.focus(), 0)")
        ;; Save: set flag, post, clear editing
        save-handler (str "evt.stopPropagation(); "
                          "$" submit-flag " = true; "
                          "@post('" data-url "?action=save'); "
                          "$" editing-signal " = {rowId: null, colKey: null}")
        ;; Cancel: clear editing, remove cell value
        cancel-edit (str "$" editing-signal " = {rowId: null, colKey: null}; "
                         init-cells
                         "$" cell-signal-path " = null")
        ;; Blur: cancel edit unless submitInProgress (Enter/Escape already handled it)
        blur-handler (str "if ($" submit-flag ") { $" submit-flag " = false; return; } "
                          cancel-edit)
        ;; Keydown: handle Enter and Escape
        keydown-handler (str "if (evt.key === 'Enter') { evt.stopPropagation(); "
                             "$" submit-flag " = true; "
                             "@post('" data-url "?action=save'); "
                             "$" editing-signal " = {rowId: null, colKey: null} } "
                             "if (evt.key === 'Escape') { $" submit-flag " = true; " cancel-edit " }")
        input-handler (str init-cells "$" cell-signal-path " = evt.target.value")
        ;; Display value rendering based on type
        display-content (case col-type
                          :boolean (if value
                                     [:svg.w-4.h-4.text-success {:xmlns "http://www.w3.org/2000/svg"
                                                                 :fill "none"
                                                                 :viewBox "0 0 24 24"
                                                                 :stroke-width "2"
                                                                 :stroke "currentColor"}
                                      [:path {:stroke-linecap "round"
                                              :stroke-linejoin "round"
                                              :d "m4.5 12.75 6 6 9-13.5"}]]
                                     [:svg.w-4.h-4.text-base-content.opacity-30 {:xmlns "http://www.w3.org/2000/svg"
                                                                                 :fill "none"
                                                                                 :viewBox "0 0 24 24"
                                                                                 :stroke-width "2"
                                                                                 :stroke "currentColor"}
                                      [:path {:stroke-linecap "round"
                                              :stroke-linejoin "round"
                                              :d "M6 18 18 6M6 6l12 12"}]])
                          ;; Default: show the value as text
                          value)
        ;; Edit input rendering based on type
        edit-input (case col-type
                     :enum
                     [:select.select.select-xs.select-ghost.flex-1.min-w-0.bg-base-200
                      {:id input-id
                       :data-on:input input-handler
                       :data-on:keydown keydown-handler
                       :data-on:blur blur-handler}
                      (for [opt (:options col)]
                        [:option {:value opt} opt])]

                     :number
                     [:input.input.input-xs.input-ghost.flex-1.min-w-0.bg-base-200
                      (cond-> {:id input-id
                               :type "number"
                               :data-on:input input-handler
                               :data-on:keydown keydown-handler
                               :data-on:blur blur-handler}
                        (:min col) (assoc :min (:min col))
                        (:max col) (assoc :max (:max col)))]

                     :boolean
                     [:input.toggle.toggle-xs.toggle-success
                      {:id input-id
                       :type "checkbox"
                       :data-bind cell-signal-path
                       :data-on:keydown keydown-handler
                       :data-on:blur blur-handler}]

                     ;; Default: text input (for :string, :text, nil, or unknown)
                     [:input.input.input-xs.input-ghost.flex-1.min-w-0.bg-base-200
                      {:id input-id
                       :data-on:input input-handler
                       :data-on:keydown keydown-handler
                       :data-on:blur blur-handler}])]
    [:td {:data-row row-idx
          :data-col col-idx
          :data-value (str value)
          :data-class (str "{'bg-info/20': $" signal-base ".cellSelection['" cell-key "']}")
          :data-on:mousedown (str "if ($" editing-signal "?.rowId) return; "
                                  "$" signal-base ".cellSelectDragging = true; "
                                  "$" signal-base ".cellSelectStart = {row: " row-idx ", col: " col-idx "}; "
                                  "$" signal-base ".cellSelection = {'" cell-key "': true}")}
     ;; Use relative container so edit overlay doesn't cause width jump
     [:div.relative
      ;; Display mode: value + pencil button (always in flow, determines cell width)
      ;; Use visibility instead of display to preserve layout space
      [:div.flex.items-center.gap-1
       {:data-class (str "{'invisible': " editing-check "}")}
       ;; Span with data-value attribute for dynamic value reading on re-edit
       [:span.flex-1 {:id span-id :data-value (str value)} display-content]
       ;; Pencil button to edit
       [:button.btn.btn-ghost.btn-xs.p-0.opacity-30.hover:opacity-100
        {:data-on:click edit-handler
         :title "Edit"}
        [:svg.w-4.h-4 {:xmlns "http://www.w3.org/2000/svg"
                       :fill "none"
                       :viewBox "0 0 24 24"
                       :stroke-width "1.5"
                       :stroke "currentColor"}
         [:path {:stroke-linecap "round"
                 :stroke-linejoin "round"
                 :d "m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.832 19.82a4.5 4.5 0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 0 1 1.13-1.897L16.863 4.487Zm0 0L19.5 7.125"}]]]]
      ;; Edit mode: input + checkmark button (absolute overlay, doesn't affect layout)
      [:div.absolute.inset-0.flex.items-center.gap-1.bg-base-100
       {:data-show editing-check}
       edit-input
       ;; Checkmark button to save
       ;; Use mousedown (not click) so submitInProgress is set BEFORE blur fires
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
                 :d "m4.5 12.75 6 6 9-13.5"}]]]]]]))

(defn render-cell
  "Renders a single data cell.

   ctx contains:
   - :value    - The cell value (possibly transformed by :display-fn)
   - :raw-value - The raw cell value from the row (for data-value attribute)
   - :row      - The full row data
   - :col      - The column definition {:key :label :render :editable :type ...}
   - :row-idx  - Row index (0-based, for cell selection)
   - :col-idx  - Column index (0-based, for cell selection)
   - :table-id - Table element ID (for cell selection signals)
   - :row-id   - The row's unique identifier (required for editable cells)
   - :data-url - URL for save POST requests (required for editable cells)"
  [{:keys [value raw-value row col row-idx col-idx table-id row-id data-url] :as ctx}]
  (let [editable? (:editable col)
        col-type (:type col)]
    (cond
      ;; Editable boolean: use auto-save toggle
      (and editable? (= col-type :boolean))
      (render-boolean-cell ctx)

      ;; Other editable types: use pencil/save flow
      editable?
      (render-editable-cell ctx)

      ;; Non-editable: standard cell rendering
      :else
      (let [{:keys [render]} col
            cell-key (str row-idx "-" col-idx)
            ;; Use raw-value for data-value if provided, otherwise fall back to value
            data-val (if (some? raw-value) raw-value value)
            display-value (if render (render value row) value)]
        [:td {:data-row row-idx
              :data-col col-idx
              :data-value (str data-val)
              :data-class (str "{'bg-info/20': $datatable." table-id ".cellSelection['" cell-key "']}")
              :data-on:mousedown (str "if ($datatable." table-id ".editing?.rowId) return; "
                                      "$datatable." table-id ".cellSelectDragging = true; "
                                      "$datatable." table-id ".cellSelectStart = {row: " row-idx ", col: " col-idx "}; "
                                      "$datatable." table-id ".cellSelection = {'" cell-key "': true}")}
         display-value]))))

(defn render-selection-cell
  "Renders a selection checkbox cell.

   ctx contains:
   - :signal-path - The datastar signal path for this row's selection"
  [{:keys [signal-path]}]
  (let [[_ selections-path row-id]
        (or (re-matches #"^(.*\.selections)\.([^.]*)$" signal-path)
            (re-matches #"^(.*\.selections)\['(.+)'\]$" signal-path))
        change-handler (str "$" selections-path " ||= {}; "
                            "$" selections-path "['" row-id "'] = evt.target.checked")]
    [:td.w-3
     [:input.checkbox.checkbox-sm
      {:type "checkbox"
       :data-on:change change-handler}]]))

(defn render-row
  "Renders a data row with optional selection checkbox.

   ctx contains:
   - :cols        - Column definitions
   - :row         - The row data
   - :selectable? - Whether row selection is enabled
   - :row-id      - The row's unique identifier
   - :row-idx     - Row index (0-based, for cell selection)
   - :table-id    - The table's element ID
   - :grouped?    - Whether this row is inside a group (adds empty cell for indent)
   - :render-cell - Optional custom cell render function
   - :data-url    - URL for save POST requests (passed to editable cells)

   Column definitions can include:
   - :display-fn  - Function (fn [row] ...) to compute display value
                    Raw value from :key is still used for data-value attribute
   - :editable    - If true, cell can be edited inline"
  [{:keys [cols row selectable? row-id row-idx table-id grouped? data-url] :as ctx}]
  (let [signal-path (str "datatable." table-id ".selections." row-id)
        render-cell-fn (or (:render-cell ctx) render-cell)]
    [:tr
     (when selectable?
       (render-selection-cell {:signal-path signal-path}))
     (when grouped? [:td])
     (for [[col-idx col] (map-indexed vector cols)]
       (let [raw-value (get row (:key col))
             display-value (if-let [display-fn (:display-fn col)]
                             (display-fn row)
                             raw-value)]
         (render-cell-fn {:value display-value
                          :raw-value raw-value
                          :row row
                          :col col
                          :row-idx row-idx
                          :col-idx col-idx
                          :table-id table-id
                          :row-id row-id
                          :data-url data-url})))]))
