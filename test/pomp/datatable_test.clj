(ns pomp.datatable-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.ui.table :as table]
            [starfederation.datastar.clojure.adapter.ring :as ring]
            [starfederation.datastar.clojure.api :as d*]))

;; =============================================================================
;; Test helpers
;; =============================================================================

(defn- find-operation-labels
  "Extracts all operation labels from rendered hiccup.
   Looks for [:a.py-1 ...] elements inside filter menu dropdowns."
  [hiccup]
  (cond
    (and (vector? hiccup)
         (= :a.py-1 (first hiccup)))
    [(last hiccup)]

    (vector? hiccup)
    (mapcat find-operation-labels hiccup)

    (seq? hiccup)
    (mapcat find-operation-labels hiccup)

    :else
    []))

(defn- find-operations-for-column
  "Finds operation labels for a specific column in rendered hiccup.
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
                (set (find-operation-labels h))

                (vector? h)
                (some find-menu h)

                (seq? h)
                (some find-menu h)

                :else
                nil))]
      (find-menu hiccup))))

(defn- sse-response->string
  "Renders an SSE Ring response body to a UTF-8 string."
  [resp]
  (let [out (java.io.ByteArrayOutputStream.)]
    (.write_body_to_stream (:body resp) resp out)
    (.toString out "UTF-8")))

