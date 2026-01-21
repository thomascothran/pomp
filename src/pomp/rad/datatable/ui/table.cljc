(ns pomp.rad.datatable.ui.table
  (:require [pomp.rad.datatable.ui.header :as header]
            [pomp.rad.datatable.ui.body :as body]
            [pomp.rad.datatable.ui.pagination :as pagination]))

(defn render
  "Renders a complete datatable with header, body, and pagination.

   (render opts) => hiccup

   Required opts:
   - :id          - Table element ID
   - :cols        - Column definitions [{:key :name :label \"Name\" :type :string} ...]
   - :rows        - Data rows to display
   - :data-url    - URL for data fetches

   Optional opts:
   - :groups       - Grouped row data (from group-rows)
   - :sort-state   - Current sort state [{:column \"name\" :direction \"asc\"}]
   - :filters      - Current filter state {:col-key [{:type \"string\" :op \"contains\" :value \"...\"}]}
   - :group-by     - Columns to group by [:col-key]
   - :total-rows   - Total row count for pagination
   - :page-size    - Current page size
   - :page-current - Current page (0-indexed)
   - :page-sizes   - Available page sizes [10 25 100]
   - :selectable?  - Enable row selection
   - :row-id-fn    - Function to get row ID (default: :id)
   - :toolbar      - Toolbar hiccup to render above table

   Render overrides:
   - :render-cell   - Custom cell render function. Receives:
                      {:value any :row map :col map}
                      Used by the default render-row. If :render-row is also provided,
                      :render-cell is passed in the context but the custom render-row
                      must explicitly use it.
                      See pomp.rad.datatable.ui.row/render-cell for default.

   - :render-row    - Custom row render function. Receives:
                      {:cols [...] :row {...} :selectable? bool :row-id id
                       :table-id str :grouped? bool :render-cell fn-or-nil}
                      See pomp.rad.datatable.ui.row/render-row for default.

   - :render-header - Custom header render function. Receives:
                      {:cols [...] :sort-state [...] :filters {...} :data-url str
                       :selectable? bool :table-id str :group-by [...]}
                      See pomp.rad.datatable.ui.header/render-sortable for default."
  [{:keys [id cols rows groups sort-state filters group-by total-rows page-size page-current page-sizes data-url selectable? row-id-fn toolbar render-row render-header render-cell filter-operations]}]
  (let [header-ctx {:cols cols
                    :sort-state sort-state
                    :filters filters
                    :data-url data-url
                    :selectable? selectable?
                    :table-id id
                    :group-by group-by
                    :filter-operations filter-operations}
        render-header-fn (or render-header header/render-sortable)]
    [:div {:id id}
     (when toolbar
       [:div.flex.items-center.px-2.py-1.border-b.border-base-300.bg-base-200
        {:style {:justify-content "flex-end"}}
        toolbar])
     [:div.overflow-x-auto
      [:table.table.table-sm
       {:data-class (str "{'select-none': $datatable." id ".cellSelectDragging}")
        :data-on:mousemove (str "pompCellSelectMove(evt, '" id "', "
                                "$datatable." id ".cellSelectDragging, "
                                "$datatable." id ".cellSelectStart)")
        :data-on:pompcellselection (str "$datatable." id ".cellSelection = evt.detail.selection")
        :data-on:mouseup__window (str "$datatable." id ".cellSelectDragging = false")
        :data-on:keydown__window (str "if (evt.key === 'Escape') { $datatable." id ".cellSelection = {} } "
                                      "else { pompCellSelectCopy(evt, '" id "', $datatable." id ".cellSelection) }")}
       (render-header-fn header-ctx)
       (body/render {:cols cols
                     :rows rows
                     :groups groups
                     :selectable? selectable?
                     :row-id-fn row-id-fn
                     :table-id id
                     :data-url data-url
                     :render-row render-row
                     :render-cell render-cell})]]
     (pagination/render {:total-rows total-rows
                         :page-size page-size
                         :page-current page-current
                         :filters filters
                         :page-sizes page-sizes
                         :data-url data-url})]))

(defn render-skeleton
  "Renders a loading skeleton for the datatable.

   (render-skeleton opts) => hiccup

   opts:
   - :id          - Table element ID
   - :cols        - Column definitions
   - :n           - Number of skeleton rows
   - :selectable? - Show selection column"
  [{:keys [id cols n selectable?]}]
  [:div {:id id}
   [:div.overflow-x-auto
    [:table.table.table-sm
     (header/render-simple {:cols cols :selectable? selectable? :table-id id})
     (body/render-skeleton {:cols cols :n n :selectable? selectable?})]]])
