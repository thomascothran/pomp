(ns pomp.rad.analysis.handler
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]))

(defn- parse-json-string
  [value]
  (when (string? value)
    (let [trimmed (str/trim value)]
      (when (seq trimmed)
        (json/read-str trimmed {:key-fn keyword})))))

(defn- request-signals
  [req]
  (or (when (map? (:body-params req))
        (:body-params req))
      (parse-json-string (get-in req [:query-params "datastar"]))
      (let [signals-raw (d*/get-signals req)]
        (cond
          (map? signals-raw) signals-raw
          (string? signals-raw) (parse-json-string signals-raw)
          (some? signals-raw) (parse-json-string (slurp signals-raw))
          :else nil))
      {}))

(defn extract-filters
  [req {:analysis/keys [filter-source-path]}]
  (or (when (seq filter-source-path)
        (get-in (request-signals req) filter-source-path))
      {}))

(defn build-context
  [req config]
  {:analysis/id (:analysis/id config)
   :analysis/filters (extract-filters req config)
   :chart/id (:chart/id config)
   :chart/type (:chart/type config)
   :chart/renderer (or (:chart/renderer config) :vega-lite)
   :ring/request req})

(defn make-chart-handler
  [{:keys [analysis-fn render-chart-fn render-html-fn render-script-fn]
    :as config}]
  (fn [req]
    (let [context (build-context req config)
          result (merge (select-keys context [:analysis/id :chart/id :chart/type :chart/renderer])
                         (analysis-fn context))
          html (render-html-fn (render-chart-fn result))
          script (when render-script-fn
                   (render-script-fn result))]
      (->sse-response req
                      {on-open
                       (fn [sse]
                          (d*/patch-elements! sse html)
                          (when (and (string? script)
                                     (not (str/blank? script)))
                            (d*/execute-script! sse script))
                           (d*/close-sse! sse))}))))
