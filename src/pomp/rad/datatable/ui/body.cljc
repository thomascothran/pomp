(ns pomp.rad.datatable.ui.body
  (:require [clojure.string :as str]
            [pomp.rad.datatable.ui.primitives :as primitives]))

(defn render-row [{:keys [cols row selectable? row-id table-id grouped?]}]
  (let [signal-path (str table-id ".selections." row-id)]
    [:tr
     (when selectable?
       [:td.w-3
        [:input.checkbox.checkbox-sm
         {:type "checkbox"
          :data-signals (str "{\"" signal-path "\": false}")
          :data-bind signal-path}]])
     (when grouped? [:td])
     (for [{:keys [key render]} cols]
       [:td (if render
              (render (get row key) row)
              (get row key))])]))

(defn render-group-row
  [{:keys [group-value row-ids cols selectable? table-id group-idx count]}]
  (let [expanded-signal (str table-id ".expanded." group-idx)
        row-id-strs (map #(str table-id "\\\\.selections\\\\." %) row-ids)
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
        expanded-signal (str table-id ".expanded." group-idx)]
    (list
     (render-group-row {:group-value group-value
                        :row-ids row-ids
                        :cols cols
                        :selectable? selectable?
                        :table-id table-id
                        :group-idx group-idx
                        :count count})
     (for [row rows]
       [:tr {:data-show (str "$" expanded-signal)}
        (when selectable?
          [:td.w-3
           [:input.checkbox.checkbox-sm
            {:type "checkbox"
             :data-signals (str "{\"" table-id ".selections." (row-id-fn row) "\": false}")
             :data-bind (str table-id ".selections." (row-id-fn row))}]])
        [:td]
        (for [{:keys [key render]} cols]
          [:td (if render
                 (render (get row key) row)
                 (get row key))])]))))

(defn render [{:keys [cols rows groups selectable? row-id-fn table-id]}]
  (let [row-id-fn (or row-id-fn :id)
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
       (for [row rows]
         (render-row {:cols cols
                      :row row
                      :selectable? selectable?
                      :row-id (row-id-fn row)
                      :table-id table-id
                      :grouped? false})))]))

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
