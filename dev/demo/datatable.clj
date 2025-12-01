(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html page get-signals]]
            [jsonista.core :as j]))

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

(defn sort-indicator [sort-state column-key]
  (let [current (first sort-state)
        col-name (name column-key)]
    (when (= (:column current) col-name)
      (if (= (:direction current) "asc") "▲" "▼"))))

(defn render-sortable-header [cols sort-state]
  [:thead
   [:tr
    (for [{:keys [key label]} cols]
      [:th.cursor-pointer.select-none.hover:bg-base-200
       {:data-on:click (format "@get('/demo/datatable/data?clicked=%s')" (name key))}
       [:span.flex.items-center.gap-1
        label
        (when-let [indicator (sort-indicator sort-state key)]
          [:span.text-xs indicator])]])]])

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

(defn render-table [id cols rows sort-state]
  [:div {:id id :class "overflow-x-auto"}
   [:table.table.table-sm
    (render-sortable-header cols sort-state)
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
        current-sort (get-in signals [:datatable :sort] [])
        clicked-column (get-in req [:query-params "clicked"])
        new-sort (next-sort-state current-sort clicked-column)
        sorted-data (sort-data philosophers new-sort)]
    (->sse-response req
                    {on-open
                     (fn [sse]
                       (when-not clicked-column
                         (d*/patch-elements! sse (->html (render-skeleton-table "datatable" columns 10)))
                         (Thread/sleep 300))
                       (when clicked-column
                         (d*/patch-signals! sse (j/write-value-as-string {:datatable {:sort new-sort}})))
                       (d*/patch-elements! sse (->html (render-table "datatable" columns sorted-data new-sort)))
                       (d*/close-sse! sse))})))

(defn make-routes [_]
  [["/datatable" page-handler]
   ["/datatable/data" data-handler]])

(comment
  (require '[demo.datatable :as dt] :reload)
  (require '[demo.util :refer [->html]])

  ;; Test next-sort-state
  (next-sort-state [] nil)
  (next-sort-state [] "name")
  (next-sort-state [{:column "name" :direction "asc"}] "name")
  (next-sort-state [{:column "name" :direction "desc"}] "name")
  (next-sort-state [{:column "name" :direction "asc"}] "region")

  ;; Test sort-indicator
  (sort-indicator [] :name)
  (sort-indicator [{:column "name" :direction "asc"}] :name)
  (sort-indicator [{:column "name" :direction "desc"}] :name)
  (sort-indicator [{:column "name" :direction "asc"}] :region)

  ;; Test sort-data
  (map :name (sort-data philosophers []))
  (map :name (sort-data philosophers [{:column "name" :direction "asc"}]))
  (map :name (sort-data philosophers [{:column "name" :direction "desc"}]))

  ;; Test render-sortable-header
  (->html (render-sortable-header columns []))
  (->html (render-sortable-header columns [{:column "name" :direction "asc"}]))

  ;; Test render-table with sort state
  (->html (render-table "test" columns (take 3 philosophers) []))
  (->html (render-table "test" columns (take 3 (sort-data philosophers [{:column "name" :direction "asc"}]))
                        [{:column "name" :direction "asc"}]))

  ;; Test page-handler
  (:body (page-handler {})))
