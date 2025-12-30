(ns pomp.rad.datatable.state.filter-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.state.filter :as filter]))

;; =============================================================================
;; Filter structure: {:col-key [{:type "string" :op "contains" :value "x"} ...]}
;; Each column maps to a VECTOR of filter specs, enabling multiple filters per column.
;;
;; With signal-based architecture, filters are managed by the frontend via signals.
;; The backend just reads filter state from signals - no query param manipulation needed
;; except for "clearFilters" which clears all filters.
;; =============================================================================

(deftest next-state-test
  (testing "returns signals unchanged when no clearFilters param"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]} {}))
        "returns signals unchanged when no query params"))

  (testing "returns signals unchanged with unrelated query params"
    (is (= {:name [{:type "string" :op "contains" :value "john"}]
            :age [{:type "number" :op "equals" :value "30"}]}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]
                               :age [{:type "number" :op "equals" :value "30"}]}
                              {"page" "2" "sort" "name"}))
        "ignores unrelated query params"))

  (testing "clearing all filters"
    (is (= {}
           (filter/next-state {:name [{:type "string" :op "contains" :value "john"}]
                               :age [{:type "number" :op "equals" :value "30"}]}
                              {"clearFilters" "1"}))
        "clears all filters when clearFilters param is present"))

  (testing "empty signals stay empty"
    (is (= {}
           (filter/next-state {} {}))
        "empty signals remain empty"))

  (testing "empty signals with clearFilters"
    (is (= {}
           (filter/next-state {} {"clearFilters" "1"}))
        "clearFilters on empty signals returns empty")))

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
