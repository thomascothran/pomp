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
;; Row selection signal initialization tests
;; =============================================================================

(deftest render-row-does-not-init-selection-signals-test
  (testing "row selection checkbox binds to row signal without pre-init"
    (letfn [(find-selection-input [hiccup]
              (cond
                (and (vector? hiccup)
                     (keyword? (first hiccup))
                     (clojure.string/starts-with? (name (first hiccup)) "input"))
                hiccup

                (vector? hiccup)
                (some find-selection-input hiccup)

                (seq? hiccup)
                (some find-selection-input hiccup)

                :else
                nil))]
      (let [result (row/render-row {:cols [{:key :name :label "Name"}]
                                    :row {:id "row-1" :name "Ada"}
                                    :row-id "row-1"
                                    :row-idx 0
                                    :table-id "people"
                                    :selectable? true})
            input (find-selection-input result)
            attrs (second input)]
        (is (= "datatable.people.selections.row-1" (:data-bind attrs))
            "Selection checkbox should be bound to row selection signal")
        (is (nil? (:data-signals attrs))
            "Selection checkbox binding should not pre-create selection signals")
        (let [change-handler (:data-on:change attrs)]
          (is (some? change-handler)
              "Selection checkbox should update signal on change")
          (when change-handler
            (is (clojure.string/includes? change-handler "$datatable.people.selections['row-1']")
                "Change handler should target row selection signal")
            (is (clojure.string/includes? change-handler "evt.target.checked")
                "Change handler should use checkbox checked state")))))))

(deftest render-selection-cell-initializes-selections-test
  (testing "selection change handler initializes selections with safe row keys"
    (let [result (row/render-selection-cell {:signal-path "datatable.people.selections['row-1']"})
          change-handler (-> result second second :data-on:change)]
      (is (some? change-handler))
      (is (clojure.string/includes? change-handler "selections ||= {}")
          "Selection handler should initialize selections map")
      (is (clojure.string/includes? change-handler "selections['row-1'] = evt.target.checked")
          "Selection handler should use bracket notation for row-id"))))

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

(defn- find-display-span
  "Finds the display span for a cell by id containing 'cell-'."
  [hiccup]
  (first (filter #(let [attrs (second %)]
                    (and (map? attrs)
                         (clojure.string/includes? (or (:id attrs) "") "cell-")))
                 (find-span hiccup))))

(defn- find-button
  "Finds button elements in hiccup.
   Matches keywords like :button or :button.btn.btn-ghost"
  [hiccup]
  (find-elements #(and (vector? %)
                       (keyword? (first %))
                       (clojure.string/starts-with? (name (first %)) "button"))
                 hiccup))

(defn- cell-selection-guarded?
  "Returns true when data-class guards missing cellSelection."
  [table-id data-class]
  (let [value (or data-class "")
        normalized (clojure.string/replace value #"\s+" "")
        prefix (str "$datatable." table-id ".cellSelection")
        guarded-fragment (str prefix "&&" prefix ".includes(")]
    (clojure.string/includes? normalized guarded-fragment)))

(defn- cell-selection-unqualified?
  "Returns true when data-class references cellSelection with bracket access."
  [data-class]
  (boolean (re-find #"cellSelection\['" (or data-class ""))))

(defn- cell-selection-optional-chaining?
  "Returns true when data-class uses optional chaining for cellSelection."
  [data-class]
  (let [value (or data-class "")]
    (or (clojure.string/includes? value "cellSelection?.")
        (clojure.string/includes? value "cellSelection\\?."))))

(defn- cell-selection-stray-class-key?
  "Returns true when data-class includes a stray cellSelection class key."
  [data-class]
  (boolean (re-find #"cellSelection\\s*:"
                    (or data-class ""))))

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
      ;; Input should not create the cell signal on render
      (let [attrs (second input)
            handler (or (:data-on:input attrs)
                        (:data-on:change attrs))]
        (is (nil? (:data-bind attrs))
            "Editable input should not bind on render")
        (is (nil? (:data-ref attrs))
            "Editable input should not create a data-ref signal")
        (is (some? handler)
            "Editable input should update cell signal on input/change")
        (when handler
          (is (clojure.string/includes? (or handler "") "$datatable.philosophers.cells['123']['name']")
              "Input handler should target cell signal path")))))

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

  (testing "editable cell display text wires double-click entry"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          display-span (find-display-span result)
          dblclick-handler (-> display-span second :data-on:dblclick)]
      (is (some? display-span))
      (is (some? dblclick-handler)
          "Display span should enter edit mode on double click")))

  (testing "editable cell has checkmark button to save"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          buttons (find-button result)]
      ;; One button has save handler with @post (on mousedown, not click, to beat blur)
      (is (some #(let [attrs (second %)]
                   (and (:data-on:mousedown attrs)
                        (clojure.string/includes? (:data-on:mousedown attrs) "@post")))
                buttons)))))