(defn- first-signals-payload
  "Extracts the first datastar signal payload map from SSE text."
  [sse-body]
  (some-> (re-find #"data: signals (\{.*\})" sse-body)
          second
          (json/read-str {:key-fn keyword})))

(defn- first-elements-payload
  "Extracts the first datastar patch-elements payload string from SSE text."
  [sse-body]
  (some-> (re-find #"(?s)event: datastar-patch-elements\ndata: elements (.*?)\n\n" sse-body)
          second))

(defn- export-script-payload
  [script fn-name]
  (some-> (re-find (re-pattern (str "window\\." fn-name "\\((\\{.*\\})\\);")) script)
          second
          (json/read-str {:key-fn keyword})))

;; =============================================================================
;; dt/render tests - filter-operations passthrough
;; This tests the public API that make-handlers uses internally
;; =============================================================================

(deftest dt-render-accepts-filter-operations-test
  (testing "render passes :filter-operations to table, affecting filter menu operations"
    (let [cols [{:key :name :label "Name" :type :string}
                {:key :status :label "Status" :type :enum}]
          filter-ops {:string [{:value "custom-str" :label "Custom String Op"}]
                      :enum [{:value "custom-enum" :label "Custom Enum Op"}]}
          rendered (table/render {:id "test-table"
                                  :cols cols
                                  :rows []
                                  :sort-state []
                                  :filters {}
                                  :total-rows 0
                                  :page-size 10
                                  :page-current 0
                                  :page-sizes [10 25]
                                  :data-url "/data"
                                  :filter-operations filter-ops})
          name-ops (find-operations-for-column rendered :name)
          status-ops (find-operations-for-column rendered :status)]

      ;; String column should use custom operations
      (is (contains? name-ops "Custom String Op")
          "String column should have custom filter operation")
      (is (not (contains? name-ops "contains"))
          "String column should NOT have default 'contains' when overridden")

      ;; Enum column should use custom operations
      (is (contains? status-ops "Custom Enum Op")
          "Enum column should have custom filter operation")
      (is (not (contains? status-ops "is any of"))
          "Enum column should NOT have default 'is any of' when overridden")))

  (testing "columns use type-appropriate defaults when no filter-operations provided"
    (let [cols [{:key :name :label "Name" :type :string}
                {:key :active :label "Active" :type :boolean}]
          rendered (table/render {:id "test-table"
                                  :cols cols
                                  :rows []
                                  :sort-state []
                                  :filters {}
                                  :total-rows 0
                                  :page-size 10
                                  :page-current 0
                                  :page-sizes [10 25]
                                  :data-url "/data"})
          name-ops (find-operations-for-column rendered :name)
          active-ops (find-operations-for-column rendered :active)]

      ;; String column gets string defaults
      (is (contains? name-ops "contains")
          "String column should have default 'contains' operation")

      ;; Boolean column gets boolean defaults
      (is (contains? active-ops "is")
          "Boolean column should have 'is' operation")
      (is (not (contains? active-ops "contains"))
          "Boolean column should NOT have 'contains' operation"))))

;; =============================================================================
;; make-handlers tests - verifies :filter-operations is accepted as a parameter
;; =============================================================================

(defn- make-get-handler [opts]
  (:get (datatable/make-handlers opts)))

(defn- make-post-handler [opts]
  (:post (datatable/make-handlers opts)))

(deftest make-handlers-accepts-filter-operations-test
  (testing "make-handlers accepts :filter-operations without error"
    (let [columns [{:key :name :label "Name" :type :string}]
          filter-ops {:string [{:value "custom" :label "Custom Op"}]}
          handler (make-get-handler {:id "test-table"
                                     :columns columns
                                     :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn (fn [_] "<html>")
                                     :filter-operations filter-ops})]
      ;; Handler should be created successfully
      (is (fn? handler)
          "make-handlers :get should return a function when :filter-operations is provided"))))

(deftest make-handlers-identity-column-requirement-test
  (testing "throws when :selectable? is true and :id column is missing"
    (let [ex (try
               (make-get-handler {:id "test-table"
                                  :columns [{:key :name :label "Name" :type :string}]
                                  :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                  :data-url "/data"
                                  :render-html-fn str
                                  :selectable? true})
               nil
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (instance? clojure.lang.ExceptionInfo ex))
      (is (re-find #"requires an :id column" (ex-message ex)))
      (is (= :id (get-in (ex-data ex) [:required-column])))))

  (testing "throws when editable columns are configured and :id column is missing"
    (let [ex (try
               (make-post-handler {:id "test-table"
                                   :columns [{:key :name :label "Name" :type :string :editable true}]
                                   :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                   :save-fn (fn [_] {:success true})
                                   :data-url "/data"
                                   :render-html-fn str})
               nil
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (instance? clojure.lang.ExceptionInfo ex))
      (is (re-find #"requires an :id column" (ex-message ex)))
      (is (true? (get-in (ex-data ex) [:editable-columns?])))))

  (testing "allows :id column to exist without being editable"
    (let [handler (make-post-handler {:id "test-table"
                                      :columns [{:key :id :label "ID" :type :number}
                                                {:key :name :label "Name" :type :string :editable true}]
                                      :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                      :save-fn (fn [_] {:success true})
                                      :data-url "/data"
                                      :render-html-fn str
                                      :selectable? true})]
      (is (fn? handler)))))

(deftest make-handlers-passes-render-table-search-to-table-render-test
  (testing "make-handlers :get forwards :render-table-search while preserving toolbar"
    (let [render-opts (atom nil)
          render-table-search (fn [_] [:div "search"])
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [query-signals _]
                                                {:rows [] :page (:page query-signals)})
                                     :count-fn (fn [_ _] {:total-rows 0})
                                     :data-url "/data"
                                     :render-html-fn identity
                                     :render-table-search render-table-search})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    table/render (fn [opts]
                                   (reset! render-opts opts)
                                   [:div])
                    table/render-skeleton (fn [_] [:div])
                    d*/patch-signals! (fn [& _])
                    d*/patch-elements! (fn [& _])
                    d*/execute-script! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {}})
        (is (= render-table-search (:render-table-search @render-opts))
            "table/render should receive :render-table-search")
        (is (some? (:toolbar @render-opts))
            "table/render should still receive :toolbar controls")))))

(deftest make-handlers-patches-global-table-search-signal-test
  (testing "global search request patches dedicated :globalTableSearch signal"
    (let [handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn str})
          resp (handler {:query-params {"action" "global-search"}
                         :headers {"datastar-request" "true"}
                         :body-params {:datatable {:test-table {:globalTableSearch "Stoa"}}}})
          table-signals (-> resp
                            sse-response->string
                            first-signals-payload
                            (get-in [:datatable :test-table]))]
      (is (contains? table-signals :globalTableSearch)
          "Handler response should patch datatable.<id>.globalTableSearch"))))

(deftest make-handlers-forwards-normalized-global-table-search-to-table-render-test
  (testing "global-search request forwards normalized :global-table-search into table/render"
    (let [handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn str
                                     :render-table-search
                                     (fn [{:keys [global-table-search]}]
                                       [:span (str "GS=" global-table-search)])})
          sse-body (-> (handler {:query-params {"action" "global-search"}
                                 :headers {"datastar-request" "true"}
                                 :body-params {:datatable {:test-table {:globalTableSearch "  Stoa  "}}}})
                       sse-response->string)]
      (is (re-find #"GS=Stoa" sse-body)
          "table/render should receive normalized global search value via :global-table-search"))))

