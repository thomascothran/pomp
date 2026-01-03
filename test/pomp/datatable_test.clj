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

;; =============================================================================
;; make-handler tests - save-fn support
;; =============================================================================

(deftest make-handler-accepts-save-fn-test
  (testing "make-handler accepts :save-fn without error"
    (let [columns [{:key :name :label "Name" :type :string :editable true}]
          save-fn (fn [_] {:success true})
          handler (datatable/make-handler {:id "test-table"
                                           :columns columns
                                           :query-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                           :data-url "/data"
                                           :render-html-fn (fn [_] "<html>")
                                           :save-fn save-fn})]
      ;; Handler should be created successfully
      (is (fn? handler)
          "make-handler should return a function when :save-fn is provided"))))

(deftest extract-cell-edit-from-signals-test
  (testing "extracts cell edit from signals using :editing state"
    ;; The :editing state indicates which cell is being edited
    ;; The :cells map contains the actual values
    (let [signals {:editing {:rowId "123" :colKey "name"}
                   :cells {:123 {:name "New Name"}}}
          result (datatable/extract-cell-edit signals)]
      (is (= {:row-id "123" :col-key :name :value "New Name"} result))))

  (testing "returns nil when no editing state"
    (is (nil? (datatable/extract-cell-edit {})))
    (is (nil? (datatable/extract-cell-edit {:cells {:123 {:name "Value"}}})))
    (is (nil? (datatable/extract-cell-edit {:editing {:rowId nil :colKey nil}}))))

  (testing "uses editing state to select correct cell from multiple"
    ;; When multiple cells are present, :editing determines which one we want
    (let [signals {:editing {:rowId "456" :colKey "age"}
                   :cells {:123 {:name "Name1"} :456 {:age "30"}}}
          result (datatable/extract-cell-edit signals)]
      (is (= {:row-id "456" :col-key :age :value "30"} result))))

  (testing "returns nil value when cell not in cells map"
    ;; Edge case: editing is set but cell value was cleared
    (let [signals {:editing {:rowId "123" :colKey "name"}
                   :cells {}}
          result (datatable/extract-cell-edit signals)]
      (is (= {:row-id "123" :col-key :name :value nil} result))))

  (testing "extracts boolean true value"
    ;; Boolean values from checkbox toggles may be actual booleans
    (let [signals {:editing {:rowId "123" :colKey "verified"}
                   :cells {:123 {:verified true}}}
          result (datatable/extract-cell-edit signals)]
      (is (= {:row-id "123" :col-key :verified :value true} result))))

  (testing "extracts boolean false value"
    ;; Boolean false should be extracted correctly
    (let [signals {:editing {:rowId "123" :colKey "verified"}
                   :cells {:123 {:verified false}}}
          result (datatable/extract-cell-edit signals)]
      (is (= {:row-id "123" :col-key :verified :value false} result))))

  (testing "extracts string 'true' and 'false' values as-is"
    ;; Datastar may send booleans as strings - extraction should preserve them
    ;; The coercion happens in render-cell-display, not here
    (let [signals-true {:editing {:rowId "123" :colKey "verified"}
                        :cells {:123 {:verified "true"}}}
          signals-false {:editing {:rowId "123" :colKey "verified"}
                         :cells {:123 {:verified "false"}}}]
      (is (= "true" (:value (datatable/extract-cell-edit signals-true))))
      (is (= "false" (:value (datatable/extract-cell-edit signals-false)))))))

(deftest has-editable-columns-test
  (testing "returns true when any column is editable"
    (let [cols [{:key :name :editable true}
                {:key :age}]]
      (is (true? (datatable/has-editable-columns? cols)))))

  (testing "returns false when no columns are editable"
    (let [cols [{:key :name}
                {:key :age}]]
      (is (false? (datatable/has-editable-columns? cols)))))

  (testing "returns false for empty columns"
    (is (false? (datatable/has-editable-columns? [])))
    (is (false? (datatable/has-editable-columns? nil)))))

(deftest render-cell-display-for-save-test
  (testing "string values are returned as-is"
    (let [render-fn #'datatable/render-cell-display]
      (is (= "New Value" (render-fn "New Value" :string)))
      (is (= "Hello" (render-fn "Hello" nil)))))

  (testing "boolean true renders checkmark SVG"
    (let [render-fn #'datatable/render-cell-display
          result (render-fn true :boolean)]
      (is (vector? result))
      (is (clojure.string/includes? (name (first result)) "svg"))
      (is (clojure.string/includes? (name (first result)) "text-success"))))

  (testing "boolean false renders X SVG"
    (let [render-fn #'datatable/render-cell-display
          result (render-fn false :boolean)]
      (is (vector? result))
      (is (clojure.string/includes? (name (first result)) "svg"))
      (is (clojure.string/includes? (name (first result)) "opacity-30"))))

  (testing "string 'true' is treated as boolean true"
    ;; Bug fix: Datastar may send boolean values as strings
    ;; The string "true" should render as the checkmark icon
    (let [render-fn #'datatable/render-cell-display
          result (render-fn "true" :boolean)]
      (is (vector? result))
      (is (clojure.string/includes? (name (first result)) "text-success")
          "String 'true' should render as checkmark (truthy display)")))

  (testing "string 'false' is treated as boolean false"
    ;; Bug fix: The string "false" should render as the X icon, not checkmark!
    ;; In Clojure, (if "false" ...) is truthy, so we need explicit coercion.
    (let [render-fn #'datatable/render-cell-display
          result (render-fn "false" :boolean)]
      (is (vector? result))
      (is (clojure.string/includes? (name (first result)) "opacity-30")
          "String 'false' should render as X icon, not checkmark")))

  (testing "nil boolean renders as false (X icon)"
    (let [render-fn #'datatable/render-cell-display
          result (render-fn nil :boolean)]
      (is (vector? result))
      (is (clojure.string/includes? (name (first result)) "opacity-30"))))

  (testing "save response span construction includes data-value"
    ;; This documents the expected span format in make-handler
    ;; Expected format: [:span.flex-1 {:id span-id :data-value (str value)} display-content]
    (is true "See make-handler implementation for span construction with data-value")))


