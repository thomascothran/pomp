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
          (row/render-cell {:value (get row (:key col)) :row row :col col}))])"
  (:require [pomp.rad.datatable.ui.cell.boolean :as cell-boolean]
            [pomp.rad.datatable.ui.cell.editable :as cell-editable]
            [pomp.rad.datatable.ui.cell.enum :as cell-enum]
            [pomp.rad.datatable.ui.cell.number :as cell-number]
            [pomp.rad.datatable.ui.cell.text :as cell-text]))

(defn render-boolean-cell
  "Renders a boolean cell as a toggle switch that saves on double-click.

   Unlike other editable cells, booleans don't use pencil/save buttons -
   they stay as visible toggles and save when double-clicked.

   ctx contains:
   - :value    - The boolean value
   - :row-id   - The row's unique identifier
   - :col      - The column definition
   - :table-id - Table element ID
   - :data-url - URL for save POST requests
   - :row-idx  - Row index (0-based, for cell selection)
   - :col-idx  - Column index (0-based, for cell selection)"
  [{:keys [value row-id col table-id data-url row-idx col-idx] :as ctx}]
  (cell-boolean/render-boolean-cell ctx))

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
   - datatable.<table-id>._editing.<row-id>.<col-key> = 'active' | 'in-flight' - tracks per-cell edit state
   - datatable.<table-id>.cells.<row-id>.<col-key> - holds the current edit value
   - datatable.<table-id>.submitInProgress - prevents double-submit on blur after Enter"
  [{:keys [value row-id col table-id data-url row-idx col-idx] :as ctx}]
  (case (:type col)
    :enum (cell-enum/render-editable-cell ctx)
    :number (cell-number/render-editable-cell ctx)
    :boolean (cell-boolean/render-editable-cell ctx)
    (cell-text/render-editable-cell ctx)))

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
      ;; Editable boolean: keep toggle UI, but require double-click to save
      (and editable? (= col-type :boolean))
      (render-boolean-cell ctx)

      ;; Other editable types: use shared double-click edit flow
      editable?
      (render-editable-cell ctx)

      ;; Non-editable: standard cell rendering
      :else
      (let [{:keys [render]} col
            col-key (name (:key col))
            cell-key (str row-idx "-" col-idx)
            signal-base (str "datatable." table-id)
            cell-base (str signal-base ".cells")
            editing-signal (str signal-base "._editing")
            editing-check (str "$" editing-signal "?.['" row-id "']?.['" col-key "'] === 'active'"
                               " || $" editing-signal "?.['" row-id "']?.['" col-key "'] === 'in-flight'")
            ;; Use raw-value for data-value if provided, otherwise fall back to value
            data-val (if (some? raw-value) raw-value value)
            display-value (if render (render value row) value)]
        (let [mousedown-handler (cell-editable/editable-mousedown-handler {:signal-base signal-base
                                                                          :editing-signal editing-signal
                                                                          :editing-check editing-check
                                                                          :cell-base cell-base
                                                                          :row-idx row-idx
                                                                          :col-idx col-idx})]
          [:td (cell-editable/td-attrs {:row-idx row-idx
                                      :col-idx col-idx
                                      :value data-val
                                      :signal-base signal-base
                                      :cell-key cell-key
                                      :mousedown-handler mousedown-handler})
           display-value])))))

(defn render-selection-cell
  "Renders a selection checkbox cell.

   ctx contains:
   - :signal-path - The datastar signal path for this row's selection"
  [{:keys [signal-path]}]
  (let [[_ selections-path row-id]
        (or (re-matches #"^(.*\.selections)\.([^.]*)$" signal-path)
             (re-matches #"^(.*\.selections)\['(.+)'\]$" signal-path))
        bind-path (str selections-path "." row-id)
        change-handler (str "$" selections-path " ||= {}; "
                            "$" selections-path "['" row-id "'] = evt.target.checked")]
    [:td.w-3
     [:input.checkbox.checkbox-sm
      {:type "checkbox"
       :data-bind bind-path
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
