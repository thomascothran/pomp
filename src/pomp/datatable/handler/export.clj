(ns pomp.datatable.handler.export
  (:require [pomp.datatable.export.csv :as export-csv]
            [pomp.datatable.export.stream :as export-stream]
            [pomp.datatable.handler.signals :as handler-signals]
            [pomp.rad.datatable.state.column :as column-state]
            [pomp.rad.datatable.state.table :as state]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response gzip-profile on-open write-profile]]
            [starfederation.datastar.clojure.api :as d*]))

(defn handle-export-action!
  [{:keys [req id columns raw-signals query-params initial-signals-fn
           export-limits export-filename-fn export-stream-rows-fn]}]
  (let [current-signals (handler-signals/current-signals {:raw-signals raw-signals
                                                          :initial-signals-fn initial-signals-fn
                                                          :req req})
        column-order (column-state/next-state (:columnOrder current-signals) columns query-params)
        ordered-cols (column-state/reorder columns column-order)
        export-columns (export-csv/derive-export-columns ordered-cols)
        export-query (-> (state/next-state current-signals query-params)
                         (assoc :columns columns
                                :project-columns export-columns
                                :group-by []
                                :page nil))
        labels-by-key (into {}
                            (map (fn [{:keys [key label]}]
                                   [key (or label (name key))])
                                 ordered-cols))
        header-row (export-csv/csv-line labels-by-key export-columns)
        export-filename (if export-filename-fn
                          (export-filename-fn {:id id
                                               :columns ordered-cols
                                               :query export-query
                                               :req req})
                          (export-csv/default-export-filename id))
        stream-context {:query export-query
                        :columns export-columns
                        :request req
                        :limits export-limits}]
    (->sse-response req
                    {write-profile gzip-profile
                      on-open
                     (fn [sse]
                       (try
                         (export-stream/emit-export-script! sse
                                                            "pompDatatableExportBegin"
                                                            {:tableId id
                                                             :filename export-filename
                                                             :header header-row})
                         (export-stream/run-export-stream! {:id id
                                                            :sse sse
                                                            :export-filename export-filename
                                                            :export-columns export-columns
                                                            :export-stream-rows-fn export-stream-rows-fn
                                                            :stream-context stream-context
                                                            :export-limits export-limits})
                         (catch Throwable ex
                           (try
                             (export-stream/emit-export-script! sse
                                                                "pompDatatableExportFail"
                                                                {:tableId id
                                                                 :message (or (ex-message ex) "Export failed")})
                             (catch Throwable fail-ex
                               (when-not (= :export-disconnected (:type (ex-data fail-ex)))
                                 (throw fail-ex)))))
                         (finally
                           (d*/close-sse! sse))))})))