(deftest render-editable-cell-double-click-handler-test
  (testing "double-click handler sets editing and initializes current cell value"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          display-span (find-display-span result)
          dblclick-handler (-> display-span second :data-on:dblclick)
          click-handler (-> display-span second :data-on:click)]
      (is (some? display-span))
      (is (some? dblclick-handler))
      ;; Clears cell selection to avoid interference
      (is (clojure.string/includes? dblclick-handler "$datatable.philosophers.cellSelection = []")
          "Edit entry should clear cellSelection with an empty array")
      (is (clojure.string/includes? dblclick-handler "$datatable.philosophers.cellSelection = null")
          "Edit entry should clear cellSelection signal")
      (is (re-find #"\$datatable\.philosophers\.cellSelection = \[\].*\$datatable\.philosophers\.cellSelection = null" dblclick-handler)
          "Edit entry should clear cellSelection with [] before null")
      ;; Sets private per-cell editing signal
      (is (clojure.string/includes? dblclick-handler "_editing"))
      (is (clojure.string/includes? dblclick-handler "['123'] ||= {}"))
      (is (clojure.string/includes? dblclick-handler "['123']['name'] = 'active'"))
      ;; Sets cell value signal
      (is (clojure.string/includes? dblclick-handler "cells"))
      (is (clojure.string/includes? dblclick-handler "editInput-123-name"))
      (is (clojure.string/includes? dblclick-handler "input.value"))
      (is (clojure.string/includes? dblclick-handler "currentValue"))
      (is (not (clojure.string/includes? (or click-handler "") "editing"))
          "Single click should not enter edit mode"))))

(deftest render-editable-cell-double-click-entry-test
  (testing "editable non-boolean cell text enters edit mode on double click only"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true :type :string}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          display-span (find-display-span result)
          span-attrs (second display-span)
          dblclick-handler (:data-on:dblclick span-attrs)
          click-handler (:data-on:click span-attrs)]
      (is (some? display-span))
      (is (some? dblclick-handler)
          "Display text should wire data-on:dblclick for edit entry")
      (when dblclick-handler
        (is (clojure.string/includes? dblclick-handler "_editing")
            "Double-click handler should set private per-cell editing signal"))
      (is (not (clojure.string/includes? (or click-handler "") "_editing"))
          "Single click on display text should not enter edit mode"))))

(deftest render-editable-cell-display-mode-hides-pencil-trigger-test
  (testing "editable non-boolean display mode does not render Edit pencil button"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true :type :string}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          edit-buttons (filter #(= "Edit" (:title (second %)))
                               (find-button result))]
      (is (empty? edit-buttons)
          "Display mode should use double-click text entry instead of an Edit pencil trigger"))))

