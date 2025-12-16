(ns pomp.rad.datatable.state.filter-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.state.filter :as filter]))

(deftest next-state-test
  (testing "adding a new filter"
    (is (= {:name {:type "text" :op "contains" :value "john"}}
           (filter/next-state {} {"filterCol" "name" "filterOp" "contains" "filterVal" "john"}))
        "adds filter to empty state")

    (is (= {:name {:type "text" :op "contains" :value "john"}
            :age {:type "text" :op "equals" :value "30"}}
           (filter/next-state {:name {:type "text" :op "contains" :value "john"}}
                              {"filterCol" "age" "filterOp" "equals" "filterVal" "30"}))
        "adds filter to existing state"))

  (testing "replacing an existing filter"
    (is (= {:name {:type "text" :op "equals" :value "jane"}}
           (filter/next-state {:name {:type "text" :op "contains" :value "john"}}
                              {"filterCol" "name" "filterOp" "equals" "filterVal" "jane"}))
        "replaces filter for same column"))

  (testing "default operator"
    (is (= {:name {:type "text" :op "contains" :value "john"}}
           (filter/next-state {} {"filterCol" "name" "filterVal" "john"}))
        "defaults to 'contains' when no operator specified"))

  (testing "removing a filter (blank value)"
    (is (= {}
           (filter/next-state {:name {:type "text" :op "contains" :value "john"}}
                              {"filterCol" "name" "filterOp" "contains" "filterVal" ""}))
        "removes filter when value is blank")

    (is (= {:age {:type "text" :op "equals" :value "30"}}
           (filter/next-state {:name {:type "text" :op "contains" :value "john"}
                               :age {:type "text" :op "equals" :value "30"}}
                              {"filterCol" "name" "filterOp" "contains" "filterVal" ""}))
        "removes only the specified filter"))

  (testing "is-empty operator (blank value allowed)"
    (is (= {:name {:type "text" :op "is-empty" :value ""}}
           (filter/next-state {} {"filterCol" "name" "filterOp" "is-empty" "filterVal" ""}))
        "is-empty filter is preserved even with blank value"))

  (testing "clearing all filters"
    (is (= {}
           (filter/next-state {:name {:type "text" :op "contains" :value "john"}
                               :age {:type "text" :op "equals" :value "30"}}
                              {"clearFilters" "1"}))
        "clears all filters when clearFilters param is present"))

  (testing "no filter params"
    (is (= {:name {:type "text" :op "contains" :value "john"}}
           (filter/next-state {:name {:type "text" :op "contains" :value "john"}} {}))
        "returns signals unchanged when no filter params")))

(deftest compute-patch-test
  (testing "no changes"
    (is (= {:name {:type "text" :op "contains" :value "john"}}
           (filter/compute-patch {:name {:type "text" :op "contains" :value "john"}}
                                 {:name {:type "text" :op "contains" :value "john"}}))
        "returns new signals when no changes"))

  (testing "adding a filter"
    (is (= {:name {:type "text" :op "contains" :value "john"}}
           (filter/compute-patch {} {:name {:type "text" :op "contains" :value "john"}}))
        "returns new filter in patch"))

  (testing "removing a filter"
    (is (= {:name nil}
           (filter/compute-patch {:name {:type "text" :op "contains" :value "john"}} {}))
        "sets removed filter to nil in patch"))

  (testing "replacing a filter"
    (is (= {:name {:type "text" :op "equals" :value "jane"}}
           (filter/compute-patch {:name {:type "text" :op "contains" :value "john"}}
                                 {:name {:type "text" :op "equals" :value "jane"}}))
        "returns updated filter in patch"))

  (testing "mixed changes"
    (is (= {:name {:type "text" :op "equals" :value "jane"}
            :age nil
            :city {:type "text" :op "contains" :value "NYC"}}
           (filter/compute-patch {:name {:type "text" :op "contains" :value "john"}
                                  :age {:type "text" :op "equals" :value "30"}}
                                 {:name {:type "text" :op "equals" :value "jane"}
                                  :city {:type "text" :op "contains" :value "NYC"}}))
        "handles add, remove, and update together")))
