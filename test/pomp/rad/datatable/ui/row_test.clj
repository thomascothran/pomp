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
      ;; One button has save handler with @post (on mousedown, not click, to beat blur)
      (is (some #(let [attrs (second %)]
                   (and (:data-on:mousedown attrs)
                        (clojure.string/includes? (:data-on:mousedown attrs) "@post")))
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
      ;; Select has correct data-bind
      (let [attrs (second select)]
        (is (clojure.string/includes? (:data-bind attrs) "datatable.philosophers.cells.123.school")))
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
          buttons (find-button result)
          edit-button (first (filter #(let [attrs (second %)]
                                        (and (:data-on:click attrs)
                                             (clojure.string/includes? (:data-on:click attrs) "editing")
                                             (not (clojure.string/includes? (:data-on:click attrs) "@post"))))
                                     buttons))
          click-handler (-> edit-button second :data-on:click)]
      ;; Reads initial value dynamically from dataset.value (not hardcoded SSR value)
      (is (clojure.string/includes? click-handler "dataset.value")))))

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
      ;; Has correct data-bind
      (is (clojure.string/includes? (:data-bind attrs) "datatable.philosophers.cells.123.age"))))

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

(deftest render-editable-cell-boolean-test
  (testing "boolean type renders toggle checkbox"
    (let [result (row/render-editable-cell {:value true
                                            :row-id "123"
                                            :col {:key :verified
                                                  :type :boolean
                                                  :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          toggle (find-toggle result)]
      ;; Has toggle input
      (is (some? toggle))
      ;; Is a checkbox
      (let [attrs (second toggle)]
        (is (= "checkbox" (:type attrs)))
        ;; Has correct data-bind
        (is (clojure.string/includes? (:data-bind attrs) "datatable.philosophers.cells.123.verified")))))

  (testing "boolean true displays checkmark icon"
    (let [result (row/render-editable-cell {:value true
                                            :row-id "123"
                                            :col {:key :verified
                                                  :type :boolean
                                                  :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          svgs (find-svg result)]
      ;; Should have SVG elements (checkmark in display, checkmark in save button)
      (is (>= (count svgs) 2))
      ;; One should have text-success class (the display checkmark)
      (is (some #(let [tag (name (first %))]
                   (clojure.string/includes? tag "text-success"))
                svgs))))

  (testing "boolean false displays X icon"
    (let [result (row/render-editable-cell {:value false
                                            :row-id "123"
                                            :col {:key :verified
                                                  :type :boolean
                                                  :editable true}
                                            :table-id "philosophers"
                                            :data-url "/data"
                                            :row-idx 0
                                            :col-idx 0})
          svgs (find-svg result)]
      ;; Should have SVG elements
      (is (>= (count svgs) 2))
      ;; One should have opacity-30 class (the X icon)
      (is (some #(let [tag (name (first %))]
                   (clojure.string/includes? tag "opacity-30"))
                svgs))))

  (testing "boolean edit handler reads and converts data-value to boolean"
    (let [result (row/render-editable-cell {:value true
                                            :row-id "123"
                                            :col {:key :verified
                                                  :type :boolean
                                                  :editable true}
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
      ;; Reads from dataset.value and converts to boolean using === 'true'
      (is (clojure.string/includes? click-handler "dataset.value"))
      (is (clojure.string/includes? click-handler "=== 'true'")))))

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
      ;; Has correct data-bind
      (is (clojure.string/includes? (:data-bind attrs) "datatable.philosophers.cells.123.name"))))

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
          buttons (find-button result)
          edit-button (first (filter #(let [attrs (second %)]
                                        (and (:data-on:click attrs)
                                             (clojure.string/includes? (:data-on:click attrs) "editing")
                                             (not (clojure.string/includes? (:data-on:click attrs) "@post"))))
                                     buttons))
          click-handler (-> edit-button second :data-on:click)]
      ;; Handler should reference the span's data-value attribute
      ;; e.g., document.getElementById('cell-philosophers-123-name').dataset.value
      ;; or    $cell_philosophers_123_name.dataset.value
      (is (or (clojure.string/includes? click-handler "dataset.value")
              (clojure.string/includes? click-handler "getAttribute"))
          "Edit handler must read current value from data attribute, not use hardcoded SSR value")))

  (testing "boolean edit handler reads value from data-value attribute"
    (let [result (row/render-editable-cell {:value true
                                            :row-id "123"
                                            :col {:key :verified
                                                  :type :boolean
                                                  :editable true}
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
      ;; Boolean values should also have data-value for consistency
      (is (some? (:data-value span-attrs))
          "Boolean display span must have data-value attribute")
      (is (= "true" (:data-value span-attrs))
          "Boolean data-value should be string 'true' or 'false'"))))

