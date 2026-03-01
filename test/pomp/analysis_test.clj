(ns pomp.analysis-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- sse-response->string
  [resp]
  (let [out (java.io.ByteArrayOutputStream.)]
    (.write_body_to_stream (:body resp) resp out)
    (.toString out "UTF-8")))

(defn- patch-elements-payloads
  [sse-body]
  (map second
       (re-seq #"(?s)event: datastar-patch-elements\ndata: elements (.*?)\n\n" sse-body)))

(defn- execute-script-payloads
  [sse-body]
  (let [execute-script-events
        (map second
             (re-seq #"(?s)event: datastar-execute-script\ndata: script (.*?)\n\n" sse-body))
        patch-script-events
        (map second
             (re-seq #"(?s)event: datastar-patch-elements\ndata: selector body\ndata: mode append\ndata: elements <script data-effect=\"el.remove\(\)\">(.*?)</script>\n\n"
                     sse-body))]
    (concat execute-script-events patch-script-events)))

(deftest make-chart-handler-entrypoint-test
  (let [public-maker (requiring-resolve 'pomp.rad.analysis/make-chart-handler)]
    (is (var? public-maker)
        "Expected public make-chart-handler entrypoint")
    (when (var? public-maker)
      (let [handler ((var-get public-maker)
                     {:analysis/id "analysis"
                      :chart/id "chart"
                      :chart/type :bar
                      :analysis/filter-source-path [:datatable :demo :filters]
                      :analysis-fn (fn [_] {:chart/buckets []})
                      :render-chart-fn identity
                      :render-html-fn (constantly "<div id=\"chart\"></div>")})]
        (is (fn? handler)
            "Expected public entrypoint to delegate to handler factory")))))

(deftest make-board-entrypoint-test
  (let [public-maker (requiring-resolve 'pomp.rad.analysis/make-board)]
    (is (var? public-maker)
        "Expected public make-board entrypoint")
    (when (var? public-maker)
      (let [board ((var-get public-maker)
                   {:analysis/id "library-analysis"
                    :analysis/filter-source-path [:datatable :library :filters]
                    :chart-definitions {:genre {:chart/id "genre-frequency"}}
                    :board-items [{:chart-key :genre}]
                    :make-chart-fn (fn [_]
                                     {:analysis-fn (constantly {:chart/buckets []})
                                      :render-card-fn (constantly [:div "card"])
                                      :render-script-fn (constantly nil)})
                    :render-html-fn identity})]
        (is (map? board)
            "Expected public make-board entrypoint to return board descriptor map")
        (is (fn? (:handler board))
            "Expected board descriptor to include :handler from delegated factory")))))

(deftest make-analysis-fn-frequency-query-shape-test
  (let [make-analysis-fn (requiring-resolve 'pomp.rad.analysis/make-analysis-fn)
        sql-calls (atom [])
        execute-calls (atom [])]
    (is (var? make-analysis-fn)
        "Expected public make-analysis-fn entrypoint")
    (when (var? make-analysis-fn)
      (let [analysis-fn ((var-get make-analysis-fn)
                         {:table-name "shared_philosophers"
                          :sql/frequency-fn (fn [table-ctx query-ctx]
                                              (swap! sql-calls conj [table-ctx query-ctx])
                                              [:deterministic-frequency-sql table-ctx query-ctx])
                          :execute! (fn [sqlvec]
                                      (swap! execute-calls conj sqlvec)
                                      [{:bucket "Stoicism" :count 2}
                                       {:bucket "Platonism" :count 1}])}
                         {:chart/id "school-frequency"
                          :query/type :frequency
                          :bucket-column :school})
            result (analysis-fn {:analysis/filters {:verified [{:type "boolean" :op "is" :value "true"}]}})]
        (testing "builds frequency query from shared context and executes deterministically"
          (is (= [[{:table-name "shared_philosophers"}
                   {:bucket-column :school
                    :filters {:verified [{:type "boolean" :op "is" :value "true"}]}}]]
                 @sql-calls))
          (is (= [[:deterministic-frequency-sql
                   {:table-name "shared_philosophers"}
                   {:bucket-column :school
                    :filters {:verified [{:type "boolean" :op "is" :value "true"}]}}]]
                 @execute-calls)))
        (testing "returns chart payload shape for downstream rendering"
          (is (contains? result :chart/spec))
          (is (contains? result :chart/buckets)))))))

(deftest make-analysis-fn-table-name-precedence-test
  (let [make-analysis-fn (requiring-resolve 'pomp.rad.analysis/make-analysis-fn)
        sql-calls (atom [])
        shared-context {:table-name "shared_table"
                        :sql/frequency-fn (fn [table-ctx query-ctx]
                                            (swap! sql-calls conj [table-ctx query-ctx])
                                            [:deterministic-frequency-sql table-ctx query-ctx])
                        :execute! (constantly [])}]
    (is (var? make-analysis-fn)
        "Expected public make-analysis-fn entrypoint")
    (when (var? make-analysis-fn)
      (let [from-shared ((var-get make-analysis-fn)
                         shared-context
                         {:chart/id "school-frequency"
                          :query/type :frequency
                          :bucket-column :school})
            from-override ((var-get make-analysis-fn)
                           shared-context
                           {:chart/id "school-frequency"
                            :query/type :frequency
                            :bucket-column :school
                            :table-name "chart_override_table"})]
        (from-shared {:analysis/filters {}})
        (from-override {:analysis/filters {}})
        (is (= ["shared_table"
                "chart_override_table"]
               (mapv (comp :table-name first) @sql-calls))
            "Expected shared :table-name default and chart-level override precedence")))))

(deftest make-analysis-fn-chart-value-sort-frequency-test
  (let [make-analysis-fn (requiring-resolve 'pomp.rad.analysis/make-analysis-fn)]
    (is (var? make-analysis-fn)
        "Expected public make-analysis-fn entrypoint")
    (when (var? make-analysis-fn)
      (let [analysis-fn ((var-get make-analysis-fn)
                         {:table-name "shared_philosophers"
                          :sql/frequency-fn (constantly [:deterministic-frequency-sql])
                          :execute! (constantly [{:bucket "Zulu" :count 2}
                                                 {:bucket "Beta" :count 3}
                                                 {:bucket "Gamma" :count 1}
                                                 {:bucket "Alpha" :count 3}])}
                         {:chart/id "school-frequency"
                          :query/type :frequency
                          :bucket-column :school
                          :chart/value-sort :frequency})
            result (analysis-fn {:analysis/filters {}})]
        (is (= [{:bucket/label "Alpha" :bucket/value 3}
                {:bucket/label "Beta" :bucket/value 3}
                {:bucket/label "Zulu" :bucket/value 2}
                {:bucket/label "Gamma" :bucket/value 1}]
               (:chart/buckets result))
            "Expected declarative :chart/value-sort :frequency to sort by value desc, then label asc")))))

(deftest make-analysis-fn-rows->values-fn-precedence-over-value-sort-test
  (let [make-analysis-fn (requiring-resolve 'pomp.rad.analysis/make-analysis-fn)]
    (is (var? make-analysis-fn)
        "Expected public make-analysis-fn entrypoint")
    (when (var? make-analysis-fn)
      (let [analysis-fn ((var-get make-analysis-fn)
                         {:table-name "shared_philosophers"
                          :sql/frequency-fn (constantly [:deterministic-frequency-sql])
                          :execute! (constantly [{:bucket "ignored" :count 100}])}
                         {:chart/id "school-frequency"
                          :query/type :frequency
                          :bucket-column :school
                          :chart/value-sort :frequency
                          :rows->values-fn (fn [_ _]
                                             [{:label "Low" :value 1}
                                              {:label "High" :value 9}])})
            result (analysis-fn {:analysis/filters {}})]
        (is (= [{:bucket/label "Low" :bucket/value 1}
                {:bucket/label "High" :bucket/value 9}]
               (:chart/buckets result))
            "Expected explicit :rows->values-fn output order to take precedence over declarative :chart/value-sort")))))

(deftest make-analysis-fn-default-rows->values-preserves-row-order-test
  (let [make-analysis-fn (requiring-resolve 'pomp.rad.analysis/make-analysis-fn)]
    (is (var? make-analysis-fn)
        "Expected public make-analysis-fn entrypoint")
    (when (var? make-analysis-fn)
      (let [analysis-fn ((var-get make-analysis-fn)
                         {:table-name "shared_philosophers"
                          :sql/frequency-fn (constantly [:deterministic-frequency-sql])
                          :execute! (constantly [{:bucket "Zulu" :count 2}
                                                 {:bucket "Alpha" :count 3}
                                                 {:bucket "Gamma" :count 1}])}
                         {:chart/id "school-frequency"
                          :query/type :frequency
                          :bucket-column :school})
            result (analysis-fn {:analysis/filters {}})]
        (is (= [{:bucket/label "Zulu" :bucket/value 2}
                {:bucket/label "Alpha" :bucket/value 3}
                {:bucket/label "Gamma" :bucket/value 1}]
               (:chart/buckets result))
            "Expected default rows-to-values mapping to preserve row order when no explicit sort is configured")))))

(deftest make-analysis-fn-histogram-range-shape-and-bucket-asc-sort-test
  (let [make-analysis-fn (requiring-resolve 'pomp.rad.analysis/make-analysis-fn)]
    (is (var? make-analysis-fn)
        "Expected public make-analysis-fn entrypoint")
    (when (var? make-analysis-fn)
      (let [analysis-fn ((var-get make-analysis-fn)
                         {:table-name "shared_philosophers"
                          :sql/histogram-fn (constantly [:deterministic-histogram-sql])
                          :execute! (constantly [{:bucket 20 :count 1}
                                                 {:bucket 0 :count 3}
                                                 {:bucket 10 :count 2}])}
                         {:chart/id "influence-histogram"
                          :chart/type :histogram
                          :query/type :histogram
                          :bucket-column :influence
                          :bucket-size 10
                          :chart/value-shape :histogram-range
                          :chart/value-sort :bucket-asc})
            result (analysis-fn {:analysis/filters {}})]
        (is (= [{:bucket/label "0-9" :bucket/value 3}
                {:bucket/label "10-19" :bucket/value 2}
                {:bucket/label "20-29" :bucket/value 1}]
               (:chart/buckets result))
            "Expected declarative histogram range labels sorted by ascending bucket when no :rows->values-fn is provided")))))

(deftest make-analysis-fn-histogram-null-count-subtitle-config-test
  (let [make-analysis-fn (requiring-resolve 'pomp.rad.analysis/make-analysis-fn)]
    (is (var? make-analysis-fn)
        "Expected public make-analysis-fn entrypoint")
    (when (var? make-analysis-fn)
      (let [analysis-fn ((var-get make-analysis-fn)
                         {:table-name "shared_philosophers"
                          :sql/histogram-fn (constantly [:deterministic-histogram-sql])
                          :sql/null-count-fn (constantly [:deterministic-null-count-sql])
                          :execute! (fn [[sql-id]]
                                      (case sql-id
                                        :deterministic-histogram-sql [{:bucket 10 :count 2}]
                                        :deterministic-null-count-sql [{:count 4}]
                                        []))}
                         {:chart/id "influence-histogram"
                          :chart/type :histogram
                          :query/type :histogram
                          :bucket-column :influence
                          :bucket-size 10
                          :include-null-count? true
                          :chart/value-shape :histogram-range
                          :chart/value-sort :bucket-asc
                          :chart/null-count-subtitle "Null values: {null-count}"})
            result (analysis-fn {:analysis/filters {}})]
        (is (= "Null values: 4"
               (get-in result [:chart/spec "title" "subtitle"]))
            "Expected declarative null-count subtitle config to inject resolved null count into default histogram spec")))))

(deftest make-chart-default-card-behavior-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)
        render-chart-calls (atom [])]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler render-card-fn] :as chart}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :frequency
              :analysis-fn (constantly {:chart/spec {:mark "bar"}
                                        :chart/buckets [{:bucket/label "Stoicism"
                                                         :bucket/value 2}]})
              :render-chart-fn (fn [result]
                                 (swap! render-chart-calls conj result)
                                 [:div#chart-body "chart"])
              :render-html-fn identity})
            sample-result {:chart/spec {:mark "bar"}
                           :chart/buckets [{:bucket/label "Stoicism"
                                            :bucket/value 2}]}
            card (render-card-fn sample-result)]
        (is (map? chart)
            "Expected make-chart to return chart descriptor map")
        (is (fn? handler)
            "Expected chart descriptor to include :handler")
        (is (fn? render-card-fn)
            "Expected chart descriptor to include default :render-card-fn")
        (is (= [sample-result] @render-chart-calls)
            "Expected default card renderer to delegate body rendering to :render-chart-fn")
        (is card
            "Expected default card renderer to return renderable card value")))))

(deftest make-chart-default-card-renders-declarative-null-count-line-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "influence-histogram"
              :chart/type :histogram
              :query/type :histogram
              :bucket-column :influence
              :bucket-size 10
              :include-null-count? true
              :chart/value-shape :histogram-range
              :chart/value-sort :bucket-asc
              :chart/null-count-line? true
              :table-name "philosophers"
              :sql/histogram-fn (constantly [:deterministic-histogram-sql])
              :sql/null-count-fn (constantly [:deterministic-null-count-sql])
              :execute! (fn [[sql-id]]
                          (case sql-id
                            :deterministic-histogram-sql [{:bucket 0 :count 2}]
                            :deterministic-null-count-sql [{:count 7}]
                            []))
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            element-payloads (vec (patch-elements-payloads (sse-response->string response)))]
        (is (some #(str/includes? % "Null values: 7") element-payloads)
            "Expected default card body to render declarative null-count line when configured")))))

(deftest make-chart-shared-context-call-shape-test
  (let [make-chart (var-get (requiring-resolve 'pomp.rad.analysis/make-chart))
        sql-calls (atom [])
        shared-context {:analysis/id "philosophers-overview"
                        :analysis/filter-source-path [:datatable :philosophers-table :filters]
                        :table-name "shared_table"
                        :execute! (constantly [{:bucket "Stoicism" :count 2}])
                        :sql/frequency-fn (fn [table-ctx query-ctx]
                                            (swap! sql-calls conj [table-ctx query-ctx])
                                            [:deterministic-frequency-sql table-ctx query-ctx])
                        :render-card-fn (constantly [:div "chart"])
                        :render-html-fn (constantly "<div id=\"chart\"></div>")}
        chart-spec {:chart/id "school-frequency"
                    :chart/type :frequency
                    :query/type :frequency
                    :bucket-column :school
                    :table-name "chart_override_table"}
        chart (make-chart shared-context chart-spec)]
    (is (contains? chart :handler)
        "Expected make-chart to return descriptor map with :handler")
    ((:handler chart) {:headers {"datastar-request" "true"}
                       :body-params {:datatable {:philosophers-table {:filters {}}}}})
    (is (= "chart_override_table"
           (-> @sql-calls first first :table-name))
        "Expected chart-spec :table-name to override shared-context :table-name")))

(deftest make-chart-custom-render-card-override-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)
        custom-render-calls (atom [])]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :frequency
              :analysis-fn (constantly {:chart/spec {:mark "bar"}
                                        :chart/buckets [{:bucket/label "Stoicism"
                                                         :bucket/value 2}]})
              :render-chart-fn (constantly [:div#default-chart-body "default"])
              :render-card-fn (fn [result]
                                (swap! custom-render-calls conj result)
                                {:card/marker "custom-card-used"})
              :render-html-fn (fn [{:keys [card/marker]}]
                                (str "<article id=\"custom-card\">" marker "</article>"))})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            payloads (vec (patch-elements-payloads (sse-response->string response)))]
        (is (= 1 (count @custom-render-calls))
            "Expected :render-card-fn override to be invoked exactly once")
        (is (some #(re-find #"custom-card-used" %) payloads)
            "Expected handler output to include custom card render marker")))))

(deftest make-chart-default-renderer-and-script-defaults-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :bar
              :query/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :chart/spec {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
                           "description" "default-spec-marker"
                           "mark" "bar"}
              :execute! (constantly [{:bucket "Stoicism" :count 2}
                                     {:bucket "Platonism" :count 1}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            sse-body (sse-response->string response)
            element-payloads (vec (patch-elements-payloads sse-body))
            script-payloads (vec (execute-script-payloads sse-body))]
        (testing "omitting render/build function keys still renders and emits Vega script"
          (is (seq element-payloads)
              "Expected patch-elements SSE payload when defaults are used")
          (is (some #(str/includes? % "school-frequency") element-payloads)
              "Expected rendered card payload to include chart id marker")
          (is (seq script-payloads)
              "Expected execute-script SSE payload when :render-script-fn is omitted")
          (is (some #(re-find #"vega-lite|vega\\.github\\.io/schema/vega-lite" %) script-payloads)
              "Expected default script payload to include Vega-Lite marker")
          (is (some #(str/includes? % "default-spec-marker") script-payloads)
              "Expected default script payload to include configured :chart/spec"))))))

(deftest make-chart-default-renderer-uses-target-class-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/target-id "school-frequency-chart-vega"
              :chart/target-class "qa-mount min-h-80"
              :chart/type :bar
              :query/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :chart/spec {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
                           "description" "target-class-marker"
                           "mark" "bar"}
              :execute! (constantly [{:bucket "Stoicism" :count 2}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            element-payloads (vec (patch-elements-payloads (sse-response->string response)))]
        (testing "default renderer uses declarative :chart/target-class when :render-chart-fn is omitted"
          (is (some #(and (or (str/includes? % "id=\"school-frequency-chart-vega\"")
                              (str/includes? % ":id \"school-frequency-chart-vega\""))
                          (str/includes? % "qa-mount")
                          (str/includes? % "min-h-80"))
                    element-payloads)
              "Expected patched chart mount to include declarative :chart/target-class"))))))

(deftest make-chart-render-chart-fn-precedence-over-target-class-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)
        render-chart-calls (atom [])]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/target-id "school-frequency-chart-vega"
              :chart/target-class "qa-ignored-by-explicit-renderer"
              :chart/type :bar
              :query/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :chart/spec {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
                           "description" "precedence-marker"
                           "mark" "bar"}
              :execute! (constantly [{:bucket "Stoicism" :count 2}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :render-chart-fn (fn [result]
                                 (swap! render-chart-calls conj result)
                                 [:div {:id "school-frequency-custom-target"
                                        :class "qa-explicit-renderer"}
                                  "explicit-renderer"])
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            element-payloads (vec (patch-elements-payloads (sse-response->string response)))]
        (testing "explicit :render-chart-fn takes precedence over declarative :chart/target-class"
          (is (= 1 (count @render-chart-calls))
              "Expected explicit :render-chart-fn override to be called")
          (is (some #(and (str/includes? % "school-frequency-custom-target")
                          (str/includes? % "qa-explicit-renderer"))
                    element-payloads)
              "Expected patched payload to include explicit renderer output")
          (is (not-any? #(str/includes? % "qa-ignored-by-explicit-renderer") element-payloads)
              "Expected declarative :chart/target-class to be ignored when explicit renderer is supplied"))))))

