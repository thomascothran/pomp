(ns demo.datatable-charts
  (:require [app :as app]
            [demo.datatable :as datatable-demo]
            [demo.util :refer [->html]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [pomp.rad.analysis :as analysis]
            [pomp.rad.analysis.board :as analysis.board]
            [pomp.rad.datatable :as datatable]
            [pomp.rad.datatable.query.sql :as sqlq]
            [pomp.rad.datatable.ui.table :as table]))

(def datatable-id "philosophers-table")
(def datatable-component-url "/demo/datatable-charts/data")
(def analysis-board-route-path "/datatable-charts/analysis-board")
(def influence-bucket-size 10)
(def board-id "chart-grid")

(defn- execute!
  [sqlvec]
  (jdbc/execute! (datatable-demo/get-datasource)
                 sqlvec
                 {:builder-fn rs/as-unqualified-lower-maps}))

(def analysis-shared-context
  {:table-name "philosophers"
   :execute! execute!})

(def chart-shared-config
  {:analysis/id "philosophers-overview"
   :analysis/filter-source-path [:datatable (keyword datatable-id) :filters]
   :render-html-fn ->html})

(def chart-definitions
  {:school-frequency (analysis/frequency-chart
                      {:id "school-frequency"
                       :title "School Frequency"
                       :bucket-column :school
                       :spec {:x-title "School"
                              :y-title "Count"}})
   :region-pie (analysis/pie-chart
                {:id "region-pie"
                 :title "Region Share"
                 :bucket-column :region})
   :influence-histogram (analysis/histogram-chart
                         {:id "influence-histogram"
                          :title "Influence Histogram"
                          :bucket-column :influence
                          :bucket-size influence-bucket-size
                          :include-null-count? true
                          :spec {:x-title "Influence range"
                                 :y-title "Count"}}
                         {:chart/null-count-subtitle "Null values: {null-count}"
                          :chart/null-count-line? true})})

(def default-board-items
  [{:chart-key :school-frequency}
   {:chart-key :region-pie
    :wrapper-class "w-full max-w-xl"}
   {:chart-key :influence-histogram}])

(defn- page-handler
  [{:keys [analysis-url
           board-id
           board-class
           data-on-signal-patch-filter
           render-board-shell-fn]} _req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (app/with-app-layout
            {:nav-title "Pomp Demo"}
            [:div.p-8.space-y-6
             [:h1.text-2xl.font-bold "Philosophers: Datatable + Analysis"]
             (render-board-shell-fn
              {:board-id board-id
               :board-class board-class
               :analysis-url analysis-url
               :data-on-signal-patch-filter data-on-signal-patch-filter
               :body [:div {:id (analysis.board/board-body-id board-id)}
                      [:div.text-sm.opacity-70 "Loading analysis board..."]]})
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
  [_opts]
  (datatable-demo/init-db!)
  (let [{:keys [get post]} (make-datatable-handlers)
        board-config (analysis/make-board
                      (merge analysis-shared-context
                             chart-shared-config
                             {:chart-definitions chart-definitions
                              :board-items default-board-items
                              :analysis-url (str "/demo" analysis-board-route-path)
                              :board-id board-id}))
        analysis-route-path analysis-board-route-path]
    [["/datatable-charts" (partial page-handler board-config)]
     ["/datatable-charts/data" {:get get
                                :post post}]
     [analysis-route-path {:get (:handler board-config)
                           :post (:handler board-config)}]]))
