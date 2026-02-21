(ns demo.datatable-charts
  (:require [app :as app]
            [demo.datatable :as datatable-demo]
            [demo.util :refer [->html]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [pomp.analysis :as analysis]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.query.sql :as sqlq]
            [pomp.rad.datatable.ui.table :as table]))

(def datatable-id "philosophers-table")
(def datatable-component-url "/demo/datatable-charts/data")
(def school-frequency-url "/demo/datatable-charts/analysis/school-frequency")
(def region-pie-url "/demo/datatable-charts/analysis/region-pie")
(def influence-histogram-url "/demo/datatable-charts/analysis/influence-histogram")
(def influence-bucket-size 10)

(def default-chart-layout
  {:chart-grid-class "flex flex-wrap gap-6"
   :school-frequency-wrapper-class "w-full max-w-3xl"
   :region-pie-wrapper-class "w-full max-w-xl"
   :influence-histogram-wrapper-class "w-full max-w-3xl"
   :school-frequency-mount-class "min-h-48 w-full"
   :region-pie-mount-class "min-h-48 w-full"
   :influence-histogram-mount-class "min-h-48 w-full"
   :school-frequency-spec-height nil
   :region-pie-spec-height nil
   :influence-histogram-spec-height nil})

(defn- execute!
  [sqlvec]
  (jdbc/execute! (datatable-demo/get-datasource)
                 sqlvec
                 {:builder-fn rs/as-unqualified-lower-maps}))

(def analysis-shared-context
  {:table-name "philosophers"
   :execute! execute!
   :sql/frequency-fn sqlq/generate-frequency-sql
   :sql/histogram-fn sqlq/generate-histogram-sql
   :sql/null-count-fn sqlq/generate-null-count-sql})

(def chart-shared-config
  {:analysis/id "philosophers-overview"
   :analysis/filter-source-path [:datatable (keyword datatable-id) :filters]
   :render-html-fn ->html})

(defn- make-chart-handler
  [chart-config]
  (let [{:keys [handler]}
        (analysis/make-chart
         (merge analysis-shared-context
                chart-shared-config
                chart-config))]
    handler))

(def chart-definitions
  {:school-frequency (analysis/frequency-chart
                      {:id "school-frequency"
                       :target-id "school-frequency-chart-vega"
                       :title "School Frequency"
                       :bucket-column :school
                       :spec {:x-title "School"
                              :y-title "Count"}})
   :region-pie (analysis/pie-chart
                {:id "region-pie"
                 :target-id "region-pie-chart-vega"
                 :title "Region Share"
                 :bucket-column :region})
   :influence-histogram (analysis/histogram-chart
                         {:id "influence-histogram"
                          :target-id "influence-histogram-chart-vega"
                          :title "Influence Histogram"
                          :bucket-column :influence
                          :bucket-size influence-bucket-size
                          :include-null-count? true
                          :spec {:x-title "Influence range"
                                 :y-title "Count"}}
                         {:chart/null-count-subtitle "Null values: {null-count}"
                          :chart/null-count-line? true})})

(def chart-layout-keys
  {:school-frequency {:mount-class-key :school-frequency-mount-class
                      :spec-height-key :school-frequency-spec-height}
   :region-pie {:mount-class-key :region-pie-mount-class
                :spec-height-key :region-pie-spec-height}
   :influence-histogram {:mount-class-key :influence-histogram-mount-class
                         :spec-height-key :influence-histogram-spec-height}})

(defn- make-handler-for-chart
  [chart-key chart-layout]
  (let [{:keys [mount-class-key spec-height-key]} (get chart-layout-keys chart-key)
        base-config (get chart-definitions chart-key)
        spec-height (get chart-layout spec-height-key)
        chart-config (-> base-config
                         (assoc :chart/target-class (get chart-layout mount-class-key))
                         (update :chart/spec-config (fnil assoc {}) :height spec-height))]
    (make-chart-handler chart-config)))

(defn- page-handler
  [chart-layout _req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (app/with-app-layout
            {:nav-title "Pomp Demo"}
            [:div.p-8.space-y-6
             [:h1.text-2xl.font-bold "Philosophers: Datatable + Analysis"]
             [:div#chart-grid
              {:class (:chart-grid-class chart-layout)}
              [:div#school-frequency-chart-wrapper
               {:class (:school-frequency-wrapper-class chart-layout)
                :data-init (str "@post('" school-frequency-url "')")
                :data-on-signal-patch (str "@post('" school-frequency-url "')")}
               [:div#school-frequency-chart.card.bg-base-100.border.border-base-300.shadow-sm
                [:div.card-body
                 [:h2.card-title "School Frequency"]
                 [:div {:id "school-frequency-chart-vega"
                        :class (:school-frequency-mount-class chart-layout)}
                  [:p.text-sm.opacity-70 "Loading chart..."]]]]]
              [:div#region-pie-chart-wrapper
               {:class (:region-pie-wrapper-class chart-layout)
                :data-init (str "@post('" region-pie-url "')")
                :data-on-signal-patch (str "@post('" region-pie-url "')")}
               [:div#region-pie-chart.card.bg-base-100.border.border-base-300.shadow-sm
                [:div.card-body
                 [:h2.card-title "Region Share"]
                 [:div {:id "region-pie-chart-vega"
                        :class (:region-pie-mount-class chart-layout)}
                  [:p.text-sm.opacity-70 "Loading chart..."]]]]]
              [:div#influence-histogram-chart-wrapper
               {:class (:influence-histogram-wrapper-class chart-layout)
                :data-init (str "@post('" influence-histogram-url "')")
                :data-on-signal-patch (str "@post('" influence-histogram-url "')")}
               [:div#influence-histogram-chart.card.bg-base-100.border.border-base-300.shadow-sm
                [:div.card-body
                 [:h2.card-title "Influence Histogram"]
                 [:div {:id "influence-histogram-chart-vega"
                        :class (:influence-histogram-mount-class chart-layout)}
                  [:p.text-sm.opacity-70 "Loading chart..."]]]]]]
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
  [opts]
  (datatable-demo/init-db!)
  (let [{:keys [get post]} (make-datatable-handlers)
        chart-layout (merge default-chart-layout (:chart-layout opts))
        school-frequency-handler (make-handler-for-chart :school-frequency chart-layout)
        region-pie-handler (make-handler-for-chart :region-pie chart-layout)
        influence-histogram-handler (make-handler-for-chart :influence-histogram chart-layout)]
    [["/datatable-charts" (partial page-handler chart-layout)]
     ["/datatable-charts/data" {:get get
                                :post post}]
     ["/datatable-charts/analysis/school-frequency" {:get school-frequency-handler
                                                     :post school-frequency-handler}]
     ["/datatable-charts/analysis/region-pie" {:get region-pie-handler
                                               :post region-pie-handler}]
     ["/datatable-charts/analysis/influence-histogram" {:get influence-histogram-handler
                                                        :post influence-histogram-handler}]]))
