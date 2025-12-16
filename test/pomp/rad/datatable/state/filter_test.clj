(ns pomp.rad.datatable.state.filter-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.state.filter :as filter]))

;; =============================================================================
;; New filter structure: {:filters {:col-key [{:type "text" :op "contains" :value "x"} ...]}}
;; Each column maps to a VECTOR of filter specs, enabling multiple filters per column.
;; =============================================================================

(deftest next-state-test
  (testing "adding a new filter to empty state"
    (is (= {:name [{:type "text" :op "contains" :value "john"}]}
           (filter/next-state {} {"filterCol" "name" "filterOp" "contains" "filterVal" "john"}))
        "adds filter as vector to empty state"))

  (testing "adding a filter to a different column"
    (is (= {:name [{:type "text" :op "contains" :value "john"}]
            :age [{:type "text" :op "equals" :value "30"}]}
           (filter/next-state {:name [{:type "text" :op "contains" :value "john"}]}
                              {"filterCol" "age" "filterOp" "equals" "filterVal" "30"}))
        "adds filter for new column"))

  (testing "adding a second filter to same column"
    (is (= {:name [{:type "text" :op "contains" :value "john"}
                   {:type "text" :op "starts-with" :value "j"}]}
           (filter/next-state {:name [{:type "text" :op "contains" :value "john"}]}
                              {"filterCol" "name" "filterOp" "starts-with" "filterVal" "j"}))
        "appends second filter to same column"))

  (testing "default operator"
    (is (= {:name [{:type "text" :op "contains" :value "john"}]}
           (filter/next-state {} {"filterCol" "name" "filterVal" "john"}))
        "defaults to 'contains' when no operator specified"))

  (testing "removing a specific filter by index"
    (is (= {:name [{:type "text" :op "starts-with" :value "j"}]}
           (filter/next-state {:name [{:type "text" :op "contains" :value "john"}
                                      {:type "text" :op "starts-with" :value "j"}]}
                              {"filterCol" "name" "filterIdx" "0" "removeFilter" "1"}))
        "removes filter at specified index")

    (is (= {}
           (filter/next-state {:name [{:type "text" :op "contains" :value "john"}]}
                              {"filterCol" "name" "filterIdx" "0" "removeFilter" "1"}))
        "removes column entry when last filter is removed"))

  (testing "is-empty operator (blank value allowed)"
    (is (= {:name [{:type "text" :op "is-empty" :value ""}]}
           (filter/next-state {} {"filterCol" "name" "filterOp" "is-empty" "filterVal" ""}))
        "is-empty filter is preserved even with blank value"))

  (testing "clearing all filters"
    (is (= {}
           (filter/next-state {:name [{:type "text" :op "contains" :value "john"}]
                               :age [{:type "text" :op "equals" :value "30"}]}
                              {"clearFilters" "1"}))
        "clears all filters when clearFilters param is present"))

  (testing "clearing filters for a single column"
    (is (= {:age [{:type "text" :op "equals" :value "30"}]}
           (filter/next-state {:name [{:type "text" :op "contains" :value "john"}]
                               :age [{:type "text" :op "equals" :value "30"}]}
                              {"filterCol" "name" "clearColFilters" "1"}))
        "clears all filters for specified column only"))

  (testing "no filter params"
    (is (= {:name [{:type "text" :op "contains" :value "john"}]}
           (filter/next-state {:name [{:type "text" :op "contains" :value "john"}]} {}))
        "returns signals unchanged when no filter params")))

(deftest compute-patch-test
  (testing "no changes"
    (is (= {:name [{:type "text" :op "contains" :value "john"}]}
           (filter/compute-patch {:name [{:type "text" :op "contains" :value "john"}]}
                                 {:name [{:type "text" :op "contains" :value "john"}]}))
        "returns new signals when no changes"))

  (testing "adding a filter to new column"
    (is (= {:name [{:type "text" :op "contains" :value "john"}]}
           (filter/compute-patch {} {:name [{:type "text" :op "contains" :value "john"}]}))
        "returns new filter in patch"))

  (testing "adding a filter to existing column"
    (is (= {:name [{:type "text" :op "contains" :value "john"}
                   {:type "text" :op "starts-with" :value "j"}]}
           (filter/compute-patch {:name [{:type "text" :op "contains" :value "john"}]}
                                 {:name [{:type "text" :op "contains" :value "john"}
                                         {:type "text" :op "starts-with" :value "j"}]}))
        "returns updated array in patch"))

  (testing "removing all filters for a column"
    (is (= {:name nil}
           (filter/compute-patch {:name [{:type "text" :op "contains" :value "john"}]} {}))
        "sets removed column to nil in patch"))

  (testing "removing one filter from column (leaving others)"
    (is (= {:name [{:type "text" :op "starts-with" :value "j"}]}
           (filter/compute-patch {:name [{:type "text" :op "contains" :value "john"}
                                         {:type "text" :op "starts-with" :value "j"}]}
                                 {:name [{:type "text" :op "starts-with" :value "j"}]}))
        "returns reduced array in patch"))

  (testing "mixed changes"
    (is (= {:name [{:type "text" :op "equals" :value "jane"}]
            :age nil
            :city [{:type "text" :op "contains" :value "NYC"}]}
           (filter/compute-patch {:name [{:type "text" :op "contains" :value "john"}]
                                  :age [{:type "text" :op "equals" :value "30"}]}
                                 {:name [{:type "text" :op "equals" :value "jane"}]
                                  :city [{:type "text" :op "contains" :value "NYC"}]}))
        "handles add, remove, and update together")))
