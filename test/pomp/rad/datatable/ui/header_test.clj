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
