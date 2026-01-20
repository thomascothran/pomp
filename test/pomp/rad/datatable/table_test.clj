(ns pomp.rad.datatable.table-test
  (:require
   [clojure.test :refer [deftest is testing]] [pomp.rad.datatable.ui.table :as table]))

(defn- strip-script-content
  "Strips the content from [:script ...] forms, keeping only [:script :present].
   This allows tests to verify the script tag exists without including the full JS content."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :script (first hiccup))
         (= 2 (count hiccup)))
    [:script :present]

    (vector? hiccup)
    (mapv strip-script-content hiccup)

    (seq? hiccup)
    (map strip-script-content hiccup)

    :else hiccup))

(def test-table-data
  {:group-by [],
   :filters {},
   :total-rows 15,
   :page-size 10,
   :page-current 0,
   :sort-state [],
   :page-sizes [10 25 100 250],
   :selectable? true,
   :groups nil
   :rows
   [{:id 1,
     :name "Socrates",
     :century "5th BC",
     :school "Classical Greek",
     :region "Greece"}
    {:id 2,
     :name "Plato",
     :century "4th BC",
     :school "Platonism",
     :region "Greece"}]
   :cols
   [{:key :name, :label "Name", :type :string}
    {:key :century, :label "Century", :type :string}
    {:key :school, :label "School", :type :enum}
    {:key :region, :label "Region", :type :enum}]
   :id "datatable",
   :data-url "/demo/datatable/data"})

;; (def expected-result
;;   (some-> "test-resources/snapshots/pomp/rad/datatable/table-test/rendered-table.edn"
;;           slurp
;;           edn/read-string))
;;
;; (deftest table-render-characterization-test
;;   (is (= expected-result
;;          (strip-script-content (dt/render test-table-data)))))

;; =============================================================================
;; table/render tests - filter-operations passthrough
;; Verifies that table/render passes :filter-operations to header context
;; =============================================================================

(defn- find-operation-labels-in-table
  "Extracts all operation labels from rendered table hiccup.
   Looks for [:a.py-1 ...] elements inside filter menu dropdowns."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :a.py-1 (first hiccup)))
    [(last hiccup)]

    (vector? hiccup)
    (mapcat find-operation-labels-in-table hiccup)

    (seq? hiccup)
    (mapcat find-operation-labels-in-table hiccup)

    :else
    []))

(defn- find-operations-for-column-in-table
  "Finds operation labels for a specific column in rendered table.
   Returns set of label strings."
  [hiccup col-key]
  (let [col-name (name col-key)
        popover-id (str "filter-" col-name)]
    (letfn [(find-menu [h]
              (cond
                ;; Found the filter menu div for our column
                (and (vector? h)
                     (= :div.bg-base-100.shadow-lg.rounded-box.p-4.w-64 (first h))
                     (map? (second h))
                     (= popover-id (:id (second h))))
                (set (find-operation-labels-in-table h))

                (vector? h)
                (some find-menu h)

                (seq? h)
                (some find-menu h)

                :else
                nil))]
      (find-menu hiccup))))

(deftest table-render-passes-filter-operations-test
  (testing "passes table-level :filter-operations to header, affecting rendered filter menus"
    (let [cols [{:key :name :label "Name" :type :string}
                {:key :status :label "Status" :type :enum}]
          table-filter-ops {:string [{:value "custom-string-op" :label "Custom String"}]
                            :enum [{:value "custom-enum-op" :label "Custom Enum"}]}
          result (table/render {:id "test-table"
                                :cols cols
                                :rows []
                                :sort-state []
                                :filters {}
                                :total-rows 0
                                :page-size 10
                                :page-current 0
                                :page-sizes [10 25]
                                :data-url "/data"
                                :filter-operations table-filter-ops})
          name-ops (find-operations-for-column-in-table result :name)
          status-ops (find-operations-for-column-in-table result :status)]

      ;; String column should use custom string operations
      (is (contains? name-ops "Custom String")
          "String column should have custom table-level operation")
      (is (not (contains? name-ops "contains"))
          "String column should NOT have default 'contains' when overridden")

      ;; Enum column should use custom enum operations
      (is (contains? status-ops "Custom Enum")
          "Enum column should have custom table-level operation")
      (is (not (contains? status-ops "is any of"))
          "Enum column should NOT have default 'is any of' when overridden")))

  (testing "columns use type-appropriate default operations when no filter-operations provided"
    (let [cols [{:key :name :label "Name" :type :string}
                {:key :active :label "Active" :type :boolean}]
          result (table/render {:id "test-table"
                                :cols cols
                                :rows []
                                :sort-state []
                                :filters {}
                                :total-rows 0
                                :page-size 10
                                :page-current 0
                                :page-sizes [10 25]
                                :data-url "/data"})
          name-ops (find-operations-for-column-in-table result :name)
          active-ops (find-operations-for-column-in-table result :active)]

      ;; String column gets string defaults
      (is (contains? name-ops "contains")
          "String column should have default 'contains' operation")

      ;; Boolean column gets boolean defaults, not string
      (is (contains? active-ops "is")
          "Boolean column should have 'is' operation")
      (is (not (contains? active-ops "contains"))
          "Boolean column should NOT have 'contains' operation"))))
