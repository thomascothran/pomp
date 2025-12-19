(ns pomp.rad.datatable.ui.row-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.ui.row :as row]))

;; =============================================================================
;; render-cell tests
;; =============================================================================

(defn- extract-cell-content
  "Extracts the display content from a rendered cell (last element of [:td ...])."
  [cell]
  (last cell))

(deftest render-cell-displays-value-test
  (testing "displays raw value by default"
    (let [cell (row/render-cell {:value "Alice"
                                 :row {:name "Alice" :age 30}
                                 :col {:key :name :label "Name"}
                                 :row-idx 0
                                 :col-idx 0
                                 :table-id "test"})]
      (is (= "Alice" (extract-cell-content cell))))))

(deftest render-cell-uses-column-render-fn-test
  (testing "uses :render fn from column when provided"
    (let [cell (row/render-cell {:value "alice"
                                 :row {:name "alice" :age 30}
                                 :col {:key :name
                                       :label "Name"
                                       :render (fn [value _row] (clojure.string/upper-case value))}
                                 :row-idx 0
                                 :col-idx 0
                                 :table-id "test"})]
      (is (= "ALICE" (extract-cell-content cell))))))

;; =============================================================================
;; render-row with :display-fn tests
;; Column :display-fn should transform the value before passing to render-cell
;; =============================================================================

(defn- extract-cell-values
  "Extracts display values from all cells in a rendered row.
   Returns a vector of the last element of each [:td ...] cell.
   Handles the for-generated sequence inside the row."
  [row-hiccup]
  (->> row-hiccup
       (mapcat (fn [item]
                 (cond
                   ;; Direct [:td ...] element
                   (and (vector? item) (= :td (first item)))
                   [item]
                   ;; Sequence of [:td ...] elements (from for)
                   (seq? item)
                   (filter #(and (vector? %) (= :td (first %))) item)
                   :else
                   [])))
       (mapv last)))

(deftest render-row-uses-display-fn-test
  (testing "column :display-fn transforms value for display"
    (let [format-century (fn [row]
                           (let [n (:century row)]
                             (str (Math/abs n)
                                  (case (mod (Math/abs n) 10)
                                    1 "st"
                                    2 "nd"
                                    3 "rd"
                                    "th")
                                  (if (neg? n) " BC" " AD"))))
          cols [{:key :name :label "Name" :type :string}
                {:key :century :label "Century" :type :number :display-fn format-century}]
          row {:id 1 :name "Socrates" :century -5}
          rendered (row/render-row {:cols cols
                                    :row row
                                    :row-id 1
                                    :row-idx 0
                                    :table-id "test"})
          values (extract-cell-values rendered)]
      ;; Name should display as-is
      (is (= "Socrates" (first values)))
      ;; Century should use display-fn: -5 -> "5th BC"
      (is (= "5th BC" (second values)))))

  (testing "raw value is still used for data-value attribute"
    ;; This test documents that :display-fn is for display only
    ;; The :key value (-5) is still stored in data-value for copy/paste
    (let [cols [{:key :century :label "Century" :type :number
                 :display-fn (fn [row] (str (Math/abs (:century row)) " BC"))}]
          row {:century -5}
          rendered (row/render-row {:cols cols
                                    :row row
                                    :row-id 1
                                    :row-idx 0
                                    :table-id "test"})
          ;; Extract td from the for sequence
          td-cell (->> rendered
                       (filter seq?)
                       first
                       (filter #(and (vector? %) (= :td (first %))))
                       first)
          attrs (second td-cell)]
      ;; data-value should be the raw value (for cell selection copy)
      (is (= "-5" (:data-value attrs))))))
