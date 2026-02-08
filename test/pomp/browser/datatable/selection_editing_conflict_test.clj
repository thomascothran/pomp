(ns pomp.browser.datatable.selection-editing-conflict-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once (browser/server-fixture {:app-handler browser/default-app-handler}) browser/driver-fixture browser/datatable-state-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def first-century-cell
  {:css "#datatable td[data-row='0'][data-col='1']"})

(def second-school-cell
  {:css "#datatable td[data-row='1'][data-col='2']"})

(def first-name-display-text
  {:css "#datatable td[data-row='0'][data-col='0'] span[id^='cell-']"})

(def first-name-input
  {:css "#datatable td[data-row='0'][data-col='0'] input"})

(def selected-cells
  {:css "#datatable td.bg-info\\/20"})

(defn- open-datatable!
  []
  (e/go browser/*driver* browser/base-url)
  (e/wait-visible browser/*driver* first-name-cell)
  (e/wait browser/*driver* 1))

(defn- selected-count
  []
  (count (e/query-all browser/*driver* selected-cells)))

(defn- drag-select!
  [start end]
  (e/drag-and-drop browser/*driver* start end)
  (e/wait-visible browser/*driver* selected-cells))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- double-click-display-text!
  [display-selector]
  (e/wait-visible browser/*driver* display-selector)
  (e/js-execute
   browser/*driver*
   (str "var selector = arguments[0];"
        "var target = document.querySelector(selector);"
        "if (!target) { return false; }"
        "target.dispatchEvent(new MouseEvent('dblclick', {bubbles: true, cancelable: true, view: window}));"
        "return true;")
   (:css display-selector)))

(defn- start-edit!
  []
  (double-click-display-text! first-name-display-text)
  (e/wait-visible browser/*driver* first-name-input))

(defn- edit-first-name!
  [value]
  (e/fill browser/*driver* first-name-input value))

(deftest edit-clears-selection-test
  (testing "entering edit mode clears existing selection"
    (open-datatable!)
    (drag-select! first-name-cell first-century-cell)
    (is (= 2 (selected-count)) "Expected two selected cells before edit")
    (start-edit!)
    (is (zero? (selected-count)) "Expected selection cleared on edit")))

(deftest drag-cancels-edit-and-selects-test
  (testing "drag selection cancels edit without saving"
    (open-datatable!)
    (let [original (first-cell-text)]
      (start-edit!)
      (edit-first-name! "Transient Edit")
      (drag-select! first-century-cell second-school-cell)
      (is (= 4 (selected-count)) "Expected drag selection after canceling edit")
      (is (= original (first-cell-text)) "Expected edit cancelled without saving"))))