(deftest make-chart-frequency-default-script-builds-real-spec-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :frequency
              :query/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :execute! (constantly [{:bucket "Stoicism" :count 2}
                                     {:bucket "Platonism" :count 1}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            script-payloads (vec (execute-script-payloads (sse-response->string response)))]
        (testing "frequency charts use default spec builder when spec/build fn are omitted"
          (is (seq script-payloads)
              "Expected execute-script SSE payload when defaults are used")
          (is (some #(and (str/includes? % "$schema")
                          (or (str/includes? % "vega.github.io/schema/vega-lite")
                              (str/includes? % "vega.github.io\\/schema\\/vega-lite")))
                    script-payloads)
              "Expected default frequency script payload to include Vega-Lite schema marker")
          (is (not-any? #(str/includes? % "const spec=null") script-payloads)
              "Expected default frequency script payload to build a real spec, not null"))))))

(deftest make-chart-frequency-default-builder-applies-spec-config-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :frequency
              :query/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :chart/spec-config {:x-title "School"
                                  :y-title "Count"
                                  :height 371}
              :execute! (constantly [{:bucket "Stoicism" :count 2}
                                     {:bucket "Platonism" :count 1}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            script-payloads (vec (execute-script-payloads (sse-response->string response)))]
        (testing "default frequency builder applies declarative :chart/spec-config fields"
          (is (seq script-payloads)
              "Expected execute-script SSE payload when defaults are used")
          (is (some #(str/includes? % "\"height\":371") script-payloads)
              "Expected execute-script payload to include declarative spec height")
          (is (some #(str/includes? % "\"title\":\"School\"") script-payloads)
              "Expected execute-script payload to include declarative x-axis title")
          (is (some #(str/includes? % "\"title\":\"Count\"") script-payloads)
              "Expected execute-script payload to include declarative y-axis title"))))))

(deftest make-chart-build-spec-fn-precedence-over-spec-config-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :frequency
              :query/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :chart/spec-config {:x-title "School"
                                  :y-title "Count"
                                  :height 371}
              :build-spec-fn (fn [_ _]
                               {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
                                "description" "build-spec-fn-marker"
                                "mark" "bar"
                                "height" 222})
              :execute! (constantly [{:bucket "Stoicism" :count 2}
                                     {:bucket "Platonism" :count 1}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            script-payloads (vec (execute-script-payloads (sse-response->string response)))]
        (testing "explicit :build-spec-fn takes precedence over declarative :chart/spec-config"
          (is (some #(str/includes? % "build-spec-fn-marker") script-payloads)
              "Expected execute-script payload to include :build-spec-fn marker")
          (is (some #(str/includes? % "\"height\":222") script-payloads)
              "Expected execute-script payload to include :build-spec-fn-controlled height")
          (is (not-any? #(str/includes? % "\"height\":371") script-payloads)
              "Expected declarative spec-config height to be ignored when :build-spec-fn is present"))))))

(deftest make-chart-build-spec-fn-precedence-over-null-count-subtitle-config-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "influence-histogram"
              :chart/type :histogram
              :query/type :histogram
              :bucket-column :influence
              :bucket-size 10
              :table-name "philosophers"
              :include-null-count? true
              :chart/value-shape :histogram-range
              :chart/value-sort :bucket-asc
              :chart/null-count-subtitle "Null values: {null-count}"
              :build-spec-fn (fn [values _]
                               {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
                                "description" "explicit-build-spec-marker"
                                "title" {"text" "Influence Histogram"
                                         "subtitle" "Subtitle from build-spec-fn"}
                                "data" {"values" values}
                                "mark" {"type" "bar"}})
              :sql/histogram-fn (constantly [:deterministic-histogram-sql])
              :sql/null-count-fn (constantly [:deterministic-null-count-sql])
              :execute! (fn [[sql-id]]
                          (case sql-id
                            :deterministic-histogram-sql [{:bucket 10 :count 2}]
                            :deterministic-null-count-sql [{:count 5}]
                            []))
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            script-payloads (vec (execute-script-payloads (sse-response->string response)))]
        (is (some #(str/includes? % "explicit-build-spec-marker") script-payloads)
            "Expected execute-script payload to include explicit :build-spec-fn marker")
        (is (some #(str/includes? % "Subtitle from build-spec-fn") script-payloads)
            "Expected execute-script payload to preserve :build-spec-fn subtitle")
        (is (not-any? #(str/includes? % "Null values: 5") script-payloads)
            "Expected declarative null-count subtitle config to be ignored when explicit :build-spec-fn is present")))))

(deftest make-chart-override-precedence-for-build-and-render-fns-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)
        build-spec-calls (atom [])
        render-chart-calls (atom [])
        render-script-calls (atom [])]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :bar
              :query/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :execute! (constantly [{:bucket "Stoicism" :count 2}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :build-spec-fn (fn [values _]
                               (swap! build-spec-calls conj values)
                               {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
                                "description" "build-spec-override-marker"
                                "mark" "bar"})
              :render-chart-fn (fn [result]
                                 (swap! render-chart-calls conj result)
                                 [:div#custom-chart-body "custom-chart-body"])

              :render-script-fn (fn [result]
                                  (swap! render-script-calls conj result)
                                  "window.__pompOverrideScript=true;")
              :render-html-fn pr-str})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            sse-body (sse-response->string response)
            element-payloads (vec (patch-elements-payloads sse-body))
            script-payloads (vec (execute-script-payloads sse-body))]
        (testing "explicit function overrides take precedence over defaults"
          (is (= [[{:label "Stoicism" :value 2}]] @build-spec-calls)
              "Expected :build-spec-fn override to receive values from default rows->values")
          (is (= 1 (count @render-chart-calls))
              "Expected :render-chart-fn override to be invoked")
          (is (= 1 (count @render-script-calls))
              "Expected :render-script-fn override to be invoked")
          (is (some #(str/includes? % "custom-chart-body") element-payloads)
              "Expected patched payload to include custom chart body marker")
          (is (some #(str/includes? % "window.__pompOverrideScript=true;") script-payloads)
              "Expected overridden script payload to be emitted"))))))

