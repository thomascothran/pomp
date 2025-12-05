(ns pomp.rad.datatable.body)

(defn render-row [{:keys [cols row selectable? row-id table-id]}]
  (let [signal-path (str table-id ".selections." row-id)]
    [:tr
     (when selectable?
       [:td.w-3
        [:input.checkbox.checkbox-sm
         {:type "checkbox"
          :data-signals (str "{\"" signal-path "\": false}")
          :data-bind signal-path}]])
     (for [{:keys [key render]} cols]
       [:td (if render
              (render (get row key) row)
              (get row key))])]))

(defn render [{:keys [cols rows selectable? row-id-fn table-id]}]
  (let [row-id-fn (or row-id-fn :id)]
    [:tbody
     (for [row rows]
       (render-row {:cols cols
                    :row row
                    :selectable? selectable?
                    :row-id (row-id-fn row)
                    :table-id table-id}))]))

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
