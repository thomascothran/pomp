(ns pomp.browser.datatable.row-selection-test
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

(def header-select-all-checkbox
  {:css "#datatable thead tr th:first-child input[type='checkbox']"})

(def row-selection-checkboxes
  {:css "#datatable tbody tr td:first-child input[type='checkbox']"})

(def first-row-selection-checkbox
  {:css "#datatable tbody tr:first-child td:first-child input[type='checkbox']"})

(def checked-row-selection-checkboxes
  {:css "#datatable tbody tr td:first-child input[type='checkbox']:checked"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell)
  (e/wait-predicate #(pos? (count (e/query-all browser/*driver* row-selection-checkboxes)))))

(defn- row-checkbox-elements
  []
  (vec (e/query-all browser/*driver* row-selection-checkboxes)))

(defn- visible-row-count
  []
  (count (row-checkbox-elements)))

(defn- selected-row-count
  []
  (count (e/query-all browser/*driver* checked-row-selection-checkboxes)))

(defn- all-visible-rows-selected?
  []
  (let [visible (visible-row-count)]
    (and (pos? visible)
         (= visible (selected-row-count)))))

(defn- no-visible-rows-selected?
  []
  (let [visible (visible-row-count)]
    (and (pos? visible)
         (zero? (selected-row-count)))))

(defn- wait-until
  [pred timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond
        (pred) true
        (> (System/currentTimeMillis) deadline) false
        :else (do
                (Thread/sleep 50)
                (recur))))))

(deftest header-select-all-and-clear-all-visible-rows-test
  (testing "header checkbox selects and then clears every visible row checkbox"
    (open-datatable!)
    (is (no-visible-rows-selected?)
        "Expected visible rows to start unchecked")

    (e/click browser/*driver* first-row-selection-checkbox)
    (is (wait-until #(= 1 (selected-row-count)) 2000)
        "Expected an individual row checkbox to toggle selection")

    (e/click browser/*driver* header-select-all-checkbox)
    (is (wait-until all-visible-rows-selected? 2000)
        "Expected header select-all to check every visible row checkbox")

    (e/click browser/*driver* header-select-all-checkbox)
    (is (wait-until no-visible-rows-selected? 2000)
        "Expected header clear-all to uncheck every visible row checkbox")))