(deftest make-chart-frequency-defaults-query-type-from-chart-type-test
  (let [make-chart (requiring-resolve 'pomp.rad.analysis/make-chart)]
    (is (var? make-chart)
        "Expected public make-chart entrypoint")
    (when (var? make-chart)
      (let [{:keys [handler]}
            ((var-get make-chart)
             {:analysis/id "philosophers-overview"
              :analysis/filter-source-path [:datatable :philosophers-table :filters]
              :chart/id "school-frequency"
              :chart/type :frequency
              :bucket-column :school
              :table-name "philosophers"
              :execute! (constantly [{:bucket "Stoicism" :count 2}
                                     {:bucket "Platonism" :count 1}])
              :sql/frequency-fn (constantly [:deterministic-sql])
              :render-html-fn pr-str})
            request {:headers {"datastar-request" "true"}
                     :body-params {:datatable {:philosophers-table {:filters {}}}}}
            response-or-throwable (try
                                    (handler request)
                                    (catch Throwable t
                                      t))]
        (is (not (instance? Throwable response-or-throwable))
            "Expected handler invocation to avoid exception when :query/type is omitted")
        (when-not (instance? Throwable response-or-throwable)
          (let [response response-or-throwable
                sse-body (sse-response->string response)
                element-payloads (vec (patch-elements-payloads sse-body))
                script-payloads (vec (execute-script-payloads sse-body))]
            (is (seq element-payloads)
                "Expected patch-elements payloads under chart-type-only config")
            (is (seq script-payloads)
                "Expected execute-script payloads under chart-type-only config")))))))

(deftest preset-chart-constructors-public-vars-test
  (let [frequency-chart (requiring-resolve 'pomp.rad.analysis/frequency-chart)
        pie-chart (requiring-resolve 'pomp.rad.analysis/pie-chart)
        histogram-chart (requiring-resolve 'pomp.rad.analysis/histogram-chart)]
    (is (var? frequency-chart)
        "Expected public frequency-chart constructor")
    (is (var? pie-chart)
        "Expected public pie-chart constructor")
    (is (var? histogram-chart)
        "Expected public histogram-chart constructor")))

(deftest preset-chart-constructors-map-input-and-allow-override-passthrough-test
  (let [frequency-chart (requiring-resolve 'pomp.rad.analysis/frequency-chart)
        pie-chart (requiring-resolve 'pomp.rad.analysis/pie-chart)
        histogram-chart (requiring-resolve 'pomp.rad.analysis/histogram-chart)
        concise-input {:id "preset-chart"
                       :title "Preset chart"
                       :target-id "preset-chart-target"
                       :bucket-column :school
                       :spec {:x-title "School"}}
        override-map {:chart/title "Override title"
                      :render-card-fn (constantly [:div "override-card"])
                      :custom/marker :passthrough}]
    (testing "frequency-chart maps concise keys and default chart/query behavior"
      (is (var? frequency-chart)
          "Expected frequency-chart constructor before behavior assertions")
      (when (var? frequency-chart)
        (is (= (merge {:chart/id "preset-chart"
                       :chart/title "Preset chart"
                       :chart/target-id "preset-chart-target"
                       :bucket-column :school
                       :chart/spec-config {:x-title "School"}
                       :chart/type :frequency
                       :query/type :frequency
                       :chart/value-sort :frequency}
                      override-map)
               ((var-get frequency-chart) concise-input override-map))
            "Expected frequency-chart to map concise keys, apply frequency defaults, and merge override passthrough keys")))
    (testing "pie-chart maps concise keys and default chart/query behavior"
      (is (var? pie-chart)
          "Expected pie-chart constructor before behavior assertions")
      (when (var? pie-chart)
        (is (= (merge {:chart/id "preset-chart"
                       :chart/title "Preset chart"
                       :chart/target-id "preset-chart-target"
                       :bucket-column :school
                       :chart/spec-config {:x-title "School"}
                       :chart/type :pie
                       :query/type :frequency
                       :chart/value-sort :frequency}
                      override-map)
               ((var-get pie-chart) concise-input override-map))
            "Expected pie-chart to map concise keys, apply pie defaults, and merge override passthrough keys")))
    (testing "histogram-chart maps concise keys and default chart/query behavior"
      (is (var? histogram-chart)
          "Expected histogram-chart constructor before behavior assertions")
      (when (var? histogram-chart)
        (is (= (merge {:chart/id "preset-chart"
                       :chart/title "Preset chart"
                       :chart/target-id "preset-chart-target"
                       :bucket-column :school
                       :chart/spec-config {:x-title "School"}
                       :chart/type :histogram
                       :query/type :histogram
                       :chart/value-shape :histogram-range
                       :chart/value-sort :bucket-asc}
                      override-map)
               ((var-get histogram-chart) concise-input override-map))
            "Expected histogram-chart to map concise keys, apply histogram defaults, and merge override passthrough keys")))))
