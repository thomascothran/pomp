(ns pomp.rad.datatable.ui.body
  (:require [clojure.string :as str]
            [pomp.rad.datatable.ui.primitives :as primitives]
            [pomp.rad.datatable.ui.row :as row]))

(defn render-row
  "Renders a data row. Delegates to row/render-row.

   Deprecated: Use pomp.rad.datatable.ui.row/render-row directly."
  [ctx]
  (row/render-row ctx))

(defn- expanded-signal
  [table-id group-idx]
  (let [base (str "datatable." table-id ".expanded")
        access (str base "['" group-idx "']")
        check (str "$" base " && $" access)
        toggle (str "$" base " ||= {}; $" access " = !$" access)]
    {:check check
     :toggle toggle}))

(defn render-group-row
  [{:keys [group-value row-ids cols selectable? table-id group-idx count]}]
  (let [{:keys [check toggle]} (expanded-signal table-id group-idx)
        row-id-strs (map #(str "datatable\\." table-id "\\.selections\\." %) row-ids)
        select-pattern (str/join "|" row-id-strs)]
    [:tr.bg-base-200
     (when selectable?
       [:td.w-3
        [:input.checkbox.checkbox-sm
         {:type "checkbox"
          :data-on:click (str "evt.target.checked ? @setAll(true, { include: '" select-pattern "' }) : @setAll(false, { include: '" select-pattern "' })")}]])
     [:td
      [:button.btn.btn-ghost.btn-xs.flex.items-center.gap-1
       {:data-on:click toggle}
       [:span {:data-show check} primitives/chevron-down]
       [:span {:data-show (str "!(" check ")")} primitives/chevron-right]
       [:span.font-medium (str group-value)]
       [:span.text-base-content.opacity-50 (str "(" count ")")]]]
     (for [_ cols]
       [:td])]))

(defn render-group
  [{:keys [group cols selectable? row-id-fn table-id group-idx row-idx-offset data-url]}]
  (let [{:keys [group-value rows row-ids count]} group
        {:keys [check]} (expanded-signal table-id group-idx)
        row-idx-offset (or row-idx-offset 0)]
    (list
     (render-group-row {:group-value group-value
                        :row-ids row-ids
                        :cols cols
                        :selectable? selectable?
                        :table-id table-id
                        :group-idx group-idx
                        :count count})
     (for [[idx r] (map-indexed vector rows)]
       (let [signal-path (str "datatable." table-id ".selections." (row-id-fn r))
             row-idx (+ row-idx-offset idx)
             row-id (row-id-fn r)]
         [:tr {:data-show check}
          (when selectable?
            (row/render-selection-cell {:signal-path signal-path}))
          [:td]
          (for [[col-idx col] (map-indexed vector cols)]
            (let [raw-value (get r (:key col))
                  display-value (if-let [display-fn (:display-fn col)]
                                  (display-fn r)
                                  raw-value)]
              (row/render-cell {:value display-value
                                :raw-value raw-value
                                :row r
                                :col col
                                :row-idx row-idx
                                :col-idx col-idx
                                :table-id table-id
                                :row-id row-id
                                :data-url data-url})))])))))

(defn render [{:keys [cols rows groups group-by selectable? row-id-fn table-id data-url render-row render-cell]}]
  (let [row-id-fn (or row-id-fn :id)
        render-row-fn (or render-row row/render-row)
        grouped? (seq groups)
        group-col-key (first group-by)
        visible-cols (if (and grouped? group-col-key)
                       (remove #(= (:key %) group-col-key) cols)
                       cols)]
    [:tbody
     (if grouped?
        ;; For grouped rows, calculate cumulative row-idx-offset
       (let [groups-with-offsets (reduce (fn [acc group]
                                           (let [offset (if (empty? acc)
                                                          0
                                                          (+ (:row-idx-offset (last acc))
                                                             (count (:rows (:group (last acc))))))]
                                             (conj acc {:group group :row-idx-offset offset})))
                                         []
                                         groups)]
         (for [[idx {:keys [group row-idx-offset]}] (map-indexed vector groups-with-offsets)]
           (render-group {:group group
                          :cols visible-cols
                          :selectable? selectable?
                          :row-id-fn row-id-fn
                          :table-id table-id
                          :data-url data-url
                          :group-idx idx
                          :row-idx-offset row-idx-offset})))
       (for [[row-idx r] (map-indexed vector rows)]
         (render-row-fn {:cols cols
                         :row r
                         :selectable? selectable?
                         :row-id (row-id-fn r)
                         :row-idx row-idx
                         :table-id table-id
                         :data-url data-url
                         :grouped? false
                         :render-cell render-cell})))]))

(defn render-skeleton-row [{:keys [cols selectable?]}]
  [:tr
   (when selectable?
     [:td [:div.skeleton.h-4.w-4]])
   (for [_ cols]
     [:td [:div.skeleton.h-4.w-full]])])

(defn render-skeleton [{:keys [cols n selectable?]}]
  [:tbody
   (for [_ (range n)]
     (render-skeleton-row {:cols cols :selectable? selectable?}))])
