(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html page get-signals]]
            [jsonista.core :as j]
            [pomp.rad.datatable.util :as dt-util]
            [pomp.rad.datatable.state :as dt-state]
            [pomp.rad.datatable.table :as dt-table]))

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

(def data-url "/demo/datatable/data")

(defn page-handler [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (page
           [:div.p-8
            {:data-signals "{datatable: {sort: [], page: {size: 10, current: 0}, filters: {}, openFilter: ''}}"}
            [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
            [:div#datatable-container
             {:data-init (str "@get('" data-url "')")}
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
        new-sort (dt-state/next-sort-state current-sort clicked-column)
        new-filters (dt-state/update-filters current-filters filter-col filter-op filter-val clear-filters?)
        filters-to-patch (if clear-filters?
                           (into {} (map (fn [[k _]] [k nil]) current-filters))
                           new-filters)
        filtered-data (dt-util/apply-filters philosophers new-filters)
        sorted-data (dt-util/sort-data filtered-data new-sort)
        total-rows (count sorted-data)
        {:keys [size current]} (dt-state/next-page-state current-page current-size total-rows page-action new-page-size)
        new-page (cond
                   clicked-column 0
                   (or filter-col clear-filters?) 0
                   :else current)
        paginated-data (dt-util/paginate-data sorted-data size new-page)
        is-initial-load? (and (nil? clicked-column) (nil? page-action) (nil? new-page-size)
                              (nil? filter-col) (not clear-filters?))]
    (->sse-response req
                    {on-open
                     (fn [sse]
                       (when is-initial-load?
                         (d*/patch-elements! sse (->html (dt-table/render-skeleton {:id "datatable"
                                                                                    :cols columns
                                                                                    :n 10})))
                         (Thread/sleep 300))
                       (when-not is-initial-load?
                         (d*/patch-signals! sse (j/write-value-as-string
                                                 {:datatable {:sort new-sort
                                                              :page {:size size :current new-page}
                                                              :filters filters-to-patch
                                                              :openFilter ""}})))
                       (d*/patch-elements! sse (->html (dt-table/render {:id "datatable"
                                                                         :cols columns
                                                                         :rows paginated-data
                                                                         :sort-state new-sort
                                                                         :filters new-filters
                                                                         :total-rows total-rows
                                                                         :page-size size
                                                                         :page-current new-page
                                                                         :page-sizes page-sizes
                                                                         :data-url data-url})))
                       (d*/close-sse! sse))})))

(defn make-routes [_]
  [["/datatable" page-handler]
   ["/datatable/data" data-handler]])

(comment
  (require '[demo.datatable :as dt] :reload)
  (require '[demo.util :refer [->html]])

  (dt-util/apply-filters philosophers {:name {:type "text" :op "contains" :value "a"}})

  (:body (page-handler {})))
