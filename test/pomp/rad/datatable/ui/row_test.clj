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

;; =============================================================================
;; Editable cell tests
;; =============================================================================

(defn- find-elements
  "Recursively finds all elements matching a predicate in hiccup structure."
  [pred hiccup]
  (cond
    (pred hiccup) [hiccup]
    (vector? hiccup) (mapcat #(find-elements pred %) hiccup)
    (seq? hiccup) (mapcat #(find-elements pred %) hiccup)
    :else []))

(defn- find-input
  "Finds the input element in a hiccup structure.
   Matches keywords like :input or :input.input-xs.w-full"
  [hiccup]
  (first (find-elements #(and (vector? %)
                              (keyword? (first %))
                              (clojure.string/starts-with? (name (first %)) "input"))
                        hiccup)))

(defn- find-span
  "Finds span elements in a hiccup structure.
   Matches keywords like :span or :span.flex-1"
  [hiccup]
  (find-elements #(and (vector? %)
                       (keyword? (first %))
                       (clojure.string/starts-with? (name (first %)) "span"))
                 hiccup))

(defn- find-button
  "Finds button elements in hiccup.
   Matches keywords like :button or :button.btn.btn-ghost"
  [hiccup]
  (find-elements #(and (vector? %)
                       (keyword? (first %))
                       (clojure.string/starts-with? (name (first %)) "button"))
                 hiccup))

(deftest render-editable-cell-structure-test
  (testing "editable cell renders input element with correct signal binding"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)]
      ;; Has td wrapper
      (is (= :td (first result)))
      ;; Contains input
      (is (some? input))
      ;; Input has correct data-bind to cell signal path
      (let [attrs (second input)]
        (is (clojure.string/includes? (:data-bind attrs) "datatable.philosophers.cells.123.name")))))

  (testing "editable cell renders span with display value"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          spans (find-span result)]
      ;; Contains span with display value (find span with ID containing "cell-")
      (is (some #(and (map? (second %))
                      (:id (second %))
                      (clojure.string/includes? (:id (second %)) "cell-"))
                spans))))

  (testing "editable cell has pencil button to enter edit mode"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          buttons (find-button result)]
      ;; Has at least one button (pencil for edit)
      (is (>= (count buttons) 1))
      ;; One button has edit handler
      (is (some #(let [attrs (second %)]
                   (and (:data-on:click attrs)
                        (clojure.string/includes? (:data-on:click attrs) "editing")))
                buttons))))

  (testing "editable cell has checkmark button to save"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          buttons (find-button result)]
      ;; One button has save handler with @post
      (is (some #(let [attrs (second %)]
                   (and (:data-on:click attrs)
                        (clojure.string/includes? (:data-on:click attrs) "@post")))
                buttons)))))

(deftest render-editable-cell-pencil-click-test
  (testing "pencil button click sets editing signal and initializes cell value"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          buttons (find-button result)
          edit-button (first (filter #(let [attrs (second %)]
                                        (and (:data-on:click attrs)
                                             (clojure.string/includes? (:data-on:click attrs) "editing")
                                             (not (clojure.string/includes? (:data-on:click attrs) "@post"))))
                                     buttons))
          click-handler (-> edit-button second :data-on:click)]
      ;; Clears cell selection to avoid interference
      (is (clojure.string/includes? click-handler "cellSelection = {}"))
      ;; Sets editing signal
      (is (clojure.string/includes? click-handler "editing"))
      (is (clojure.string/includes? click-handler "rowId"))
      (is (clojure.string/includes? click-handler "'123'"))
      ;; Sets cell value signal
      (is (clojure.string/includes? click-handler "cells"))
      ;; Stops propagation to avoid cell selection
      (is (clojure.string/includes? click-handler "stopPropagation")))))

(deftest render-editable-cell-mousedown-skips-when-editing-test
  (testing "mousedown on editable cell is skipped when editing"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      (is (clojure.string/includes? mousedown-handler "if ($datatable.philosophers.editing.rowId) return"))))

  (testing "mousedown on non-editable cell is skipped when any cell is editing"
    (let [result (row/render-cell {:value "Athens"
                                   :row {:id 123 :name "Plato" :city "Athens"}
                                   :col {:key :city}
                                   :table-id "philosophers"
                                   :row-idx 0
                                   :col-idx 1})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      ;; Uses optional chaining since editing signal may not exist in non-editable tables
      (is (clojure.string/includes? mousedown-handler "if ($datatable.philosophers.editing?.rowId) return")))))

(deftest render-editable-cell-escape-test
  (testing "escape clears editing signal and removes cell value"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          attrs (second input)
          keydown-handler (:data-on:keydown attrs)]
      ;; Handles Escape key
      (is (clojure.string/includes? keydown-handler "Escape"))
      ;; Clears editing signal (sets to null)
      (is (clojure.string/includes? keydown-handler "null"))
      ;; Sets submitInProgress flag to prevent blur from submitting
      (is (clojure.string/includes? keydown-handler "submitInProgress")))))

(deftest render-editable-cell-blur-test
  (testing "blur cancels edit unless submitInProgress flag is set"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          attrs (second input)
          blur-handler (:data-on:blur attrs)]
      ;; Has blur handler
      (is (some? blur-handler))
      ;; Checks submitInProgress flag and returns early if set
      (is (clojure.string/includes? blur-handler "submitInProgress"))
      (is (clojure.string/includes? blur-handler "return"))
      ;; Clears editing signal when not in progress
      (is (clojure.string/includes? blur-handler "editing"))
      (is (clojure.string/includes? blur-handler "null")))))

(deftest render-editable-cell-enter-test
  (testing "enter submits and clears editing"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          attrs (second input)
          keydown-handler (:data-on:keydown attrs)]
      ;; Handles Enter key
      (is (clojure.string/includes? keydown-handler "Enter"))
      ;; Posts to data-url
      (is (clojure.string/includes? keydown-handler "@post"))
      (is (clojure.string/includes? keydown-handler "/data")))))

(deftest render-cell-editable-delegation-test
  (testing "editable column uses render-editable-cell"
    (let [result (row/render-cell {:value "Plato"
                                   :row-id "123"
                                   :row {:id 123 :name "Plato"}
                                   :col {:key :name :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          input (find-input result)]
      ;; Should contain input element for editable cell
      (is (some? input))))

  (testing "non-editable column renders normally without input"
    (let [result (row/render-cell {:value "Plato"
                                   :row {:id 123 :name "Plato"}
                                   :col {:key :name}
                                   :table-id "philosophers"
                                   :row-idx 0
                                   :col-idx 0})
          input (find-input result)]
      ;; Should NOT contain input element
      (is (nil? input)))))

