(ns pomp.browser.datatable.column-reorder-test
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

(def header-labels
  {:css "#datatable thead th span.font-semibold"})

(def name-header-target
  {:xpath "//th[.//span[contains(@class,'font-semibold') and normalize-space(text())='Name']]"})

(def region-drag-handle
  {:xpath "//th//button[@draggable='true'][.//span[contains(@class,'font-semibold') and normalize-space(text())='Region']]"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- header-texts
  []
  (mapv #(e/get-element-text-el browser/*driver* %)
        (e/query-all browser/*driver* header-labels)))

(defn- drag-region-before-name!
  []
  (e/drag-and-drop browser/*driver* region-drag-handle name-header-target)
  (e/wait browser/*driver* 1))

(deftest drag-region-before-name-reorders-columns-test
  (testing "dragging Region before Name reorders the columns"
    (open-datatable!)
    (is (= ["Name" "Century" "School" "Region" "Verified"] (header-texts))
        "Expected default column order")
    (drag-region-before-name!)
    (is (= ["Region" "Name" "Century" "School" "Verified"] (header-texts))
        "Expected Region to move before Name")))
