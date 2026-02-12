(ns pomp.browser.datatable.pagination-test
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

(def default-page-size 10)

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- visible-row-count
  []
  (count (e/query-all browser/*driver* {:css "#datatable tbody tr"})))

(defn- total-row-count
  []
  (let [query-fn (:query-fn datatable/*state*)
        {:keys [rows]} (query-fn {:filters {}
                                  :sort []
                                  :page {:size Integer/MAX_VALUE :current 0}}
                                 nil)]
    (count rows)))

(defn- last-page-index
  [page-size]
  (max 0 (quot (dec (total-row-count)) page-size)))

(defn- expected-page-first-name
  [page-number]
  (let [query-fn (:query-fn datatable/*state*)
        {:keys [rows]} (query-fn {:filters {}
                                  :sort []
                                  :page {:size default-page-size :current page-number}}
                                 nil)]
    (-> rows first :name)))

(defn- expected-global-search-rows
  [search-text]
  (let [query-fn (:query-fn datatable/*state*)
        {:keys [rows]} (query-fn {:columns (:columns datatable/*state*)
                                  :search-string search-text
                                  :filters {}
                                  :sort []
                                  :group-by []
                                  :page {:size default-page-size :current 0}}
                                 nil)]
    rows))

(defn- global-search-debounce-action
  []
  (e/js-execute browser/*driver*
                (str "const inputs = Array.from(document.querySelectorAll('input'));"
                     "const searchInput = inputs.find((input) => {"
                     "  const debounce = input.getAttribute('data-on:input__debounce.300ms') || '';"
                     "  return debounce.includes('action=global-search');"
                     "});"
                     "return searchInput ? searchInput.getAttribute('data-on:input__debounce.300ms') : null;")))

(defn- fill-global-search!
  [search-text]
  (e/js-execute browser/*driver*
                (str "const inputs = Array.from(document.querySelectorAll('input'));"
                     "const searchInput = inputs.find((input) => {"
                     "  const debounce = input.getAttribute('data-on:input__debounce.300ms') || '';"
                     "  return debounce.includes('action=global-search');"
                     "});"
                     "if (!searchInput) { return false; }"
                     "searchInput.focus();"
                     "searchInput.value = arguments[0];"
                     "searchInput.dispatchEvent(new Event('input', {bubbles: true}));"
                     "return true;")
                search-text))

(deftest pagination-next-page-test
  (testing "next page updates the first row"
    (open-datatable!)
    (let [expected-name (expected-page-first-name 1)]
      (e/click browser/*driver* next-page-button)
      (e/wait-has-text browser/*driver* first-name-cell expected-name)
      (is (= expected-name (first-cell-text))
          "Expected first row on page 2"))))

(deftest pagination-page-size-change-test
  (testing "changing page size updates row count and disables paging"
    (open-datatable!)
    (let [selected-page-size 25
          total-rows (total-row-count)
          has-additional-pages? (> total-rows selected-page-size)]
      (is (= 10 (visible-row-count))
          "Expected default page size row count")
      (e/select browser/*driver* page-size-select (str selected-page-size))
      (e/wait-predicate #(= (min selected-page-size total-rows) (visible-row-count)))
      (is (= (min selected-page-size total-rows) (visible-row-count))
          "Expected page-size-limited row count")
      (is (= has-additional-pages? (not (e/disabled? browser/*driver* next-page-button)))
          "Expected next enabled only when more rows remain")
      (is (= has-additional-pages? (not (e/disabled? browser/*driver* last-page-button)))
          "Expected last enabled only when more rows remain"))))

(deftest pagination-first-last-buttons-test
  (testing "first and last buttons navigate between pages"
    (open-datatable!)
    (let [last-page (last-page-index default-page-size)
          expected-last-page-name (expected-page-first-name last-page)
          expected-first-page-name (expected-page-first-name 0)]
      (e/click browser/*driver* last-page-button)
      (e/wait-has-text browser/*driver* first-name-cell expected-last-page-name)
      (is (= expected-last-page-name (first-cell-text))
          "Expected first row on last page")
      (is (e/disabled? browser/*driver* next-page-button)
          "Expected next disabled on last page")
      (is (e/disabled? browser/*driver* last-page-button)
          "Expected last disabled on last page")
      (e/click browser/*driver* first-page-button)
      (e/wait-has-text browser/*driver* first-name-cell expected-first-page-name)
      (is (= expected-first-page-name (first-cell-text))
          "Expected first row on first page")
      (is (e/disabled? browser/*driver* first-page-button)
          "Expected first disabled on first page")
      (is (e/disabled? browser/*driver* prev-page-button)
          "Expected prev disabled on first page"))))

(deftest pagination-global-search-resets-page-test
  (testing "global search narrows results and resets pagination to first page"
    (open-datatable!)
    (let [search-text "Socrates"
          second-page-first-name (expected-page-first-name 1)
          expected-rows (expected-global-search-rows search-text)
          expected-first-name (-> expected-rows first :name)]
      (is (< (count expected-rows) default-page-size)
          "Expected search fixture value to narrow visible rows")
      (e/click browser/*driver* next-page-button)
      (e/wait-has-text browser/*driver* first-name-cell second-page-first-name)
      (is (not (e/disabled? browser/*driver* prev-page-button))
          "Expected to be on a page greater than index 0 before searching")
      (let [debounce-action (global-search-debounce-action)]
        (is (string? debounce-action)
            "Expected a global search input with a debounce action")
        (when (string? debounce-action)
          (is (clojure.string/includes? debounce-action "action=global-search")
              "Expected global search input debounce action to post with action=global-search")
          (is (true? (fill-global-search! search-text))
              "Expected to fill global search input")
          (is (= second-page-first-name (first-cell-text))
              "Expected rows to remain unchanged immediately before debounce fires")
          (is (not (e/disabled? browser/*driver* prev-page-button))
              "Expected pagination controls to remain on current page before debounce fires")
          (e/wait-predicate #(= (count expected-rows) (visible-row-count)))
          (e/wait-has-text browser/*driver* first-name-cell expected-first-name)
          (is (= expected-first-name (first-cell-text))
              "Expected global search to narrow rows and show first matching result")
          (is (= (count expected-rows) (visible-row-count))
              "Expected visible rows to match global search query result count")
          (is (e/disabled? browser/*driver* first-page-button)
              "Expected first-page button disabled after page reset to index 0")
          (is (e/disabled? browser/*driver* prev-page-button)
              "Expected prev-page button disabled after page reset to index 0"))))))
