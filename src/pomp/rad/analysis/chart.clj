(ns pomp.rad.analysis.chart
  (:require [clojure.string :as str]
            [pomp.rad.analysis.handler :as analysis.handler]
            [pomp.rad.analysis.renderer.vega-lite :as vega-lite]
            [pomp.rad.datatable.query.sql :as sqlq]))

(defn- default-rows->values
  [rows chart]
  (let [rows* (case (:chart/value-sort chart)
                :frequency (sort-by (fn [{:keys [bucket count]}]
                                      [(- (or count 0))
                                       (or bucket "Unknown")])
                                    rows)
                :bucket-asc (sort-by (fn [{:keys [bucket]}]
                                       [(nil? bucket) bucket])
                                     rows)
                rows)
        bucket-size (:bucket-size chart)
        histogram-range? (= :histogram-range (:chart/value-shape chart))]
    (mapv (fn [{:keys [bucket count]}]
            {:label (if (and histogram-range?
                             (number? bucket)
                             (number? bucket-size))
                      (str (long bucket) "-" (long (+ bucket bucket-size -1)))
                      (or bucket "Unknown"))
             :value count})
          rows*)))

(defn- resolve-null-count-template
  [template null-count]
  (when template
    (str/replace template "{null-count}" (str null-count))))

(defn- default-values->buckets
  [values _chart]
  (mapv (fn [{:keys [label value]}]
          {:bucket/label label
           :bucket/value value})
        values))

(defn- default-build-spec
  [values {:keys [chart/type chart/title chart/spec-config chart/null-count-subtitle]} {:keys [chart/null-count]}]
  (let [null-count-subtitle* (when (and (= :histogram type)
                                        (some? null-count)
                                        null-count-subtitle)
                               (resolve-null-count-template null-count-subtitle null-count))
        spec-options (cond-> (merge {:values values
                                     :title title}
                                    spec-config)
                       null-count-subtitle* (assoc :subtitle null-count-subtitle*))]
    (case type
      :bar (vega-lite/bar-spec spec-options)
      :frequency (vega-lite/bar-spec spec-options)
      :pie (vega-lite/pie-spec spec-options)
      :histogram (vega-lite/histogram-spec spec-options)
      nil)))

(defn- default-target-id
  [chart-id]
  (str chart-id "-vega-lite"))

(defn- default-query-type
  [config]
  (or (:query/type config)
      (case (:chart/type config)
        :frequency :frequency
        :histogram :histogram
        nil)))

(defn- default-render-chart
  [{:keys [chart/id chart/target-id chart/target-class]}]
  (let [target-id* (or target-id
                       (default-target-id id))
        target-class* (or target-class
                          "w-full min-h-[16rem]")]
    (fn [_]
      [:div {:id target-id*
             :data-chart-id id
             :class target-class*}])))

(defn- default-render-script
  [{:keys [chart/id chart/target-id]}]
  (let [target-id* (or target-id
                       (default-target-id id))]
    (fn [{:keys [chart/spec]}]
      (vega-lite/render-script {:target-id target-id*
                                :spec spec}))))

(def ^:private concise-chart-key->internal
  {:id :chart/id
   :title :chart/title
   :target-id :chart/target-id
   :spec :chart/spec-config})

(defn- map-concise-chart-keys
  [concise]
  (reduce-kv (fn [acc k v]
               (assoc acc (get concise-chart-key->internal k k) v))
             {}
             concise))

(defn- preset-chart
  [defaults concise overrides]
  (merge defaults
         (map-concise-chart-keys concise)
         overrides))

(defn frequency-chart
  ([concise]
   (frequency-chart concise {}))
  ([concise overrides]
   (preset-chart {:chart/type :frequency
                  :query/type :frequency
                  :chart/value-sort :frequency}
                 concise
                 overrides)))

(defn pie-chart
  ([concise]
   (pie-chart concise {}))
  ([concise overrides]
   (preset-chart {:chart/type :pie
                  :query/type :frequency
                  :chart/value-sort :frequency}
                 concise
                 overrides)))

(defn histogram-chart
  ([concise]
   (histogram-chart concise {}))
  ([concise overrides]
   (preset-chart {:chart/type :histogram
                  :query/type :histogram
                  :chart/value-shape :histogram-range
                  :chart/value-sort :bucket-asc}
                 concise
                 overrides)))

