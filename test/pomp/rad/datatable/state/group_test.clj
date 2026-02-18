(ns pomp.rad.datatable.state.group-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.state.group :as group-state]))

(def multi-group-rows
  [{:id 1 :name "Socrates" :century "4th BC" :school "Academy"}
   {:id 2 :name "Plato" :century "4th BC" :school "Academy"}
   {:id 3 :name "Aristotle" :century "4th BC" :school "Platonism"}
   {:id 4 :name "Zeno" :century "3rd BC" :school "Stoicism"}])

(deftest next-state-appends-group-by-selection-test
  (testing "adding a group appends to the existing group-by sequence"
    (is (= [:century :school]
           (group-state/next-state [:century]
                                  {"groupBy" "school"})))
    (is (= [:century :school :region]
           (group-state/next-state [:century :school]
                                  {"groupBy" "region"})))
    (is (= [:century]
           (group-state/next-state [:century]
                                  {})))))

(deftest next-state-ungroup-removes-the-last-grouping
  (testing "ungroup removes only the last grouped column"
    (is (= [:century]
           (group-state/next-state [:century :school]
                                  {"ungroup" "true"}))
        "ungroup should remove one level, not clear all when multiple levels exist")
    (is (= []
           (group-state/next-state [:century]
                                  {"ungroup" "true"}))
        "ungroup should still clear the final single grouped column")))

(deftest next-state-clear-groups-removes-every-level
  (testing "clearGroups removes all grouped columns"
    (is (= []
           (group-state/next-state [:century :school :region]
                                   {"clearGroups" "true"})))))

(deftest group-rows-builds-multi-column-tree-test
  (testing "grouping by multiple columns returns nested groups with leaf counts"
    (let [groups (group-state/group-rows multi-group-rows [:century :school])
          [century-4th century-3rd] groups
          [school-academy school-platonism] (:rows century-4th)]

      (is (= ["4th BC" "3rd BC"] (mapv :group-value groups))
          "Top-level groups follow first-column order with multi-group inputs")
      (is (= ["Academy" "Platonism"]
             (mapv :group-value (:rows century-4th)))
          "Second-level groups for first century include school buckets")

      (is (= 3 (:count century-4th))
          "Top-level group count includes all leaf rows in the bucket")
      (is (= 1 (:count century-3rd))
          "Top-level singleton count is preserved")
      (is (= 2 (:count school-academy))
          "School bucket count reflects multiple rows")
      (is (= 1 (:count school-platonism))
          "School bucket count reflects a singleton")
      (is (= ["Socrates" "Plato"] (map :name (:rows school-academy)))
          "Leaf row order is stable under nested grouping")
      (is (= ["Aristotle"] (map :name (:rows school-platonism)))
          "Leaf rows remain available at the deepest level")
      (is (= ["Stoicism"] (map :group-value (:rows century-3rd)))
          "Sibling top-level groups keep second-level child nodes"))))
