(ns pomp.browser.datatable.grouping-sort-filter-pagination-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]
            [pomp.test.fixtures.browser.datatable :as datatable]))

(use-fixtures :once
  (browser/server-fixture {:routes datatable/routes
                           :middlewares datatable/middlewares
                           :router-data datatable/router-data})
  browser/driver-fixture
  datatable/datatable-db-fixture
  datatable/datatable-state-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def school-menu-button
  {:css "button[popovertarget='col-menu-school']"})

(def group-by-school-item
  {:xpath "//div[@id='col-menu-school']//a[contains(normalize-space(.), 'Group by')]"})

(def region-menu-button
  {:css "button[popovertarget='col-menu-region']"})

(def group-by-region-item
  {:xpath "//div[@id='col-menu-region']//a[contains(normalize-space(.), 'Group by')]"})

(def ungroup-group-item
  {:xpath "//div[@id='col-menu-group']//a[contains(normalize-space(.), 'Ungroup')]"})

(def school-header-button
  {:xpath "//th//button[.//span[contains(@class,'font-semibold') and normalize-space(text())='School']]"})

(def century-header-button
  {:xpath "//th//button[.//span[contains(@class,'font-semibold') and normalize-space(text())='Century']]"})

(def school-filter-button
  {:css "button[popovertarget='filter-school']"})

(def school-filter-input
  {:css "#filter-school input[name='filterVal']"})

(def school-filter-apply
  {:xpath "//div[@id='filter-school']//button[normalize-space(text())='Apply']"})

(def region-filter-button
  {:css "button[popovertarget='filter-region']"})

(def region-filter-input
  {:css "#filter-region input[name='filterVal']"})

(def region-filter-apply
  {:xpath "//div[@id='filter-region']//button[normalize-space(text())='Apply']"})

(def group-row-selector
  {:css "#datatable tr[data-group-level]"})

(def group-menu-button
  {:css "button[popovertarget='col-menu-group']"})

(def top-level-group-row-selector
  {:css "#datatable tr[data-group-level='1']"})

(def group-sort-asc-item
  {:xpath "//div[@id='col-menu-group']//a[contains(normalize-space(.), 'Sort ascending')]"})

(def group-sort-desc-item
  {:xpath "//div[@id='col-menu-group']//a[contains(normalize-space(.), 'Sort descending')]"})

(def next-page-button
  {:xpath "//button[normalize-space(text())='›']"})

