(ns pomp.rad.datatable.ui.body
  (:require [clojure.string :as str]
            [pomp.rad.datatable.ui.primitives :as primitives]
            [pomp.rad.datatable.ui.row :as row]))

(defn render-row
  "Renders a data row. Delegates to row/render-row.
   
   Deprecated: Use pomp.rad.datatable.ui.row/render-row directly."
  [ctx]
  (row/render-row ctx))

(defn render-group-row
  [{:keys [group-value row-ids cols selectable? table-id group-idx count]}]
  (let [expanded-signal (str "datatable." table-id ".expanded." group-idx)
        row-id-strs (map #(str "datatable\\\\." table-id "\\\\.selections\\\\." %) row-ids)
        select-pattern (str/join "|" row-id-strs)]
    [:tr.bg-base-200
     (when selectable?
       [:td.w-3
        [:input.checkbox.checkbox-sm
         {:type "checkbox"
          :data-on:click (str "evt.target.checked ? @setAll(true, { include: '" select-pattern "' }) : @setAll(false, { include: '" select-pattern "' })")}]])
     [:td
      [:button.btn.btn-ghost.btn-xs.flex.items-center.gap-1
       {:data-on:click (str "$" expanded-signal " = !$" expanded-signal)
        :data-signals (str "{\"" expanded-signal "\": false}")}
       [:span {:data-show (str "$" expanded-signal)} primitives/chevron-down]
       [:span {:data-show (str "!$" expanded-signal)} primitives/chevron-right]
       [:span.font-medium (str group-value)]
       [:span.text-base-content.opacity-50 (str "(" count ")")]]]
     (for [_ cols]
       [:td])]))

(defn render-group
  [{:keys [group cols selectable? row-id-fn table-id group-idx]}]
  (let [{:keys [group-value rows row-ids count]} group
        expanded-signal (str "datatable." table-id ".expanded." group-idx)]
    (list
     (render-group-row {:group-value group-value
                        :row-ids row-ids
                        :cols cols
                        :selectable? selectable?
                        :table-id table-id
                        :group-idx group-idx
                        :count count})
     (for [r rows]
       (let [signal-path (str "datatable." table-id ".selections." (row-id-fn r))]
         [:tr {:data-show (str "$" expanded-signal)}
          (when selectable?
            (row/render-selection-cell {:signal-path signal-path}))
          [:td]
          (for [col cols]
            (row/render-cell {:value (get r (:key col))
                              :row r
                              :col col}))])))))

(defn render [{:keys [cols rows groups selectable? row-id-fn table-id render-row render-cell]}]
  (let [row-id-fn (or row-id-fn :id)
        render-row-fn (or render-row row/render-row)
        grouped? (seq groups)]
    [:tbody
     (if grouped?
       (for [[idx group] (map-indexed vector groups)]
         (render-group {:group group
                        :cols cols
                        :selectable? selectable?
                        :row-id-fn row-id-fn
                        :table-id table-id
                        :group-idx idx}))
       (for [r rows]
         (render-row-fn {:cols cols
                         :row r
                         :selectable? selectable?
                         :row-id (row-id-fn r)
                         :table-id table-id
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
