(ns pomp.rad.datatable.ui.header-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.ui.header :as header]))

;; =============================================================================
;; Helper to extract filter-menu render calls from header output
;; =============================================================================

(defn- find-filter-menu-calls
  "Walks the hiccup tree looking for filter-menu render output.
   Filter menus are identified by their popover id pattern: 'filter-{col-name}'
   Returns a vector of maps with :col-key extracted from the popover id."
  [hiccup]
  (cond
    ;; Found a filter button - extract col-key from popovertarget
    (and (vector? hiccup)
         (= :button.btn.btn-ghost.btn-xs.px-1 (first hiccup))
         (map? (second hiccup))
         (some-> (second hiccup) :popovertarget (clojure.string/starts-with? "filter-")))
    (let [popover-id (-> hiccup second :popovertarget)
          col-name (subs popover-id 7)] ;; remove "filter-" prefix
      [{:col-key (keyword col-name)}])

    (vector? hiccup)
    (mapcat find-filter-menu-calls hiccup)

    (seq? hiccup)
    (mapcat find-filter-menu-calls hiccup)

    :else
    []))

(defn- find-operation-labels-in-header
  "Extracts all operation labels from rendered header hiccup.
   Looks for [:a.py-1 ...] elements inside the dropdown menus."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :a.py-1 (first hiccup)))
    [(last hiccup)]

    (vector? hiccup)
    (mapcat find-operation-labels-in-header hiccup)

    (seq? hiccup)
    (mapcat find-operation-labels-in-header hiccup)

    :else
    []))

(defn- find-operations-for-column
  "Finds operation labels for a specific column in rendered header.
   Returns set of label strings."
  [hiccup col-key]
  (let [col-name (name col-key)
        popover-id (str "filter-" col-name)]
    ;; Walk hiccup to find the filter menu div for this column
    (letfn [(find-menu [h]
              (cond
                ;; Found the filter menu div for our column
                (and (vector? h)
                     (= :div.bg-base-100.shadow-lg.rounded-box.p-4.w-64 (first h))
                     (map? (second h))
                     (= popover-id (:id (second h))))
                (set (find-operation-labels-in-header h))

                (vector? h)
                (some find-menu h)

                (seq? h)
                (some find-menu h)

                :else
                nil))]
      (find-menu hiccup))))

;; =============================================================================
;; render-sortable tests - type-aware filter operations
;; Verifies that render-sortable passes column type and filter-operations
;; to filter-menu/render so each column gets appropriate operations.
;; =============================================================================

(deftest render-sortable-passes-col-type-test
  (testing "passes column :type to filter-menu, resulting in type-specific operations"
    (let [cols [{:key :name :label "Name" :type :string}
                {:key :active :label "Active" :type :boolean}
                {:key :created :label "Created" :type :date}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"})
          ;; Get operations rendered for each column
          name-ops (find-operations-for-column result :name)
          active-ops (find-operations-for-column result :active)
          created-ops (find-operations-for-column result :created)]

      ;; String column should have string operations
      (is (contains? name-ops "contains")
          "String column should have 'contains' operation")
      (is (contains? name-ops "starts with")
          "String column should have 'starts with' operation")

      ;; Boolean column should have boolean operations, not string
      (is (contains? active-ops "is")
          "Boolean column should have 'is' operation")
      (is (contains? active-ops "is not")
          "Boolean column should have 'is not' operation")
      (is (not (contains? active-ops "contains"))
          "Boolean column should NOT have 'contains' operation")

      ;; Date column should have date operations
      (is (contains? created-ops "after")
          "Date column should have 'after' operation")
      (is (contains? created-ops "before")
          "Date column should have 'before' operation"))))

(deftest render-sortable-passes-filter-operations-test
  (testing "passes table-level :filter-operations to filter-menu"
    (let [cols [{:key :name :label "Name" :type :string}]
          table-filter-ops {:string [{:value "custom-op" :label "Custom String Op"}]}
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"
                                          :filter-operations table-filter-ops})
          name-ops (find-operations-for-column result :name)]

      ;; Should use the table-level override
      (is (contains? name-ops "Custom String Op")
          "Should render table-level custom operation")
      (is (not (contains? name-ops "contains"))
          "Should NOT render default 'contains' when overridden")))

  (testing "passes column-level :filter-operations to filter-menu"
    (let [cols [{:key :status :label "Status" :type :enum
                 :filter-operations [{:value "col-op" :label "Column Op"}]}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"})
          status-ops (find-operations-for-column result :status)]

      ;; Should use the column-level override
      (is (contains? status-ops "Column Op")
          "Should render column-level custom operation")
      (is (not (contains? status-ops "is any of"))
          "Should NOT render default enum 'is any of' when overridden"))))