(deftest render-editable-cell-hover-affordance-test
  (testing "editable non-boolean display mode shows hover-only non-clickable pencil affordance"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true :type :string}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          display-span (find-display-span result)
          hover-indicators (find-elements
                            (fn [el]
                              (when (vector? el)
                                (let [attrs (second el)
                                      classes (str (name (first el)) " " (or (:class attrs) "") " " (or (:data-class attrs) ""))]
                                  (and (or (clojure.string/starts-with? (name (first el)) "span")
                                           (clojure.string/starts-with? (name (first el)) "svg")
                                           (clojure.string/starts-with? (name (first el)) "div"))
                                       (or (clojure.string/includes? classes "hidden")
                                           (clojure.string/includes? classes "invisible")
                                           (clojure.string/includes? classes "opacity-0"))
                                       (or (clojure.string/includes? classes "hover:")
                                           (clojure.string/includes? classes "group-hover:"))
                                       (nil? (:data-on:click attrs))
                                       (nil? (:data-on:mousedown attrs))
                                       (not= "button" (:role attrs))
                                       (nil? (:tabindex attrs))))))
                            result)
          edit-buttons (filter #(= "Edit" (:title (second %)))
                               (find-button result))]
      (is (some? display-span)
          "Double-click text entry remains on the display span")
      (is (some? (:data-on:dblclick (second display-span)))
          "Display span should remain wired for double-click edit entry")
      (is (seq hover-indicators)
          "Display mode should include a visual pencil affordance that is hidden by default and shown on hover")
      (is (empty? edit-buttons)
          "Pencil affordance should be visual-only and not a clickable Edit button"))))

