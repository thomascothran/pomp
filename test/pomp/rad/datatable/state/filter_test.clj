(ns pomp.rad.datatable.state.filter-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.state.filter :as filter]))

;; =============================================================================
;; New filter structure: {:filters {:col-key [{:type "text" :op "contains" :value "x"} ...]}}
;; Each column maps to a VECTOR of filter specs, enabling multiple filters per column.
;; =============================================================================

(deftest next-state-test
  (testing "adding a new filter to empty state"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/next-state {} {"filterCol" "name" "filterOp" "contains" "filterVal" "john"}))
        "adds filter as vector to empty state"))

  (testing "adding a filter to a different column"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]
            :age [{:type "string" :op "equals" :value "30"}]}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]}
                              {"filterCol" "age" "filterOp" "equals" "filterVal" "30"}))
        "adds filter for new column"))

  (testing "adding a second filter to same column"
    (is (= {:name [{:type "string" :op "contains" :value "john"}
                   {:type "string" :op "starts-with" :value "j"}]}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]}
                              {"filterCol" "name" "filterOp" "starts-with" "filterVal" "j"}))
        "appends second filter to same column"))

  (testing "default operator"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/next-state {} {"filterCol" "name" "filterVal" "john"}))
        "defaults to 'contains' when no operator specified"))

  (testing "removing a specific filter by index"
    (is (= {:name [{:type "string" :op "starts-with" :value "j"}]}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}
                                      {:type "string" :op "starts-with" :value "j"}]}
                              {"filterCol" "name" "filterIdx" "0" "removeFilter" "1"}))
        "removes filter at specified index")

    (is (= {}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]}
                              {"filterCol" "name" "filterIdx" "0" "removeFilter" "1"}))
        "removes column entry when last filter is removed"))

  (testing "is-empty operator (blank value allowed)"
    (is (= {:name [{:type "string" :op "is-empty" :value ""}]}
           (filter/next-state {} {"filterCol" "name" "filterOp" "is-empty" "filterVal" ""}))
        "is-empty filter is preserved even with blank value"))

  (testing "clearing all filters"
    (is (= {}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]
                               :age [{:type "string" :op "equals" :value "30"}]}
                              {"clearFilters" "1"}))
        "clears all filters when clearFilters param is present"))

  (testing "clearing filters for a single column"
    (is (= {:age [{:type "string" :op "equals" :value "30"}]}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]
                               :age [{:type "string" :op "equals" :value "30"}]}
                              {"filterCol" "name" "clearColFilters" "1"}))
        "clears all filters for specified column only"))

  (testing "no filter params"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]} {}))
        "returns signals unchanged when no filter params")))

(deftest compute-patch-test
  (testing "no changes"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/compute-patch {:name [{:type "string" :op "contains" :value "john"}]}
                                 {:name [{:type "string" :op "contains" :value "john"}]}))
        "returns new signals when no changes"))

  (testing "adding a filter to new column"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/compute-patch {} {:name [{:type "string" :op "contains" :value "john"}]}))
        "returns new filter in patch"))

  (testing "adding a filter to existing column"
    (is (= {:name [{:type "string" :op "contains" :value "john"}
                   {:type "string" :op "starts-with" :value "j"}]}
           (filter/compute-patch {:name [{:type "string" :op "contains" :value "john"}]}
                                 {:name [{:type "string" :op "contains" :value "john"}
                                         {:type "string" :op "starts-with" :value "j"}]}))
        "returns updated array in patch"))

  (testing "removing all filters for a column"
    (is (= {:name nil}
           (filter/compute-patch {:name [{:type "string" :op "contains" :value "john"}]} {}))
        "sets removed column to nil in patch"))

  (testing "removing one filter from column (leaving others)"
    (is (= {:name [{:type "string" :op "starts-with" :value "j"}]}
           (filter/compute-patch {:name [{:type "string" :op "contains" :value "john"}
                                         {:type "string" :op "starts-with" :value "j"}]}
                                 {:name [{:type "string" :op "starts-with" :value "j"}]}))
        "returns reduced array in patch"))

  (testing "mixed changes"
    (is (= {:name [{:type "string" :op "equals" :value "jane"}]
            :age nil
            :city [{:type "string" :op "contains" :value "NYC"}]}
           (filter/compute-patch {:name [{:type "string" :op "contains" :value "john"}]
                                  :age [{:type "string" :op "equals" :value "30"}]}
                                 {:name [{:type "string" :op "equals" :value "jane"}]
                                  :city [{:type "string" :op "contains" :value "NYC"}]}))
        "handles add, remove, and update together")))

;; =============================================================================
;; Filter type support - filters should store the column type
;; =============================================================================

(deftest next-state-filter-type-test
  (testing "stores filter type from filterType param"
    (is (= {:active [{:type "boolean" :op "is" :value "true"}]}
           (filter/next-state {} {"filterCol" "active"
                                  "filterType" "boolean"
                                  "filterOp" "is"
                                  "filterVal" "true"}))
        "boolean filter type is stored"))

  (testing "stores date filter type"
    (is (= {:created [{:type "date" :op "after" :value "2024-01-01"}]}
           (filter/next-state {} {"filterCol" "created"
                                  "filterType" "date"
                                  "filterOp" "after"
                                  "filterVal" "2024-01-01"}))
        "date filter type is stored"))

  (testing "stores enum filter type"
    (is (= {:status [{:type "enum" :op "is" :value "active"}]}
           (filter/next-state {} {"filterCol" "status"
                                  "filterType" "enum"
                                  "filterOp" "is"
                                  "filterVal" "active"}))
        "enum filter type is stored"))

  (testing "defaults to text when filterType not provided"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/next-state {} {"filterCol" "name"
                                  "filterOp" "contains"
                                  "filterVal" "john"}))
        "defaults to text type for backwards compatibility"))

  (testing "string filterType is stored as text"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/next-state {} {"filterCol" "name"
                                  "filterType" "string"
                                  "filterOp" "contains"
                                  "filterVal" "john"}))
        "string type is normalized to text")))
