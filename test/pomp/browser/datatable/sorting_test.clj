(ns pomp.browser.datatable.sorting-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once browser/driver-fixture browser/datatable-state-fixture)

(def name-header-button
  {:xpath "//th//button[.//span[contains(@class,'font-semibold') and normalize-space(text())='Name']]"})

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(defn- open-datatable!
  []
  (e/go browser/*driver* browser/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- expected-first-name
  [direction]
  (let [query-fn (:query-fn browser/*state*)
        {:keys [rows]} (query-fn {:filters {}
                                  :sort [{:column "name" :direction direction}]
                                  :page {:size 10 :current 0}}
                                 nil)]
    (-> rows first :name)))

(deftest sort-by-name-asc-desc-test
  (testing "sorting by name updates the first row"
    (open-datatable!)
    (e/click browser/*driver* name-header-button)
    (e/wait browser/*driver* 1)
    (is (= (expected-first-name "asc") (first-cell-text))
        "Expected ascending sort after first click")
    (e/click browser/*driver* name-header-button)
    (e/wait browser/*driver* 1)
    (is (= (expected-first-name "desc") (first-cell-text))
        "Expected descending sort after second click")))