(deftest render-editable-cell-initializes-cells-test
  (testing "double-click entry and input handlers initialize cells with safe row keys"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "row-1"
                                            :col {:key :name :editable true}
                                            :table-id "people"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          input-handler (let [attrs (second input)]
                          (or (:data-on:input attrs)
                              (:data-on:change attrs)))
          display-span (find-display-span result)
          edit-handler (-> display-span second :data-on:dblclick)]
      (is (some? input-handler))
      (is (clojure.string/includes? input-handler "cells ||= {}"))
      (is (clojure.string/includes? input-handler "cells['row-1'] ||= {}"))
      (is (clojure.string/includes? input-handler "cells['row-1']['name']"))
      (is (some? edit-handler))
      (is (clojure.string/includes? edit-handler "cells ||= {}"))
      (is (clojure.string/includes? edit-handler "cells['row-1'] ||= {}"))
      (is (clojure.string/includes? edit-handler "cells['row-1']['name']")))))

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
      (is (clojure.string/includes? mousedown-handler "_editing"))
      (is (clojure.string/includes? mousedown-handler "Object.values"))
      (is (clojure.string/includes? mousedown-handler "= false"))
      (is (clojure.string/includes? mousedown-handler "cells[editingRow][editingCol] = null"))
      (is (clojure.string/includes? mousedown-handler "else { return; }"))
      (is (clojure.string/includes? mousedown-handler "_cellSelectDragging = true")
          "Editable mousedown should set private _cellSelectDragging")
      (is (clojure.string/includes? mousedown-handler "_cellSelectStart = {row: 0, col: 0}")
          "Editable mousedown should set private _cellSelectStart")
      (is (not (clojure.string/includes? mousedown-handler ".cellSelectDragging"))
          "Editable mousedown should not set public cellSelectDragging")
      (is (not (clojure.string/includes? mousedown-handler ".cellSelectStart"))
          "Editable mousedown should not set public cellSelectStart")))

  (testing "mousedown on editable cell assigns single-cell cellSelection"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      (is (some? mousedown-handler))
      (is (clojure.string/includes? mousedown-handler "$datatable.philosophers.cellSelection = ['0-0']")
          "mousedown should assign one-cell selection for click copy/paste")))

  (testing "mousedown on editable cell skips selection when clicking inputs"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      (is (some? mousedown-handler))
      (is (or (clojure.string/includes? mousedown-handler "evt.target.closest('input, button, select, textarea')")
              (clojure.string/includes? mousedown-handler "evt.target.closest(\"input, button, select, textarea\")"))
          "Editable cell should guard against selection when interacting with inputs")))

  (testing "mousedown on non-editable cell is skipped when any cell is editing"
    (let [result (row/render-cell {:value "Athens"
                                   :row {:id 123 :name "Plato" :city "Athens"}
                                   :col {:key :city}
                                   :table-id "philosophers"
                                   :row-idx 0
                                   :col-idx 1})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      ;; Uses private per-cell editing map to detect active edits
      (is (clojure.string/includes? mousedown-handler "_editing"))
      (is (clojure.string/includes? mousedown-handler "Object.values"))
      (is (clojure.string/includes? mousedown-handler "= false"))
      (is (clojure.string/includes? mousedown-handler "cells[editingRow][editingCol] = null"))
      (is (clojure.string/includes? mousedown-handler "else { return; }"))))

  (testing "mousedown on non-editable cell skips selection when clicking inputs"
    (let [result (row/render-cell {:value "Athens"
                                   :row {:id 123 :name "Plato" :city "Athens"}
                                   :col {:key :city}
                                   :table-id "philosophers"
                                   :row-idx 0
                                   :col-idx 1})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      (is (some? mousedown-handler))
      (is (or (clojure.string/includes? mousedown-handler "evt.target.closest('input, button, select, textarea')")
              (clojure.string/includes? mousedown-handler "evt.target.closest(\"input, button, select, textarea\")"))
          "Non-editable cell should guard against selection when interacting with inputs")))

  (testing "mousedown on non-editable cell assigns single-cell cellSelection"
    (let [result (row/render-cell {:value "Athens"
                                   :row {:id 123 :name "Plato" :city "Athens"}
                                   :col {:key :city}
                                   :table-id "philosophers"
                                   :row-idx 0
                                   :col-idx 1})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      (is (some? mousedown-handler))
      (is (clojure.string/includes? mousedown-handler "$datatable.philosophers.cellSelection = ['0-1']")
          "mousedown should assign one-cell selection for click copy/paste"))))

(deftest render-cell-selection-highlight-guards-test
  (testing "non-editable cells guard highlight against missing cellSelection"
    (let [result (row/render-cell {:value "Athens"
                                   :row {:id 123 :name "Plato" :city "Athens"}
                                   :col {:key :city}
                                   :table-id "philosophers"
                                   :row-idx 0
                                   :col-idx 1})
          td-attrs (second result)
          data-class (:data-class td-attrs)]
      (is (some? data-class))
      (is (cell-selection-guarded? "philosophers" data-class)
          "Highlight should guard when cellSelection is missing")
      (is (clojure.string/includes? data-class "includes('0-1')")
          "Highlight should use row/col key for selection")
      (is (not (cell-selection-unqualified? data-class))
          "Highlight should not use bracket access for cellSelection")
      (is (not (cell-selection-optional-chaining? data-class))
          "Highlight should not use optional chaining for cellSelection")
      (is (not (cell-selection-stray-class-key? data-class))
          "Highlight should not include stray cellSelection class keys")))

  (testing "editable cells guard highlight against missing cellSelection"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          td-attrs (second result)
          data-class (:data-class td-attrs)]
      (is (some? data-class))
      (is (cell-selection-guarded? "philosophers" data-class)
          "Editable highlight should guard when cellSelection is missing")
      (is (clojure.string/includes? data-class "includes('0-0')")
          "Editable highlight should use row/col key for selection")
      (is (not (cell-selection-unqualified? data-class))
          "Editable highlight should not use bracket access for cellSelection")
      (is (not (cell-selection-optional-chaining? data-class))
          "Editable highlight should not use optional chaining for cellSelection")
      (is (not (cell-selection-stray-class-key? data-class))
          "Editable highlight should not include stray cellSelection class keys")))

  (testing "boolean cells guard highlight against missing cellSelection"
    (let [result (row/render-boolean-cell {:value true
                                           :row-id "123"
                                           :col {:key :verified :type :boolean :editable true}
                                           :table-id "philosophers"
                                           :data-url "/data"
                                           :row-idx 0
                                           :col-idx 0})
          td-attrs (second result)
          data-class (:data-class td-attrs)]
      (is (some? data-class))
      (is (cell-selection-guarded? "philosophers" data-class)
          "Boolean highlight should guard when cellSelection is missing")
      (is (clojure.string/includes? data-class "includes('0-0')")
          "Boolean highlight should use row/col key for selection")
      (is (not (cell-selection-unqualified? data-class))
          "Boolean highlight should not use bracket access for cellSelection")
      (is (not (cell-selection-optional-chaining? data-class))
          "Boolean highlight should not use optional chaining for cellSelection")
      (is (not (cell-selection-stray-class-key? data-class))
          "Boolean highlight should not include stray cellSelection class keys"))))

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