(defn- find-filter-apply-button-onclick
  "Finds the Apply button onclick handler for a specific column in rendered header.
   Returns the onclick string."
  [hiccup col-key]
  (let [col-name (name col-key)
        popover-id (str "filter-" col-name)]
    (letfn [(find-button [h]
              (cond
                ;; Found the Apply button
                (and (vector? h)
                     (= :button.btn.btn-sm.btn-primary.flex-1 (first h))
                     (map? (second h))
                     (= "Apply" (last h)))
                (:data-on:click (second h))

                (vector? h)
                (some find-button h)

                (seq? h)
                (some find-button h)

                :else
                nil))]
      ;; First find the right filter menu by id, then find the button
       (letfn [(find-menu [h]
                 (cond
                   (and (vector? h)
                        (= :div.bg-base-100.shadow-lg.rounded-box.p-4.w-64 (first h))
                        (map? (second h))
                        (= popover-id (:id (second h))))
                   (find-button h)

                  (vector? h)
                  (some find-menu h)

                  (seq? h)
                  (some find-menu h)

                   :else
                   nil))]
         (find-menu hiccup)))))

(defn- find-grouped-header-label
  "Finds the synthetic grouped header label text in rendered header hiccup."
  [hiccup]
  (letfn [(find-label [h]
            (cond
              (and (vector? h)
                   (= :button.flex.items-center.gap-1.hover:text-primary.transition-colors (first h)))
              (some (fn [child]
                      (when (and (vector? child)
                                 (= :span.font-semibold (first child)))
                        (last child)))
                    h)

              (vector? h)
              (some find-label h)

              (seq? h)
              (some find-label h)

               :else
               nil))]
    (find-label hiccup)))

(defn- popovertargets-in-node
  "Returns every popovertarget value found in a hiccup node."
  [node]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          current (when-let [target (:popovertarget attrs)] [target])]
      (into (vec (or current []))
            (mapcat popovertargets-in-node node)))

    (seq? node)
    (mapcat popovertargets-in-node node)

    :else
    []))

(defn- find-first-node
  "Returns the first hiccup node that satisfies predicate."
  [pred node]
  (cond
    (and (vector? node) (pred node))
    node

    (vector? node)
    (some #(find-first-node pred %) node)

    (seq? node)
    (some #(find-first-node pred %) node)

    :else
    nil))

(defn- find-header-select-all-click-handler
  "Finds the header select-all checkbox click handler string."
  [hiccup]
  (let [node (find-first-node
              (fn [h]
                (and (vector? h)
                     (keyword? (first h))
                     (clojure.string/starts-with? (name (first h)) "input")
                     (map? (second h))
                     (= "checkbox" (-> h second :type))
                     (contains? (second h) :data-on:click)))
              hiccup)]
    (some-> node second :data-on:click)))

(deftest render-sortable-passes-table-id-to-filter-menu-test
  (testing "passes table-id to filter-menu for signal updates"
    (let [cols [{:key :name :label "Name" :type :string}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "philosophers"})
           onclick (find-filter-apply-button-onclick result :name)]
       ;; The Apply button should update the signal with the table-id in the path
       (is (clojure.string/includes? onclick "$datatable.philosophers.filters.name")
           "Apply button should reference the correct signal path with table-id"))))

(deftest render-sortable-grouped-header-shows-grouped-column-label-test
  (testing "grouped synthetic header shows grouped column label"
    (let [cols [{:key :school :label "School" :type :string}
                 {:key :name :label "Name" :type :string}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"
                                          :group-by [:school]})
           grouped-label (find-grouped-header-label result)]
       (is (= "School" grouped-label)
           "Grouped header should show grouped column label, not a generic label"))))