(def pagination-label
  {:xpath "//div[contains(@class,'mt-4')]//div[contains(normalize-space(.), ' of ')]"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- group-by-school!
  []
  (e/click browser/*driver* school-menu-button)
  (e/wait-visible browser/*driver* group-by-school-item)
  (e/click browser/*driver* group-by-school-item)
  (e/wait-visible browser/*driver* group-row-selector))

(defn- group-by-school-and-region!
  []
  (group-by-school!)
  (e/click browser/*driver* region-menu-button)
  (e/wait-visible browser/*driver* group-by-region-item)
  (e/click browser/*driver* group-by-region-item)
  (e/wait-visible browser/*driver* group-row-selector))

(defn- group-row-texts
  []
  (let [rows (e/query-all browser/*driver* group-row-selector)]
    (mapv #(e/get-element-text-el browser/*driver* %) rows)))

(defn- first-group-text
  []
  (first (group-row-texts)))

(defn- expected-school-order
  []
  (->> (:rows datatable/*state*)
       (map :school)
       distinct
       sort
       vec))

(defn- expected-school-count
  [school]
  (->> (:rows datatable/*state*)
       (filter #(= school (:school %)))
       count))

(defn- group-row-count
  []
  (count (e/query-all browser/*driver* group-row-selector)))

(defn- top-level-group-row-count
  []
  (count (e/query-all browser/*driver* top-level-group-row-selector)))

(defn- expected-top-level-group-count
  [rows]
  (->> rows (map :school) distinct count))

(defn- expected-grouped-row-count
  [rows]
  (let [school-count (expected-top-level-group-count rows)
        school-region-count (->> rows (map (juxt :school :region)) distinct count)]
    (+ school-count school-region-count)))

(defn- apply-school-filter!
  [value]
  (e/click browser/*driver* school-filter-button)
  (e/wait-visible browser/*driver* school-filter-input)
  (e/fill browser/*driver* school-filter-input value)
  (e/click browser/*driver* school-filter-apply))

(defn- apply-region-filter!
  [value]
  (e/click browser/*driver* region-filter-button)
  (e/wait-visible browser/*driver* region-filter-input)
  (e/fill browser/*driver* region-filter-input value)
  (e/click browser/*driver* region-filter-apply))

(defn- parse-pagination-label
  [text]
  (when-let [[_ start end total] (re-find #"(\d+)\D+(\d+)\D+of\D+(\d+)" (or text ""))]
    {:start (Long/parseLong start)
     :end (Long/parseLong end)
     :total (Long/parseLong total)}))

(defn- pagination-state
  []
  (some-> (e/get-element-text browser/*driver* pagination-label)
          parse-pagination-label))

(defn- wait-for-pagination-state!
  [expected?]
  (e/wait-visible browser/*driver* pagination-label)
  (e/wait-predicate
   #(when-let [state (pagination-state)]
      (expected? state)))
  (pagination-state))

(deftest grouped-school-sort-and-filter-test
  (testing "grouped school column supports sorting and filtering"
    (open-datatable!)
    (group-by-school!)
    (let [school-order (expected-school-order)
          first-school (first school-order)
          last-school (last school-order)]
      (e/click browser/*driver* school-header-button)
      (e/wait-predicate #(str/includes? (first-group-text) first-school))
      (is (str/includes? (first-group-text) first-school)
          "Expected first group to match ascending school order")
      (e/click browser/*driver* school-header-button)
      (e/wait-predicate #(str/includes? (first-group-text) last-school))
      (is (str/includes? (first-group-text) last-school)
          "Expected first group to match descending school order"))
    (e/click browser/*driver* school-filter-button)
    (e/wait-visible browser/*driver* school-filter-input)
    (e/fill browser/*driver* school-filter-input "Stoicism")
    (e/click browser/*driver* school-filter-apply)
    (e/wait-predicate #(= 1 (group-row-count)))
    (let [texts (group-row-texts)
          expected-count (expected-school-count "Stoicism")
          stoic-row (first texts)]
      (is (= 1 (count texts))
          "Expected only one group after filtering by school")
      (is (str/includes? stoic-row "Stoicism")
          "Expected Stoicism group after filtering")
      (is (str/includes? stoic-row (str "(" expected-count ")"))
          "Expected Stoicism group count to match filtered rows"))

    (testing "non-grouped column sorting is disabled"
      (let [aria-disabled (e/get-element-attr browser/*driver* century-header-button "aria-disabled")
            disabled? (or (e/disabled? browser/*driver* century-header-button)
                          (= "true" aria-disabled))]
        (is disabled?
            "Expected non-grouped sort control to be disabled when grouped"))
      (let [initial-texts (group-row-texts)]
        (e/click browser/*driver* century-header-button)
        (e/wait-predicate #(= initial-texts (group-row-texts)))
        (is (= initial-texts (group-row-texts))
            "Expected non-grouped sort to leave group order unchanged")))

    (testing "filtering by non-grouped column removes empty groups"
      (open-datatable!)
      (group-by-school!)
      (e/click browser/*driver* region-filter-button)
      (e/wait-visible browser/*driver* region-filter-input)
      (e/fill browser/*driver* region-filter-input "China")
      (e/click browser/*driver* region-filter-apply)
      (e/wait-predicate #(= 2 (group-row-count)))
      (let [texts (group-row-texts)
            schools (->> texts
                         (mapcat (fn [text]
                                   (filter #(str/includes? text %)
                                           ["Confucianism" "Taoism" "Stoicism"])))
                         set)]
        (is (= 2 (count texts))
            "Expected only China groups to remain")
        (is (= #{"Confucianism" "Taoism"} schools)
            "Expected only Confucianism/Taoism groups after region filter")))))

(deftest grouped-pagination-uses-real-row-count-test
  (testing "grouped pagination totals and ranges are based on real rows"
    (open-datatable!)
    (group-by-school!)
    (let [row-count (count (:rows datatable/*state*))
          page-size 10
          expected-first-page-end (min page-size row-count)
          expected-second-page-start (inc expected-first-page-end)
          expected-second-page-end (min row-count (+ expected-first-page-end page-size))]
      (let [{:keys [start end total]} (wait-for-pagination-state!
                                       #(and (= 1 (:start %))
                                             (= expected-first-page-end (:end %))
                                             (= row-count (:total %))))]
        (is (= 1 start) "Expected pagination label to start at 1")
        (is (= expected-first-page-end end)
            "Expected first page label range to use real-row page size")
        (is (= row-count total)
            "Expected pagination label total to match real row count"))
      (e/click browser/*driver* next-page-button)
      (let [{:keys [start end total]} (wait-for-pagination-state!
                                       #(and (= expected-second-page-start (:start %))
                                             (= expected-second-page-end (:end %))
                                             (= row-count (:total %))))]
        (is (= expected-second-page-start start)
            "Expected second page label start to advance by page size")
        (is (= expected-second-page-end end)
            "Expected second page label end to cap at real row count")
        (is (= row-count total)
            "Expected second page total to remain the real row count")))))

(deftest grouped-school-sort-available-on-group-column-test
  (testing "group column menu exposes sorting and updates group order"
    (open-datatable!)
    (group-by-school!)
    (e/click browser/*driver* group-menu-button)
    (let [asc-available? (e/exists? browser/*driver* group-sort-asc-item)
          desc-available? (e/exists? browser/*driver* group-sort-desc-item)]
      (is asc-available?
          "Expected group column menu to offer sort ascending")
      (is desc-available?
          "Expected group column menu to offer sort descending")
      (when (and asc-available? desc-available?)
        (let [school-order (expected-school-order)
              first-school (first school-order)
              last-school (last school-order)]
          (e/click browser/*driver* group-sort-asc-item)
          (e/wait-predicate #(str/includes? (first-group-text) first-school))
          (is (str/includes? (first-group-text) first-school)
              "Expected group column sort ascending to order groups")
        (e/click browser/*driver* group-menu-button)
        (e/click browser/*driver* group-sort-desc-item)
          (e/wait-predicate #(str/includes? (first-group-text) last-school))
           (is (str/includes? (first-group-text) last-school)
               "Expected group column sort descending to order groups"))))))

(deftest grouped-school-and-region-filter-by-school-only-test
  (testing "grouping by school and region supports school-only filtering"
    (open-datatable!)
    (group-by-school-and-region!)
    (apply-school-filter! "Stoicism")
    (let [filtered-rows (filter #(= "Stoicism" (:school %)) (:rows datatable/*state*))
          expected-top-level (expected-top-level-group-count filtered-rows)
          expected-total (expected-grouped-row-count filtered-rows)]
      (e/wait-predicate #(= expected-total (group-row-count)))
      (is (= expected-top-level (top-level-group-row-count))
          "Expected school-only filter to keep only matching school groups")
      (is (= expected-total (group-row-count))
          "Expected school-only filter to keep nested region groups for that school"))

    (testing "school filter remains applied after ungroup/regroup"
      (let [filtered-rows (filter #(= "Stoicism" (:school %)) (:rows datatable/*state*))
            expected-top-level (expected-top-level-group-count filtered-rows)
            expected-total (expected-grouped-row-count filtered-rows)]
        (e/click browser/*driver* group-menu-button)
        (e/wait-visible browser/*driver* ungroup-group-item)
        (e/click browser/*driver* ungroup-group-item)
        (e/wait-predicate #(= expected-top-level (top-level-group-row-count)))
        (is (= expected-top-level (top-level-group-row-count))
            "Expected ungrouping to keep the active school filter")

        (e/click browser/*driver* region-menu-button)
        (e/wait-visible browser/*driver* group-by-region-item)
        (e/click browser/*driver* group-by-region-item)
        (e/wait-predicate #(= expected-total (group-row-count)))
        (is (= expected-top-level (top-level-group-row-count))
            "Expected regrouping to keep the active school filter")
        (is (= expected-total (group-row-count))
            "Expected regrouping to restore nested groups from filtered rows")))))

(deftest grouped-school-and-region-filter-by-region-only-test
  (testing "grouping by school and region supports region-only filtering"
    (open-datatable!)
    (group-by-school-and-region!)
    (apply-region-filter! "China")
    (let [filtered-rows (filter #(= "China" (:region %)) (:rows datatable/*state*))
          expected-top-level (expected-top-level-group-count filtered-rows)
          expected-total (expected-grouped-row-count filtered-rows)
          expected-schools (->> filtered-rows (map :school) set)]
      (e/wait-predicate #(= expected-total (group-row-count)))
      (let [group-texts (group-row-texts)]
      (is (= expected-top-level (top-level-group-row-count))
          "Expected region-only filter to keep only schools in that region")
      (is (= expected-total (group-row-count))
          "Expected region-only filter to keep nested groups for matching school/region pairs")
      (is (every? (fn [school]
                    (some #(str/includes? % school) group-texts))
                  expected-schools)
          "Expected group rows to include each school represented in the region")))))

(deftest grouped-school-and-region-filter-by-school-and-region-test
  (testing "grouping by school and region supports combined filtering"
    (open-datatable!)
    (group-by-school-and-region!)
    (apply-school-filter! "Stoicism")
    (apply-region-filter! "Greece")
    (let [filtered-rows (filter #(and (= "Stoicism" (:school %))
                                      (= "Greece" (:region %)))
                                (:rows datatable/*state*))
          expected-top-level (expected-top-level-group-count filtered-rows)
          expected-total (expected-grouped-row-count filtered-rows)]
      (e/wait-predicate #(= expected-total (group-row-count)))
      (let [group-texts (group-row-texts)]
      (is (= expected-top-level (top-level-group-row-count))
          "Expected combined filters to keep only the matching school group")
      (is (= expected-total (group-row-count))
          "Expected combined filters to keep only matching school-region groups")
      (is (some #(str/includes? % "Stoicism") group-texts)
          "Expected grouped rows to include the selected school")
      (is (not-any? #(str/includes? % "Rome") group-texts)
          "Expected combined filters to exclude non-matching regions")))))
