(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html page get-signals]]
            [jsonista.core :as j]
            [pomp.rad.datatable.core :as dt]
            [pomp.rad.datatable.state.column :as column-state]
            [pomp.rad.datatable.state.group :as group-state]
            [pomp.rad.datatable.state.filter :as filter-state]
            [pomp.rad.datatable.ui.columns-menu :as columns-menu]))

(def columns
  [{:key :name :label "Name" :type :text}
   {:key :century :label "Century" :type :text}
   {:key :school :label "School" :type :enum}
   {:key :region :label "Region" :type :enum}])

(def philosophers
  [{:id 1 :name "Socrates" :century "5th BC" :school "Classical Greek" :region "Greece"}
   {:id 2 :name "Plato" :century "4th BC" :school "Platonism" :region "Greece"}
   {:id 3 :name "Aristotle" :century "4th BC" :school "Peripatetic" :region "Greece"}
   {:id 4 :name "Confucius" :century "5th BC" :school "Confucianism" :region "China"}
   {:id 5 :name "Laozi" :century "6th BC" :school "Taoism" :region "China"}
   {:id 6 :name "Epicurus" :century "3rd BC" :school "Epicureanism" :region "Greece"}
   {:id 7 :name "Zeno of Citium" :century "3rd BC" :school "Stoicism" :region "Greece"}
   {:id 8 :name "Marcus Aurelius" :century "2nd" :school "Stoicism" :region "Rome"}
   {:id 9 :name "Seneca" :century "1st" :school "Stoicism" :region "Rome"}
   {:id 10 :name "Augustine" :century "4th" :school "Christian Platonism" :region "North Africa"}
   {:id 11 :name "Thomas Aquinas" :century "13th" :school "Scholasticism" :region "Italy"}
   {:id 12 :name "René Descartes" :century "17th" :school "Rationalism" :region "France"}
   {:id 13 :name "John Locke" :century "17th" :school "Empiricism" :region "England"}
   {:id 14 :name "Immanuel Kant" :century "18th" :school "German Idealism" :region "Germany"}
   {:id 15 :name "Friedrich Nietzsche" :century "19th" :school "Existentialism" :region "Germany"}])

(def page-sizes [10 25 100 250])

(def data-url "/demo/datatable/data")

(defn page-handler [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (page
           [:div.p-8
            {:data-signals "{datatable: {sort: [], page: {size: 10, current: 0}, filters: {}, groupBy: [], openFilter: '', columnOrder: ['name', 'century', 'school', 'region'], columns: {name: {visible: true}, century: {visible: true}, school: {visible: true}, region: {visible: true}}, dragging: null, dragOver: null, expanded: {}}}"}
            [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
            [:div#datatable-container
             {:data-init (str "@get('" data-url "')")}
             [:div#datatable]]]))})

(defn data-handler [req]
  (let [query-params (:query-params req)
        raw-signals (get-in (get-signals req) [:datatable] {})
        current-signals (-> raw-signals
                            (assoc :group-by (mapv keyword (:groupBy raw-signals))))
        columns-state (:columns current-signals)
        initial-load? (empty? query-params)
        column-order (column-state/next-state (:columnOrder current-signals) columns query-params)
        ordered-cols (column-state/reorder columns column-order)
        visible-cols (column-state/filter-visible ordered-cols columns-state)
        query-fn (dt/query-fn philosophers)
        {:keys [signals rows total-rows]} (dt/query current-signals query-params query-fn)
        group-by (:group-by signals)
        groups (when (seq group-by) (group-state/group-rows rows group-by))
        filters-patch (filter-state/compute-patch (:filters current-signals) (:filters signals))]
    (->sse-response req
                    {on-open
                     (fn [sse]
                       (when initial-load?
                         (d*/patch-elements! sse (->html (dt/render-skeleton {:id "datatable"
                                                                              :cols visible-cols
                                                                              :n 10
                                                                              :selectable? true})))
                         (Thread/sleep 300))
                       (d*/patch-signals! sse (j/write-value-as-string
                                               {:datatable {:sort (:sort signals)
                                                            :page (:page signals)
                                                            :filters filters-patch
                                                            :groupBy (mapv name group-by)
                                                            :openFilter ""
                                                            :columnOrder column-order
                                                            :dragging nil
                                                            :dragOver nil}}))
                       (d*/patch-elements! sse (->html (dt/render {:id "datatable"
                                                                   :cols visible-cols
                                                                   :rows rows
                                                                   :groups groups
                                                                   :sort-state (:sort signals)
                                                                   :filters (:filters signals)
                                                                   :group-by group-by
                                                                   :total-rows total-rows
                                                                   :page-size (get-in signals [:page :size])
                                                                   :page-current (get-in signals [:page :current])
                                                                   :page-sizes page-sizes
                                                                   :data-url data-url
                                                                   :selectable? true
                                                                   :toolbar (columns-menu/render {:cols ordered-cols
                                                                                                  :columns-state columns-state
                                                                                                  :table-id "datatable"
                                                                                                  :data-url data-url})})))
                       (d*/close-sse! sse))})))

(defn make-routes [_]
  [["/datatable" page-handler]
   ["/datatable/data" data-handler]])

(comment
  (require '[demo.datatable :as dt] :reload)
  (require '[demo.util :refer [->html]])

  (:body (page-handler {})))