(deftest make-handlers-table-search-query-wiring-test
  (testing "uses :table-search-query when provided"
    (let [query-fn-calls (atom 0)
          table-search-query-calls (atom [])
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _]
                                                (swap! query-fn-calls inc)
                                                {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :table-search-query (fn [query-signals req]
                                                           (swap! table-search-query-calls conj {:query-signals query-signals
                                                                                                 :req req})
                                                           {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn str})]
      (handler {:query-params {"action" "global-search"}
                :headers {"datastar-request" "true"}
                :body-params {:datatable {:test-table {:globalTableSearch "Stoa"}}}})
      (is (= 1 (count @table-search-query-calls))
          "When provided, :table-search-query should run in query flow")
      (is (zero? @query-fn-calls)
          "When :table-search-query is provided, :rows-fn should not be used for global search")))

  (testing "uses :rows-fn when :table-search-query is absent"
    (let [query-fn-calls (atom 0)
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _]
                                                (swap! query-fn-calls inc)
                                                {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn str})]
      (handler {:query-params {"action" "global-search"}
                :headers {"datastar-request" "true"}
                :body-params {:datatable {:test-table {:globalTableSearch "Stoa"}}}})
      (is (= 1 @query-fn-calls)
          "Without :table-search-query, default :rows-fn behavior should remain")))

  (testing "non-global actions still compose through :table-search-query when global search is active"
    (let [query-fn-calls (atom 0)
          table-search-query-signals (atom nil)
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [query-signals _]
                                                (swap! query-fn-calls inc)
                                                {:rows [] :total-rows 0 :page (:page query-signals)})
                                     :table-search-query (fn [query-signals _]
                                                           (reset! table-search-query-signals query-signals)
                                                           {:rows [] :total-rows 0 :page (:page query-signals)})
                                     :data-url "/data"
                                     :render-html-fn str})]
      (handler {:query-params {"clicked" "name"}
                :headers {"datastar-request" "true"}
                :body-params {:datatable {:test-table {:globalTableSearch "  Stoa  "
                                                       :page {:size 25 :current 2}}}}})
      (is (some? @table-search-query-signals)
          "When global search has a valid value, normal table actions should still use :table-search-query")
      (is (zero? @query-fn-calls)
          "When :table-search-query handles composed global search, :rows-fn should not run")
      (is (= "Stoa" (:search-string @table-search-query-signals))
          "Composed query flow should normalize and forward the global search string")
      (is (= [{:column "name" :direction "asc"}] (:sort @table-search-query-signals))
          "Composed query flow should include non-global action state updates (sort)")))

  (testing "non-global actions fall back to :rows-fn when global search is blank or too short"
    (let [query-fn-signals (atom nil)
          table-search-query-calls (atom 0)
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [query-signals _]
                                                (reset! query-fn-signals query-signals)
                                                {:rows [] :total-rows 0 :page (:page query-signals)})
                                     :table-search-query (fn [_ _]
                                                           (swap! table-search-query-calls inc)
                                                           {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn str})]
      (handler {:query-params {"clicked" "name"}
                :headers {"datastar-request" "true"}
                :body-params {:datatable {:test-table {:globalTableSearch " a "
                                                       :page {:size 10 :current 1}}}}})
      (is (zero? @table-search-query-calls)
          "Short global search should not force :table-search-query for normal actions")
      (is (some? @query-fn-signals)
          "Short global search should keep default query flow")
      (is (= "" (:search-string @query-fn-signals))
          "Fallback query flow should still normalize short global search to empty"))))

