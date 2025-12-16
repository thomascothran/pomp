(ns pomp.rad.datatable.table
  (:require [pomp.rad.datatable.header :as header]
            [pomp.rad.datatable.body :as body]
            [pomp.rad.datatable.pagination :as pagination]
            [pomp.rad.datatable.filter-menu :as filter-menu]
            [pomp.rad.datatable.sort :as sort]
            [pomp.rad.datatable.group :as group]))

(defn next-state
  [signals query-params]
  (let [new-group-by (group/next-state (:group-by signals) query-params)
        grouping-changed? (not= new-group-by (:group-by signals))
        new-filters (if grouping-changed?
                      {}
                      (or (filter-menu/next-state (:filters signals) query-params) {}))
        new-sort (or (sort/next-state (:sort signals) query-params) [])
        new-page (pagination/next-state (:page signals) query-params)]
    {:filters new-filters
     :sort new-sort
     :page new-page
     :group-by new-group-by}))

(defn query
  [signals query-params query-fn]
  (let [new-signals (next-state signals query-params)
        {:keys [rows total-rows page]} (query-fn new-signals)]
    {:signals (assoc new-signals :page page)
     :rows rows
     :total-rows total-rows}))

(defn render
  [{:keys [id cols rows groups sort-state filters group-by total-rows page-size page-current page-sizes data-url selectable? row-id-fn toolbar]
    :as opts}]
  [:div {:id id}
   (when toolbar
     [:div.flex.items-center.px-2.py-1.border-b.border-base-300.bg-base-200
      {:style {:justify-content "flex-end"}}
      toolbar])
   [:div.overflow-x-auto
    [:table.table.table-sm
     (header/render-sortable {:cols cols
                              :sort-state sort-state
                              :filters filters
                              :data-url data-url
                              :selectable? selectable?
                              :table-id id
                              :group-by group-by})
     (body/render {:cols cols
                   :rows rows
                   :groups groups
                   :selectable? selectable?
                   :row-id-fn row-id-fn
                   :table-id id})]]
   (pagination/render {:total-rows total-rows
                       :page-size page-size
                       :page-current page-current
                       :filters filters
                       :page-sizes page-sizes
                       :data-url data-url})])

(defn render-skeleton
  [{:keys [id cols n selectable?]}]
  [:div {:id id}
   [:div.overflow-x-auto
    [:table.table.table-sm
     (header/render-simple {:cols cols :selectable? selectable? :table-id id})
     (body/render-skeleton {:cols cols :n n :selectable? selectable?})]]])
