(ns pomp.rad.datatable.header
  (:require [pomp.rad.datatable.filter-menu :as filter-menu]))

(def sort-icon-both
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "1.5"
         :stroke "currentColor"
         :class "w-3 h-3"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M3 7.5 7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5"}]])

(def sort-icon-asc
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "2"
         :stroke "currentColor"
         :class "w-3 h-3"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M4.5 15.75l7.5-7.5 7.5 7.5"}]])

(def sort-icon-desc
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "2"
         :stroke "currentColor"
         :class "w-3 h-3"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M19.5 8.25l-7.5 7.5-7.5-7.5"}]])

(defn render-simple
  [{:keys [cols selectable? table-id]}]
  [:thead
   [:tr
    (when selectable?
      [:th
       [:input.checkbox.checkbox-sm
        {:type "checkbox"
         :data-on:click (str "evt.target.checked ? @setAll(true, { include: '" table-id "\\\\.selections\\\\..*' }) : @setAll(false, { include: '" table-id "\\\\.selections\\\\..*' })")}]])
    (for [{:keys [label]} cols]
      [:th label])]])

(defn render-sortable
  [{:keys [cols sort-state filters data-url selectable? table-id]}]
  (let [total-cols (count cols)]
    [:thead
     [:tr
      (when selectable?
        [:th
         [:input.checkbox.checkbox-sm
          {:type "checkbox"
           :data-on:click (str "evt.target.checked ? @setAll(true, { include: '" table-id "\\\\.selections\\\\..*' }) : @setAll(false, { include: '" table-id "\\\\.selections\\\\..*' })")}]])
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
            :data-on:dragstart (str "$" table-id ".dragging = '" col-name "'")
            :data-on:dragend (str "$" table-id ".dragging = null; $" table-id ".dragOver = null")
            :data-on:dragover__prevent (str "$" table-id ".dragOver = '" col-name "'")
            :data-on:dragleave (str "if ($" table-id ".dragOver === '" col-name "') $" table-id ".dragOver = null")
            :data-on:drop (str "@get('" data-url "?moveCol=' + $" table-id ".dragging + '&beforeCol=" col-name "')")
            :data-class (str "{'border-l-4 border-primary': $" table-id ".dragOver === '" col-name "' && $" table-id ".dragging !== '" col-name "'}")}
           [:div.flex.items-center.justify-between.gap-2
            [:button.flex.items-center.gap-1.hover:text-primary.transition-colors
             {:data-on:click (str "@get('" data-url "?clicked=" col-name "')")}
             [:span {:class (if is-sorted? "opacity-100" "opacity-30")}
              (cond
                (and is-sorted? (= sort-dir "asc")) sort-icon-asc
                (and is-sorted? (= sort-dir "desc")) sort-icon-desc
                :else sort-icon-both)]
             [:span.font-semibold label]]
            (filter-menu/render
             {:col-key key
              :col-label label
              :current-filter-op current-filter-op
              :current-filter-value current-filter-val
              :col-idx idx
              :total-cols total-cols
              :data-url data-url})]]))]]))