(defn make-analysis-fn
  [shared-context chart-spec]
  (let [execute! (:execute! shared-context)
        frequency-sql-fn (or (:sql/frequency-fn shared-context)
                             sqlq/generate-frequency-sql)
        histogram-sql-fn (or (:sql/histogram-fn shared-context)
                             sqlq/generate-histogram-sql)
        null-count-sql-fn (or (:sql/null-count-fn shared-context)
                              sqlq/generate-null-count-sql)
        rows->values-fn (or (:rows->values-fn chart-spec)
                            default-rows->values)
        values->buckets-fn (or (:values->buckets-fn chart-spec)
                               default-values->buckets)
         build-spec-fn (or (:build-spec-fn chart-spec)
                           (when-not (:chart/spec chart-spec)
                             (fn [values context]
                               (default-build-spec values chart-spec context))))
        result-fn (:result-fn chart-spec)
        table-name (or (:table-name chart-spec)
                       (:table-name shared-context))]
    (fn [{:analysis/keys [filters] :as context}]
      (let [query-context (cond-> {:bucket-column (:bucket-column chart-spec)
                                   :filters (or filters {})}
                            (contains? chart-spec :bucket-size)
                            (assoc :bucket-size (:bucket-size chart-spec)))
            table-context {:table-name table-name}
            query-type (default-query-type chart-spec)
            sqlvec (case query-type
                     :frequency (frequency-sql-fn table-context query-context)
                     :histogram (histogram-sql-fn table-context query-context))
            rows (execute! sqlvec)
            null-count (when (and (= :histogram query-type)
                                  (:include-null-count? chart-spec))
                         (or (-> (execute! (null-count-sql-fn table-context query-context))
                                 first
                                 :count)
                             0))
            values (rows->values-fn rows chart-spec)
            buckets (values->buckets-fn values chart-spec)
            base-result (cond-> {:chart/spec (if build-spec-fn
                                               (build-spec-fn values (assoc context
                                                                            :chart/null-count null-count))
                                               (:chart/spec chart-spec))
                                 :chart/buckets buckets}
                          (some? null-count) (assoc :chart/null-count null-count))]
        (if result-fn
          (result-fn (assoc context
                            :chart/query-context query-context
                            :chart/rows rows
                            :chart/values values
                            :chart/buckets buckets
                            :chart/null-count null-count
                            :chart/base-result base-result))
          base-result)))))

(defn default-render-card
  [{:keys [chart/id chart/title chart/card-id chart/card-class chart/card-body-class chart/null-count-line? render-chart-fn]}]
  (fn [{:keys [chart/buckets chart/null-count anomaly/category anomaly/message] :as result}]
    [:div {:id (or card-id (str id "-chart"))
           :class (or card-class "card bg-base-100 border border-base-300 shadow-sm")}
     [:div {:class (or card-body-class "card-body gap-3")}
      [:div.flex.items-center.justify-between
       [:h2.card-title (or title id)]
       [:span.badge.badge-ghost (str (count buckets) " buckets")]]
      (when (and null-count-line?
                 (some? null-count))
        [:p {:class "text-sm text-base-content/70"}
         (str "Null values: " null-count)])
      (if category
        [:div.alert.alert-warning
         [:span (or message "Unable to load chart.")]]
        (render-chart-fn result))]]))

(defn make-chart
  ([shared-context chart-spec]
   (make-chart (merge shared-context chart-spec)))
  ([{:keys [analysis-fn render-card-fn render-chart-fn execute!]
      :as config}]
         (let [query-type* (default-query-type config)
               analysis-fn* (or analysis-fn
                            (when (and execute! query-type*)
                              (make-analysis-fn (select-keys config [:table-name
                                                                     :execute!
                                                                     :sql/frequency-fn
                                                                     :sql/histogram-fn
                                                                     :sql/null-count-fn])
                                               (assoc config :query/type query-type*))))
           render-chart-fn* (or render-chart-fn
                                (default-render-chart config))
          render-script-fn* (or (:render-script-fn config)
                                (default-render-script config))
          render-card-fn* (or render-card-fn
                              (default-render-card (assoc config :render-chart-fn render-chart-fn*)))
          handler (analysis.handler/make-chart-handler
                   (-> config
                       (assoc :analysis-fn analysis-fn*
                              :render-chart-fn render-card-fn*
                              :render-script-fn render-script-fn*)))]
      (assoc config
             :analysis-fn analysis-fn*
             :render-card-fn render-card-fn*
             :render-chart-fn render-chart-fn*
             :render-script-fn render-script-fn*
             :handler handler))))
