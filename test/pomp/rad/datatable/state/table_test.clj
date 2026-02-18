(ns pomp.rad.datatable.state.table-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.state.table :as table-state]))

(deftest next-state-global-search-normalization-test
  (testing "global-search action reads dedicated signal, normalizes it, and resets to first page"
    (let [signals {:filters {:school [{:type "enum" :op "is" :value "Stoa"}]}
                   :sort [{:column "name" :direction "desc"}]
                   :page {:size 25 :current 3}
                   :group-by [:school]
                   :globalTableSearch "  Stoa  "}
          next-signals (table-state/next-state signals {"action" "global-search"})]
      (is (= "Stoa" (:search-string next-signals))
          "Global search value should be normalized before query selection")
      (is (= {:size 25 :current 0} (:page next-signals))
          "Global-search action should reset pagination to page 0")
      (is (= (:filters signals) (:filters next-signals))
          "Global-search action should preserve existing column filters")
      (is (= (:sort signals) (:sort next-signals))
          "Global-search action should preserve existing sort state"))))

(deftest query-global-search-short-input-normalization-test
  (testing "query pipeline forwards normalized short global search as empty"
    (let [captured-signals (atom nil)
          signals {:filters {:school [{:type "enum" :op "is" :value "Stoa"}]}
                   :sort [{:column "name" :direction "asc"}]
                   :page {:size 10 :current 1}
                   :group-by []
                   :globalTableSearch " a "}
          query-fn (fn [query-signals _]
                     (reset! captured-signals query-signals)
                     {:rows [] :total-rows 0 :page (:page query-signals)})]
      (table-state/query-rows signals {"action" "global-search"} {} query-fn)
      (is (= "" (:search-string @captured-signals))
          "Search shorter than 2 chars should be normalized to empty")
      (is (= (:filters signals) (:filters @captured-signals))
          "Global search should not clear existing filters")
       (is (= (:sort signals) (:sort @captured-signals))
           "Global search should not clear existing sort state"))))

(deftest next-state-grouping-transition-preserves-filters-test
  (testing "adding a grouped column preserves existing filters"
    (let [signals {:filters {:school [{:type "enum" :op "is" :value "Academy"}]
                             :region [{:type "enum" :op "is" :value "Greece"}]}
                   :sort []
                   :page {:size 10 :current 2}
                   :group-by [:school]
                   :globalTableSearch ""}
          next-signals (table-state/next-state signals {"groupBy" "region"})]
      (is (= [:school :region] (:group-by next-signals))
          "Grouping transition should append new grouped column")
      (is (= (:filters signals) (:filters next-signals))
          "Grouping transition should preserve active column filters")))

  (testing "ungrouping preserves remaining active filters"
    (let [signals {:filters {:school [{:type "enum" :op "is" :value "Academy"}]
                             :region [{:type "enum" :op "is" :value "Greece"}]}
                   :sort []
                   :page {:size 10 :current 2}
                   :group-by [:school :region]
                   :globalTableSearch ""}
          next-signals (table-state/next-state signals {"ungroup" "true"})]
      (is (= [:school] (:group-by next-signals))
          "Ungroup should remove only the deepest grouped column")
      (is (= (:filters signals) (:filters next-signals))
          "Ungroup transition should not wipe active filters"))))
