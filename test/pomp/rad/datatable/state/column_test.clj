(ns pomp.rad.datatable.state.column-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.state.column :as column]))

(deftest move-test
  (testing "moving column left (before a column to its left)"
    (is (= ["B" "A" "C"] (column/move ["A" "B" "C"] "B" "A"))
        "B moved before A")
    (is (= ["C" "A" "B"] (column/move ["A" "B" "C"] "C" "A"))
        "C moved before A")
    (is (= ["A" "C" "B"] (column/move ["A" "B" "C"] "C" "B"))
        "C moved before B"))

  (testing "moving column right (before a column to its right)"
    (is (= ["A" "C" "B"] (column/move ["A" "B" "C"] "B" "C"))
        "B moved to C's position, C shifts left")
    (is (= ["B" "C" "A"] (column/move ["A" "B" "C"] "A" "C"))
        "A moved to C's position, B and C shift left"))

  (testing "edge cases"
    (is (= ["A" "B" "C"] (column/move ["A" "B" "C"] "A" "A"))
        "moving to same position is no-op")
    (is (= ["A" "B" "C"] (column/move ["A" "B" "C"] nil "B"))
        "nil move-col is no-op")
    (is (= ["A" "B" "C"] (column/move ["A" "B" "C"] "B" nil))
        "nil target-col is no-op")
    (is (= ["A" "B" "C"] (column/move ["A" "B" "C"] "X" "B"))
        "non-existent move-col is no-op")
    (is (= ["A" "B" "C"] (column/move ["A" "B" "C"] "B" "X"))
        "non-existent target-col is no-op")))
