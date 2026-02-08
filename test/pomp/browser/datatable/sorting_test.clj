(ns pomp.browser.datatable.sorting-test
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

(def name-header-button
  {:xpath "//th//button[.//span[contains(@class,'font-semibold') and normalize-space(text())='Name']]"})

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- expected-first-name
  [direction]
  (let [query-fn (:query-fn datatable/*state*)
        {:keys [rows]} (query-fn {:filters {}
                                  :sort [{:column "name" :direction direction}]
                                  :page {:size 10 :current 0}}
                                 nil)]
    (-> rows first :name)))

(deftest sort-by-name-asc-desc-test
  (testing "sorting by name updates the first row"
    (open-datatable!)
    (let [expected-asc (expected-first-name "asc")
          expected-desc (expected-first-name "desc")]
    (e/click browser/*driver* name-header-button)
    (e/wait-has-text browser/*driver* first-name-cell expected-asc)
    (is (= expected-asc (first-cell-text))
        "Expected ascending sort after first click")
    (e/click browser/*driver* name-header-button)
    (e/wait-has-text browser/*driver* first-name-cell expected-desc)
    (is (= expected-desc (first-cell-text))
        "Expected descending sort after second click"))))
