(ns pomp.browser.datatable.visibility-fn-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]
            [pomp.test.fixtures.browser.datatable :as datatable]))

(use-fixtures :once
  (browser/server-fixture {:routes datatable/visibility-routes
                           :middlewares datatable/middlewares
                           :router-data datatable/router-data})
  browser/driver-fixture
  datatable/datatable-db-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def table-rows
  {:css "#datatable tbody tr"})

(defn- open-datatable!
  [scope]
  (e/go browser/*driver* (str datatable/visibility-base-url "?scope=" scope))
  (e/wait-visible browser/*driver* first-name-cell))

(defn- table-text
  []
  (-> (e/get-element-text browser/*driver* {:css "#datatable tbody"})
      (str/replace #"\s+" " ")
      str/trim))

(defn- visible-row-count
  []
  (count (e/query-all browser/*driver* table-rows)))

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

(deftest visibility-fn-scope-filters-browser-rows-test
  (testing "scope query param includes allowed rows and excludes forbidden rows"
    (open-datatable! "rome")
    (let [rows-text (table-text)]
      (is (str/includes? rows-text "Marcus Aurelius")
          "Expected Rome-scoped rows to include Rome philosophers")
      (is (str/includes? rows-text "Seneca")
          "Expected Rome-scoped rows to include another Rome philosopher")
      (is (not (str/includes? rows-text "Socrates"))
          "Expected Rome-scoped rows to exclude Greece philosophers"))

    (is (true? (fill-global-search! "Socrates"))
        "Expected to fill global search input")
    (e/wait-predicate #(zero? (visible-row-count)))
    (is (zero? (visible-row-count))
        "Expected global search to return zero rows for forbidden names")))
