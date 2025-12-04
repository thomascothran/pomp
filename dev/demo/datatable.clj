(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html page get-signals]]
            [jsonista.core :as j]
            [pomp.rad.datatable.filter-menu :as dt-filter]
            [pomp.rad.datatable.table :as dt-table]
            [pomp.rad.datatable.in-memory-query :as dt-imq]))

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
  (let [query-params (:query-params req)
        current-signals (get-in (get-signals req) [:datatable] {})
        initial-load? (empty? query-params)
        query-fn (dt-imq/query-fn philosophers)
        {:keys [signals rows total-rows]} (dt-table/query current-signals query-params query-fn)
        filters-patch (dt-filter/compute-patch (:filters current-signals) (:filters signals))]
    (->sse-response req
                    {on-open
                     (fn [sse]
                       (when initial-load?
                         (d*/patch-elements! sse (->html (dt-table/render-skeleton {:id "datatable"
                                                                                    :cols columns
                                                                                    :n 10})))
                         (Thread/sleep 300))
                       (when-not initial-load?
                         (d*/patch-signals! sse (j/write-value-as-string
                                                 {:datatable {:sort (:sort signals)
                                                              :page (:page signals)
                                                              :filters filters-patch
                                                              :openFilter ""}})))
                       (d*/patch-elements! sse (->html (dt-table/render {:id "datatable"
                                                                         :cols columns
                                                                         :rows rows
                                                                         :sort-state (:sort signals)
                                                                         :filters (:filters signals)
                                                                         :total-rows total-rows
                                                                         :page-size (get-in signals [:page :size])
                                                                         :page-current (get-in signals [:page :current])
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