(deftest make-handlers-table-search-query-contract-payload-test
  (testing ":table-search-query receives contract payload keys"
    (let [captured-query-signals (atom nil)
          columns [{:key :id :label "ID" :type :number}
                   {:key :name :label "Name" :type :string}
                   {:key :school :label "School" :type :enum}
                   {:key :region :label "Region" :type :string}]
          handler (make-get-handler {:id "test-table"
                                     :columns columns
                                     :rows-fn (fn [_ _]
                                                {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :table-search-query (fn [query-signals _]
                                                           (reset! captured-query-signals query-signals)
                                                           {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn str})]
      (handler {:query-params {"sortCol" "school" "sortDir" "desc" "groupBy" "school"}
                :headers {"datastar-request" "true"}
                :body-params {:datatable {:test-table {:globalTableSearch "Stoa"
                                                       :columns {:id {:visible false}
                                                                 :school {:visible false}
                                                                 :region {:visible false}}
                                                       :filters {:region [{:type "string" :op "equals" :value "Greece"}]
                                                                 :not-a-real-column [{:type "string" :op "equals" :value "x"}]}
                                                       :project-columns [:not-a-real-column :region]
                                                       :page {:size 25 :current 1}}}}})
      (is (map? @captured-query-signals)
          "Expected :table-search-query to receive a query payload map")
      (is (every? #(contains? @captured-query-signals %)
                  [:columns :search-string :filters :sort :page :group-by :project-columns])
          "Payload contract must include :columns, :search-string, :filters, :sort, :page, :group-by, and :project-columns")
      (is (= #{:id :name :school :region}
             (set (:project-columns @captured-query-signals)))
          "Projection should include visible, filtered, grouped/sorted, and id columns derived from server config")
      (is (not (contains? (set (:project-columns @captured-query-signals)) :not-a-real-column))
          "Projection must ignore unknown client-provided column keys"))))

(deftest make-handlers-export-stream-contract-payload-test
  (testing "export action derives full-query payload and configured columns"
    (let [stream-calls (atom [])
          scripts (atom [])
          columns [{:key :id :label "ID" :type :number}
                   {:key :name :label "Name" :type :string}
                   {:key :school :label "School" :type :enum}]
          handler (make-get-handler {:id "test-table"
                                     :columns columns
                                     :rows-fn (fn [_ _] {:rows [] :page {:size 10 :current 0}})
                                     :export-stream-rows-fn (fn [ctx _on-row! on-complete!]
                                                              (swap! stream-calls conj ctx)
                                                              (on-complete! {:row-count 0}))
                                     :data-url "/data"
                                     :render-html-fn str})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/execute-script! (fn [_ payload]
                                         (swap! scripts conj payload))
                    d*/patch-signals! (fn [& _])
                    d*/patch-elements! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "export"
                                 "sortCol" "school"
                                 "sortDir" "desc"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {:globalTableSearch "  stoa  "
                                                         :groupBy ["school"]
                                                         :page {:size 10 :current 1}
                                                         :columnOrder ["school" "name" "id"]
                                                         :columns {:id {:visible false}}}}}})
        (is (= 1 (count @stream-calls)) "Export action should invoke stream contract once")
        (let [{:keys [query columns limits]} (first @stream-calls)]
          (is (= [:school :name :id] columns)
              "Export contract should include configured columns in current order")
          (is (= [:school :name :id] (:project-columns query))
              "Export query should project configured columns")
          (is (= [] (:group-by query))
              "Export query should disable grouped pagination semantics")
          (is (nil? (:page query))
              "Export query should ignore pagination scope")
          (is (= "stoa" (:search-string query))
              "Export query should preserve normalized global search")
          (is (= "school" (get-in query [:sort 0 :column]))
              "Export query should preserve active sorting column")
          (is (= "desc" (get-in query [:sort 0 :direction]))
              "Export query should preserve active sorting direction")
          (is (nil? limits)
              "Export limits should be nil by default"))
        (is (some #(re-find #"pompDatatableExportBegin" %) @scripts)
            "Export should emit begin script event")
        (is (some #(re-find #"pompDatatableExportFinish" %) @scripts)
            "Export should emit finish script event")))))

(deftest make-handlers-export-stream-chunks-rows-test
  (testing "export emits CSV chunks in batches rather than one script event per row"
    (let [scripts (atom [])
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :id :label "ID" :type :number}
                                               {:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _] {:rows [] :page {:size 10 :current 0}})
                                     :export-limits {:chunk-rows 2}
                                     :export-stream-rows-fn (fn [_ctx on-row! on-complete!]
                                                              (on-row! {:id 1 :name "Socrates"})
                                                              (on-row! {:id 2 :name "Plato"})
                                                              (on-row! {:id 3 :name "Zeno"})
                                                              (on-complete! {:row-count 3}))
                                     :data-url "/data"
                                     :render-html-fn str})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/execute-script! (fn [_ payload]
                                         (swap! scripts conj payload))
                    d*/patch-signals! (fn [& _])
                    d*/patch-elements! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "export"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {}}}})
        (let [append-payloads (->> @scripts
                                   (keep #(export-script-payload % "pompDatatableExportAppend"))
                                   (map :chunk)
                                   vec)]
          (is (= 2 (count append-payloads))
              "chunk-rows=2 should flush three rows as two append events")
          (is (re-find #"1,Socrates" (first append-payloads)))
          (is (re-find #"2,Plato" (first append-payloads)))
          (is (re-find #"3,Zeno" (second append-payloads))))))))

(deftest make-handlers-export-stream-enforces-guardrails-test
  (testing "export fails when max row limit is exceeded"
    (let [scripts (atom [])
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :id :label "ID" :type :number}
                                               {:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _] {:rows [] :page {:size 10 :current 0}})
                                     :export-limits {:max-rows 2 :chunk-rows 10}
                                     :export-stream-rows-fn (fn [_ctx on-row! on-complete!]
                                                              (on-row! {:id 1 :name "Socrates"})
                                                              (on-row! {:id 2 :name "Plato"})
                                                              (on-row! {:id 3 :name "Zeno"})
                                                              (on-complete! {:row-count 3}))
                                     :data-url "/data"
                                     :render-html-fn str})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/execute-script! (fn [_ payload]
                                         (swap! scripts conj payload))
                    d*/patch-signals! (fn [& _])
                    d*/patch-elements! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "export"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {}}}})
        (is (some #(re-find #"pompDatatableExportFail" %) @scripts)
            "Export should emit fail event when row limit is exceeded")
        (is (not-any? #(re-find #"pompDatatableExportFinish" %) @scripts)
            "Export should not emit finish after row-limit failure"))))

  (testing "export fails when timeout is exceeded"
    (let [scripts (atom [])
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :id :label "ID" :type :number}
                                               {:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _] {:rows [] :page {:size 10 :current 0}})
                                     :export-limits {:timeout-ms 5}
                                     :export-stream-rows-fn (fn [_ctx _on-row! on-complete!]
                                                              (Thread/sleep 20)
                                                              (on-complete! {:row-count 0}))
                                     :data-url "/data"
                                     :render-html-fn str})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/execute-script! (fn [_ payload]
                                         (swap! scripts conj payload))
                    d*/patch-signals! (fn [& _])
                    d*/patch-elements! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "export"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {}}}})
        (is (some #(re-find #"pompDatatableExportFail" %) @scripts)
            "Export should emit fail event when timeout is exceeded")
        (is (not-any? #(re-find #"pompDatatableExportFinish" %) @scripts)
            "Export should not emit finish after timeout failure"))))

  (testing "export fails when max byte limit is exceeded"
    (let [scripts (atom [])
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :id :label "ID" :type :number}
                                               {:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _] {:rows [] :page {:size 10 :current 0}})
                                     :export-limits {:max-bytes 8}
                                     :export-stream-rows-fn (fn [_ctx on-row! on-complete!]
                                                              (on-row! {:id 1 :name "Socrates"})
                                                              (on-complete! {:row-count 1}))
                                     :data-url "/data"
                                     :render-html-fn str})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/execute-script! (fn [_ payload]
                                         (swap! scripts conj payload))
                    d*/patch-signals! (fn [& _])
                    d*/patch-elements! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "export"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {}}}})
        (is (some #(re-find #"pompDatatableExportFail" %) @scripts)
            "Export should emit fail event when max byte limit is exceeded")
        (is (not-any? #(re-find #"pompDatatableExportFinish" %) @scripts)
            "Export should not emit finish after max-byte failure")))))