(deftest render-sortable-grouped-header-shows-group-sequence-label-test
  (testing "grouped synthetic header shows ordered multi-group label"
    (let [cols [{:key :century :label "Century" :type :string}
                {:key :school :label "School" :type :string}
                {:key :name :label "Name" :type :string}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"
                                          :group-by [:century :school]})
          grouped-label (find-grouped-header-label result)]
      (is (= "Grouped by: Century > School"
             grouped-label)
          "Grouped header should show ordered grouping sequence"))))

(deftest render-sortable-grouped-header-excludes-grouped-column-filter-test
  (testing "grouped synthetic header does not include filter control"
    (let [cols [{:key :school :label "School" :type :string}
                {:key :name :label "Name" :type :string}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"
                                          :group-by [:school]})
          grouped-header-th (find-first-node
                             (fn [node]
                               (and (= :th (first node))
                                    (contains? (set (popovertargets-in-node node)) "col-menu-group")))
                             result)
          grouped-popovertargets (set (popovertargets-in-node grouped-header-th))]
      (is (some? grouped-header-th)
          "Expected grouped synthetic header cell to be present")
      (is (not (contains? grouped-popovertargets "filter-school"))
          "Grouped synthetic header should not expose grouped column filter popover"))))

(deftest render-sortable-grouped-header-keeps-grouped-column-menu-test
  (testing "grouped mode keeps grouped column as a regular visible/filterable header"
    (let [cols [{:key :school :label "School" :type :string}
                {:key :name :label "Name" :type :string}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"
                                          :group-by [:school]})
          popovertargets (set (popovertargets-in-node result))]
      (is (contains? popovertargets "col-menu-group")
          "Expected grouped mode to render synthetic grouped column menu")
      (is (contains? popovertargets "col-menu-school")
          "Expected grouped mode to keep grouped column menu visible")
      (is (contains? popovertargets "filter-school")
          "Expected grouped mode to keep grouped column filter visible"))))

(deftest render-sortable-multi-group-shows-independent-grouped-column-filters-test
  (testing "multi-group mode keeps each grouped column visible with independent filter affordances"
    (let [cols [{:key :school :label "School" :type :string}
                {:key :region :label "Region" :type :string}
                {:key :name :label "Name" :type :string}]
          result (header/render-sortable {:cols cols
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "test"
                                          :group-by [:school :region]})
          popovertargets (set (popovertargets-in-node result))]
      (is (contains? popovertargets "col-menu-school")
          "Expected School grouped column menu to remain visible")
      (is (contains? popovertargets "col-menu-region")
          "Expected Region grouped column menu to remain visible")
      (is (contains? popovertargets "filter-school")
          "Expected School grouped column filter to remain visible")
      (is (contains? popovertargets "filter-region")
          "Expected Region grouped column filter to remain visible"))))

(deftest render-sortable-select-all-handler-is-signal-driven-test
  (testing "header select-all writes known row selection signals without DOM querying"
    (let [result (header/render-sortable {:cols [{:key :name :label "Name" :type :string}]
                                          :sort-state []
                                          :filters {}
                                          :data-url "/data"
                                          :table-id "people"
                                          :selectable? true
                                          :visible-row-ids ["row-1" "row-2"]})
          click-handler (find-header-select-all-click-handler result)]
      (is (some? click-handler)
          "Expected selectable headers to emit a click handler")
      (is (not (clojure.string/includes? click-handler "document.querySelectorAll"))
          "Header select-all should not query DOM checkboxes")
      (is (not (clojure.string/includes? click-handler "dispatchEvent"))
          "Header select-all should not dispatch synthetic checkbox events")
      (is (clojure.string/includes? click-handler "$datatable.people.selections['row-1'] = evt.target.checked")
          "Header select-all should set row-1 selection signal directly")
      (is (clojure.string/includes? click-handler "$datatable.people.selections['row-2'] = evt.target.checked")
          "Header select-all should set row-2 selection signal directly"))))
