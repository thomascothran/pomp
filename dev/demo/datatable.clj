(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html page]]))

(def columns
  [{:key :name :label "Name"}
   {:key :century :label "Century"}
   {:key :school :label "School"}
   {:key :region :label "Region"}])

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

(defn render-table [id cols rows]
  [:div {:id id :class "overflow-x-auto"}
   [:table.table.table-sm
    (render-table-header cols)
    (render-table-body cols rows)]])

(defn render-skeleton-row [cols]
  [:tr
   (for [_ cols]
     [:td [:div.skeleton.h-4.w-full]])])

(defn render-skeleton-body [cols n]
  [:tbody
   (for [_ (range n)]
     (render-skeleton-row cols))])

(defn render-skeleton-table [id cols n]
  [:div {:id id :class "overflow-x-auto"}
   [:table.table.table-sm
    (render-table-header cols)
    (render-skeleton-body cols n)]])

(defn page-handler [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (page
           [:div.p-8
            [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
            [:div#datatable-container
             {:data-init "@get('/demo/datatable/data')"}
             [:div#datatable]]]))})

(defn data-handler [req]
  (->sse-response req
                  {on-open
                   (fn [sse]
                     (d*/patch-elements! sse (->html (render-skeleton-table "datatable" columns 10)))
                     (Thread/sleep 500)
                     (d*/patch-elements! sse (->html (render-table "datatable" columns philosophers)))
                     (d*/close-sse! sse))}))

(defn make-routes [_]
  [["/datatable" page-handler]
   ["/datatable/data" data-handler]])
