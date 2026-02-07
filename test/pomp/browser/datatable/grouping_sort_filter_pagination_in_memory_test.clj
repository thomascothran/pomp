(ns pomp.browser.datatable.grouping-sort-filter-pagination-in-memory-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once (browser/in-memory-server-fixture {:app-handler browser/default-in-memory-app-handler}) browser/driver-fixture browser/datatable-state-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def school-menu-button
  {:css "button[popovertarget='col-menu-school']"})

(def group-by-school-item
  {:xpath "//div[@id='col-menu-school']//a[contains(normalize-space(.), 'Group by')]"})

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
  {:css "#datatable tr.bg-base-200"})

(def group-menu-button
  {:css "button[popovertarget='col-menu-group']"})

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
  (e/go browser/*driver* browser/in-memory-base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- group-by-school!
  []
  (e/click browser/*driver* school-menu-button)
  (e/wait-visible browser/*driver* group-by-school-item)
  (e/click browser/*driver* group-by-school-item)
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
  (->> (:rows browser/*state*)
       (map :school)
       distinct
       sort
       vec))

(defn- expected-school-count
  [school]
  (->> (:rows browser/*state*)
       (filter #(= school (:school %)))
       count))

(defn- group-row-count
  []
  (count (e/query-all browser/*driver* group-row-selector)))

(defn- parse-pagination-label
  [text]
  (let [[_ start end total] (re-find #"(\d+)\D+(\d+)\D+of\D+(\d+)" text)]
    {:start (Long/parseLong start)
     :end (Long/parseLong end)
     :total (Long/parseLong total)}))

(deftest grouped-school-sort-and-filter-in-memory-test
  (testing "grouped school column supports sorting and filtering"
    (open-datatable!)
    (group-by-school!)
    (e/click browser/*driver* school-header-button)
    (e/wait browser/*driver* 1)
    (is (str/includes? (first-group-text) (first (expected-school-order)))
        "Expected first group to match ascending school order")
    (e/click browser/*driver* school-header-button)
    (e/wait browser/*driver* 1)
    (is (str/includes? (first-group-text) (last (expected-school-order)))
        "Expected first group to match descending school order")
    (e/click browser/*driver* school-filter-button)
    (e/wait-visible browser/*driver* school-filter-input)
    (e/fill browser/*driver* school-filter-input "Stoicism")
    (e/click browser/*driver* school-filter-apply)
    (e/wait browser/*driver* 1)
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
        (e/wait browser/*driver* 1)
        (is (= initial-texts (group-row-texts))
            "Expected non-grouped sort to leave group order unchanged")))

    (testing "filtering by non-grouped column removes empty groups"
      (open-datatable!)
      (group-by-school!)
      (e/click browser/*driver* region-filter-button)
      (e/wait-visible browser/*driver* region-filter-input)
      (e/fill browser/*driver* region-filter-input "China")
      (e/click browser/*driver* region-filter-apply)
      (e/wait browser/*driver* 1)
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

(deftest grouped-pagination-uses-group-count-in-memory-test
  (testing "grouped pagination pages by group count"
    (open-datatable!)
    (group-by-school!)
    (let [group-count (count (expected-school-order))
          page-size 10
          expected-first-page (min page-size group-count)
          expected-second-page (- group-count expected-first-page)]
      (is (= expected-first-page (group-row-count))
          "Expected first page to show only page-size groups")
      (let [{:keys [start end total]} (parse-pagination-label
                                       (e/get-element-text browser/*driver* pagination-label))]
        (is (= 1 start) "Expected pagination label to start at 1")
        (is (= expected-first-page end) "Expected pagination label end to match group count")
        (is (= group-count total) "Expected pagination label total to match group count"))
      (e/click browser/*driver* next-page-button)
      (e/wait browser/*driver* 1)
      (is (= expected-second-page (group-row-count))
          "Expected second page to show remaining groups"))))

(deftest grouped-school-sort-available-on-group-column-in-memory-test
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
        (e/click browser/*driver* group-sort-asc-item)
        (e/wait browser/*driver* 1)
        (is (str/includes? (first-group-text) (first (expected-school-order)))
            "Expected group column sort ascending to order groups")
        (e/click browser/*driver* group-menu-button)
        (e/click browser/*driver* group-sort-desc-item)
        (e/wait browser/*driver* 1)
        (is (str/includes? (first-group-text) (last (expected-school-order)))
            "Expected group column sort descending to order groups")))))
