(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html page get-signals]]
            [jsonista.core :as j]
            [clojure.string :as str]))

(def columns
  [{:key :name :label "Name" :type :text}
   {:key :century :label "Century" :type :text}
   {:key :school :label "School" :type :enum}
   {:key :region :label "Region" :type :enum}])

(def philosophers
  [{:name "Socrates" :century "5th BC" :school "Classical Greek" :region "Greece"}
   {:name "Plato" :century "4th BC" :school "Platonism" :region "Greece"}
   {:name "Aristotle" :century "4th BC" :school "Peripatetic" :region "Greece"}
   {:name "Confucius" :century "5th BC" :school "Confucianism" :region "China"}
   {:name "Laozi" :century "6th BC" :school "Taoism" :region "China"}
   {:name "Epicurus" :century "3rd BC" :school "Epicureanism" :region "Greece"}
   {:name "Zeno of Citium" :century "3rd BC" :school "Stoicism" :region "Greece"}
   {:name "Marcus Aurelius" :century "2nd" :school "Stoicism" :region "Rome"}
   {:name "Seneca" :century "1st" :school "Stoicism" :region "Rome"}
   {:name "Augustine" :century "4th" :school "Christian Platonism" :region "North Africa"}
   {:name "Thomas Aquinas" :century "13th" :school "Scholasticism" :region "Italy"}
   {:name "René Descartes" :century "17th" :school "Rationalism" :region "France"}
   {:name "John Locke" :century "17th" :school "Empiricism" :region "England"}
   {:name "Immanuel Kant" :century "18th" :school "German Idealism" :region "Germany"}
   {:name "Friedrich Nietzsche" :century "19th" :school "Existentialism" :region "Germany"}])

(def page-sizes [10 25 100 250])

