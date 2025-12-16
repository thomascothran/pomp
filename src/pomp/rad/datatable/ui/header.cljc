(ns pomp.rad.datatable.ui.header
  (:require [pomp.rad.datatable.ui.primitives :as primitives]
            [pomp.rad.datatable.ui.filter-menu :as filter-menu]
            [pomp.rad.datatable.ui.column-menu :as column-menu]))

(defn render-simple [{:keys [cols selectable? table-id]}]
  [:thead
   [:tr
    (when selectable?
      [:th.w-3
       [:input.checkbox.checkbox-sm
        {:type "checkbox"
         :data-on:click (str "evt.target.checked ? @setAll(true, { include: 'datatable\\\\." table-id "\\\\.selections\\\\..*' }) : @setAll(false, { include: 'datatable\\\\." table-id "\\\\.selections\\\\..*' })")}]])
    (for [{:keys [label]} cols]
      [:th label])]])

(defn render-sortable
  [{:keys [cols sort-state filters data-url selectable? table-id group-by]}]
  (let [total-cols (count cols)
        grouped? (seq group-by)
        group-col-key (first group-by)
        group-col (when grouped? (some #(when (= (:key %) group-col-key) %) cols))]
    [:thead
     [:tr
      (when selectable?
        [:th.w-3
         [:input.checkbox.checkbox-sm
          {:type "checkbox"
           :data-on:click (str "evt.target.checked ? @setAll(true, { include: 'datatable\\\\." table-id "\\\\.selections\\\\..*' }) : @setAll(false, { include: 'datatable\\\\." table-id "\\\\.selections\\\\..*' })")}]])
      (when grouped?
        [:th
         [:div.flex.items-center.justify-between.gap-2
          [:span.font-semibold "Group"]
          (column-menu/render-group-column {:data-url data-url})]])
      (for [[idx {:keys [key label]}] (map-indexed vector cols)]
        (let [col-name (name key)
              current-filter (get filters key)
              current-filter-op (:op current-filter)
              current-filter-val (:value current-filter)
              current-sort (first sort-state)
              is-sorted? (= (:column current-sort) col-name)
              sort-dir (:direction current-sort)]
          [:th
           {:draggable "true"
            :data-on:dragstart (str "$datatable." table-id ".dragging = '" col-name "'")
            :data-on:dragend (str "$datatable." table-id ".dragging = null; $datatable." table-id ".dragOver = null")
            :data-on:dragover__prevent (str "$datatable." table-id ".dragOver = '" col-name "'")
            :data-on:dragleave (str "if ($datatable." table-id ".dragOver === '" col-name "') $datatable." table-id ".dragOver = null")
            :data-on:drop (str "@get('" data-url "?moveCol=' + $datatable." table-id ".dragging + '&beforeCol=" col-name "')")
            :data-class (str "{'border-l-4 border-primary': $datatable." table-id ".dragOver === '" col-name "' && $datatable." table-id ".dragging !== '" col-name "'}")}
           [:div.flex.items-center.justify-between.gap-2
            [:button.flex.items-center.gap-1.hover:text-primary.transition-colors
             {:data-on:click (str "@get('" data-url "?clicked=" col-name "')")}
             [:span {:class (if is-sorted? "opacity-100" "opacity-30")}
              (cond
                (and is-sorted? (= sort-dir "asc")) primitives/sort-icon-asc
                (and is-sorted? (= sort-dir "desc")) primitives/sort-icon-desc
                :else primitives/sort-icon-both)]
             [:span.font-semibold label]]
            [:div.flex.items-center
             (filter-menu/render
              {:col-key key
               :col-label label
               :current-filter-op current-filter-op
               :current-filter-value current-filter-val
               :col-idx idx
               :total-cols total-cols
               :data-url data-url})
             (column-menu/render
              {:col-key key
               :col-label label
               :data-url data-url
               :table-id table-id})]]]))]]))
