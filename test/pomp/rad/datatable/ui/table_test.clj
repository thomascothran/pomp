(ns pomp.rad.datatable.ui.table-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.ui.table :as table]))

(defn- find-elements [pred hiccup]
  (cond
    (pred hiccup) [hiccup]
    (vector? hiccup) (mapcat #(find-elements pred %) hiccup)
    (seq? hiccup) (mapcat #(find-elements pred %) hiccup)
    :else []))

(defn- find-table-el [hiccup]
  (first (find-elements #(and (vector? %)
                              (keyword? (first %))
                              (= "table" (first (str/split (name (first %)) #"\."))))
                        hiccup)))

(deftest render-table-delegates-non-boolean-edit-entry-test
  (testing "table shell owns default non-boolean double-click edit entry"
    (let [result (table/render {:id "philosophers"
                                :cols [{:key :name :label "Name" :type :string :editable true}
                                       {:key :school :label "School" :type :enum :editable true :options ["Stoicism" "Platonism"]}
                                       {:key :verified :label "Verified" :type :boolean :editable true}]
                                :rows [{:id "row-1" :name "Plato" :school "Stoicism" :verified true}]
                                :sort-state []
                                :filters {}
                                :total-rows 1
                                :page-size 10
                                :page-current 0
                                :page-sizes [10]
                                :data-url "/data"})
          table-el (find-table-el result)
          table-attrs (second table-el)
          dblclick-handler (:data-on:dblclick table-attrs)]
      (is (some? table-el))
      (is (some? dblclick-handler)
          "Table shell should own delegated double-click edit entry")
      (when dblclick-handler
        (is (str/includes? dblclick-handler "data-pomp-role")
            "Delegated handler should target display-role metadata")
        (is (str/includes? dblclick-handler "data-col-type")
            "Delegated handler should inspect cell metadata")
        (is (str/includes? dblclick-handler "boolean")
            "Delegated handler should distinguish non-boolean edit entry")
        (is (str/includes? dblclick-handler "_editing")
            "Delegated handler should enter edit mode")
        (is (str/includes? dblclick-handler "cells")
            "Delegated handler should seed sparse draft state from the table shell")
        (is (str/includes? dblclick-handler "rowId")
            "Delegated handler should record the active row id in one editing object")
        (is (str/includes? dblclick-handler "colKey")
            "Delegated handler should record the active column key in one editing object")
        (is (str/includes? dblclick-handler "colType")
            "Delegated handler should record the active column type in one editing object")
        (is (str/includes? dblclick-handler "initialValue")
            "Delegated handler should capture the initial value for clean-vs-dirty exit decisions")
        (is (not (str/includes? dblclick-handler "setTimeout"))
            "Delegated edit entry should not rely on setTimeout for focus")
          (is (or (str/includes? dblclick-handler "dataset.value")
                  (str/includes? dblclick-handler "getAttribute('data-value')")
                  (str/includes? dblclick-handler "getAttribute(\"data-value\")"))
              "Delegated handler should read the current display value from metadata")))))

(deftest render-table-delegates-shared-non-boolean-editor-lifecycle-test
  (testing "table shell owns delegated text, number, and enum editor lifecycle"
    (let [result (table/render {:id "philosophers"
                                :cols [{:key :name :label "Name" :type :string :editable true}
                                       {:key :age :label "Age" :type :number :editable true}
                                       {:key :school :label "School" :type :enum :editable true :options ["Stoicism" "Platonism"]}
                                       {:key :verified :label "Verified" :type :boolean :editable true}]
                                :rows [{:id "row-1" :name "Plato" :age 42 :school "Stoicism" :verified true}]
                                :sort-state []
                                :filters {}
                                :total-rows 1
                                :page-size 10
                                :page-current 0
                                :page-sizes [10]
                                :data-url "/data"})
          table-el (find-table-el result)
          table-attrs (second table-el)
           input-handler (:data-on:input table-attrs)
           change-handler (:data-on:change table-attrs)
           mousedown-handler (:data-on:mousedown__capture table-attrs)
           keydown-handler (:data-on:keydown table-attrs)
           focusout-handler (:data-on:focusout__capture table-attrs)]
      (is (some? table-el))
      (is (some? input-handler)
          "Table shell should own delegated input updates for text and number editors")
      (when input-handler
        (is (str/includes? input-handler "data-pomp-role")
            "Delegated input handler should target editor-input metadata")
        (is (str/includes? input-handler "data-col-type")
            "Delegated input handler should inspect column type metadata")
        (is (str/includes? input-handler "text")
            "Delegated input handler should keep text editors on the shared path")
         (is (str/includes? input-handler "number")
             "Delegated input handler should keep number editors on the shared path")
         (is (str/includes? input-handler "cells")
             "Delegated input handler should update sparse draft state"))
      (is (some? change-handler)
          "Table shell should own delegated change updates for enum editors")
       (when change-handler
         (is (str/includes? change-handler "data-pomp-role")
             "Delegated change handler should target editor-input metadata")
        (is (str/includes? change-handler "data-col-type")
            "Delegated change handler should inspect column type metadata")
         (is (str/includes? change-handler "enum")
             "Delegated change handler should keep enum editors on the shared path")
         (is (str/includes? change-handler "cells")
             "Delegated change handler should update sparse draft state"))
      (is (some? mousedown-handler)
          "Table shell should close or submit the active editor on outside mousedown")
      (when mousedown-handler
        (is (str/includes? mousedown-handler "evt.currentTarget.querySelector")
            "Delegated mousedown handler should look up the active editor from shared editing state")
        (is (str/includes? mousedown-handler "contains(evt.target)")
            "Delegated mousedown handler should ignore clicks inside the active editor")
        (is (str/includes? mousedown-handler "initialValue")
            "Delegated mousedown handler should compare draft and initial values")
        (is (or (str/includes? mousedown-handler "@post")
                (str/includes? mousedown-handler "cells"))
            "Delegated mousedown handler should submit or clear sparse draft state"))
      (is (some? keydown-handler)
          "Table shell should own delegated keydown submit/cancel for shared non-boolean editors")
      (when keydown-handler
        (is (str/includes? keydown-handler "Enter")
            "Delegated keydown handler should submit on Enter")
        (is (str/includes? keydown-handler "Escape")
            "Delegated keydown handler should cancel on Escape")
        (is (str/includes? keydown-handler "enum")
            "Delegated keydown handler should keep enum on the shared lifecycle path")
        (is (str/includes? keydown-handler "mode")
            "Delegated keydown handler should transition the shared editing mode")
        (is (not (str/includes? keydown-handler "submitInProgress"))
            "Delegated keydown handler should not depend on a submitInProgress flag")
        (is (str/includes? keydown-handler "_editing")
            "Delegated keydown handler should update edit lifecycle state"))
      (is (some? focusout-handler)
          "Table shell should own delegated focus exit for shared non-boolean editors")
      (when focusout-handler
        (is (str/includes? focusout-handler "data-col-type")
            "Delegated focus-exit handler should inspect editor metadata")
        (is (str/includes? focusout-handler "enum")
            "Delegated focus-exit handler should keep enum on the shared lifecycle path")
        (is (str/includes? focusout-handler "initialValue")
            "Delegated focus-exit handler should compare draft and initial values")
        (is (not (str/includes? focusout-handler "submitInProgress"))
            "Delegated focus-exit handler should not depend on a submitInProgress flag")
        (is (or (str/includes? focusout-handler "@post")
                (str/includes? focusout-handler "cells"))
            "Delegated focus-exit handler should submit or clear sparse draft state")))))
