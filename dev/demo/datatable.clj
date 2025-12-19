(ns demo.datatable
  (:require [demo.util :refer [->html page]]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.query.in-memory :as imq]))

(defn format-century
  "Formats a numeric century as a human-readable string.
   Negative values are BC, positive are AD.
   e.g., -5 -> \"5th BC\", 17 -> \"17th AD\""
  [row]
  (let [n (:century row)
        abs-n (abs n)
        suffix (case (mod abs-n 10)
                 1 (if (= (mod abs-n 100) 11) "th" "st")
                 2 (if (= (mod abs-n 100) 12) "th" "nd")
                 3 (if (= (mod abs-n 100) 13) "th" "rd")
                 "th")
        era (if (neg? n) " BC" " AD")]
    (str abs-n suffix era)))

(def columns
  [{:key :name :label "Name" :type :string}
   {:key :century :label "Century" :type :number :display-fn format-century}
   {:key :school :label "School" :type :enum :groupable true}
   {:key :region :label "Region" :type :enum :groupable true}])

(def philosophers
  [{:id 1 :name "Socrates" :century -5 :school "Classical Greek" :region "Greece"}
   {:id 2 :name "Plato" :century -4 :school "Platonism" :region "Greece"}
   {:id 3 :name "Aristotle" :century -4 :school "Peripatetic" :region "Greece"}
   {:id 4 :name "Confucius" :century -5 :school "Confucianism" :region "China"}
   {:id 5 :name "Laozi" :century -6 :school "Taoism" :region "China"}
   {:id 6 :name "Epicurus" :century -3 :school "Epicureanism" :region "Greece"}
   {:id 7 :name "Zeno of Citium" :century -3 :school "Stoicism" :region "Greece"}
   {:id 8 :name "Marcus Aurelius" :century 2 :school "Stoicism" :region "Rome"}
   {:id 9 :name "Seneca" :century 1 :school "Stoicism" :region "Rome"}
   {:id 10 :name "Augustine" :century 4 :school "Christian Platonism" :region "North Africa"}
   {:id 11 :name "Thomas Aquinas" :century 13 :school "Scholasticism" :region "Italy"}
   {:id 12 :name "René Descartes" :century 17 :school "Rationalism" :region "France"}
   {:id 13 :name "John Locke" :century 17 :school "Empiricism" :region "England"}
   {:id 14 :name "Immanuel Kant" :century 18 :school "German Idealism" :region "Germany"}
   {:id 15 :name "Friedrich Nietzsche" :century 19 :school "Existentialism" :region "Germany"}])

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
