(ns pomp.rad.datatable.table-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [pomp.rad.datatable.ui.table :as table]))

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

(defn- find-table-attrs
  "Finds the :table.table.table-sm attrs map in rendered hiccup."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :table.table.table-sm (first hiccup)))
    (second hiccup)

    (vector? hiccup)
    (some find-table-attrs hiccup)

    (seq? hiccup)
    (some find-table-attrs hiccup)

     :else nil))

(defn- find-toolbar-node
  "Finds the toolbar container div in rendered hiccup."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :div.flex.items-center.px-2.py-1.border-b.border-base-300.bg-base-200 (first hiccup)))
    hiccup

    (vector? hiccup)
    (some find-toolbar-node hiccup)

    (seq? hiccup)
    (some find-toolbar-node hiccup)

    :else nil))

(defn- find-group-toggle-handler
  "Finds the data-on:click handler for a group toggle button."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (keyword? (first hiccup))
         (string/starts-with? (name (first hiccup)) "button")
         (map? (second hiccup))
         (string/includes? (or (:data-on:click (second hiccup)) "") "expanded"))
    (:data-on:click (second hiccup))

    (vector? hiccup)
    (some find-group-toggle-handler hiccup)

    (seq? hiccup)
    (some find-group-toggle-handler hiccup)

    :else nil))

(defn- find-data-show-values
  "Collects all data-show expressions from rendered hiccup."
  [hiccup]
  (cond
    (vector? hiccup)
    (let [attrs (second hiccup)
          children (if (map? attrs) (drop 2 hiccup) (rest hiccup))
          current (when (and (map? attrs) (contains? attrs :data-show))
                    [(:data-show attrs)])]
      (concat current (mapcat find-data-show-values children)))

    (seq? hiccup)
    (mapcat find-data-show-values hiccup)

    :else []))

(defn- find-header-select-all-click-handler
  "Finds the header select-all checkbox click handler in rendered table hiccup."
  [hiccup]
  (letfn [(find-node [node]
            (cond
              (and (vector? node)
                   (keyword? (first node))
                   (string/starts-with? (name (first node)) "input")
                   (map? (second node))
                   (= "checkbox" (-> node second :type))
                   (contains? (second node) :data-on:click))
              node

              (vector? node)
              (some find-node node)

              (seq? node)
              (some find-node node)

              :else nil))]
    (some-> (find-node hiccup) second :data-on:click)))

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

(deftest table-render-toolbar-slots-test
  (testing "renders toolbar left and right content in the same row"
    (let [left [:div {:id "left-slot"} "search"]
          right [:div {:id "right-slot"} "columns"]
          result (table/render {:id "test-table"
                                :cols [{:key :name :label "Name" :type :string}]
                                :rows []
                                :sort-state []
                                :filters {}
                                :total-rows 0
                                :page-size 10
                                :page-current 0
                                :page-sizes [10 25]
                                :data-url "/data"
                                :toolbar-left left
                                :toolbar-right right})
          toolbar-node (find-toolbar-node result)
          toolbar-attrs (second toolbar-node)
          toolbar-children (drop 2 toolbar-node)
          left-container (first toolbar-children)
          right-container (second toolbar-children)]
      (is (some? toolbar-node))
      (is (= "space-between" (get-in toolbar-attrs [:style :justify-content])))
      (is (= [:div.flex.items-center.gap-2 left] left-container))
      (is (= [:div.flex.items-center.gap-2.ml-auto right] right-container))))

  (testing "keeps legacy :toolbar content right aligned"
    (let [legacy-toolbar [:button.btn.btn-sm "Columns"]
          result (table/render {:id "test-table"
                                :cols [{:key :name :label "Name" :type :string}]
                                :rows []
                                :sort-state []
                                :filters {}
                                :total-rows 0
                                :page-size 10
                                :page-current 0
                                :page-sizes [10 25]
                                :data-url "/data"
                                :toolbar legacy-toolbar})
          toolbar-node (find-toolbar-node result)
          toolbar-children (drop 2 toolbar-node)
          right-container (second toolbar-children)]
      (is (some? toolbar-node))
      (is (= [:div.flex.items-center.gap-2.ml-auto legacy-toolbar] right-container)))))

(deftest table-render-passes-global-search-to-custom-renderer-test
  (testing "custom table-search renderer receives current :global-table-search"
    (let [renderer-ctx (atom nil)
          result (table/render {:id "test-table"
                                :cols [{:key :name :label "Name" :type :string}]
                                :rows []
                                :sort-state []
                                :filters {}
                                :total-rows 0
                                :page-size 10
                                :page-current 0
                                :page-sizes [10 25]
                                :data-url "/data"
                                :global-table-search "Sto"
                                :render-table-search (fn [ctx]
                                                       (reset! renderer-ctx ctx)
                                                       [:div {:id "custom-search"} "search"])})]
      (is (some? result))
      (is (= "Sto" (:global-table-search @renderer-ctx))
          "Renderer context should include the current :global-table-search value"))))

