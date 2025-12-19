(ns demo.datatable
  (:require [demo.util :refer [->html page]]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.query.in-memory :as imq]))

(def columns
  [{:key :name :label "Name" :type :string}
   {:key :century :label "Century" :type :string}
   {:key :school :label "School" :type :enum :groupable true}
   {:key :region :label "Region" :type :enum :groupable true}])

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

(def data-url "/demo/datatable/data")

(defn page-handler [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (page
           [:div.p-8
            [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
            [:div#datatable-container
             {:data-init (str "@get('" data-url "')")}
             [:div#datatable]]]))})

(defn make-data-handler
  []
  (datatable/make-handler
   {:id "datatable"
    :columns columns
    :query-fn (imq/query-fn philosophers)
    :data-url data-url
    :render-html-fn ->html
    :page-sizes [10 25 100 250]
    :selectable? true}))

(defn make-routes [_]
  [["/datatable" page-handler]
   ["/datatable/data" (make-data-handler)]])

(comment
  (require '[demo.datatable :as dt] :reload)
  (:body (page-handler {})))
