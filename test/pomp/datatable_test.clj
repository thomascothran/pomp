(ns pomp.datatable-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.core :as dt]))

;; =============================================================================
;; Test helpers
;; =============================================================================

(defn- find-operation-labels
  "Extracts all operation labels from rendered hiccup.
   Looks for [:a.py-1 ...] elements inside filter menu dropdowns."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :a.py-1 (first hiccup)))
    [(last hiccup)]

    (vector? hiccup)
    (mapcat find-operation-labels hiccup)

    (seq? hiccup)
    (mapcat find-operation-labels hiccup)

    :else
    []))

(defn- find-operations-for-column
  "Finds operation labels for a specific column in rendered hiccup.
   Returns set of label strings."
  [hiccup col-key]
  (let [col-name (name col-key)
        popover-id (str "filter-" col-name)]
    (letfn [(find-menu [h]
              (cond
                ;; Found the filter menu div for our column
                (and (vector? h)
                     (= :div.bg-base-100.shadow-lg.rounded-box.p-4.w-64 (first h))
                     (map? (second h))
                     (= popover-id (:id (second h))))
                (set (find-operation-labels h))

                (vector? h)
                (some find-menu h)

                (seq? h)
                (some find-menu h)

                :else
                nil))]
      (find-menu hiccup))))

;; =============================================================================
;; dt/render tests - filter-operations passthrough
;; This tests the public API that make-handler uses internally
;; =============================================================================

(deftest dt-render-accepts-filter-operations-test
  (testing "dt/render passes :filter-operations to table, affecting filter menu operations"
    (let [cols [{:key :name :label "Name" :type :string}
                {:key :status :label "Status" :type :enum}]
          filter-ops {:string [{:value "custom-str" :label "Custom String Op"}]
                      :enum [{:value "custom-enum" :label "Custom Enum Op"}]}
          rendered (dt/render {:id "test-table"
                               :cols cols
                               :rows []
                               :sort-state []
                               :filters {}
                               :total-rows 0
                               :page-size 10
                               :page-current 0
                               :page-sizes [10 25]
                               :data-url "/data"
                               :filter-operations filter-ops})
          name-ops (find-operations-for-column rendered :name)
          status-ops (find-operations-for-column rendered :status)]

      ;; String column should use custom operations
      (is (contains? name-ops "Custom String Op")
          "String column should have custom filter operation")
      (is (not (contains? name-ops "contains"))
          "String column should NOT have default 'contains' when overridden")

      ;; Enum column should use custom operations  
      (is (contains? status-ops "Custom Enum Op")
          "Enum column should have custom filter operation")
      (is (not (contains? status-ops "is any of"))
          "Enum column should NOT have default 'is any of' when overridden")))

  (testing "columns use type-appropriate defaults when no filter-operations provided"
    (let [cols [{:key :name :label "Name" :type :string}
                {:key :active :label "Active" :type :boolean}]
          rendered (dt/render {:id "test-table"
                               :cols cols
                               :rows []
                               :sort-state []
                               :filters {}
                               :total-rows 0
                               :page-size 10
                               :page-current 0
                               :page-sizes [10 25]
                               :data-url "/data"})
          name-ops (find-operations-for-column rendered :name)
          active-ops (find-operations-for-column rendered :active)]

      ;; String column gets string defaults
      (is (contains? name-ops "contains")
          "String column should have default 'contains' operation")

      ;; Boolean column gets boolean defaults
      (is (contains? active-ops "is")
          "Boolean column should have 'is' operation")
      (is (not (contains? active-ops "contains"))
          "Boolean column should NOT have 'contains' operation"))))

;; =============================================================================
;; make-handler tests - verifies :filter-operations is accepted as a parameter
;; =============================================================================

(deftest make-handler-accepts-filter-operations-test
  (testing "make-handler accepts :filter-operations without error"
    (let [columns [{:key :name :label "Name" :type :string}]
          filter-ops {:string [{:value "custom" :label "Custom Op"}]}
          handler (datatable/make-handler {:id "test-table"
                                           :columns columns
                                           :query-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                           :data-url "/data"
                                           :render-html-fn (fn [_] "<html>")
                                           :filter-operations filter-ops})]
      ;; Handler should be created successfully
      (is (fn? handler)
          "make-handler should return a function when :filter-operations is provided"))))