(deftest make-handlers-toolbar-includes-default-export-control-test
  (testing "default render includes export action near columns control"
    (let [handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                     :export-stream-rows-fn (fn [_ _ on-complete!]
                                                              (on-complete! {:row-count 0}))
                                     :data-url "/data"
                                     :render-html-fn str})
          sse-body (-> (handler {:query-params {}})
                       sse-response->string)]
      (is (re-find #"Export CSV" sse-body)
          "Default toolbar should render export control")
      (is (re-find #"action=export" sse-body)
          "Export control should request export action"))))

(deftest make-handlers-save-bypasses-query-flow-test
  (testing "save action bypasses query flow even when :table-search-query is present"
    (let [query-fn-calls (atom 0)
          table-search-query-calls (atom 0)
          save-calls (atom 0)
          handler (make-post-handler {:id "test-table"
                                      :columns [{:key :id :label "ID" :type :number}
                                                {:key :name :label "Name" :type :string :editable true}]
                                      :rows-fn (fn [_ _]
                                                 (swap! query-fn-calls inc)
                                                 {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                      :table-search-query (fn [_ _]
                                                            (swap! table-search-query-calls inc)
                                                            {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                      :save-fn (fn [_]
                                                 (swap! save-calls inc)
                                                 {:success true})
                                      :data-url "/data"
                                      :render-html-fn str})]
      (handler {:query-params {"action" "save"}
                :headers {"datastar-request" "true"}
                :body-params {:datatable {:test-table {:cells {:123 {:name "Updated"}}}}}})
      (is (= 1 @save-calls)
          "Save action should still execute :save-fn")
      (is (zero? @query-fn-calls)
          "Save action should bypass default query flow")
      (is (zero? @table-search-query-calls)
          "Save action should bypass table-search query flow"))))

;; =============================================================================
;; make-handlers tests - save-fn support
;; =============================================================================

(deftest make-handlers-accepts-save-fn-test
  (testing "make-handlers :post accepts :save-fn without error"
    (let [columns [{:key :id :label "ID" :type :number}
                   {:key :name :label "Name" :type :string :editable true}]
          save-fn (fn [_] {:success true})
          handler (make-post-handler {:id "test-table"
                                      :columns columns
                                      :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                      :data-url "/data"
                                      :render-html-fn (fn [_] "<html>")
                                      :save-fn save-fn})]
      ;; Handler should be created successfully
      (is (fn? handler)
          "make-handlers :post should return a function when :save-fn is provided"))))

(deftest make-handlers-save-cleans-edit-signal-without-cell-rerender-test
  (testing "save response clears per-cell edit signal and does not patch cell html"
    (let [patches (atom [])
          element-patches (atom [])
          handler (make-post-handler {:id "test-table"
                                      :columns [{:key :id :label "ID" :type :number}
                                                {:key :name :label "Name" :type :string :editable true}]
                                      :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                      :data-url "/data"
                                      :render-html-fn identity
                                      :save-fn (fn [_] {:success true})})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/patch-signals! (fn [_ payload]
                                        (swap! patches conj payload))
                    d*/patch-elements! (fn [_ payload]
                                         (swap! element-patches conj payload))
                    d*/execute-script! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "save"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {:cells {:123 {:name "Updated"}}}}}})
        (is (seq @patches) "Save should patch signals to clear edit state")
        (is (empty? @element-patches) "Save should not re-render cell HTML in response")
        (when-let [last-patch (last @patches)]
          (let [payload (json/read-str last-patch {:key-fn keyword})
                table-signals (get-in payload [:datatable :test-table])
                editing (get table-signals :_editing)
                row-editing (get editing :123)]
            (is (contains? table-signals :_editing)
                "Save should patch _editing signal map")
            (is (contains? table-signals :cells)
                "Save should include cells cleanup patch")
            (is (nil? (:cells table-signals))
                "Save should clear cells signal after submit")
            (is (contains? editing :123)
                "Save should patch _editing for the edited row")
            (is (contains? row-editing :name)
                "Save should patch edited column state")
            (is (= false (get row-editing :name))
                "Save should clear _editing[row][col] to false after submit")))))))

