(ns pomp.rad.analysis.board-test
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

(defn- board-request
  [filters]
  {:headers {"datastar-request" "true"}
   :body-params {:datatable {:library {:filters filters}}}})

(deftest make-board-derives-identity-and-class-markers-test
  (let [make-board (requiring-resolve 'pomp.rad.analysis.board/make-board)
        board ((var-get make-board)
               {:analysis/id "library-analysis"
                :analysis/filter-source-path [:datatable :library :filters]
                :chart-definitions {:genre {:chart/id "genre-frequency"}
                                    :era {:chart/id "era-frequency"}}
                :board-items [{:chart-key :genre}
                              {:chart-key :era
                               :instance-id "era-main"}]
                :make-chart-fn (fn [_]
                                 {:analysis-fn (constantly {:chart/buckets []})
                                  :render-card-fn (constantly [:div "card"])
                                  :render-script-fn (constantly nil)})
                :render-html-fn pr-str})
        [default-item custom-item] (:board-items board)]
    (testing "default instance identity derives from :chart-key"
      (is (= "genre" (:instance-id default-item)))
      (is (= "genre__wrapper" (:wrapper-id default-item)))
      (is (= "genre__card" (:card-id default-item)))
      (is (= "genre__mount-point" (:mount-id default-item))))
    (testing "custom :instance-id overrides identity base"
      (is (= "era-main" (:instance-id custom-item)))
      (is (= "era-main__wrapper" (:wrapper-id custom-item)))
      (is (= "era-main__card" (:card-id custom-item)))
      (is (= "era-main__mount-point" (:mount-id custom-item))))
    (testing "wrapper and mount classes include identity suffix markers"
      (is (str/includes? (:wrapper-class default-item) "chart-wrapper--genre"))
      (is (str/includes? (:mount-class default-item) "chart-mount--genre"))
      (is (str/includes? (:wrapper-class custom-item) "chart-wrapper--era-main"))
      (is (str/includes? (:mount-class custom-item) "chart-mount--era-main")))))

(deftest make-board-default-render-applies-layout-class-to-body-test
  (let [make-board (requiring-resolve 'pomp.rad.analysis.board/make-board)
        board ((var-get make-board)
               {:analysis/id "library-analysis"
                :analysis/filter-source-path [:datatable :library :filters]
                :chart-definitions {:genre {:chart/id "genre-frequency"}}
                :board-items [{:chart-key :genre}]
                :make-chart-fn (fn [_]
                                 {:analysis-fn (constantly {:chart/buckets []})
                                  :render-card-fn (constantly [:div "card"])
                                  :render-script-fn (constantly nil)})
                :render-html-fn pr-str})
        rendered ((:render-board-fn board) board)
        shell-attrs (second rendered)
        body-attrs (second (nth rendered 2))]
    (is (= "chart-grid__body" (:id body-attrs)))
    (is (= "flex flex-wrap gap-6" (:class body-attrs))
        "Expected default board layout classes on body container")
    (is (not= "flex flex-wrap gap-6" (:class shell-attrs))
        "Expected shell to keep trigger wiring separate from layout classes")
    (is (contains? shell-attrs :data-init))
    (is (contains? shell-attrs :data-on-signal-patch))))

(deftest make-board-handler-renders-all-mounts-in-one-patch-test
  (let [make-board (requiring-resolve 'pomp.rad.analysis.board/make-board)
        board ((var-get make-board)
               {:analysis/id "library-analysis"
                :analysis/filter-source-path [:datatable :library :filters]
                :table-name "books"
                :sql/frequency-fn (constantly [:deterministic-frequency-sql])
                :execute! (constantly [{:bucket "Fantasy" :count 2}])
                :chart-definitions {:genre {:chart/id "genre-frequency"
                                            :chart/type :frequency
                                            :query/type :frequency
                                            :bucket-column :genre}
                                    :era {:chart/id "era-frequency"
                                          :chart/type :frequency
                                          :query/type :frequency
                                          :bucket-column :era}}
                :board-items [{:chart-key :genre}
                              {:chart-key :era}]
                :render-html-fn pr-str})
        response ((:handler board) (board-request {}))
        payloads (vec (patch-elements-payloads (sse-response->string response)))]
    (is (= 1 (count payloads))
        "Expected one board patch response per handler request")
    (is (str/includes? (first payloads) "genre__mount-point")
        "Expected board payload to include first chart mount id")
    (is (str/includes? (first payloads) "era__mount-point")
        "Expected board payload to include second chart mount id")
    (is (str/includes? (first payloads) "chart-grid__body")
        "Expected board payload to target board body container")
    (is (str/includes? (first payloads) "buckets")
        "Expected default card rendering marker to appear in board payload")
    (is (not (str/includes? (first payloads) "data-init"))
        "Expected board patch payload to exclude shell trigger attributes")
    (is (not (str/includes? (first payloads) "data-on-signal-patch"))
        "Expected board patch payload to exclude shell trigger attributes")))

(deftest make-board-derives-signal-patch-filter-from-source-path-test
  (let [make-board (requiring-resolve 'pomp.rad.analysis.board/make-board)
        board ((var-get make-board)
               {:analysis/id "library-analysis"
                :analysis/filter-source-path [:datatable :library :filters]
                :chart-definitions {:genre {:chart/id "genre-frequency"}}
                :board-items [{:chart-key :genre}]
                :make-chart-fn (fn [_]
                                 {:analysis-fn (constantly {:chart/buckets []})
                                  :render-card-fn (constantly [:div "card"])
                                  :render-script-fn (constantly nil)})
                :render-html-fn pr-str})]
    (is (= "{ include: '^datatable\\.library\\.filters(\\.|$)' }"
           (:data-on-signal-patch-filter board))
        "Expected board signal patch filter to target datatable filter path only")))

(deftest make-board-handler-recomputes-all-items-from-filters-test
  (let [make-board (requiring-resolve 'pomp.rad.analysis.board/make-board)
        sql-calls (atom [])
        execute-calls (atom [])
        board ((var-get make-board)
               {:analysis/id "library-analysis"
                :analysis/filter-source-path [:datatable :library :filters]
                :table-name "books"
                :sql/frequency-fn (fn [_ query-ctx]
                                    (swap! sql-calls conj query-ctx)
                                    [:deterministic-frequency-sql query-ctx])
                :execute! (fn [sqlvec]
                            (swap! execute-calls conj sqlvec)
                            [{:bucket "Fantasy" :count 1}])
                :chart-definitions {:genre {:chart/id "genre-frequency"
                                            :chart/type :frequency
                                            :query/type :frequency
                                            :bucket-column :genre}
                                    :era {:chart/id "era-frequency"
                                          :chart/type :frequency
                                          :query/type :frequency
                                          :bucket-column :era}}
                :board-items [{:chart-key :genre}
                              {:chart-key :era}]
                :render-html-fn pr-str})
        filters {:publisher [{:type "enum" :op "is" :value "Penguin"}]}
        response ((:handler board) (board-request filters))
        _ (sse-response->string response)]
    (is (= 2 (count @sql-calls))
        "Expected one analysis SQL build per configured board item")
    (is (= 2 (count @execute-calls))
        "Expected one execute! call per configured board item")
    (is (= #{:genre :era}
           (set (map :bucket-column @sql-calls)))
        "Expected recomputation to cover all configured chart items")
    (is (every? #(= filters (:filters %)) @sql-calls)
        "Expected each item recomputation to receive extracted filters")))

(deftest make-board-handler-honors-render-board-item-override-test
  (let [make-board (requiring-resolve 'pomp.rad.analysis.board/make-board)
        board ((var-get make-board)
               {:analysis/id "library-analysis"
                :analysis/filter-source-path [:datatable :library :filters]
                :table-name "books"
                :sql/frequency-fn (constantly [:deterministic-frequency-sql])
                :execute! (constantly [{:bucket "Fantasy" :count 1}])
                :chart-definitions {:genre {:chart/id "genre-frequency"
                                            :chart/type :frequency
                                            :query/type :frequency
                                            :bucket-column :genre}}
                :board-items [{:chart-key :genre}]
                :render-board-item-fn (fn [{:keys [wrapper-id rendered-card]}]
                                        [:section {:data-marker (str "custom-item-" wrapper-id)}
                                         rendered-card])
                :render-html-fn pr-str})
        response ((:handler board) (board-request {}))
        payloads (vec (patch-elements-payloads (sse-response->string response)))]
    (is (some #(str/includes? % "custom-item-") payloads)
        "Expected render-board-item override marker in board SSE payload")))
