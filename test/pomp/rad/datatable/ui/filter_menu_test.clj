(ns pomp.rad.datatable.ui.filter-menu-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.ui.filter-menu :as filter-menu]))

;; =============================================================================
;; normalize-operations tests
;; Converts operation specs to canonical {:value :label} format.
;; Accepts: string, {:value :label}, or vector of either.
;; =============================================================================

(deftest normalize-operations-test
  (testing "string input converts to map with humanized label"
    (is (= [{:value "contains" :label "contains"}]
           (filter-menu/normalize-operations ["contains"])))
    (is (= [{:value "starts-with" :label "starts with"}]
           (filter-menu/normalize-operations ["starts-with"])))
    (is (= [{:value "is-not-empty" :label "is not empty"}]
           (filter-menu/normalize-operations ["is-not-empty"]))))

  (testing "map input passes through unchanged"
    (is (= [{:value "contains" :label "Includes"}]
           (filter-menu/normalize-operations [{:value "contains" :label "Includes"}]))))

  (testing "mixed input normalizes strings, preserves maps"
    (is (= [{:value "contains" :label "contains"}
            {:value "equals" :label "Matches exactly"}
            {:value "is-empty" :label "is empty"}]
           (filter-menu/normalize-operations
            ["contains"
             {:value "equals" :label "Matches exactly"}
             "is-empty"]))))

  (testing "empty vector returns empty vector"
    (is (= []
           (filter-menu/normalize-operations []))))

  (testing "nil returns empty vector"
    (is (= []
           (filter-menu/normalize-operations nil)))))

;; =============================================================================
;; default-filter-operations tests
;; Map of type keyword to vector of operation specs.
;; =============================================================================

(deftest default-filter-operations-test
  (testing "contains expected type keys"
    (is (contains? filter-menu/default-filter-operations :string))
    (is (contains? filter-menu/default-filter-operations :boolean))
    (is (contains? filter-menu/default-filter-operations :date))
    (is (contains? filter-menu/default-filter-operations :enum)))

  (testing "string operations include expected values"
    (let [ops (filter-menu/default-filter-operations :string)
          values (set (map :value ops))]
      (is (contains? values "contains"))
      (is (contains? values "equals"))
      (is (contains? values "starts-with"))
      (is (contains? values "ends-with"))
      (is (contains? values "is-empty"))
      (is (contains? values "is-not-empty"))
      (is (contains? values "is-any-of"))))

  (testing "boolean operations include expected values"
    (let [ops (filter-menu/default-filter-operations :boolean)
          values (set (map :value ops))]
      (is (contains? values "is"))
      (is (contains? values "is-not"))
      (is (contains? values "is-empty"))
      (is (contains? values "is-not-empty"))))

  (testing "date operations include expected values"
    (let [ops (filter-menu/default-filter-operations :date)
          values (set (map :value ops))]
      (is (contains? values "is"))
      (is (contains? values "is-not"))
      (is (contains? values "after"))
      (is (contains? values "on-or-after"))
      (is (contains? values "before"))
      (is (contains? values "on-or-before"))
      (is (contains? values "is-empty"))
      (is (contains? values "is-not-empty"))))

  (testing "enum operations include expected values"
    (let [ops (filter-menu/default-filter-operations :enum)
          values (set (map :value ops))]
      (is (contains? values "is"))
      (is (contains? values "is-not"))
      (is (contains? values "is-any-of"))
      (is (contains? values "is-empty"))
      (is (contains? values "is-not-empty"))))

  (testing "all operations have :value and :label keys"
    (doseq [[type ops] filter-menu/default-filter-operations
            op ops]
      (is (contains? op :value) (str "Missing :value in " type " op: " op))
      (is (contains? op :label) (str "Missing :label in " type " op: " op)))))

;; =============================================================================
;; operations-for-column tests
;; Returns operations for a column based on precedence:
;; 1. Column :filter-operations (if specified)
;; 2. Table :filter-operations for the column's type (if specified)
;; 3. Default operations for the column's type
;; =============================================================================

