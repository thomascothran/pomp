(ns pomp.browser.datatable.editing-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [etaoin.keys :as keys]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once (browser/server-fixture {:app-handler browser/default-app-handler}) browser/driver-fixture browser/datatable-state-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def first-name-edit-button
  {:css "#datatable td[data-row='0'][data-col='0'] button[title='Edit']"})

(def first-name-input
  {:css "#datatable td[data-row='0'][data-col='0'] input"})

(def first-name-save-button
  {:css "#datatable td[data-row='0'][data-col='0'] button[title='Save']"})

(def century-cell
  {:css "#datatable td[data-row='0'][data-col='1']"})

(def century-edit-button
  {:css "#datatable td[data-row='0'][data-col='1'] button[title='Edit']"})

(def century-input
  {:css "#datatable td[data-row='0'][data-col='1'] input"})

(defn- open-datatable!
  []
  (e/go browser/*driver* browser/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- first-cell-text
  []
  (e/get-element-text browser/*driver* first-name-cell))

(defn- edit-first-name!
  [value]
  (e/click browser/*driver* first-name-edit-button)
  (e/wait-visible browser/*driver* first-name-input)
  (e/fill browser/*driver* first-name-input (keys/with-ctrl "a") value)
  (e/click browser/*driver* first-name-save-button)
  (e/wait browser/*driver* 1))

(defn- open-century-edit!
  []
  (e/click browser/*driver* century-edit-button)
  (e/wait-visible browser/*driver* century-input))

(defn- press-key!
  [key]
  (let [keyboard (-> (e/make-key-input)
                     (e/add-key-press key))]
    (e/perform-actions browser/*driver* keyboard)))

(defn- edit-first-name-with-key!
  [value key]
  (e/click browser/*driver* first-name-edit-button)
  (e/wait-visible browser/*driver* first-name-input)
  (e/fill browser/*driver* first-name-input (keys/with-ctrl "a") value)
  (press-key! key)
  (e/wait browser/*driver* 1))

(deftest edit-name-cell-test
  (testing "editing a string cell updates the display"
    (open-datatable!)
    (let [original-name (first-cell-text)
          updated-name (str original-name " Updated")]
      (edit-first-name! updated-name)
      (is (= updated-name (first-cell-text))
          "Expected updated name after save")
      (edit-first-name! original-name)
      (is (= original-name (first-cell-text))
          "Expected name reverted after cleanup"))))

(deftest edit-name-cell-enter-saves-test
  (testing "pressing Enter saves edits"
    (open-datatable!)
    (let [original-name (first-cell-text)
          updated-name (str original-name " Entered")]
      (edit-first-name-with-key! updated-name keys/enter)
      (is (= updated-name (first-cell-text))
          "Expected updated name after Enter")
      (edit-first-name! original-name)
      (is (= original-name (first-cell-text))
          "Expected name reverted after cleanup"))))

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
      (e/click browser/*driver* first-name-edit-button)
      (e/wait-visible browser/*driver* first-name-input)
      (e/fill browser/*driver* first-name-input (keys/with-ctrl "a") updated-name)
      (e/click browser/*driver* century-cell)
      (e/wait browser/*driver* 1)
      (is (= original-name (first-cell-text))
          "Expected name unchanged after click away"))))

(deftest edit-century-cell-filters-non-numeric-test
  (testing "numeric input filters non-numeric characters"
    (open-datatable!)
    (open-century-edit!)
    (e/fill browser/*driver* century-input (keys/with-ctrl "a") "123abc")
    (is (= "123" (e/get-element-value browser/*driver* century-input))
        "Expected century input to filter non-numeric characters")
    (press-key! keys/escape)))