(defn sort-data [rows sort-spec]
  (if (empty? sort-spec)
    rows
    (let [{:keys [column direction]} (first sort-spec)
          col-key (keyword column)
          comparator (if (= direction "asc")
                       compare
                       #(compare %2 %1))]
      (sort-by #(get % col-key) comparator rows))))

(defn apply-text-filter [rows col-key filter-op filter-value]
  (let [search-term (str/lower-case (or filter-value ""))
        get-cell-val #(str/lower-case (str (get % col-key)))]
    (case filter-op
      "contains" (if (str/blank? filter-value)
                   rows
                   (filter #(str/includes? (get-cell-val %) search-term) rows))
      "not-contains" (if (str/blank? filter-value)
                       rows
                       (remove #(str/includes? (get-cell-val %) search-term) rows))
      "equals" (filter #(= (get-cell-val %) search-term) rows)
      "not-equals" (remove #(= (get-cell-val %) search-term) rows)
      "starts-with" (filter #(str/starts-with? (get-cell-val %) search-term) rows)
      "ends-with" (filter #(str/ends-with? (get-cell-val %) search-term) rows)
      "is-empty" (filter #(str/blank? (str (get % col-key))) rows)
      rows)))

(defn apply-filters [rows filters]
  (reduce (fn [filtered-rows [col-key filter-spec]]
            (case (:type filter-spec)
              "text" (apply-text-filter filtered-rows col-key (:op filter-spec) (:value filter-spec))
              filtered-rows))
          rows
          filters))

(defn update-filters [current-filters filter-col filter-op filter-val clear-filters?]
  (cond
    clear-filters? {}
    (nil? filter-col) current-filters
    (and (str/blank? filter-val) (not= filter-op "is-empty")) (dissoc current-filters (keyword filter-col))
    :else (assoc current-filters (keyword filter-col) {:type "text" :op (or filter-op "contains") :value filter-val})))

(defn paginate-data [rows page-size page-current]
  (->> rows
       (drop (* page-current page-size))
       (take page-size)))

(defn total-pages [total-rows page-size]
  (int (Math/ceil (/ total-rows page-size))))

(defn next-sort-state [current-sort clicked-column]
  (if (nil? clicked-column)
    current-sort
    (let [current (first current-sort)
          current-col (:column current)
          current-dir (:direction current)]
      (cond
        (not= current-col clicked-column)
        [{:column clicked-column :direction "asc"}]

        (= current-dir "asc")
        [{:column clicked-column :direction "desc"}]

        :else
        []))))

(defn next-page-state [current-page current-size total-rows page-action new-size]
  (let [size (if new-size (parse-long new-size) current-size)
        total-pgs (total-pages total-rows size)
        page (cond
               new-size 0
               (= page-action "first") 0
               (= page-action "prev") (max 0 (dec current-page))
               (= page-action "next") (min (dec total-pgs) (inc current-page))
               (= page-action "last") (dec total-pgs)
               :else (min current-page (max 0 (dec total-pgs))))]
    {:size size :current page}))

(defn sort-indicator [sort-state column-key]
  (let [current (first sort-state)
        col-name (name column-key)]
    (when (= (:column current) col-name)
      (if (= (:direction current) "asc") "▲" "▼"))))

(defn has-active-filters? [filters]
  (seq filters))

(defn render-filter-dropdown [col-key col-label current-filter-op current-filter-value col-idx total-cols]
  (let [col-name (name col-key)
        current-op (or current-filter-op "contains")
        has-filter? (or (not (str/blank? current-filter-value)) (= current-op "is-empty"))
        use-dropdown-end? (>= col-idx (/ total-cols 2))
        funnel-icon [:svg {:xmlns "http://www.w3.org/2000/svg"
                           :fill "none"
                           :viewBox "0 0 24 24"
                           :stroke-width "1.5"
                           :stroke "currentColor"
                           :class "w-4 h-4"}
                     [:path {:stroke-linecap "round"
                             :stroke-linejoin "round"
                             :d "M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 0 1-.659 1.591l-5.432 5.432a2.25 2.25 0 0 0-.659 1.591v2.927a2.25 2.25 0 0 1-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 0 0-.659-1.591L3.659 7.409A2.25 2.25 0 0 1 3 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0 1 12 3Z"}]]]
    [:div {:class (str "dropdown" (when use-dropdown-end? " dropdown-end"))}
     [:div.btn.btn-ghost.btn-xs.px-1
      {:tabindex "0"
       :role "button"
       :class (if has-filter? "text-primary" "opacity-50 hover:opacity-100")}
      funnel-icon]
     [:div.dropdown-content.z-50.bg-base-100.shadow-lg.rounded-box.p-4.w-64
      {:tabindex "0"}
      [:form.flex.flex-col.gap-3
       {:data-on:submit__prevent (format "@get('/demo/datatable/data?filterCol=%s&filterOp=' + evt.target.elements['filterOp'].value + '&filterVal=' + evt.target.elements['filterVal'].value)" col-name)}
       [:div.text-sm.font-semibold (str "Filter " col-label)]
       [:select.select.select-sm.select-bordered.w-full
        {:name "filterOp"}
        [:option {:value "contains" :selected (= current-op "contains")} "contains"]
        [:option {:value "not-contains" :selected (= current-op "not-contains")} "does not contain"]
        [:option {:value "equals" :selected (= current-op "equals")} "equals"]
        [:option {:value "not-equals" :selected (= current-op "not-equals")} "does not equal"]
        [:option {:value "starts-with" :selected (= current-op "starts-with")} "starts with"]
        [:option {:value "ends-with" :selected (= current-op "ends-with")} "ends with"]
        [:option {:value "is-empty" :selected (= current-op "is-empty")} "is empty"]]
       [:input.input.input-sm.input-bordered.w-full
        {:type "text"
         :name "filterVal"
         :placeholder "Value..."
         :value (or current-filter-value "")}]
       [:button.btn.btn-sm.btn-primary.w-full {:type "submit"} "Apply"]]]]))

(defn render-sortable-header [cols sort-state filters]
  (let [total-cols (count cols)
        sort-icon [:svg {:xmlns "http://www.w3.org/2000/svg"
                         :fill "none"
                         :viewBox "0 0 24 24"
                         :stroke-width "1.5"
                         :stroke "currentColor"
                         :class "w-3 h-3"}
                   [:path {:stroke-linecap "round"
                           :stroke-linejoin "round"
                           :d "M3 7.5 7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5"}]]]
    [:thead
     [:tr
      (for [[idx {:keys [key label]}] (map-indexed vector cols)]
        (let [col-name (name key)
              current-filter (get filters key)
              current-filter-op (:op current-filter)
              current-filter-val (:value current-filter)
              current-sort (first sort-state)
              is-sorted? (= (:column current-sort) col-name)
              sort-dir (:direction current-sort)]
          [:th
           [:div.flex.items-center.justify-between.gap-2
            [:button.flex.items-center.gap-1.hover:text-primary.transition-colors
             {:data-on:click (format "@get('/demo/datatable/data?clicked=%s')" col-name)}
             [:span {:class (if is-sorted? "opacity-100" "opacity-30")}
              (if is-sorted?
                (if (= sort-dir "asc")
                  [:svg {:xmlns "http://www.w3.org/2000/svg"
                         :fill "none"
                         :viewBox "0 0 24 24"
                         :stroke-width "2"
                         :stroke "currentColor"
                         :class "w-3 h-3"}
                   [:path {:stroke-linecap "round"
                           :stroke-linejoin "round"
                           :d "M4.5 15.75l7.5-7.5 7.5 7.5"}]]
                  [:svg {:xmlns "http://www.w3.org/2000/svg"
                         :fill "none"
                         :viewBox "0 0 24 24"
                         :stroke-width "2"
                         :stroke "currentColor"
                         :class "w-3 h-3"}
                   [:path {:stroke-linecap "round"
                           :stroke-linejoin "round"
                           :d "M19.5 8.25l-7.5 7.5-7.5-7.5"}]])
                sort-icon)]
             [:span.font-semibold label]]
            (render-filter-dropdown key label current-filter-op current-filter-val idx total-cols)]]))]]))

(defn render-table-header [cols]
  [:thead
   [:tr
    (for [{:keys [label]} cols]
      [:th label])]])

(defn render-table-row [cols row]
  [:tr
   (for [{:keys [key]} cols]
     [:td (get row key)])])

(defn render-table-body [cols rows]
  [:tbody
   (for [row rows]
     (render-table-row cols row))])

(defn render-pagination-controls [total-rows page-size page-current filters]
  (let [total-pgs (total-pages total-rows page-size)
        start (if (zero? total-rows) 0 (+ 1 (* page-current page-size)))
        end (min (* (+ page-current 1) page-size) total-rows)
        on-first? (= page-current 0)
        on-last? (or (zero? total-rows) (>= (+ page-current 1) total-pgs))]
    [:div.flex.items-center.justify-between.mt-4.text-sm.opacity-70
     [:div.flex.items-center.gap-4
      (when (has-active-filters? filters)
        [:button.btn.btn-sm.btn-ghost.text-error.opacity-100
         {:data-on:click "@get('/demo/datatable/data?clearFilters=1')"}
         "✕ Clear filters"])
      [:div.flex.items-center.gap-1
       [:select.select.select-ghost.select-sm.font-medium
        {:data-on:change "@get('/demo/datatable/data?pageSize=' + evt.target.value)"}
        (for [size page-sizes]
          [:option {:value size :selected (= size page-size)} size])]
       [:span.whitespace-nowrap "per page"]]]
     [:div (format "%d–%d of %d" start end total-rows)]
     [:div.flex.items-center.gap-1
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click "@get('/demo/datatable/data?page=first')"
        :disabled on-first?}
       "«"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click "@get('/demo/datatable/data?page=prev')"
        :disabled on-first?}
       "‹"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click "@get('/demo/datatable/data?page=next')"
        :disabled on-last?}
       "›"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click "@get('/demo/datatable/data?page=last')"
        :disabled on-last?}
       "»"]]]))

(defn render-table [id cols rows sort-state filters total-rows page-size page-current]
  [:div {:id id}
   [:div.overflow-x-auto
    [:table.table.table-sm
     (render-sortable-header cols sort-state filters)
     (render-table-body cols rows)]]
   (render-pagination-controls total-rows page-size page-current filters)])

(defn render-skeleton-row [cols]
  [:tr
   (for [_ cols]
     [:td [:div.skeleton.h-4.w-full]])])

(defn render-skeleton-body [cols n]
  [:tbody
   (for [_ (range n)]
     (render-skeleton-row cols))])

(defn render-skeleton-table [id cols n]
  [:div {:id id}
   [:div.overflow-x-auto
    [:table.table.table-sm
     (render-table-header cols)
     (render-skeleton-body cols n)]]])

(defn page-handler [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (page
           [:div.p-8
            {:data-signals "{datatable: {sort: [], page: {size: 10, current: 0}, filters: {}, openFilter: ''}}"}
            [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
            [:div#datatable-container
             {:data-init "@get('/demo/datatable/data')"}
             [:div#datatable]]]))})

(defn data-handler [req]
  (let [signals (get-signals req)
        current-sort (get-in signals [:datatable :sort] [])
        current-page (get-in signals [:datatable :page :current] 0)
        current-size (get-in signals [:datatable :page :size] 10)
        current-filters (get-in signals [:datatable :filters] {})
        clicked-column (get-in req [:query-params "clicked"])
        page-action (get-in req [:query-params "page"])
        new-page-size (get-in req [:query-params "pageSize"])
        filter-col (get-in req [:query-params "filterCol"])
        filter-op (get-in req [:query-params "filterOp"])
        filter-val (get-in req [:query-params "filterVal"])
        clear-filters? (some? (get-in req [:query-params "clearFilters"]))
        new-sort (next-sort-state current-sort clicked-column)
        new-filters (update-filters current-filters filter-col filter-op filter-val clear-filters?)
        filters-to-patch (if clear-filters?
                           (into {} (map (fn [[k _]] [k nil]) current-filters))
                           new-filters)
        filtered-data (apply-filters philosophers new-filters)
        sorted-data (sort-data filtered-data new-sort)
        total-rows (count sorted-data)
        {:keys [size current]} (next-page-state current-page current-size total-rows page-action new-page-size)
        new-page (cond
                   clicked-column 0
                   (or filter-col clear-filters?) 0
                   :else current)
        paginated-data (paginate-data sorted-data size new-page)
        is-initial-load? (and (nil? clicked-column) (nil? page-action) (nil? new-page-size)
                              (nil? filter-col) (not clear-filters?))]
    (->sse-response req
                    {on-open
                     (fn [sse]
                       (when is-initial-load?
                         (d*/patch-elements! sse (->html (render-skeleton-table "datatable" columns 10)))
                         (Thread/sleep 300))
                       (when-not is-initial-load?
                         (d*/patch-signals! sse (j/write-value-as-string
                                                 {:datatable {:sort new-sort
                                                              :page {:size size :current new-page}
                                                              :filters filters-to-patch
                                                              :openFilter ""}})))
                       (d*/patch-elements! sse (->html (render-table "datatable" columns paginated-data new-sort
                                                                     new-filters total-rows size new-page)))
                       (d*/close-sse! sse))})))

(defn make-routes [_]
  [["/datatable" page-handler]
   ["/datatable/data" data-handler]])

(comment
  (require '[demo.datatable :as dt] :reload)
  (require '[demo.util :refer [->html]])

  ;; Test filtering
  (apply-filters philosophers {:name {:type "text" :value "a"}})
  (apply-filters philosophers {:name {:type "text" :value "a"} :century {:type "text" :value "4th"}})

  ;; Test pagination
  (paginate-data philosophers 5 0)
  (total-pages 15 5)

  ;; Test render-sortable-header with filters
  (->html (render-sortable-header columns [] {}))
  (->html (render-sortable-header columns [] {:name {:type "text" :value "a"}}))

  ;; Test render-table with filters
  (->html (render-table "test" columns (take 5 philosophers) [] {} 15 10 0))
  (->html (render-table "test" columns (take 5 philosophers) [] {:name {:type "text" :value "a"}} 10 10 0))

  ;; Test page-handler
  (:body (page-handler {})))
