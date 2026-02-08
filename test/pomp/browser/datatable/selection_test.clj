(ns pomp.browser.datatable.selection-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [etaoin.keys :as keys]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once
  (browser/server-fixture
   {:app-handler (fn [req]
                   (when (not= "/favicon.ico"
                               (:uri req))
                     (def req req))
                   (browser/default-app-handler req))})
  browser/driver-fixture
  browser/datatable-state-fixture)

(def start-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def end-cell
  {:css "#datatable td[data-row='1'][data-col='1']"})

(def selected-cells
  {:css "#datatable td.bg-info\\/20"})

(defn- open-datatable!
  []
  (e/go browser/*driver* browser/base-url)
  (e/wait browser/*driver* 3)
  (e/screenshot browser/*driver* "wtaf2.png")
  (e/wait-visible browser/*driver* start-cell))

(defn- drag-select-2x2!
  []
  (e/drag-and-drop browser/*driver* start-cell end-cell)
  (e/wait-visible browser/*driver* selected-cells))

(defn- selected-count
  []
  (count (e/query-all browser/*driver* selected-cells)))

(defn- press-escape!
  []
  (let [keyboard (-> (e/make-key-input)
                     (e/add-key-press keys/escape))]
    (e/perform-actions browser/*driver* keyboard)))

(deftest drag-selects-rectangular-cells-test
  (testing "drag selection highlights a rectangular range"
    (open-datatable!)
    (drag-select-2x2!)
    (is (= 4 (selected-count))
        "Expected a 2x2 drag to highlight four cells")))

(deftest escape-clears-selection-test
  (testing "Escape clears the current selection"
    (open-datatable!)
    (drag-select-2x2!)
    (press-escape!)
    (e/wait browser/*driver* 1)
    (is (zero? (selected-count))
        "Expected Escape to clear the selection")))

(comment
  (require '[kaocha.repl :as k])
  (k/run #'drag-selects-rectangular-cells-test))
