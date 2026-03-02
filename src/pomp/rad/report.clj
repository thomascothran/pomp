(ns pomp.rad.report
  (:require [pomp.rad.analysis :as analysis]
            [pomp.rad.datatable :as datatable]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [starfederation.datastar.clojure.api :as d*]))

(defn- export-action?
  [req]
  (= "export" (get-in req [:query-params "action"])))

(defn- datatable-emitter-for-request
  [datatable-emitters req]
  (or (get datatable-emitters (:request-method req))
      (:post datatable-emitters)
      (:get datatable-emitters)))

(defn- report-refresh-handler
  [{:keys [datatable-emitters analysis-emitter]}]
  (fn [req]
    (let [datatable-emit! (datatable-emitter-for-request datatable-emitters req)]
      (->sse-response req
                      {on-open
                       (fn [sse]
                          (datatable-emit! sse req)
                          (analysis-emitter sse req)
                          (d*/close-sse! sse))}))))

(defn- report-export-handler
  [{:keys [datatable-emitters]}]
  (fn [req]
    (let [datatable-emit! (datatable-emitter-for-request datatable-emitters req)]
      (->sse-response req
                      {on-open
                       (fn [sse]
                          (datatable-emit! sse req)
                          (d*/close-sse! sse))}))))

(defn make-report
  [{:keys [data-url datatable analysis] :as config}]
  (let [datatable-config (assoc datatable :data-url data-url)
        datatable-emitters (datatable/make-emitters datatable-config)
        analysis-config (assoc analysis :analysis-url data-url)
        analysis-descriptor (analysis/make-board analysis-config)
        coordinated-handler (report-refresh-handler {:datatable-emitters datatable-emitters
                                                     :analysis-emitter (:emit! analysis-descriptor)})
        export-handler (report-export-handler {:datatable-emitters datatable-emitters})]
    {:datatable datatable-config
     :analysis analysis-descriptor
     :handler (fn [req]
                 (if (export-action? req)
                   (export-handler req)
                   (coordinated-handler req)))}))
