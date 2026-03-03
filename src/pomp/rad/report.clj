(ns pomp.rad.report
  "Public API for composing datatable + analysis into one report endpoint.

  `make-report` wires a datatable emitter set and an analysis board emitter behind
  a single Ring handler so one Datastar request can refresh both table and charts.

  Export requests (`?action=export`) are delegated to the datatable export path
  and intentionally skip analysis refresh work."
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
  "Builds a composed report descriptor map.

  Required keys in `config`:
  - `:data-url` shared endpoint for report refresh/export requests
  - `:datatable` config map used to build datatable emitters via
    `pomp.rad.datatable/make-emitters`
  - `:analysis` config map used to build analysis board descriptor via
    `pomp.rad.analysis/make-board`

  URL propagation:
  - `:data-url` is injected into datatable config as `:data-url`
  - `:data-url` is injected into analysis config as `:analysis-url`

  Return value:
  - `{:datatable datatable-config
      :analysis analysis-descriptor
      :handler ring-handler}`

  Handler behavior:
  - `?action=export`: emit datatable export events only, then close SSE
  - otherwise: emit datatable update, then analysis board update, then close SSE"
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