(deftest render-editable-cell-save-state-machine-test
  (testing "non-boolean edit lifecycle uses string states and optimistic client updates"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name :editable true :type :string}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          td-attrs (second result)
          td-class (:data-class td-attrs)
          input (find-input result)
          keydown-handler (-> input second :data-on:keydown)
          display-span (find-display-span result)
          dblclick-handler (-> display-span second :data-on:dblclick)
          save-handler (->> (find-button result)
                            (keep #(-> % second :data-on:mousedown))
                            (filter #(clojure.string/includes? % "@post"))
                            first)]
      (is (clojure.string/includes? (or dblclick-handler "") "['123']['name'] = 'active'")
          "Double-click should mark cell edit state as 'active'")
      (is (not (clojure.string/includes? (or dblclick-handler "") "['123']['name'] = true"))
          "Double-click should not use boolean true for edit state")
      (is (clojure.string/includes? (or td-class "") "'bg-warning/20'")
          "Cell should include pending background class when state is in-flight")
      (is (clojure.string/includes? (or td-class "") "'in-flight'")
          "Pending class should be keyed off the 'in-flight' edit state")
      (is (some? save-handler)
          "Editable cell should include a save handler")
      (is (re-find #"(?s)\['123'\]\['name'\]\s*=\s*'in-flight'.*@post" (or save-handler ""))
          "Save button should set edit state to 'in-flight' before posting")
      (is (re-find #"(?s)\['123'\]\['name'\]\s*=\s*'in-flight'.*@post" (or keydown-handler ""))
          "Enter submit should set edit state to 'in-flight' before posting")
      (is (clojure.string/includes? (or save-handler "") "dataset.value")
          "Save button should optimistically update data-value client-side")
      (is (clojure.string/includes? (or save-handler "") "textContent")
          "Save button should optimistically update display text client-side"))))

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

;; =============================================================================
;; Type-aware editable cell tests
;; =============================================================================

(defn- find-select
  "Finds select elements in a hiccup structure.
   Matches keywords that contain 'select' as the base tag."
  [hiccup]
  (first (find-elements #(and (vector? %)
                              (keyword? (first %))
                              (let [tag-name (name (first %))]
                                (or (= tag-name "select")
                                    (clojure.string/starts-with? tag-name "select."))))
                        hiccup)))

(defn- find-options
  "Finds option elements in a hiccup structure."
  [hiccup]
  (find-elements #(and (vector? %)
                       (= :option (first %)))
                 hiccup))

(defn- find-toggle
  "Finds toggle (checkbox) elements in a hiccup structure.
   Matches input elements with 'toggle' in their class names."
  [hiccup]
  (first (find-elements #(and (vector? %)
                              (keyword? (first %))
                              (let [tag-str (name (first %))]
                                (and (or (= tag-str "input")
                                         (clojure.string/starts-with? tag-str "input."))
                                     (clojure.string/includes? tag-str "toggle"))))
                        hiccup)))

(defn- find-svg
  "Finds SVG elements in a hiccup structure."
  [hiccup]
  (find-elements #(and (vector? %)
                       (keyword? (first %))
                       (clojure.string/starts-with? (name (first %)) "svg"))
                 hiccup))

