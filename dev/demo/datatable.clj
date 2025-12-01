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

(defn apply-text-filter [rows col-key filter-value]
  (if (str/blank? filter-value)
    rows
    (let [search-term (str/lower-case filter-value)]
      (filter #(str/includes? (str/lower-case (str (get % col-key))) search-term) rows))))

(defn apply-filters [rows filters]
  (reduce (fn [filtered-rows [col-key filter-spec]]
            (case (:type filter-spec)
              "text" (apply-text-filter filtered-rows col-key (:value filter-spec))
              filtered-rows))
          rows
          filters))

(defn update-filters [current-filters filter-col filter-val clear-filters?]
  (cond
    clear-filters? {}
    (nil? filter-col) current-filters
    (str/blank? filter-val) (dissoc current-filters (keyword filter-col))
    :else (assoc current-filters (keyword filter-col) {:type "text" :value filter-val})))

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

(defn render-filter-dropdown [col-key current-filter-value]
  (let [col-name (name col-key)]
    [:div.relative
     [:button.btn.btn-ghost.btn-xs
      {:data-on:click (format "$datatable.openFilter = $datatable.openFilter === '%s' ? '' : '%s'" col-name col-name)
       :class (if (not (str/blank? current-filter-value)) "text-primary" "")}
      "⏥"]
     [:div.absolute.right-0.top-full.mt-1.z-50.bg-base-100.shadow-lg.rounded-box.p-3.w-52
      {:data-show (format "$datatable.openFilter === '%s'" col-name)}
      [:div.form-control
       [:label.label [:span.label-text (str "Filter " col-name)]]
       [:input.input.input-sm.input-bordered.w-full
        {:type "text"
         :placeholder "Type to filter..."
         :value (or current-filter-value "")
         :data-on:input__debounce.300ms (format "@get('/demo/datatable/data?filterCol=%s&filterVal=' + evt.target.value)" col-name)}]]]]))

(defn render-sortable-header [cols sort-state filters]
  [:thead
   [:tr
    (for [{:keys [key label]} cols]
      (let [col-name (name key)
            current-filter (get-in filters [key :value])]
        [:th
         [:div.flex.items-center.gap-1
          [:span.cursor-pointer.select-none.hover:bg-base-200.px-1.rounded
           {:data-on:click (format "@get('/demo/datatable/data?clicked=%s')" col-name)}
           label
           (when-let [indicator (sort-indicator sort-state key)]
             [:span.text-xs.ml-1 indicator])]
          (render-filter-dropdown key current-filter)]]))]])

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
    [:div.flex.items-center.justify-between.mt-4
     [:div.flex.items-center.gap-4
      (when (has-active-filters? filters)
        [:button.btn.btn-sm.btn-ghost.text-error
         {:data-on:click "@get('/demo/datatable/data?clearFilters=1')"}
         "✕ Clear filters"])
      [:div.flex.items-center.gap-2
       [:span.text-sm "Rows per page:"]
       [:select.select.select-sm.select-bordered
        {:data-on:change "@get('/demo/datatable/data?pageSize=' + evt.target.value)"}
        (for [size page-sizes]
          [:option {:value size :selected (= size page-size)} size])]]]
     [:div.text-sm
      (format "Showing %d-%d of %d" start end total-rows)]
     [:div.join
      [:button.join-item.btn.btn-sm
       {:data-on:click "@get('/demo/datatable/data?page=first')"
        :disabled on-first?}
       "«"]
      [:button.join-item.btn.btn-sm
       {:data-on:click "@get('/demo/datatable/data?page=prev')"
        :disabled on-first?}
       "‹"]
      [:button.join-item.btn.btn-sm
       {:data-on:click "@get('/demo/datatable/data?page=next')"
        :disabled on-last?}
       "›"]
      [:button.join-item.btn.btn-sm
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
        filter-val (get-in req [:query-params "filterVal"])
        clear-filters? (some? (get-in req [:query-params "clearFilters"]))
        new-sort (next-sort-state current-sort clicked-column)
        new-filters (update-filters current-filters filter-col filter-val clear-filters?)
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
                                                              :filters new-filters
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
  (apply-text-filter philosophers :name "soc")
  (apply-filters philosophers {:name {:type "text" :value "a"}})
  (apply-filters philosophers {:name {:type "text" :value "a"} :century {:type "text" :value "4th"}})

  ;; Test update-filters
  (update-filters {} "name" "socr" false)
  (update-filters {:name {:type "text" :value "socr"}} "century" "5th" false)
  (update-filters {:name {:type "text" :value "socr"}} "name" "" false)
  (update-filters {:name {:type "text" :value "socr"}} nil nil true)

  ;; Test pagination
  (paginate-data philosophers 5 0)
  (total-pages 15 5)

  ;; Test render-filter-dropdown
  (->html (render-filter-dropdown :name ""))
  (->html (render-filter-dropdown :name "socr"))

  ;; Test render-sortable-header with filters
  (->html (render-sortable-header columns [] {}))
  (->html (render-sortable-header columns [] {:name {:type "text" :value "a"}}))

  ;; Test render-table with filters
  (->html (render-table "test" columns (take 5 philosophers) [] {} 15 10 0))
  (->html (render-table "test" columns (take 5 philosophers) [] {:name {:type "text" :value "a"}} 10 10 0))

  ;; Test page-handler
  (:body (page-handler {})))
