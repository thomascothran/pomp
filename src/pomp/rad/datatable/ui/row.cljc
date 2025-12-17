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

(defn render-cell
  "Renders a single data cell.

   ctx contains:
   - :value    - The cell value
   - :row      - The full row data
   - :col      - The column definition {:key :label :render ...}
   - :row-idx  - Row index (0-based, for cell selection)
   - :col-idx  - Column index (0-based, for cell selection)
   - :table-id - Table element ID (for cell selection signals)"
  [{:keys [value row col row-idx col-idx table-id]}]
  (let [{:keys [render]} col
        cell-key (str row-idx "-" col-idx)
        display-value (if render (render value row) value)]
    [:td {:data-row row-idx
          :data-col col-idx
          :data-value (str value)
          :data-class (str "{'bg-info/20': $datatable." table-id ".cellSelection['" cell-key "']}")
          :data-on:mousedown (str "$datatable." table-id ".cellSelectDragging = true; "
                                  "$datatable." table-id ".cellSelectStart = {row: " row-idx ", col: " col-idx "}; "
                                  "$datatable." table-id ".cellSelection = {'" cell-key "': true}")}
     display-value]))

(defn render-selection-cell
  "Renders a selection checkbox cell.

   ctx contains:
   - :signal-path - The datastar signal path for this row's selection"
  [{:keys [signal-path]}]
  [:td.w-3
   [:input.checkbox.checkbox-sm
    {:type "checkbox"
     :data-signals (str "{\"" signal-path "\": false}")
     :data-bind signal-path}]])

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
   - :render-cell - Optional custom cell render function"
  [{:keys [cols row selectable? row-id row-idx table-id grouped?] :as ctx}]
  (let [signal-path (str "datatable." table-id ".selections." row-id)
        render-cell-fn (or (:render-cell ctx) render-cell)]
    [:tr
     (when selectable?
       (render-selection-cell {:signal-path signal-path}))
     (when grouped? [:td])
     (for [[col-idx col] (map-indexed vector cols)]
       (render-cell-fn {:value (get row (:key col))
                        :row row
                        :col col
                        :row-idx row-idx
                        :col-idx col-idx
                        :table-id table-id}))]))