(deftest make-handlers-save-enum-cleans-edit-signal-without-cell-rerender-test
  (testing "enum save clears per-cell edit signal and does not patch cell html"
    (let [patches (atom [])
          element-patches (atom [])
          handler (make-post-handler {:id "test-table"
                                      :columns [{:key :id :label "ID" :type :number}
                                                {:key :school
                                                 :label "School"
                                                 :type :enum
                                                 :editable true
                                                 :options [{:value "Stoicism" :label "Stoicism"}
                                                           {:value "Academy" :label "Academy"}]}]
                                      :rows-fn (fn [_ _] {:rows [] :total-rows 0 :page {:size 10 :current 0}})
                                      :data-url "/data"
                                      :render-html-fn identity
                                      :save-fn (fn [_] {:success true})})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/patch-signals! (fn [_ payload]
                                        (swap! patches conj payload))
                    d*/patch-elements! (fn [_ payload]
                                         (swap! element-patches conj payload))
                    d*/execute-script! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "save"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {:cells {:123 {:school "Academy"}}}}}})
        (is (seq @patches) "Enum save should patch signals to clear edit state")
        (is (empty? @element-patches) "Enum save should not re-render cell HTML in response")
        (when-let [last-patch (last @patches)]
          (let [payload (json/read-str last-patch {:key-fn keyword})
                table-signals (get-in payload [:datatable :test-table])
                editing (get table-signals :_editing)
                row-editing (get editing :123)]
            (is (contains? table-signals :_editing)
                "Enum save should patch _editing signal map")
            (is (contains? table-signals :cells)
                "Enum save should include cells cleanup patch")
            (is (nil? (:cells table-signals))
                "Enum save should clear cells signal after submit")
            (is (contains? editing :123)
                "Enum save should patch _editing for the edited row")
            (is (contains? row-editing :school)
                "Enum save should patch edited column state")
            (is (= false (get row-editing :school))
                "Enum save should clear _editing[row][col] to false after submit")))))))

