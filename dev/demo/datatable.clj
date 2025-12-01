(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html page get-signals]]))

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

(defn sort-data [rows sort-spec]
  (if (empty? sort-spec)
    rows
    (let [{:keys [column direction]} (first sort-spec)
          col-key (keyword column)
          comparator (if (= direction "asc")
                       compare
                       #(compare %2 %1))]
      (sort-by #(get % col-key) comparator rows))))

(defn sort-click-expression [column-key]
  (let [col-name (name column-key)]
    (str "(() => {"
         "const sort = $datatable.sort;"
         "const current = sort[0];"
         (format "if (!current || current.column !== '%s') { $datatable.sort = [{column: '%s', direction: 'asc'}]; }"
                 col-name col-name)
         (format "else if (current.direction === 'asc') { $datatable.sort = [{column: '%s', direction: 'desc'}]; }"
                 col-name)
         "else { $datatable.sort = []; }"
         "})(); @get('/demo/datatable/data')")))

(defn sort-indicator-expression [column-key]
  (let [col-name (name column-key)]
    (format "((() => { const s = $datatable.sort[0]; if (s && s.column === '%s') return s.direction === 'asc' ? '▲' : '▼'; return ''; })())"
            col-name)))

(defn render-sortable-header [cols]
  [:thead
   [:tr
    (for [{:keys [key label]} cols]
      [:th.cursor-pointer.select-none.hover:bg-base-200
       {:data-on:click (sort-click-expression key)}
       [:span.flex.items-center.gap-1
        label
        [:span.text-xs {:data-text (sort-indicator-expression key)}]]])]])

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
    (render-sortable-header cols)
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
            {:data-signals "{datatable: {sort: []}}"}
            [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
            [:div#datatable-container
             {:data-init "@get('/demo/datatable/data')"}
             [:div#datatable]]]))})

(defn data-handler [req]
  (let [signals (get-signals req)
        sort-spec (get-in signals [:datatable :sort] [])]
    (->sse-response req
                    {on-open
                     (fn [sse]
                       (d*/patch-elements! sse (->html (render-skeleton-table "datatable" columns 10)))
                       (Thread/sleep 300)
                       (let [sorted-data (sort-data philosophers sort-spec)]
                         (d*/patch-elements! sse (->html (render-table "datatable" columns sorted-data))))
                       (d*/close-sse! sse))})))

(defn make-routes [_]
  [["/datatable" page-handler]
   ["/datatable/data" data-handler]])

(comment
  (require '[demo.datatable :as dt] :reload)
  (require '[demo.util :refer [->html]])

  ;; Test sort-data
  (map :name (sort-data philosophers []))
  (map :name (sort-data philosophers [{:column "name" :direction "asc"}]))
  (map :name (sort-data philosophers [{:column "name" :direction "desc"}]))
  (map :region (sort-data philosophers [{:column "region" :direction "asc"}]))

  ;; Test sort-click-expression
  (sort-click-expression :name)

  ;; Test sort-indicator-expression  
  (sort-indicator-expression :name)

  ;; Test render-sortable-header
  (->html (render-sortable-header columns))

  ;; Test render-table with sorted data
  (->html (render-table "test" columns (take 3 (sort-data philosophers [{:column "name" :direction "asc"}]))))

  ;; Test page-handler
  (:body (page-handler {})))
