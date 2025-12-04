(ns pomp.rad.datatable.table
  (:require [pomp.rad.datatable.header :as header]
            [pomp.rad.datatable.body :as body]
            [pomp.rad.datatable.pagination :as pagination]
            [pomp.rad.datatable.filter-menu :as filter-menu]
            [pomp.rad.datatable.sort :as sort]))

(defn next-state
  [signals query-params rows]
  (let [new-filters (or (filter-menu/next-state (:filters signals) query-params) {})
        new-sort (or (sort/next-state (:sort signals) query-params) [])
        total-rows (count (filter-menu/apply-filters rows new-filters))
        new-page (pagination/next-state (:page signals) query-params total-rows)]
    {:signals {:filters new-filters :sort new-sort :page new-page}
     :total-rows total-rows}))

(defn process-data
  [rows signals]
  (-> rows
      (filter-menu/apply-filters (:filters signals))
      (sort/sort-data (:sort signals))
      (pagination/paginate-data (:page signals))))

(defn render
  [{:keys [id cols rows sort-state filters total-rows page-size page-current page-sizes data-url]}]
  [:div {:id id}
   [:div.overflow-x-auto
    [:table.table.table-sm
     (header/render-sortable {:cols cols
                              :sort-state sort-state
                              :filters filters
                              :data-url data-url})
     (body/render cols rows)]]
   (pagination/render {:total-rows total-rows
                       :page-size page-size
                       :page-current page-current
                       :filters filters
                       :page-sizes page-sizes
                       :data-url data-url})])

(defn render-skeleton
  [{:keys [id cols n]}]
  [:div {:id id}
   [:div.overflow-x-auto
    [:table.table.table-sm
     (header/render-simple cols)
     (body/render-skeleton cols n)]]])