(deftest render-editable-cell-enum-test
  (testing "enum type renders select dropdown with options"
    (let [result (row/render-editable-cell {:value "Stoicism"
                                            :row-id "123"
                                            :col {:key :school
                                                  :type :enum
                                                  :editable true
                                                  :options ["Stoicism" "Platonism" "Aristotelianism"]}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          select (find-select result)
          options (find-options result)]
      ;; Has select element
      (is (some? select))
      ;; Select should not create cell signal on render
      (let [attrs (second select)
            handler (or (:data-on:change attrs)
                        (:data-on:input attrs))
            mousedown-handler (:data-on:mousedown attrs)]
        (is (nil? (:data-bind attrs))
            "Select should not bind on render")
        (is (nil? (:data-ref attrs))
            "Select should not create a data-ref signal")
        (is (some? handler)
            "Select should update cell signal on change")
        (is (clojure.string/includes? (or handler "") "$datatable.philosophers.cells['123']['school']")
            "Select handler should target cell signal path")
        (is (some? mousedown-handler)
            "Select should set mousedown handler")
        (is (clojure.string/includes? (or mousedown-handler "") "enumBlurLock")
            "Select mousedown should set enum blur lock"))
      ;; Has all options
      (is (= 3 (count options)))
      ;; Options have correct values
      (is (= ["Stoicism" "Platonism" "Aristotelianism"]
             (map #(-> % second :value) options)))))

  (testing "enum edit handler reads current value from data-value attribute"
    (let [result (row/render-editable-cell {:value "Stoicism"
                                            :row-id "123"
                                            :col {:key :school
                                                  :type :enum
                                                  :editable true
                                                  :options ["Stoicism" "Platonism"]}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          display-span (find-display-span result)
          click-handler (-> display-span second :data-on:dblclick)]
      ;; Reads initial value dynamically from dataset.value (not hardcoded SSR value)
      (is (clojure.string/includes? click-handler "dataset.value"))
      (is (clojure.string/includes? click-handler "editInput-123-school"))
      (is (clojure.string/includes? click-handler "input.value"))))

  (testing "enum select autosaves on change without blur"
    (let [result (row/render-editable-cell {:value "Stoicism"
                                            :row-id "123"
                                            :col {:key :school
                                                  :type :enum
                                                  :editable true
                                                  :options ["Stoicism" "Platonism"]}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          select (find-select result)
          attrs (second select)
          change-handler (:data-on:change attrs)
          keydown-handler (:data-on:keydown attrs)
          blur-handler (:data-on:blur attrs)]
      (is (some? blur-handler)
          "Enum select should cancel on blur")
      (is (clojure.string/includes? blur-handler "setTimeout")
          "Enum blur should defer cancellation")
      (is (clojure.string/includes? blur-handler "Date.now")
          "Enum blur should compare timestamps")
      (is (clojure.string/includes? blur-handler "enumBlurLock")
          "Enum blur should honor blur lock")
      (is (clojure.string/includes? blur-handler "_editing")
          "Enum blur should check private per-cell editing signal")
      (is (some? change-handler)
          "Enum select should autosave on change")
      (when change-handler
        (is (clojure.string/includes? change-handler "@post")
            "Enum change handler should post")
        (is (clojure.string/includes? change-handler "action=save")
            "Enum change handler should post to save action")
        (is (clojure.string/includes? change-handler "'in-flight'")
            "Enum change handler should mark in-flight state")
        (is (clojure.string/includes? change-handler "dataset.value")
            "Enum change handler should optimistically update data-value")
        (is (clojure.string/includes? change-handler "textContent")
            "Enum change handler should optimistically update display text"))
      (is (some? keydown-handler)
          "Enum select should keep keydown handler")
      (is (clojure.string/includes? keydown-handler "Escape")
          "Escape should cancel enum editing")
      (is (clojure.string/includes? keydown-handler "ArrowDown")
          "ArrowDown should update blur lock")
      (is (clojure.string/includes? keydown-handler "ArrowUp")
          "ArrowUp should update blur lock")
      (is (not (clojure.string/includes? keydown-handler "@post"))
          "Enum keydown should not post on Enter")))

  (testing "enum edit overlay omits checkmark button"
    (let [result (row/render-editable-cell {:value "Stoicism"
                                            :row-id "123"
                                            :col {:key :school
                                                  :type :enum
                                                  :editable true
                                                  :options ["Stoicism" "Platonism"]}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          buttons (find-button result)
          save-button (some #(when-let [handler (:data-on:mousedown (second %))]
                               (when (clojure.string/includes? handler "@post")
                                 %))
                            buttons)]
      (is (nil? save-button)
          "Enum editing should not render a checkmark submit button"))))

(deftest render-editable-cell-number-test
  (testing "number type renders number input"
    (let [result (row/render-editable-cell {:value 42
                                            :row-id "123"
                                            :col {:key :age
                                                  :type :number
                                                  :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          attrs (second input)]
      ;; Has input element with type="number"
      (is (some? input))
      (is (= "number" (:type attrs)))
      ;; Uses handler to update cell signal
      (let [handler (or (:data-on:input attrs)
                        (:data-on:change attrs))]
        (is (nil? (:data-bind attrs))
            "Number input should not bind on render")
        (is (nil? (:data-ref attrs))
            "Number input should not create a data-ref signal")
        (is (some? handler)
            "Number input should update cell signal on input/change")
        (is (clojure.string/includes? (or handler "") "$datatable.philosophers.cells['123']['age']")
            "Number input handler should target cell signal path"))))

  (testing "number type respects min/max constraints"
    (let [result (row/render-editable-cell {:value 25
                                            :row-id "123"
                                            :col {:key :age
                                                  :type :number
                                                  :editable true
                                                  :min 0
                                                  :max 150}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          attrs (second input)]
      (is (= 0 (:min attrs)))
      (is (= 150 (:max attrs)))))

  (testing "number type without min/max has no constraints"
    (let [result (row/render-editable-cell {:value 25
                                            :row-id "123"
                                            :col {:key :age
                                                  :type :number
                                                  :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          attrs (second input)]
      (is (nil? (:min attrs)))
      (is (nil? (:max attrs))))))

(deftest render-boolean-cell-test
  (testing "boolean cell renders as toggle (not pencil/save flow)"
    (let [result (row/render-cell {:value true
                                   :row-id "123"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          toggle (find-toggle result)
          buttons (find-button result)]
      (is (some? toggle))
      (let [attrs (second toggle)]
        (is (= "checkbox" (:type attrs)))
        (is (nil? (:data-ref attrs))
            "Toggle input should not create a data-ref signal"))
      (is (empty? buttons)
          "Boolean cell should not render edit buttons")))

  (testing "boolean cell mousedown assigns single-cell cellSelection"
    (let [result (row/render-cell {:value true
                                   :row-id "123"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      (is (some? mousedown-handler))
      (is (clojure.string/includes? mousedown-handler "_cellSelectDragging = true")
          "Boolean mousedown should set private _cellSelectDragging")
      (is (clojure.string/includes? mousedown-handler "_cellSelectStart = {row: 0, col: 0}")
          "Boolean mousedown should set private _cellSelectStart")
      (is (not (clojure.string/includes? mousedown-handler ".cellSelectDragging"))
          "Boolean mousedown should not set public cellSelectDragging")
      (is (not (clojure.string/includes? mousedown-handler ".cellSelectStart"))
          "Boolean mousedown should not set public cellSelectStart")
      (is (clojure.string/includes? mousedown-handler "$datatable.philosophers.cellSelection = ['0-0']")
          "mousedown should assign one-cell selection for click copy/paste")))

  (testing "boolean cell mousedown skips selection when clicking inputs"
    (let [result (row/render-cell {:value true
                                   :row-id "123"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          td-attrs (second result)
          mousedown-handler (:data-on:mousedown td-attrs)]
      (is (some? mousedown-handler))
      (is (or (clojure.string/includes? mousedown-handler "evt.target.closest('input, button, select, textarea')")
              (clojure.string/includes? mousedown-handler "evt.target.closest(\"input, button, select, textarea\")"))
          "Boolean cell should guard against selection when interacting with inputs")))

  (testing "boolean true has checked attribute"
    (let [result (row/render-cell {:value true
                                   :row-id "123"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          toggle (find-toggle result)
          attrs (second toggle)]
      (is (true? (:checked attrs)))))

  (testing "boolean false does not have checked attribute"
    (let [result (row/render-cell {:value false
                                   :row-id "123"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          toggle (find-toggle result)
          attrs (second toggle)]
      (is (false? (:checked attrs)))))

  (testing "toggle blocks single click and saves on double click"
    (let [result (row/render-cell {:value true
                                   :row-id "123"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          toggle (find-toggle result)
          attrs (second toggle)
          click-handler (:data-on:click attrs)
          dblclick-handler (:data-on:dblclick attrs)]
      (is (some? click-handler))
      (is (clojure.string/includes? click-handler "evt.preventDefault"))
      (is (clojure.string/includes? click-handler "evt.stopPropagation"))
      (is (some? dblclick-handler))
      (is (clojure.string/includes? dblclick-handler "nextChecked"))
      (is (clojure.string/includes? dblclick-handler "evt.target.checked = nextChecked"))
      (is (re-find #"(?s)\['123'\]\['verified'\]\s*=\s*'in-flight'.*@post" (or dblclick-handler ""))
          "Boolean save should set edit state to 'in-flight' before posting")
      (is (clojure.string/includes? dblclick-handler "@post"))
      (is (clojure.string/includes? dblclick-handler "/data"))
      (is (nil? (:data-on:change attrs))
          "Boolean toggle should not save on single-click change")))

  (testing "toggle double-click handler initializes cells with safe row keys"
    (let [result (row/render-cell {:value true
                                   :row-id "row-1"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          toggle (find-toggle result)
          attrs (second toggle)
          dblclick-handler (:data-on:dblclick attrs)]
      (is (some? dblclick-handler))
      (is (clojure.string/includes? dblclick-handler "cells ||= {}"))
      (is (clojure.string/includes? dblclick-handler "cells['row-1'] ||= {}"))
      (is (clojure.string/includes? dblclick-handler "cells['row-1']['verified']"))))

  (testing "toggle has correct ID for server patching"
    (let [result (row/render-cell {:value true
                                   :row-id "123"
                                   :col {:key :verified
                                         :type :boolean
                                         :editable true}
                                   :table-id "philosophers"
                                   :data-url "/data"
                                   :row-idx 0
                                   :col-idx 0})
          toggle (find-toggle result)
          attrs (second toggle)]
      (is (= "cell-philosophers-123-verified" (:id attrs))))))

(deftest render-editable-cell-string-default-test
  (testing "string type uses text input (same as default)"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name
                                                  :type :string
                                                  :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          attrs (second input)]
      ;; Has input element
      (is (some? input))
      ;; No type attribute means text input
      (is (nil? (:type attrs)))
      ;; Uses handler to update cell signal
      (let [handler (or (:data-on:input attrs)
                        (:data-on:change attrs))]
        (is (nil? (:data-bind attrs))
            "Text input should not bind on render")
        (is (nil? (:data-ref attrs))
            "Text input should not create a data-ref signal")
        (is (some? handler)
            "Text input should update cell signal on input/change")
        (is (clojure.string/includes? (or handler "") "$datatable.philosophers.cells['123']['name']")
            "Text input handler should target cell signal path"))))

  (testing "nil type uses text input (default)"
    (let [result (row/render-editable-cell {:value "Plato"
                                            :row-id "123"
                                            :col {:key :name
                                                  :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          input (find-input result)
          select (find-select result)
          toggle (find-toggle result)]
      ;; Has text input, not select or toggle
      (is (some? input))
      (is (nil? select))
      (is (nil? toggle)))))

(deftest render-editable-cell-reads-current-value-test
  (testing "display span has data-value attribute for dynamic value reading"
    ;; Bug fix: When a cell is saved, the server patches the span content,
    ;; but clicking edit again should use the NEW value, not the stale SSR value.
    ;; The span must have a data-value attribute that gets updated on save,
    ;; and the edit handler must read from this attribute.
    (let [result (row/render-editable-cell {:value "Aristotle"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          spans (find-span result)
          display-span (first (filter #(and (map? (second %))
                                            (:id (second %))
                                            (clojure.string/includes? (:id (second %)) "cell-"))
                                      spans))
          span-attrs (second display-span)]
      ;; Span must have data-value attribute with the current value
      (is (some? (:data-value span-attrs))
          "Display span must have data-value attribute")
      (is (= "Aristotle" (:data-value span-attrs))
          "data-value should contain the current value")))

  (testing "edit handler reads value from data-value attribute, not hardcoded SSR value"
    ;; The edit handler JavaScript should read the current value dynamically
    ;; from the span's data-value attribute, not use a hardcoded value.
    (let [result (row/render-editable-cell {:value "Aristotle"
                                            :row-id "123"
                                            :col {:key :name :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          display-span (find-display-span result)
          click-handler (-> display-span second :data-on:dblclick)]
      ;; Handler should reference the span's data-value attribute
      ;; e.g., document.getElementById('cell-philosophers-123-name').dataset.value
      ;; or    $cell_philosophers_123_name.dataset.value
      (is (or (clojure.string/includes? click-handler "dataset.value")
              (clojure.string/includes? click-handler "getAttribute"))
          "Edit handler must read current value from data attribute, not use hardcoded SSR value"))))
