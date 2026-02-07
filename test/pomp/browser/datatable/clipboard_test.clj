(ns pomp.browser.datatable.clipboard-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [etaoin.keys :as keys]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once browser/server-fixture browser/driver-fixture browser/datatable-state-fixture)

(def start-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def end-cell
  {:css "#datatable td[data-row='1'][data-col='1']"})

(def selected-cells
  {:css "#datatable td.bg-info\\/20"})

(defn- open-datatable!
  []
  (e/go browser/*driver* browser/base-url)
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

(defn- clipboard-text
  []
  (e/js-execute browser/*driver* "return window.__copiedText;"))

(deftest copy-selection-to-clipboard-test
  (testing "copying a selection writes TSV to clipboard"
    (open-datatable!)
    (drag-select-2x2!)
    (stub-clipboard!)
    (press-copy!)
    (e/wait browser/*driver* 1)
    (is (= "Socrates\t5th BC\nPlato\t4th BC" (clipboard-text))
        "Expected TSV copy from selected 2x2 range")))
