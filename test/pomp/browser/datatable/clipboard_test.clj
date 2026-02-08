(ns pomp.browser.datatable.clipboard-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [etaoin.keys :as keys]
            [pomp.test.fixtures.browser :as browser]
            [pomp.test.fixtures.browser.datatable :as datatable]))

(use-fixtures :once
  (browser/server-fixture {:routes datatable/routes
                           :middlewares datatable/middlewares
                           :router-data datatable/router-data})
  browser/driver-fixture
  datatable/datatable-db-fixture
  datatable/datatable-state-fixture)

(def start-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def end-cell
  {:css "#datatable td[data-row='1'][data-col='1']"})

(def selected-cells
  {:css "#datatable td.bg-info\\/20"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* start-cell))

(defn- drag-select-2x2!
  []
  (e/drag-and-drop browser/*driver* start-cell end-cell)
  (e/wait-visible browser/*driver* selected-cells))

(defn- stub-clipboard!
  []
  (e/js-execute
   browser/*driver*
   (str "window.__copiedText = null;"
        "try { Object.defineProperty(navigator, 'clipboard', { value: { writeText: function(text) { window.__copiedText = text; return Promise.resolve(); } }, configurable: true }); }"
        "catch (e) { navigator.clipboard = { writeText: function(text) { window.__copiedText = text; return Promise.resolve(); } }; }")))

(defn- press-copy!
  []
  (let [keyboard (-> (e/make-key-input)
                     (e/add-key-down keys/control-left)
                     (e/add-key-press "c")
                     (e/add-key-up keys/control-left))]
    (e/perform-actions browser/*driver* keyboard)))

(defn- press-escape!
  []
  (let [keyboard (-> (e/make-key-input)
                     (e/add-key-press keys/escape))]
    (e/perform-actions browser/*driver* keyboard)))

(defn- clipboard-text
  []
  (e/js-execute browser/*driver* "return window.__copiedText;"))

(defn- copy-attempt-count
  []
  (or (e/js-execute browser/*driver* "return window.__pompDatatableCopyAttemptCount;") 0))

(deftest copy-selection-to-clipboard-test
  (testing "copying a selection writes TSV to clipboard"
    (open-datatable!)
    (drag-select-2x2!)
    (stub-clipboard!)
    (let [expected-text "Socrates\t5th BC\nPlato\t4th BC"]
      (press-copy!)
      (e/wait-predicate #(= expected-text (clipboard-text)))
      (is (= expected-text (clipboard-text))
          "Expected TSV copy from selected 2x2 range"))))

(deftest escape-clear-prevents-copy-test
  (testing "copy shortcut does nothing after Escape clears selection"
    (open-datatable!)
    (drag-select-2x2!)
    (stub-clipboard!)
    (press-escape!)
    (e/wait-predicate #(zero? (count (e/query-all browser/*driver* selected-cells))))
    (let [attempt-count-before (copy-attempt-count)]
      (press-copy!)
      (e/wait-predicate #(< attempt-count-before (copy-attempt-count))))
    (is (nil? (clipboard-text))
        "Expected no clipboard write when selection has been cleared")))
