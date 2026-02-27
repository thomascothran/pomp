(ns pomp.datatable.export.stream
  (:require [clojure.data.json :as json]
            [pomp.datatable.export.csv :as export-csv]
            [starfederation.datastar.clojure.api :as d*]))

(defn emit-export-script!
  [sse fn-name payload]
  (let [result (d*/execute-script! sse
                                   (str "if (typeof window !== 'undefined' && typeof window."
                                        fn-name
                                        " === 'function') { window."
                                        fn-name
                                        "("
                                        (json/write-str payload)
                                        "); }"))]
    (when (false? result)
      (throw (ex-info "Export stream disconnected"
                      {:type :export-disconnected
                       :fn-name fn-name})))))

(defn- utf8-bytes
  [s]
  (count (.getBytes ^String s "UTF-8")))

(defn run-export-stream!
  [{:keys [id sse export-filename export-columns export-stream-rows-fn stream-context export-limits]}]
  (let [chunk-size (max 1 (long (or (:chunk-rows export-limits) 100)))
        timeout-ms (:timeout-ms export-limits)
        max-rows (:max-rows export-limits)
        max-bytes (:max-bytes export-limits)
        deadline-ms (when timeout-ms (+ (System/currentTimeMillis) timeout-ms))
        row-count (volatile! 0)
        byte-count (volatile! 0)
        chunk-row-count (volatile! 0)
        chunk-buffer (volatile! "")
        fail-if-timeout! (fn []
                           (when (and deadline-ms (> (System/currentTimeMillis) deadline-ms))
                             (throw (ex-info "Export exceeded timeout"
                                             {:type :export-timeout
                                              :timeout-ms timeout-ms}))))
        flush-chunk! (fn []
                       (when (seq @chunk-buffer)
                         (emit-export-script! sse
                                              "pompDatatableExportAppend"
                                              {:tableId id
                                               :chunk @chunk-buffer})
                         (vreset! chunk-buffer "")
                         (vreset! chunk-row-count 0)))
        on-row! (fn [row]
                  (fail-if-timeout!)
                  (let [line (export-csv/csv-line row export-columns)
                        next-row-count (inc @row-count)
                        next-byte-count (+ @byte-count (utf8-bytes line))]
                    (when (and max-rows (> next-row-count max-rows))
                      (throw (ex-info "Export exceeded max rows"
                                      {:type :export-max-rows
                                       :max-rows max-rows
                                       :row-count next-row-count})))
                    (when (and max-bytes (> next-byte-count max-bytes))
                      (throw (ex-info "Export exceeded max bytes"
                                      {:type :export-max-bytes
                                       :max-bytes max-bytes
                                       :byte-count next-byte-count})))
                    (vreset! row-count next-row-count)
                    (vreset! byte-count next-byte-count)
                    (vreset! chunk-buffer (str @chunk-buffer line))
                    (let [next-chunk-count (inc @chunk-row-count)]
                      (vreset! chunk-row-count next-chunk-count)
                      (when (>= next-chunk-count chunk-size)
                        (flush-chunk!)))))
        on-complete! (fn [metadata]
                       (fail-if-timeout!)
                       (flush-chunk!)
                       (emit-export-script! sse
                                            "pompDatatableExportFinish"
                                            {:tableId id
                                             :filename export-filename
                                             :metadata (merge metadata
                                                              {:row-count @row-count
                                                               :byte-count @byte-count})}))]
    (if timeout-ms
      (let [stream-future (future
                            (export-stream-rows-fn stream-context on-row! on-complete!))
            timeout-result (deref stream-future timeout-ms ::timeout)]
        (when (= ::timeout timeout-result)
          (future-cancel stream-future)
          (throw (ex-info "Export exceeded timeout"
                          {:type :export-timeout
                           :timeout-ms timeout-ms}))))
      (export-stream-rows-fn stream-context on-row! on-complete!))))
