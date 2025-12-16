(ns pomp.rad.datatable.ui.table
  (:require [pomp.rad.datatable.ui.header :as header]
            [pomp.rad.datatable.ui.body :as body]
            [pomp.rad.datatable.ui.pagination :as pagination]))

(defn render
  [{:keys [id cols rows groups sort-state filters group-by total-rows page-size page-current page-sizes data-url selectable? row-id-fn toolbar render-row render-header render-cell]}]
  (let [header-ctx {:cols cols
                    :sort-state sort-state
                    :filters filters
                    :data-url data-url
                    :selectable? selectable?
                    :table-id id
                    :group-by group-by}
        render-header-fn (or render-header header/render-sortable)]
    [:div {:id id}
     (when toolbar
       [:div.flex.items-center.px-2.py-1.border-b.border-base-300.bg-base-200
        {:style {:justify-content "flex-end"}}
        toolbar])
     [:div.overflow-x-auto
      [:table.table.table-sm
       (render-header-fn header-ctx)
       (body/render {:cols cols
                     :rows rows
                     :groups groups
                     :selectable? selectable?
                     :row-id-fn row-id-fn
                     :table-id id
                     :render-row render-row
                     :render-cell render-cell})]]
     (pagination/render {:total-rows total-rows
                         :page-size page-size
                         :page-current page-current
                         :filters filters
                         :page-sizes page-sizes
                         :data-url data-url})]))

(defn render-skeleton
  [{:keys [id cols n selectable?]}]
  [:div {:id id}
   [:div.overflow-x-auto
    [:table.table.table-sm
     (header/render-simple {:cols cols :selectable? selectable? :table-id id})
     (body/render-skeleton {:cols cols :n n :selectable? selectable?})]]])
