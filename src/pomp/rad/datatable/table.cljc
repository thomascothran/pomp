(ns pomp.rad.datatable.table
  (:require [pomp.rad.datatable.header :as header]
            [pomp.rad.datatable.body :as body]
            [pomp.rad.datatable.pagination :as pagination]))

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