(deftest make-handlers-initial-patch-omits-per-row-signals-test
  (testing "initial signal patch omits per-row/per-cell signals"
    (let [patches (atom [])
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :id :label "ID" :type :number}
                                               {:key :name :label "Name" :type :string :editable true}
                                               {:key :status :label "Status" :type :string}]
                                     :rows-fn (fn [_ _]
                                                {:rows [{:id "1" :name "Ada" :status "active"}
                                                        {:id "2" :name "Bob" :status "inactive"}]
                                                 :total-rows 2
                                                 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn (fn [_] "<html>")})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/patch-signals! (fn [_ payload]
                                        (swap! patches conj payload))
                    d*/patch-elements! (fn [& _])
                    d*/execute-script! (fn [& _])
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"groupBy" "status"}})
        (is (seq @patches) "Expected patch-signals to be emitted on load")
        (let [payload (first @patches)
              signals (json/read-str payload {:key-fn keyword})
              table-signals (get-in signals [:datatable :test-table])]
          (is (map? table-signals) "Expected initial patch-signals payload")
          (is (not (contains? table-signals :expanded))
              "Expanded signals should be absent on initial render")
          (is (not (contains? table-signals :editing))
              "Editing signals should be absent on initial render")
          (is (not (contains? table-signals :cells))
              "Cell signals should be absent on initial render")
          (is (not (contains? table-signals :submitInProgress))
              "Submit flag should be absent on initial render"))))))

(deftest make-handlers-get-skips-initial-load-effects-when-signals-present-test
  (testing "get handler does not render skeleton or execute script when signals exist"
    (let [element-patches (atom [])
          scripts (atom 0)
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [_ _]
                                                {:rows []
                                                 :total-rows 0
                                                 :page {:size 10 :current 0}})
                                     :data-url "/data"
                                     :render-html-fn identity})]
      (with-redefs [ring/->sse-response (fn [_ opts]
                                          (when-let [on-open-fn (get opts ring/on-open)]
                                            (on-open-fn ::fake-sse))
                                          {:status 200})
                    d*/patch-signals! (fn [& _])
                    d*/patch-elements! (fn [_ payload]
                                         (swap! element-patches conj payload))
                    d*/execute-script! (fn [& _]
                                         (swap! scripts inc))
                    d*/close-sse! (fn [& _])]
        (handler {:query-params {"action" "global-search"}
                  :headers {"datastar-request" "true"}
                  :body-params {:datatable {:test-table {:globalTableSearch "Stoa"}}}})
        (is (= 1 (count @element-patches))
            "Signal-bearing GET should only patch the rendered table")
        (is (= 0 @scripts)
            "Signal-bearing GET should not execute initial-load script")))))