(deftest table-render-header-select-all-handler-targets-visible-row-signals-test
  (testing "table render passes visible row ids to header select-all signal writes"
    (let [result (table/render {:id "datatable"
                                :cols [{:key :name :label "Name" :type :string}]
                                :rows [{:id "row-1" :name "Ada"}
                                       {:id "row-2" :name "Grace"}]
                                :sort-state []
                                :filters {}
                                :total-rows 2
                                :page-size 10
                                :page-current 0
                                :page-sizes [10 25]
                                :data-url "/data"
                                :selectable? true})
          click-handler (find-header-select-all-click-handler result)]
      (is (some? click-handler)
          "Expected selectable table header to include a select-all handler")
      (is (string/includes? click-handler "$datatable.datatable.selections['row-1'] = evt.target.checked")
          "Expected header handler to write visible row-1 selection signal directly")
      (is (string/includes? click-handler "$datatable.datatable.selections['row-2'] = evt.target.checked")
          "Expected header handler to write visible row-2 selection signal directly"))))

(deftest table-group-expand-handlers-test
  (let [groups [{:group-value "Classical Greek"
                 :rows [{:id 1 :name "Socrates" :school "Classical Greek"}]
                 :row-ids #{1}
                 :count 1}]
        result (table/render {:id "test-table"
                              :cols [{:key :name :label "Name" :type :string}
                                     {:key :school :label "School" :type :enum}]
                              :rows []
                              :groups groups
                              :group-by [:school]
                              :sort-state []
                              :filters {}
                              :total-rows 1
                              :page-size 10
                              :page-current 0
                              :page-sizes [10 25]
                              :data-url "/data"})
        toggle-handler (find-group-toggle-handler result)
        expanded-shows (->> (find-data-show-values result)
                            (filter #(string/includes? % "expanded")))]
    (testing "group toggle initializes expanded map and uses bracket access"
      (is (some? toggle-handler))
      (is (string/includes? toggle-handler "expanded ||= {}"))
      (is (string/includes? toggle-handler "expanded['0']"))
      (is (not (string/includes? toggle-handler ".expanded.0"))
          "Toggle should not use dot access for numeric keys"))

    (testing "group rows show/hide with bracket-based expanded state"
      (is (seq expanded-shows))
      (is (every? #(string/includes? % "expanded['0']") expanded-shows))
      (is (not-any? #(string/includes? % ".expanded.0") expanded-shows)
          "data-show should not use dot access for numeric keys"))))

(deftest table-cell-selection-handlers-test
  (let [result (table/render {:id "test-table"
                              :cols [{:key :name :label "Name" :type :string}]
                              :rows []
                              :sort-state []
                              :filters {}
                              :total-rows 0
                              :page-size 10
                              :page-current 0
                              :page-sizes [10 25]
                              :data-url "/data"})
          table-attrs (find-table-attrs result)]
    (testing "table-level drag handlers use private drag signals"
      (let [mousemove-handler (:data-on:mousemove table-attrs)
            mouseup-handler (:data-on:mouseup__window table-attrs)
            data-class (:data-class table-attrs)]
        (is (some? mousemove-handler))
        (is (some? mouseup-handler))
        (is (some? data-class))
        (is (string/includes? mousemove-handler "$datatable.test-table._cellSelectDragging")
            "mousemove should pass private _cellSelectDragging signal")
        (is (string/includes? mousemove-handler "$datatable.test-table._cellSelectStart")
            "mousemove should pass private _cellSelectStart signal")
        (is (string/includes? mouseup-handler "$datatable.test-table._cellSelectDragging = false")
            "mouseup should clear private _cellSelectDragging signal")
        (is (string/includes? data-class "$datatable.test-table._cellSelectDragging")
            "table class should use private _cellSelectDragging signal")
        (is (not (string/includes? mousemove-handler ".cellSelectDragging"))
            "mousemove should not reference public cellSelectDragging")
        (is (not (string/includes? mousemove-handler ".cellSelectStart"))
            "mousemove should not reference public cellSelectStart")))

    (testing "pompcellselection filters truthy selections and only sets when non-empty"
      (let [handler (:data-on:pompcellselection table-attrs)]
        (is (some? handler))
        (is (string/includes? handler "evt.detail.selection"))
        (is (re-find #"filter" handler)
            "Selection handler should filter entries")
        (is (not (re-find #"Object\.keys" handler))
            "Selection handler should not normalize map-shaped selections")
        (is (not (re-find #"Array\.isArray" handler))
            "Selection handler should not include map fallback guards")
        (is (re-find #"\.length" handler)
            "Selection handler should check filtered length")
        (is (string/includes? handler "cellSelection = []")
            "Selection handler should clear selection first")
        (is (string/includes? handler "cellSelection = null")
            "Selection handler should remove signal when empty")))

    (testing "escape clears selection by removing signal"
      (let [handler (:data-on:keydown__window table-attrs)]
        (is (some? handler))
        (is (string/includes? handler "Escape"))
        (is (string/includes? handler "cellSelection = []")
            "Escape should clear selection before removing signal")
        (is (string/includes? handler "cellSelection = null")
            "Escape should remove the cellSelection signal")
        (is (not (string/includes? handler "cellSelection = {}"))
            "Escape should not set an empty selection")))))
