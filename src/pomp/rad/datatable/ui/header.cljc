(ns pomp.rad.datatable.ui.header
  (:require [clojure.string :as string]
            [pomp.rad.datatable.ui.primitives :as primitives]
            [pomp.rad.datatable.ui.filter-menu :as filter-menu]
            [pomp.rad.datatable.ui.column-menu :as column-menu]))

(defn- select-all-visible-handler
  [table-id visible-row-ids]
  (let [assignments (->> visible-row-ids
                         (remove nil?)
                         (map str)
                         (map (fn [row-id]
                                (str "$datatable." table-id ".selections['" row-id "'] = evt.target.checked; ")))
                         (string/join ""))]
    (str "$datatable." table-id ".selections ||= {}; "
         assignments
         "$datatable." table-id ".selections = { ...$datatable." table-id ".selections };")))

(defn render-simple [{:keys [cols selectable? table-id visible-row-ids]}]
  [:thead
   [:tr
    (when selectable?
      [:th.w-3
       [:input.checkbox.checkbox-sm
        {:type "checkbox"
         :data-on:click (select-all-visible-handler table-id visible-row-ids)}]])
    (for [{:keys [label]} cols]
      [:th label])]])

(defn render-sortable
  [{:keys [cols sort-state filters data-url selectable? table-id group-by filter-operations visible-row-ids]}]
  (let [table-filter-ops filter-operations
        grouped? (seq group-by)
        group-col-key (first group-by)
        visible-cols (if grouped?
                       (remove #(= (:key %) group-col-key) cols)
                       cols)
        total-cols (count visible-cols)
        group-col (some #(when (= (:key %) group-col-key) %) cols)
        current-sort (first sort-state)
        group-col-name (when group-col-key (name group-col-key))
        group-col-label (or (:label group-col) "Group")
        group-col-type (:type group-col)
        group-col-filter-ops (:filter-operations group-col)
        group-col-filters (get filters group-col-key)
        group-first-filter (first group-col-filters)
        group-current-filter-op (:op group-first-filter)
        group-current-filter-val (:value group-first-filter)
        group-sorted? (= (:column current-sort) group-col-name)
        group-sort-dir (:direction current-sort)]
    [:thead
     [:tr
      (when selectable?
        [:th.w-3
         [:input.checkbox.checkbox-sm
          {:type "checkbox"
           :data-on:click (select-all-visible-handler table-id visible-row-ids)}]])
      (when grouped?
        [:th
         [:div.flex.items-center.justify-between.gap-2
          [:button.flex.items-center.gap-1.hover:text-primary.transition-colors
           (cond-> {}
             group-col-name
             (assoc :data-on:click (str "@post('" data-url "?clicked=" group-col-name "')")))
           [:span {:class (if group-sorted? "opacity-100" "opacity-30")}
            (cond
              (and group-sorted? (= group-sort-dir "asc")) primitives/sort-icon-asc
              (and group-sorted? (= group-sort-dir "desc")) primitives/sort-icon-desc
              :else primitives/sort-icon-both)]
           [:span.font-semibold group-col-label]]
          [:div.flex.items-center
           (filter-menu/render
            {:col-key group-col-key
             :col-label group-col-label
             :col-type group-col-type
             :col-filter-ops group-col-filter-ops
             :table-filter-ops table-filter-ops
             :current-filter-op group-current-filter-op
             :current-filter-value group-current-filter-val
             :table-id table-id
             :data-url data-url})
           (column-menu/render-group-column {:data-url data-url
                                             :group-col-key group-col-key})]]])
      (for [[idx {:keys [key label type groupable filter-operations] :as col}] (map-indexed vector visible-cols)]
        (let [col-name (name key)
               ;; filters is now {:col-key [{:type "text" :op "..." :value "..."}]}
               ;; Get the first filter for display in the menu
              col-filters (get filters key)
              first-filter (first col-filters)
              current-filter-op (:op first-filter)
              current-filter-val (:value first-filter)
              is-sorted? (= (:column current-sort) col-name)
              sort-dir (:direction current-sort)
              sort-disabled? (and grouped? (not= key group-col-key))
              show-filter? (not (and grouped? (= key group-col-key)))]
          [:th
           {:style {:resize "horizontal" :overflow "hidden" :min-width "80px"}
             ;; Drop target handlers stay on th
            :data-on:dragover__prevent (str "$datatable." table-id ".dragOver = '" col-name "'")
            :data-on:dragleave (str "if ($datatable." table-id ".dragOver === '" col-name "') $datatable." table-id ".dragOver = null")
            :data-on:drop (str "@post('" data-url "?moveCol=' + $datatable." table-id ".dragging + '&beforeCol=" col-name "')")
            :data-class (str "{'border-l-4 border-primary': $datatable." table-id ".dragOver === '" col-name "' && $datatable." table-id ".dragging !== '" col-name "'}")}
           [:div.flex.items-center.justify-between.gap-2
             ;; Label is the drag handle
            [:button.flex.items-center.gap-1.hover:text-primary.transition-colors.cursor-grab
             (cond-> {:draggable "true"
                      :data-on:dragstart (str "$datatable." table-id ".dragging = '" col-name "'")
                      :data-on:dragend (str "$datatable." table-id ".dragging = null; $datatable." table-id ".dragOver = null")}
               (not sort-disabled?)
               (assoc :data-on:click (str "@post('" data-url "?clicked=" col-name "')"))
               sort-disabled?
               (assoc :aria-disabled "true"))
             [:span {:class (if is-sorted? "opacity-100" "opacity-30")}
              (cond
                (and is-sorted? (= sort-dir "asc")) primitives/sort-icon-asc
                (and is-sorted? (= sort-dir "desc")) primitives/sort-icon-desc
                :else primitives/sort-icon-both)]
             [:span.font-semibold label]]
            [:div.flex.items-center
             (when show-filter?
               (filter-menu/render
                {:col-key key
                 :col-label label
                 :col-type type
                 :col-filter-ops filter-operations
                 :table-filter-ops table-filter-ops
                 :current-filter-op current-filter-op
                 :current-filter-value current-filter-val
                 :table-id table-id
                 :col-idx idx
                 :total-cols total-cols
                 :data-url data-url}))
             (column-menu/render
              {:col-key key
               :col-label label
               :data-url data-url
               :table-id table-id
               :groupable? groupable
               :group-by group-by})]]]))]]))
