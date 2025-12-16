(ns pomp.rad.datatable.query.in-memory-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.query.in-memory :as query]))

(def test-rows
  [{:id 1 :name "Alice" :age 30 :city "New York"}
   {:id 2 :name "Bob" :age 25 :city "Boston"}
   {:id 3 :name "Charlie" :age 35 :city "Chicago"}
   {:id 4 :name "Diana" :age 28 :city "Denver"}
   {:id 5 :name "Eve" :age 32 :city ""}])

;; =============================================================================
;; Filter Tests
;; =============================================================================

(deftest apply-filters-contains-test
  (testing "contains filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}]
           (query/apply-filters test-rows {:name {:type "text" :op "contains" :value "lic"}}))
        "filters rows where name contains 'lic'")

    (is (= test-rows
           (query/apply-filters test-rows {:name {:type "text" :op "contains" :value ""}}))
        "blank value returns all rows")))

(deftest apply-filters-not-contains-test
  (testing "not-contains filter"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name {:type "text" :op "not-contains" :value "lic"}}))
        "filters rows where name does not contain 'lic'")

    (is (= test-rows
           (query/apply-filters test-rows {:name {:type "text" :op "not-contains" :value ""}}))
        "blank value returns all rows")))

(deftest apply-filters-equals-test
  (testing "equals filter"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}]
           (query/apply-filters test-rows {:name {:type "text" :op "equals" :value "bob"}}))
        "filters rows where name equals 'bob' (case-insensitive)")

    (is (= []
           (query/apply-filters test-rows {:name {:type "text" :op "equals" :value "bobby"}}))
        "returns empty when no match")))

(deftest apply-filters-not-equals-test
  (testing "not-equals filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name {:type "text" :op "not-equals" :value "bob"}}))
        "filters rows where name does not equal 'bob'")))

(deftest apply-filters-starts-with-test
  (testing "starts-with filter"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name {:type "text" :op "starts-with" :value "ch"}}))
        "filters rows where name starts with 'ch'")))

(deftest apply-filters-ends-with-test
  (testing "ends-with filter"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:name {:type "text" :op "ends-with" :value "e"}}))
        "filters rows where name ends with 'e'")))

(deftest apply-filters-is-empty-test
  (testing "is-empty filter"
    (is (= [{:id 5 :name "Eve" :age 32 :city ""}]
           (query/apply-filters test-rows {:city {:type "text" :op "is-empty" :value ""}}))
        "filters rows where city is empty")))

(deftest apply-filters-multiple-columns-test
  (testing "multiple column filters (AND logic)"
    (is (= [{:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/apply-filters test-rows {:name {:type "text" :op "contains" :value "ar"}
                                           :city {:type "text" :op "starts-with" :value "ch"}}))
        "filters with multiple columns use AND logic")))

(deftest apply-filters-case-insensitive-test
  (testing "case insensitivity"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}]
           (query/apply-filters test-rows {:name {:type "text" :op "contains" :value "ALICE"}}))
        "filtering is case-insensitive")))

;; =============================================================================
;; Sort Tests
;; =============================================================================

(deftest sort-data-test
  (testing "sort ascending"
    (is (= [{:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 5 :name "Eve" :age 32 :city ""}]
           (query/sort-data test-rows [{:column "name" :direction "asc"}]))
        "sorts by name ascending"))

  (testing "sort descending"
    (is (= [{:id 5 :name "Eve" :age 32 :city ""}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}
            {:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 1 :name "Alice" :age 30 :city "New York"}]
           (query/sort-data test-rows [{:column "name" :direction "desc"}]))
        "sorts by name descending"))

  (testing "sort by numeric column"
    (is (= [{:id 2 :name "Bob" :age 25 :city "Boston"}
            {:id 4 :name "Diana" :age 28 :city "Denver"}
            {:id 1 :name "Alice" :age 30 :city "New York"}
            {:id 5 :name "Eve" :age 32 :city ""}
            {:id 3 :name "Charlie" :age 35 :city "Chicago"}]
           (query/sort-data test-rows [{:column "age" :direction "asc"}]))
        "sorts by age ascending"))

  (testing "empty sort spec"
    (is (= test-rows
           (query/sort-data test-rows []))
        "returns rows unchanged when sort spec is empty")))

;; =============================================================================
;; Pagination Tests
;; =============================================================================

(deftest paginate-data-test
  (testing "first page"
    (is (= {:rows [{:id 1 :name "Alice" :age 30 :city "New York"}
                   {:id 2 :name "Bob" :age 25 :city "Boston"}]
            :current 0}
           (query/paginate-data test-rows {:size 2 :current 0}))
        "returns first page"))

  (testing "middle page"
    (is (= {:rows [{:id 3 :name "Charlie" :age 35 :city "Chicago"}
                   {:id 4 :name "Diana" :age 28 :city "Denver"}]
            :current 1}
           (query/paginate-data test-rows {:size 2 :current 1}))
        "returns middle page"))

  (testing "last page (partial)"
    (is (= {:rows [{:id 5 :name "Eve" :age 32 :city ""}]
            :current 2}
           (query/paginate-data test-rows {:size 2 :current 2}))
        "returns last page with fewer items"))

  (testing "page beyond range clamps to last page"
    (is (= {:rows [{:id 5 :name "Eve" :age 32 :city ""}]
            :current 2}
           (query/paginate-data test-rows {:size 2 :current 100}))
        "clamps to last page when current is too high"))

  (testing "nil current defaults to last page"
    (is (= {:rows [{:id 5 :name "Eve" :age 32 :city ""}]
            :current 2}
           (query/paginate-data test-rows {:size 2 :current nil}))
        "nil current goes to last page"))

  (testing "empty rows"
    (is (= {:rows [] :current 0}
           (query/paginate-data [] {:size 10 :current 0}))
        "handles empty rows")))

;; =============================================================================
;; Integration Tests (query-fn)
;; =============================================================================

(deftest query-fn-test
  (let [qfn (query/query-fn test-rows)]
    (testing "filter + sort + paginate"
      ;; city starts-with "d" matches only Denver (Diana)
      ;; total-rows = 1, so page 1 clamps to page 0
      (is (= {:rows [{:id 4 :name "Diana" :age 28 :city "Denver"}]
              :total-rows 1
              :page {:size 1 :current 0}}
             (qfn {:filters {:city {:type "text" :op "starts-with" :value "d"}}
                   :sort [{:column "name" :direction "asc"}]
                   :page {:size 1 :current 1}}))
          "combines filter, sort, and pagination (clamps page when beyond range)"))

    (testing "no filters"
      (is (= {:rows test-rows
              :total-rows 5
              :page {:size 10 :current 0}}
             (qfn {:filters {}
                   :sort []
                   :page {:size 10 :current 0}}))
          "returns all rows when no filters"))

    (testing "total-rows reflects filtered count"
      ;; name contains "e" matches: Alice, Charlie, Eve = 3
      (is (= 3
             (:total-rows (qfn {:filters {:name {:type "text" :op "contains" :value "e"}}
                                :sort []
                                :page {:size 10 :current 0}})))
          "total-rows is count after filtering, before pagination"))))