(deftest operations-for-column-test
  (testing "returns column ops when provided (highest precedence)"
    (let [col-ops [{:value "custom" :label "Custom Op"}]
          table-ops {:string [{:value "table-op" :label "Table Op"}]}
          result (filter-menu/operations-for-column :string col-ops table-ops)]
      (is (= [{:value "custom" :label "Custom Op"}] result))))

  (testing "returns table ops when column ops nil"
    (let [table-ops {:string [{:value "table-op" :label "Table Op"}]}
          result (filter-menu/operations-for-column :string nil table-ops)]
      (is (= [{:value "table-op" :label "Table Op"}] result))))

  (testing "returns default ops when both nil"
    (let [result (filter-menu/operations-for-column :string nil nil)]
      (is (= (filter-menu/default-filter-operations :string) result))))

  (testing "returns default ops when table-ops doesn't have the type"
    (let [table-ops {:boolean [{:value "table-op" :label "Table Op"}]}
          result (filter-menu/operations-for-column :string nil table-ops)]
      (is (= (filter-menu/default-filter-operations :string) result))))

  (testing "normalizes string inputs from column ops"
    (let [col-ops ["contains" "equals"]
          result (filter-menu/operations-for-column :string col-ops nil)]
      (is (= [{:value "contains" :label "contains"}
              {:value "equals" :label "equals"}]
             result))))

  (testing "normalizes string inputs from table ops"
    (let [table-ops {:string ["starts-with" "ends-with"]}
          result (filter-menu/operations-for-column :string nil table-ops)]
      (is (= [{:value "starts-with" :label "starts with"}
              {:value "ends-with" :label "ends with"}]
             result))))

  (testing "falls back to :string for unknown column type"
    (let [result (filter-menu/operations-for-column :unknown-type nil nil)]
      (is (= (filter-menu/default-filter-operations :string) result))))

  (testing "handles :text as alias for :string"
    (let [result (filter-menu/operations-for-column :text nil nil)]
      (is (= (filter-menu/default-filter-operations :string) result)))))

;; =============================================================================
;; render tests
;; Verifies that render uses operations-for-column to get the right operations
;; based on column type and any overrides.
;; =============================================================================

(defn- find-operation-labels
  "Extracts operation labels from rendered filter menu hiccup.
   Looks for [:a.py-1 ...] elements inside the dropdown."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :a.py-1 (first hiccup)))
    ;; Found an operation link, extract the label (last element)
    [(last hiccup)]

    (vector? hiccup)
    (mapcat find-operation-labels hiccup)

    (seq? hiccup)
    (mapcat find-operation-labels hiccup)

    :else
    []))

(deftest render-uses-operations-for-column-test
  (testing "renders string operations by default (no type specified)"
    (let [result (filter-menu/render {:col-key :name
                                      :col-label "Name"
                                      :data-url "/data"})
          labels (set (find-operation-labels result))]
      ;; Should include string operations
      (is (contains? labels "contains"))
      (is (contains? labels "equals"))
      (is (contains? labels "is any of"))))

  (testing "renders string operations for :string type"
    (let [result (filter-menu/render {:col-key :name
                                      :col-label "Name"
                                      :col-type :string
                                      :data-url "/data"})
          labels (set (find-operation-labels result))]
      (is (contains? labels "contains"))
      (is (contains? labels "starts with"))))

  (testing "renders boolean operations for :boolean type"
    (let [result (filter-menu/render {:col-key :active
                                      :col-label "Active"
                                      :col-type :boolean
                                      :data-url "/data"})
          labels (set (find-operation-labels result))]
      (is (contains? labels "is"))
      (is (contains? labels "is not"))
      ;; Should NOT contain string-specific operations
      (is (not (contains? labels "contains")))
      (is (not (contains? labels "starts with")))))

  (testing "renders date operations for :date type"
    (let [result (filter-menu/render {:col-key :created
                                      :col-label "Created"
                                      :col-type :date
                                      :data-url "/data"})
          labels (set (find-operation-labels result))]
      (is (contains? labels "after"))
      (is (contains? labels "before"))
      (is (contains? labels "on or after"))))

  (testing "renders enum operations for :enum type"
    (let [result (filter-menu/render {:col-key :status
                                      :col-label "Status"
                                      :col-type :enum
                                      :data-url "/data"})
          labels (set (find-operation-labels result))]
      (is (contains? labels "is"))
      (is (contains? labels "is any of"))
      ;; Should NOT contain string-specific operations
      (is (not (contains? labels "contains")))))

  (testing "uses column-level filter-operations override"
    (let [result (filter-menu/render {:col-key :name
                                      :col-label "Name"
                                      :col-type :string
                                      :col-filter-ops [{:value "custom" :label "Custom Only"}]
                                      :data-url "/data"})
          labels (set (find-operation-labels result))]
      (is (contains? labels "Custom Only"))
      ;; Should NOT contain default operations
      (is (not (contains? labels "contains")))))

  (testing "uses table-level filter-operations override"
    (let [result (filter-menu/render {:col-key :name
                                      :col-label "Name"
                                      :col-type :string
                                      :table-filter-ops {:string [{:value "table-op" :label "Table Op"}]}
                                      :data-url "/data"})
          labels (set (find-operation-labels result))]
      (is (contains? labels "Table Op"))
      ;; Should NOT contain default operations
      (is (not (contains? labels "contains"))))))
