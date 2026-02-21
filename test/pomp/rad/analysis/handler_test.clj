(ns pomp.rad.analysis.handler-test
  (:require [clojure.test :refer [deftest is testing]]))

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

(deftest extract-filters-test
  (let [extract-filters (requiring-resolve 'pomp.rad.analysis.handler/extract-filters)
        expected-filters {:school [{:type "enum" :op "is" :value "Stoicism"}]
                          :verified [{:type "boolean" :op "is" :value "true"}]}
        req {:body-params {:datatable {:philosophers-table {:filters expected-filters}}}}
        req-with-query-signals {:query-params {"datastar" "{\"datatable\":{\"philosophers-table\":{\"filters\":{\"school\":[{\"type\":\"enum\",\"op\":\"is\",\"value\":\"Stoicism\"}],\"verified\":[{\"type\":\"boolean\",\"op\":\"is\",\"value\":\"true\"}]}}}}"}}]
    (testing "resolves filters from :analysis/filter-source-path"
      (is (= expected-filters
             (extract-filters req {:analysis/filter-source-path [:datatable :philosophers-table :filters]}))))
    (testing "supports keyword/string variants for datatable filter path"
      (is (= expected-filters
             (extract-filters req {:analysis/filter-source-path ["datatable" "philosophers-table" "filters"]}))))
    (testing "missing path returns empty map"
      (is (= {}
             (extract-filters req {:analysis/filter-source-path [:datatable :missing-table :filters]}))))
    (testing "falls back to datastar query payload when :body-params is absent"
      (is (= expected-filters
             (extract-filters req-with-query-signals
                              {:analysis/filter-source-path [:datatable :philosophers-table :filters]}))))))

(deftest build-context-test
  (let [build-context (requiring-resolve 'pomp.rad.analysis.handler/build-context)
        expected-filters {:school [{:type "enum" :op "is" :value "Stoicism"}]}
        req {:request-method :post
             :body-params {:datatable {:philosophers-table {:filters expected-filters}}}}
        ctx (build-context req {:analysis/id "philosophers-overview"
                                :analysis/filter-source-path [:datatable :philosophers-table :filters]
                                :chart/id "school-frequency"
                                :chart/type :bar})]
    (testing "includes required namespaced keys"
      (is (contains? ctx :analysis/id))
      (is (contains? ctx :analysis/filters))
      (is (contains? ctx :chart/id))
      (is (contains? ctx :chart/type))
      (is (contains? ctx :ring/request)))
    (testing "populates values from config, extracted filters, and request"
      (is (= "philosophers-overview" (:analysis/id ctx)))
      (is (= expected-filters (:analysis/filters ctx)))
      (is (= "school-frequency" (:chart/id ctx)))
      (is (= :bar (:chart/type ctx)))
      (is (= req (:ring/request ctx))))
    (testing "defaults :chart/renderer to :vega-lite when config omits it"
      (is (= :vega-lite (:chart/renderer ctx))))))

(deftest make-chart-handler-success-envelope-and-sse-test
  (let [make-chart-handler (requiring-resolve 'pomp.rad.analysis.handler/make-chart-handler)
        render-chart-calls (atom [])
        render-html-calls (atom [])]
    (is (var? make-chart-handler)
        "Expected make-chart-handler factory to exist")
    (when (var? make-chart-handler)
      (let [handler (make-chart-handler
                     {:analysis/id "philosophers-overview"
                      :analysis/filter-source-path [:datatable :philosophers-table :filters]
                      :chart/id "school-frequency"
                      :chart/type :bar
                      :analysis-fn (fn [_]
                                     {:chart/buckets [{:bucket/label "Stoicism" :bucket/value 12}]})
                      :render-chart-fn (fn [result]
                                         (swap! render-chart-calls conj result)
                                         {:marker (str "chart-" (:chart/id result))})
                      :render-html-fn (fn [view]
                                        (swap! render-html-calls conj view)
                                        (str "<section id=\"chart-patch\">" (:marker view) "</section>"))})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            sse-body (sse-response->string response)
            element-payloads (vec (patch-elements-payloads sse-body))
            result (first @render-chart-calls)]
        (testing "success path includes identity envelope keys"
          (is (= "philosophers-overview" (:analysis/id result)))
          (is (= "school-frequency" (:chart/id result)))
          (is (= :bar (:chart/type result)))
          (is (= :vega-lite (:chart/renderer result))))
        (testing "handler renders chart payload through render functions and patches elements"
          (is (= 1 (count @render-chart-calls)))
          (is (= 1 (count @render-html-calls)))
          (is (seq element-payloads))
          (is (some #(re-find #"chart-patch" %) element-payloads))
          (is (some #(re-find #"chart-school-frequency" %) element-payloads)))))))

(deftest make-chart-handler-render-script-sse-test
  (let [make-chart-handler (requiring-resolve 'pomp.rad.analysis.handler/make-chart-handler)
        render-script-calls (atom [])]
    (is (var? make-chart-handler)
        "Expected make-chart-handler factory to exist")
    (when (var? make-chart-handler)
      (let [handler (make-chart-handler
                     {:analysis/id "philosophers-overview"
                      :analysis/filter-source-path [:datatable :philosophers-table :filters]
                      :chart/id "school-frequency"
                      :chart/type :bar
                      :analysis-fn (fn [_]
                                     {:chart/spec {"mark" "bar"}})
                      :render-chart-fn identity
                      :render-html-fn (constantly "<section id=\"chart-patch\"></section>")
                      :render-script-fn (fn [result]
                                          (swap! render-script-calls conj result)
                                          (str "window.__chartRenderer='" (name (:chart/renderer result)) "';"))})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            sse-body (sse-response->string response)
            script-payloads (vec (execute-script-payloads sse-body))
            result (first @render-script-calls)]
        (testing "render-script-fn receives success envelope including :chart/renderer"
          (is (= :vega-lite (:chart/renderer result))))
        (testing "handler emits datastar execute-script event when :render-script-fn is provided"
          (is (= 1 (count @render-script-calls)))
          (is (seq script-payloads))
          (is (some #(re-find #"window.__chartRenderer='vega-lite'" %) script-payloads)))))))

(deftest make-chart-handler-anomaly-envelope-preservation-test
  (let [make-chart-handler (requiring-resolve 'pomp.rad.analysis.handler/make-chart-handler)
        render-chart-calls (atom [])]
    (is (var? make-chart-handler)
        "Expected make-chart-handler factory to exist")
    (when (var? make-chart-handler)
      (let [handler (make-chart-handler
                     {:analysis/id "philosophers-overview"
                      :analysis/filter-source-path [:datatable :philosophers-table :filters]
                      :chart/id "school-frequency"
                      :chart/type :bar
                      :analysis-fn (fn [_]
                                     {:anomaly/category :anomaly/forbidden
                                      :anomaly/message "No access"})
                      :render-chart-fn (fn [result]
                                         (swap! render-chart-calls conj result)
                                         {:status (:anomaly/category result)})
                      :render-html-fn (fn [view]
                                        (str "<section id=\"anomaly-patch\">" (:status view) "</section>"))})
            response (handler {:headers {"datastar-request" "true"}
                               :body-params {:datatable {:philosophers-table {:filters {}}}}})
            sse-body (sse-response->string response)
            element-payloads (vec (patch-elements-payloads sse-body))
            result (first @render-chart-calls)]
        (testing "anomaly path includes identity envelope keys"
          (is (= "philosophers-overview" (:analysis/id result)))
          (is (= "school-frequency" (:chart/id result)))
          (is (= :bar (:chart/type result))))
        (testing "handler preserves :anomaly/category unchanged"
          (is (= :anomaly/forbidden (:anomaly/category result))))
        (testing "anomaly path still emits patch-elements"
          (is (seq element-payloads))
          (is (some #(re-find #"anomaly-patch" %) element-payloads)))))))