(deftest make-handlers-initial-signals-fn-first-load-seeding-test
  (testing "first load applies :initial-signals-fn to query-signals and visible columns"
    (let [initial-signals-calls (atom 0)
          captured-query-signals (atom nil)
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}
                                               {:key :age :label "Age" :type :number}]
                                     :rows-fn (fn [query-signals _]
                                                (reset! captured-query-signals query-signals)
                                                {:rows [] :total-rows 0 :page (:page query-signals)})
                                     :initial-signals-fn (fn [_]
                                                           (swap! initial-signals-calls inc)
                                                           {:page {:size 25 :current 2}
                                                            :columns {:age {:visible false}}})
                                     :data-url "/data"
                                     :render-html-fn str})
          sse-body (-> (handler {:query-params {}})
                       sse-response->string)
          first-elements (first-elements-payload sse-body)]
      (is (= 1 @initial-signals-calls)
          "First load should call :initial-signals-fn exactly once")
      (is (= {:size 25 :current 2} (:page @captured-query-signals))
          "Seeded page should flow into rows-fn query-signals on first load")
      (is (re-find #"\"Name\"" first-elements)
          "First render should include visible column headers")
      (is (not (re-find #"\"Age\"" first-elements))
          "Seeded hidden columns should be omitted from first rendered headers")))

  (testing "signal-bearing requests do not apply :initial-signals-fn"
    (let [initial-signals-calls (atom 0)
          captured-query-signals (atom nil)
          handler (make-get-handler {:id "test-table"
                                     :columns [{:key :name :label "Name" :type :string}]
                                     :rows-fn (fn [query-signals _]
                                                (reset! captured-query-signals query-signals)
                                                {:rows [] :total-rows 0 :page (:page query-signals)})
                                     :initial-signals-fn (fn [_]
                                                           (swap! initial-signals-calls inc)
                                                           {:page {:size 99 :current 9}})
                                     :data-url "/data"
                                     :render-html-fn str})]
      (handler {:headers {"datastar-request" "true"}
                :query-params {}
                :body-params {:datatable {:test-table {:page {:size 25 :current 1}}}}})
      (is (zero? @initial-signals-calls)
          "Signal-bearing requests should ignore :initial-signals-fn")
      (is (= {:size 25 :current 1} (:page @captured-query-signals))
          "Request-provided page should win over seeded first-load defaults"))))

(deftest extract-cell-edit-from-signals-test
  (testing "extracts a single edited cell directly from :cells"
    (let [signals {:cells {:123 {:name "New Name"}}}
          result (datatable/extract-cell-edit signals)]
      (is (= {:row-id "123" :col-key :name :value "New Name"} result))))

  (testing "returns nil when there are no edited-cell candidates"
    (is (nil? (datatable/extract-cell-edit {})))
    (is (nil? (datatable/extract-cell-edit {:cells {}})))
    (is (nil? (datatable/extract-cell-edit {:cells {:123 {}}}))))

  (testing "throws when more than one edited-cell candidate exists"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Expected exactly one edited cell"
         (datatable/extract-cell-edit {:cells {:123 {:name "Ada"}
                                               :456 {:age "30"}}}))))

  (testing "ignores :editing and uses :cells as source of truth"
    (let [signals {:editing {:rowId "999" :colKey "other"}
                   :cells {:123 {:verified false}}}
          result (datatable/extract-cell-edit signals)]
      (is (= {:row-id "123" :col-key :verified :value false} result))))

  (testing "preserves boolean and string boolean values from :cells"
    (is (= true
           (:value (datatable/extract-cell-edit {:cells {:123 {:verified true}}}))))
    (is (= "false"
           (:value (datatable/extract-cell-edit {:cells {:123 {:verified "false"}}}))))))

(deftest has-editable-columns-test
  (testing "returns true when any column is editable"
    (let [cols [{:key :name :editable true}
                {:key :age}]]
      (is (true? (datatable/has-editable-columns? cols)))))

  (testing "returns false when no columns are editable"
    (let [cols [{:key :name}
                {:key :age}]]
      (is (false? (datatable/has-editable-columns? cols)))))

  (testing "returns false for empty columns"
    (is (false? (datatable/has-editable-columns? [])))
    (is (false? (datatable/has-editable-columns? nil)))))

(deftest selection-map-guard-test
  (testing "selection map should skip start cell hover"
    (let [js-source (slurp "resources/public/pomp/js/datatable.js")]
      (is (re-find #"if \(row === start\.row && col === start\.col\) return;" js-source)
          "Expected guard to avoid selection map when hovering start cell"))))
