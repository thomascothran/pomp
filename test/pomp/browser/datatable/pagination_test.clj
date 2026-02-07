(ns pomp.browser.datatable.pagination-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once (browser/server-fixture {:app-handler browser/default-app-handler}) browser/driver-fixture browser/datatable-state-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def page-size-select
  {:css "#datatable select.select-ghost.select-sm.font-medium"})

(def first-page-button
  {:xpath "//button[normalize-space(text())='«']"})

(def prev-page-button
  {:xpath "//button[normalize-space(text())='‹']"})

(def next-page-button
  {:xpath "//button[normalize-space(text())='›']"})

(def last-page-button
  {:xpath "//button[normalize-space(text())='»']"})

(defn- open-datatable!
  []
  (e/go browser/*driver* browser/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- visible-row-count
  []
  (count (e/query-all browser/*driver* {:css "#datatable tbody tr"})))

(defn- expected-page-first-name
  [page-number]
  (let [query-fn (:query-fn browser/*state*)
        {:keys [rows]} (query-fn {:filters {}
                                  :sort []
                                  :page {:size 10 :current page-number}}
                                 nil)]
    (-> rows first :name)))

(deftest pagination-next-page-test
  (testing "next page updates the first row"
    (open-datatable!)
    (e/click browser/*driver* next-page-button)
    (e/wait browser/*driver* 1)
    (is (= (expected-page-first-name 1) (first-cell-text))
        "Expected first row on page 2")))

(deftest pagination-page-size-change-test
  (testing "changing page size updates row count and disables paging"
    (open-datatable!)
    (is (= 10 (visible-row-count))
        "Expected default page size row count")
    (e/select browser/*driver* page-size-select "25")
    (e/wait browser/*driver* 1)
    (is (= 15 (visible-row-count))
        "Expected all rows visible at page size 25")
    (is (e/disabled? browser/*driver* next-page-button)
        "Expected next disabled when all rows fit")
    (is (e/disabled? browser/*driver* last-page-button)
        "Expected last disabled when all rows fit")))

(deftest pagination-first-last-buttons-test
  (testing "first and last buttons navigate between pages"
    (open-datatable!)
    (e/click browser/*driver* last-page-button)
    (e/wait browser/*driver* 1)
    (is (= (expected-page-first-name 1) (first-cell-text))
        "Expected first row on last page")
    (is (e/disabled? browser/*driver* next-page-button)
        "Expected next disabled on last page")
    (is (e/disabled? browser/*driver* last-page-button)
        "Expected last disabled on last page")
    (e/click browser/*driver* first-page-button)
    (e/wait browser/*driver* 1)
    (is (= (expected-page-first-name 0) (first-cell-text))
        "Expected first row on first page")
    (is (e/disabled? browser/*driver* first-page-button)
        "Expected first disabled on first page")
    (is (e/disabled? browser/*driver* prev-page-button)
        "Expected prev disabled on first page")))
