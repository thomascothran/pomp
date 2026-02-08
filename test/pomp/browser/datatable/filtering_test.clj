(ns pomp.browser.datatable.filtering-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
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

(def name-filter-button
  {:css "button[popovertarget='filter-name']"})

(def name-filter-input
  {:css "#filter-name input[name='filterVal']"})

(def name-filter-apply
  {:xpath "//div[@id='filter-name']//button[normalize-space(text())='Apply']"})

(def name-filter-clear
  {:xpath "//div[@id='filter-name']//button[normalize-space(text())='Clear']"})

(def century-filter-button
  {:css "button[popovertarget='filter-century']"})

(def century-filter-input
  {:css "#filter-century input[name='filterVal']"})

(def century-filter-apply
  {:xpath "//div[@id='filter-century']//button[normalize-space(text())='Apply']"})

(def table-rows
  {:css "#datatable tbody tr"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- visible-row-count
  []
  (count (e/query-all browser/*driver* table-rows)))

(defn- show-popover!
  [popover-id]
  (e/js-execute browser/*driver*
                (str "document.getElementById('" popover-id "').showPopover();")))

(defn- popover-open?
  [popover-id]
  (boolean (e/js-execute browser/*driver*
                         (str "return document.getElementById('" popover-id "').matches(':popover-open');"))))

(defn- click-popover-button!
  [popover-id label]
  (e/js-execute
   browser/*driver*
   (str "var popoverId = arguments[0];"
        "var label = arguments[1];"
        "var buttons = Array.from(document.querySelectorAll('#' + popoverId + ' button'));"
        "var target = buttons.find(btn => btn.textContent.trim() === label);"
        "if (target) { target.click(); return true; }"
        "return false;")
   popover-id
   label))

(defn- expected-filter-rows
  [value]
  (let [query-fn (:query-fn datatable/*state*)
        {:keys [rows]} (query-fn {:filters {:name [{:type "string" :op "contains" :value value}]}
                                  :sort []
                                  :page {:size 10 :current 0}}
                                 nil)]
    rows))

(deftest filter-by-name-test
  (testing "filtering by name limits visible rows"
    (open-datatable!)
    (e/click browser/*driver* name-filter-button)
    (e/wait-visible browser/*driver* name-filter-input)
    (e/fill browser/*driver* name-filter-input "Socrates")
    (let [expected (expected-filter-rows "Socrates")]
      (e/click browser/*driver* name-filter-apply)
      (e/wait-predicate #(= (count expected) (visible-row-count)))
      (is (= (count expected) (visible-row-count))
          "Expected row count to match filtered results")
      (is (= (-> expected first :name) (first-cell-text))
          "Expected first row to match filtered value"))))

(deftest filter-by-name-empty-test
  (testing "empty name filter keeps all rows"
    (open-datatable!)
    (show-popover! "filter-name")
    (e/wait-visible browser/*driver* name-filter-input)
    (e/fill browser/*driver* name-filter-input "")
    (e/click browser/*driver* name-filter-apply)
    (e/wait-predicate #(= 10 (visible-row-count)))
    (is (= 10 (visible-row-count))
        "Expected empty name filter to keep all rows")
    (is (not (popover-open? "filter-name"))
        "Expected name filter popover to close")))

(deftest filter-by-name-special-chars-test
  (testing "name filter with special chars yields no rows"
    (open-datatable!)
    (show-popover! "filter-name")
    (e/wait-visible browser/*driver* name-filter-input)
    (e/fill browser/*driver* name-filter-input "!@#%")
    (e/click browser/*driver* name-filter-apply)
    (e/wait-predicate #(zero? (visible-row-count)))
    (is (= 0 (visible-row-count))
        "Expected special characters to match no rows")
    (is (not (popover-open? "filter-name"))
        "Expected name filter popover to close")))

(deftest filter-by-century-non-numeric-test
  (testing "non-numeric century filter is ignored"
    (open-datatable!)
    (show-popover! "filter-century")
    (e/wait-visible browser/*driver* century-filter-input)
    (e/fill browser/*driver* century-filter-input "abc")
    (e/click browser/*driver* century-filter-apply)
    (e/wait-predicate #(= 10 (visible-row-count)))
    (is (= 10 (visible-row-count))
        "Expected non-numeric century filter to keep all rows")
    (is (not (popover-open? "filter-century"))
        "Expected century filter popover to close")))

(deftest filter-by-name-clear-closes-popover-test
  (testing "clear closes name popover and resets rows"
    (open-datatable!)
    (show-popover! "filter-name")
    (e/wait-visible browser/*driver* name-filter-input)
    (e/fill browser/*driver* name-filter-input "Socrates")
    (e/click browser/*driver* name-filter-apply)
    (e/wait-predicate #(< (visible-row-count) 10))
    (show-popover! "filter-name")
    (e/wait-visible browser/*driver* name-filter-clear)
    (click-popover-button! "filter-name" "Clear")
    (e/wait-predicate #(= 10 (visible-row-count)))
    (is (= 10 (visible-row-count))
        "Expected clear to reset row count")
    (is (not (popover-open? "filter-name"))
        "Expected name filter popover to close")))
