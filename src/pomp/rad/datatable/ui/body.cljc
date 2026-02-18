(ns pomp.rad.datatable.ui.body
  (:require [clojure.string :as str]
            [pomp.rad.datatable.ui.primitives :as primitives]
            [pomp.rad.datatable.ui.row :as row]))

(defn- js-bracket-escape
  [value]
  (-> (str value)
      (str/replace "\\" "\\\\")
      (str/replace "'" "\\'")))

(defn- segment-path-part
  [segment]
  (if (and (vector? segment) (= 2 (count segment)))
    (let [[group-key group-value] segment]
      (str (js-bracket-escape (if (keyword? group-key)
                                 (name group-key)
                                 group-key))
           "="
           (js-bracket-escape group-value)))
    (js-bracket-escape segment)))

(defn- group-path
  [path]
  (->> path
       (map segment-path-part)
       (str/join "-")))

(defn- group-identity-segment
  [group idx]
  (if (contains? group :group-key)
    [(name (:group-key group)) (str (:group-value group))]
    idx))

(defn- visible-check
  [ancestor-check self-check]
  (if ancestor-check
    (str ancestor-check " && " self-check)
    self-check))

(defn- grouped-row?
  [row]
  (and (map? row)
       (contains? row :group-key)
       (contains? row :rows)))

(defn render-row
  "Renders a data row. Delegates to row/render-row.

   Deprecated: Use pomp.rad.datatable.ui.row/render-row directly."
  [ctx]
  (row/render-row ctx))

(defn- expanded-signal
  [table-id group-idx]
  (let [base (str "datatable." table-id ".expanded")
        path (if (vector? group-idx)
               (group-path group-idx)
               group-idx)
        access (str base "['" path "']")
        check (str "$" base " && $" access)
        toggle (str "$" base " ||= {}; $" access " = !$" access)]
    {:check check
     :toggle toggle}))

(defn- group-indent-style
  [group-idx]
  (let [level (count (or group-idx []))
        px (max 0 (* 18 (dec level)))]
    (when (pos? px)
      {:padding-left (str px "px")})))

(defn render-group-row
  [{:keys [group-value row-ids cols selectable? table-id group-idx count ancestor-check]
    :as group-row}]
  (let [{:keys [check toggle]} (expanded-signal table-id group-idx)
        row-id-strs (map #(str "datatable\\." table-id "\\.selections\\." %) row-ids)
        select-pattern (str/join "|" row-id-strs)
        group-level (clojure.core/count (or group-idx []))
        group-count (or count 0)
        attrs (cond-> {:data-group-level group-level}
                ancestor-check (assoc :data-show ancestor-check))]
    [:tr.bg-base-200
     attrs
     (when selectable?
       [:td.w-3
        [:input.checkbox.checkbox-sm
         {:type "checkbox"
          :data-on:click (str "evt.target.checked ? @setAll(true, { include: '" select-pattern "' }) : @setAll(false, { include: '" select-pattern "' })")} ]])
      [:td
       [:button
        {:class (str "btn btn-ghost btn-xs flex items-center gap-1"
                     (when (> group-level 1)
                       " border-l-2 border-base-300 rounded-none"))
         :data-on:click toggle
         :style (group-indent-style group-idx)}
        [:span {:data-show check} primitives/chevron-down]
        [:span {:data-show (str "!(" check ")")} primitives/chevron-right]
        [:span.font-medium (str group-value)]
        [:span.text-base-content.opacity-50 (str "(" group-count ")")]]]
     (for [_ cols]
       [:td])]))

(defn- groups-with-offsets
  [groups row-idx-offset]
  (second
   (reduce (fn [[offset out] group]
             [(+ offset (or (:count group) 0))
              (conj out {:group group
                         :row-idx-offset offset})])
           [(or row-idx-offset 0) []]
           groups)))

(defn- render-leaf-row
  [{:keys [r cols selectable? row-idx table-id data-url row-id check render-cell]}]
  (let [render-cell-fn (or render-cell row/render-cell)
        selection-cell (when selectable?
                        (row/render-selection-cell {:signal-path (str "datatable." table-id ".selections." row-id)}))]
    (into
     [:tr {:data-show check}
      selection-cell
      [:td]]
     (for [[col-idx col] (map-indexed vector cols)]
       (let [raw-value (get r (:key col))
             display-value (if-let [display-fn (:display-fn col)]
                            (display-fn r)
                            raw-value)]
         (render-cell-fn {:value display-value
                          :raw-value raw-value
                          :row r
                          :col col
                          :row-idx row-idx
                          :col-idx col-idx
                          :table-id table-id
                          :row-id row-id
                          :data-url data-url}))))))

(defn- render-group
  [{:keys [group cols selectable? row-id-fn table-id data-url render-row render-cell ancestor-check group-idx row-idx-offset]}]
  (let [{:keys [group-value rows row-ids count]} group
        {:keys [check]} (expanded-signal table-id group-idx)
        row-idx-offset (or row-idx-offset 0)
        descendants-check (visible-check ancestor-check check)
        child-rows (if (and (seq rows) (grouped-row? (first rows)))
                    (let [[_ out] (reduce (fn [[offset out] [idx child]]
                                            (let [child-count (or (:count child)
                                                                 (count (:rows child) 0))
                                                  next-rows (render-group {:group child
                                                                          :cols cols
                                                                          :selectable? selectable?
                                                                          :row-id-fn row-id-fn
                                                                          :table-id table-id
                                                                          :data-url data-url
                                                                          :render-row render-row
                                                                          :render-cell render-cell
                                                                          :ancestor-check descendants-check
                                                                           :group-idx (conj group-idx (group-identity-segment child idx))
                                                                          :row-idx-offset offset})]
                                              [ (+ offset child-count)
                                               (into out next-rows)]))
                                            [row-idx-offset []]
                                            (map-indexed vector rows))]
                      out)
                    (for [[idx row] (map-indexed vector rows)
                          :let [row-id (row-id-fn row)
                                row-idx (+ row-idx-offset idx)]
                          :when (map? row)]
                      (render-leaf-row {:r row
                                        :cols cols
                                        :selectable? selectable?
                                        :row-idx row-idx
                                        :table-id table-id
                                        :data-url data-url
                                        :row-id row-id
                                        :check descendants-check
                                        :render-cell render-cell}))) ]
      (into [(render-group-row {:group-value group-value
                               :row-ids row-ids
                               :cols cols
                               :selectable? selectable?
                               :table-id table-id
                               :group-idx group-idx
                               :count count
                               :ancestor-check ancestor-check})
            ]
            child-rows)))

(defn render
  [{:keys [cols rows groups group-by selectable? row-id-fn table-id data-url render-row render-cell]}]
  (let [row-id-fn (or row-id-fn :id)
        grouped? (seq groups)
        visible-cols cols]
    [:tbody
      (if grouped?
        (let [groups-with-offsets (groups-with-offsets groups 0)]
          (for [[idx {:keys [group row-idx-offset]}] (map-indexed vector groups-with-offsets)]
            (render-group {:group group
                          :cols visible-cols
                          :selectable? selectable?
                         :row-id-fn row-id-fn
                         :table-id table-id
                         :data-url data-url
                          :render-row (or render-row row/render-row)
                          :render-cell render-cell
                            :group-idx [(group-identity-segment group idx)]
                           :row-idx-offset row-idx-offset})))
        (for [[row-idx r] (map-indexed vector rows)]
          ((or render-row row/render-row)
           {:cols cols
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
