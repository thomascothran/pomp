(ns pomp.browser.datatable.editing-test
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

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def first-name-display-text
  {:css "#datatable td[data-row='0'][data-col='0'] span[id^='cell-']"})

(def first-name-input
  {:css "#datatable td[data-row='0'][data-col='0'] input"})

(def first-name-save-button
  {:css "#datatable td[data-row='0'][data-col='0'] button[title='Save']"})

(def century-cell
  {:css "#datatable td[data-row='0'][data-col='1']"})

(def century-display-text
  {:css "#datatable td[data-row='0'][data-col='1'] span[id^='cell-']"})

(def century-input
  {:css "#datatable td[data-row='0'][data-col='1'] input"})

(def school-display-text
  {:css "#datatable td[data-row='0'][data-col='2'] span[id^='cell-']"})

(def school-cell
  {:css "#datatable td[data-row='0'][data-col='2']"})

(def school-select
  {:css "#datatable td[data-row='0'][data-col='2'] select"})

(def verified-checkbox
  {:css "#datatable td[data-row='0'][data-col='4'] input[type='checkbox']"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell)
  (e/wait-visible browser/*driver* first-name-display-text)
  (e/wait browser/*driver* 1))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- double-click-display-text!
  [display-selector editor-selector]
  (e/wait-visible browser/*driver* display-selector)
  (dotimes [_ 5]
    (when-not (e/visible? browser/*driver* editor-selector)
      (e/click browser/*driver* display-selector)
      (e/js-execute
       browser/*driver*
       (str "var selector = arguments[0];"
            "var target = document.querySelector(selector);"
            "if (!target) { return false; }"
            "target.dispatchEvent(new MouseEvent('dblclick', {bubbles: true, cancelable: true, view: window}));"
            "return true;")
       (:css display-selector))
      (e/wait browser/*driver* 0.2))))

(defn- edit-first-name!
  [value]
  (double-click-display-text! first-name-display-text first-name-input)
  (e/wait-visible browser/*driver* first-name-input)
  (e/fill browser/*driver* first-name-input (keys/with-ctrl "a") value)
  (e/click browser/*driver* first-name-save-button)
  (e/wait-invisible browser/*driver* first-name-input)
  (e/wait-visible browser/*driver* first-name-display-text))

(defn- open-century-edit!
  []
  (double-click-display-text! century-display-text century-input)
  (e/wait-visible browser/*driver* century-input))

(defn- press-key!
  [key]
  (let [keyboard (-> (e/make-key-input)
                     (e/add-key-press key))]
    (e/perform-actions browser/*driver* keyboard)))

(defn- edit-first-name-with-key!
  [value key]
  (double-click-display-text! first-name-display-text first-name-input)
  (e/wait-visible browser/*driver* first-name-input)
  (e/fill browser/*driver* first-name-input (keys/with-ctrl "a") value)
  (press-key! key)
  (e/wait-invisible browser/*driver* first-name-input)
  (e/wait-visible browser/*driver* first-name-display-text))

(defn- checkbox-selected?
  [selector]
  (e/selected? browser/*driver* selector))

(deftest edit-name-cell-test
  (testing "editing a string cell updates the display"
    (open-datatable!)
    (let [original-name (first-cell-text)
          updated-name (str original-name " Updated")]
      (edit-first-name! updated-name)
      (is (= updated-name (first-cell-text))
          "Expected updated name after save"))))

(deftest edit-name-cell-enter-saves-test
  (testing "pressing Enter saves edits"
    (open-datatable!)
    (let [original-name (first-cell-text)
          updated-name (str original-name " Entered")]
      (edit-first-name-with-key! updated-name keys/enter)
      (is (= updated-name (first-cell-text))
          "Expected updated name after Enter"))))

(deftest edit-name-cell-escape-cancels-test
  (testing "pressing Escape cancels edits"
    (open-datatable!)
    (let [original-name (first-cell-text)
          updated-name (str original-name " Escaped")]
      (edit-first-name-with-key! updated-name keys/escape)
      (is (= original-name (first-cell-text))
          "Expected name unchanged after Escape"))))

(deftest edit-name-cell-click-away-cancels-test
  (testing "clicking away cancels edits"
    (open-datatable!)
      (let [original-name (first-cell-text)
            updated-name (str original-name " Clicked")]
      (double-click-display-text! first-name-display-text first-name-input)
      (e/wait-visible browser/*driver* first-name-input)
      (e/fill browser/*driver* first-name-input (keys/with-ctrl "a") updated-name)
      (e/click browser/*driver* century-cell)
      (e/wait browser/*driver* 1)
      (is (= original-name (first-cell-text))
          "Expected name unchanged after click away"))))

(deftest editable-cell-single-click-does-not-enter-edit-mode-test
  (testing "single click on editable non-boolean display text does not enter edit mode"
    (open-datatable!)
    (e/wait-invisible browser/*driver* first-name-input)
    (e/click browser/*driver* first-name-cell)
    (e/wait-invisible browser/*driver* first-name-input)
    (is (not (e/visible? browser/*driver* first-name-input))
        "Expected single click to leave string cell in display mode")))

(deftest enum-cell-double-click-enters-edit-mode-test
  (testing "double click on editable enum display text enters edit mode"
    (open-datatable!)
    (e/wait-invisible browser/*driver* school-select)
    (double-click-display-text! school-display-text school-select)
    (e/wait-visible browser/*driver* school-select)
    (is (e/visible? browser/*driver* school-select)
        "Expected enum editor to be visible after double click")
    (press-key! keys/escape)))

(deftest boolean-cell-single-click-toggles-test
  (testing "single click toggles editable boolean cell"
    (open-datatable!)
    (let [before (checkbox-selected? verified-checkbox)]
      (e/click browser/*driver* verified-checkbox)
      (e/wait browser/*driver* 1)
      (is (not= before (checkbox-selected? verified-checkbox))
          "Expected checkbox state to toggle on single click"))))

(deftest edit-century-cell-filters-non-numeric-test
  (testing "numeric input filters non-numeric characters"
    (open-datatable!)
    (open-century-edit!)
    (e/fill browser/*driver* century-input (keys/with-ctrl "a") "123abc")
    (is (= "123" (e/get-element-value browser/*driver* century-input))
        "Expected century input to filter non-numeric characters")
    (press-key! keys/escape)))
