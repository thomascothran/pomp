(ns demo.datatable-charts
  (:require [app :as app]
            [demo.datatable :as datatable-demo]
            [demo.util :refer [->html]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [pomp.analysis :as analysis]
            [pomp.rad.analysis.renderer.vega-lite :as vega-lite]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.query.sql :as sqlq]
            [pomp.rad.datatable.ui.table :as table]))

(def datatable-id "philosophers-table")
(def datatable-component-url "/demo/datatable-charts/data")
(def school-frequency-url "/demo/datatable-charts/analysis/school-frequency")
(def region-pie-url "/demo/datatable-charts/analysis/region-pie")
(def influence-histogram-url "/demo/datatable-charts/analysis/influence-histogram")
(def influence-bucket-size 10)

(defn- execute!
  [sqlvec]
  (jdbc/execute! (datatable-demo/get-datasource)
                 sqlvec
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defn- school-frequency-analysis-fn
  [{:analysis/keys [filters]}]
  (let [frequency-sql (sqlq/generate-frequency-sql {:table-name "philosophers"}
                                                   {:bucket-column :school
                                                     :filters filters})
        rows (execute! frequency-sql)
        values (->> rows
                    (map (fn [{:keys [bucket count]}]
                           {:label (or bucket "Unknown")
                            :value count}))
                    (sort-by (juxt (comp - :value) :label))
                    vec)]
    {:chart/spec (vega-lite/bar-spec {:values values
                                      :title "School Frequency"
                                      :x-title "School"
                                      :y-title "Count"})
     :chart/buckets (mapv (fn [{:keys [label value]}]
                            {:bucket/label label
                             :bucket/value value})
                          values)}))

(defn- render-school-frequency-chart
  [{:keys [chart/buckets anomaly/category anomaly/message]}]
  [:div#school-frequency-chart.card.bg-base-100.border.border-base-300.shadow-sm
   [:div.card-body.gap-3
    [:div.flex.items-center.justify-between
     [:h2.card-title "School Frequency"]
     [:span.badge.badge-ghost (str (count buckets) " buckets")]]
    (if category
      [:div.alert.alert-warning
       [:span (or message "Unable to load school frequency.")]]
      [:div#school-frequency-chart-vega.min-h-48.w-full])]])

(defn- region-pie-analysis-fn
  [{:analysis/keys [filters]}]
  (let [frequency-sql (sqlq/generate-frequency-sql {:table-name "philosophers"}
                                                   {:bucket-column :region
                                                     :filters filters})
        rows (execute! frequency-sql)
        values (->> rows
                    (map (fn [{:keys [bucket count]}]
                           {:label (or bucket "Unknown")
                            :value count}))
                    (sort-by (juxt (comp - :value) :label))
                    vec)]
    {:chart/spec (vega-lite/pie-spec {:values values
                                      :title "Region Share"})
     :chart/buckets (mapv (fn [{:keys [label value]}]
                            {:bucket/label label
                             :bucket/value value})
                          values)}))

(defn- render-region-pie-chart
  [{:keys [chart/buckets anomaly/category anomaly/message]}]
  [:div#region-pie-chart.card.bg-base-100.border.border-base-300.shadow-sm
   [:div.card-body.gap-3
    [:div.flex.items-center.justify-between
     [:h2.card-title "Region Share"]
     [:span.badge.badge-ghost (str (count buckets) " buckets")]]
    (if category
      [:div.alert.alert-warning
       [:span (or message "Unable to load region pie chart.")]]
      [:div#region-pie-chart-vega.min-h-48.w-full])]])

(defn- influence-histogram-analysis-fn
  [{:analysis/keys [filters]}]
  (let [histogram-sql (sqlq/generate-histogram-sql {:table-name "philosophers"}
                                                    {:bucket-column :influence
                                                     :bucket-size influence-bucket-size
                                                     :filters filters})
        null-count-sql (sqlq/generate-null-count-sql {:table-name "philosophers"}
                                                     {:bucket-column :influence
                                                      :filters filters})
         rows (execute! histogram-sql)
         null-count (or (-> (execute! null-count-sql) first :count) 0)
         values (->> rows
                     (map (fn [{:keys [bucket count]}]
                            (let [start (long bucket)
                                  end (+ start influence-bucket-size -1)]
                              {:start start
                               :label (str start "-" end)
                               :value count})))
                     (sort-by :start)
                     (mapv #(select-keys % [:label :value])))]
    {:chart/spec (vega-lite/histogram-spec {:values values
                                            :title "Influence Histogram"
                                            :subtitle (str "Null values: " null-count)
                                            :x-title "Influence range"
                                            :y-title "Count"})
     :chart/buckets (mapv (fn [{:keys [label value]}]
                            {:bucket/label label
                             :bucket/value value})
                          values)
     :chart/null-count null-count}))

(defn- render-influence-histogram-chart
  [{:keys [chart/buckets chart/null-count anomaly/category anomaly/message]}]
  [:div#influence-histogram-chart.card.bg-base-100.border.border-base-300.shadow-sm
   [:div.card-body.gap-3
    [:div.flex.items-center.justify-between
     [:h2.card-title "Influence Histogram"]
     [:span.badge.badge-ghost (str (count buckets) " buckets")]]
    [:p.text-sm.opacity-70 (str "Null values: " null-count)]
    (if category
      [:div.alert.alert-warning
       [:span (or message "Unable to load influence histogram.")]]
      [:div#influence-histogram-chart-vega.min-h-48.w-full])]])

(def school-frequency-handler
  (analysis/make-chart-handler
   {:analysis/id "philosophers-overview"
    :analysis/filter-source-path [:datatable (keyword datatable-id) :filters]
    :chart/id "school-frequency"
    :chart/type :frequency
    :chart/renderer :vega-lite
    :analysis-fn school-frequency-analysis-fn
    :render-chart-fn render-school-frequency-chart
    :render-html-fn ->html
     :render-script-fn (fn [{category :anomaly/category
                             spec :chart/spec}]
                         (when-not category
                           (vega-lite/render-script {:target-id "school-frequency-chart-vega"
                                                     :spec spec})))}))

(def region-pie-handler
  (analysis/make-chart-handler
   {:analysis/id "philosophers-overview"
    :analysis/filter-source-path [:datatable (keyword datatable-id) :filters]
    :chart/id "region-pie"
    :chart/type :pie
    :chart/renderer :vega-lite
    :analysis-fn region-pie-analysis-fn
    :render-chart-fn render-region-pie-chart
    :render-html-fn ->html
     :render-script-fn (fn [{category :anomaly/category
                             spec :chart/spec}]
                         (when-not category
                           (vega-lite/render-script {:target-id "region-pie-chart-vega"
                                                     :spec spec})))}))

(def influence-histogram-handler
  (analysis/make-chart-handler
   {:analysis/id "philosophers-overview"
    :analysis/filter-source-path [:datatable (keyword datatable-id) :filters]
    :chart/id "influence-histogram"
    :chart/type :histogram
    :chart/renderer :vega-lite
    :analysis-fn influence-histogram-analysis-fn
    :render-chart-fn render-influence-histogram-chart
    :render-html-fn ->html
     :render-script-fn (fn [{category :anomaly/category
                             spec :chart/spec}]
                         (when-not category
                           (vega-lite/render-script {:target-id "influence-histogram-chart-vega"
                                                     :spec spec})))}))

(defn- page-handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (app/with-app-layout
            {:nav-title "Pomp Demo"}
            [:div.p-8.space-y-6
             [:h1.text-2xl.font-bold "Philosophers: Datatable + Analysis"]
             [:div#school-frequency-chart-wrapper
              {:data-init (str "@post('" school-frequency-url "')")
               :data-on-signal-patch (str "@post('" school-frequency-url "')")}
               [:div#school-frequency-chart.card.bg-base-100.border.border-base-300.shadow-sm
                [:div.card-body
                 [:h2.card-title "School Frequency"]
                  [:div#school-frequency-chart-vega.min-h-48.w-full [:p.text-sm.opacity-70 "Loading chart..."]]]]]
                [:div#region-pie-chart-wrapper
                 {:data-init (str "@post('" region-pie-url "')")
                  :data-on-signal-patch (str "@post('" region-pie-url "')")}
                 [:div#region-pie-chart.card.bg-base-100.border.border-base-300.shadow-sm
                  [:div.card-body
                   [:h2.card-title "Region Share"]
                    [:div#region-pie-chart-vega.min-h-48.w-full [:p.text-sm.opacity-70 "Loading chart..."]]]]]
                [:div#influence-histogram-chart-wrapper
                 {:data-init (str "@post('" influence-histogram-url "')")
                  :data-on-signal-patch (str "@post('" influence-histogram-url "')")}
                 [:div#influence-histogram-chart.card.bg-base-100.border.border-base-300.shadow-sm
                  [:div.card-body
                   [:h2.card-title "Influence Histogram"]
                    [:div#influence-histogram-chart-vega.min-h-48.w-full [:p.text-sm.opacity-70 "Loading chart..."]]]]]
                [:div#datatable-container
                 {:data-init (str "@get('" datatable-component-url "')")}
                 [:div {:id datatable-id}]]]))})

(defn- make-datatable-handlers
  []
  (let [table-search-rows (sqlq/rows-fn {:table-name "philosophers"} execute!)
        table-count (sqlq/count-fn {:table-name "philosophers"} execute!)]
    (datatable/make-handlers
     {:id datatable-id
      :columns datatable-demo/columns
      :rows-fn table-search-rows
      :count-fn table-count
      :table-search-query table-search-rows
      :save-fn (sqlq/save-fn {:table "philosophers"} execute!)
      :data-url datatable-component-url
      :render-html-fn ->html
      :render-table-search table/default-render-table-search
      :page-sizes [10 25 100 250]
      :initial-signals-fn (fn [_]
                            {:columns {:influence {:visible false}
                                       :id {:visible false}}})
      :selectable? true})))

(defn make-routes
  [_]
  (datatable-demo/init-db!)
  (let [{:keys [get post]} (make-datatable-handlers)]
     [["/datatable-charts" page-handler]
      ["/datatable-charts/data" {:get get
                                  :post post}]
      ["/datatable-charts/analysis/school-frequency" {:get school-frequency-handler
                                                        :post school-frequency-handler}]
      ["/datatable-charts/analysis/region-pie" {:get region-pie-handler
                                                   :post region-pie-handler}]
      ["/datatable-charts/analysis/influence-histogram" {:get influence-histogram-handler
                                                           :post influence-histogram-handler}]]))
