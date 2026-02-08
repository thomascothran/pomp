(ns pomp.browser.datatable.column-visibility-test
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

(def region-header
  {:xpath "//th//span[contains(@class,'font-semibold') and normalize-space(text())='Region']"})

(def region-menu-button
  {:css "button[popovertarget='col-menu-region']"})

(def hide-region-item
  {:xpath "//div[@id='col-menu-region']//a[contains(normalize-space(.), 'Hide column')]"})

(def columns-menu-button
  {:css "button[popovertarget='columns-menu']"})

(def region-checkbox
  {:xpath "//div[@id='columns-menu']//label[contains(normalize-space(.), 'Region')]//input"})

(def columns-apply-button
  {:xpath "//div[@id='columns-menu']//button[normalize-space(text())='Apply']"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- region-header-present?
  []
  (seq (e/query-all browser/*driver* region-header)))

(deftest hide-and-show-region-column-test
  (testing "hiding and showing a column updates the header"
    (open-datatable!)
    (is (region-header-present?) "Expected Region column visible initially")
    (e/click browser/*driver* region-menu-button)
    (e/wait-visible browser/*driver* hide-region-item)
    (e/click browser/*driver* hide-region-item)
    (e/wait browser/*driver* 1)
    (is (not (region-header-present?)) "Expected Region column hidden")
    (e/click browser/*driver* columns-menu-button)
    (e/wait-visible browser/*driver* columns-apply-button)
    (e/click browser/*driver* region-checkbox)
    (e/click browser/*driver* columns-apply-button)
    (e/wait browser/*driver* 1)
    (is (region-header-present?) "Expected Region column visible again")))
