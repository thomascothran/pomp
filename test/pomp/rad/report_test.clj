(ns pomp.rad.report-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- sse-response->string
  [resp]
  (let [out (java.io.ByteArrayOutputStream.)]
    (.write_body_to_stream (:body resp) resp out)
    (.toString out "UTF-8")))

(defn- resolve-make-report
  []
  (try
    (requiring-resolve 'pomp.rad.report/make-report)
    (catch Throwable _
      nil)))

(defn- report-config
  ([] (report-config {}))
  ([{:keys [rows-fn
            count-fn
            export-stream-rows-fn
            analysis-sql-fn
            analysis-execute!]}]
  {:report/id "library-report"
   :data-url "/report/data"
   :render-html-fn pr-str
   :datatable {:id "library-table"
               :columns [{:key :id :label "ID" :type :number}
                         {:key :title :label "Title" :type :string}]
               :rows-fn (or rows-fn
                            (fn [_ _]
                              {:rows [{:id 1 :title "Republic"}]
                               :total-rows 1
                               :page {:size 10 :current 0}}))
               :count-fn (or count-fn (fn [_ _] {:total-rows 1}))
               :data-url "/report/data"
               :render-html-fn pr-str
               :export-stream-rows-fn (or export-stream-rows-fn
                                        (fn [_ctx on-row! on-complete!]
                                          (on-row! {:id 1 :title "Republic"})
                                          (on-complete! {:row-count 1})))}
   :analysis {:analysis/id "library-analysis"
              :analysis/filter-source-path [:datatable :library-table :filters]
              :table-name "books"
              :sql/frequency-fn (or analysis-sql-fn
                                    (constantly [:deterministic-frequency-sql]))
              :execute! (or analysis-execute!
                            (constantly [{:bucket "Philosophy" :count 1}]))
              :chart-definitions {:genre {:chart/id "genre-frequency"
                                          :chart/type :frequency
                                          :query/type :frequency
                                          :bucket-column :genre}}
              :board-items [{:chart-key :genre}]
              :render-html-fn pr-str}}))

(deftest make-report-builds-composed-descriptor-with-handler-test
  (let [make-report (resolve-make-report)]
    (is (var? make-report)
        "Expected public `pomp.rad.report/make-report` factory to exist")
    (when (var? make-report)
      (let [report ((var-get make-report) (report-config {}))]
        (is (map? report)
            "Expected make-report to return a report descriptor map")
        (is (map? (:datatable report))
            "Expected report descriptor to expose composed datatable descriptor")
        (is (map? (:analysis report))
            "Expected report descriptor to expose composed analysis descriptor")
        (is (fn? (:handler report))
            "Expected report descriptor to expose a unified data handler")))))

(deftest report-handler-coordinates-datatable-and-analysis-query-paths-test
  (let [make-report (resolve-make-report)]
    (is (var? make-report)
        "Expected public `pomp.rad.report/make-report` factory to exist")
    (when (var? make-report)
      (let [datatable-query-calls (atom [])
            analysis-query-calls (atom [])
            report ((var-get make-report)
                    (report-config
                     {:rows-fn (fn [query-signals _req]
                                 (swap! datatable-query-calls conj query-signals)
                                 {:rows [{:id 1 :title "ROW-MARKER-REPUBLIC"}]
                                  :total-rows 1
                                  :page {:size 10 :current 0}})
                      :analysis-execute! (fn [sqlvec]
                                           (swap! analysis-query-calls conj sqlvec)
                                           [{:bucket "ANALYSIS-MARKER-PHILOSOPHY" :count 1}])}))
            response ((:handler report)
                      {:headers {"datastar-request" "true"}
                       :body-params {:datatable {:library-table {:filters {}}}}})
            sse-body (sse-response->string response)]
        (testing "single request cycle executes both query paths"
          (is (= 1 (count @datatable-query-calls))
              "Expected report refresh to execute the datatable query path once")
          (is (= 1 (count @analysis-query-calls))
              "Expected report refresh to execute the analysis query path once"))
        (testing "single SSE response includes evidence from both real render paths"
          (is (str/includes? sse-body "ROW-MARKER-REPUBLIC")
              "Expected SSE payload to include datatable-rendered row content")
          (is (str/includes? sse-body "ANALYSIS-MARKER-PHILOSOPHY")
              "Expected SSE payload to include analysis-rendered query content"))))))

(deftest report-handler-preserves-datatable-export-action-test
  (let [make-report (resolve-make-report)]
    (is (var? make-report)
        "Expected public `pomp.rad.report/make-report` factory to exist")
    (when (var? make-report)
      (let [export-query-calls (atom [])
            analysis-query-calls (atom [])
            report ((var-get make-report)
                    (report-config
                     {:export-stream-rows-fn (fn [ctx on-row! on-complete!]
                                               (swap! export-query-calls conj ctx)
                                               (on-row! {:id 42 :title "EXPORT-MARKER-ROW"})
                                               (on-complete! {:row-count 1}))
                      :analysis-execute! (fn [sqlvec]
                                           (swap! analysis-query-calls conj sqlvec)
                                           [{:bucket "should-not-run" :count 1}])}))
            response ((:handler report)
                      {:query-params {"action" "export"}
                       :headers {"datastar-request" "true"}
                       :body-params {:datatable {:library-table {}}}})
            sse-body (sse-response->string response)]
        (is (= 1 (count @export-query-calls))
            "Expected export action to execute datatable export stream path")
        (is (zero? (count @analysis-query-calls))
            "Expected export action to bypass analysis query path")
        (is (str/includes? sse-body "pompDatatableExportBegin")
            "Expected report handler to preserve datatable export passthrough behavior")
        (is (str/includes? sse-body "pompDatatableExportFinish")
            "Expected report export passthrough to preserve datatable export completion event")
        (is (str/includes? sse-body "EXPORT-MARKER-ROW")
            "Expected report export passthrough to stream datatable export row content")))))
