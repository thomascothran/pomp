(ns pomp.test.fixtures.browser.datatable
  (:require [demo.datatable :as demo.datatable]
            [demo.util :as demo.util]
            [dev.http :as dev.http]
            [muuntaja.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [pomp.rad.datatable :as datatable]
            [pomp.rad.datatable.ui.table :as table]
            [pomp.rad.datatable.query.in-memory :as in-memory]
            [pomp.rad.datatable.query.sql :as sqlq]))

(def base-url "http://localhost:9393/demo/datatable")
(def in-memory-base-url "http://localhost:9394/demo/datatable-in-memory")
(def visibility-base-url "http://localhost:9393/demo/datatable-visibility")

(def middlewares
  (dev.http/make-middleware))

(def router-data
  {:data {:muuntaja m/instance}})

(def routes
  [["/demo" (demo.datatable/make-routes {})]])

(def in-memory-data-url "/demo/datatable-in-memory/data")
(def visibility-data-url "/demo/datatable-visibility/data")

(defn in-memory-page-handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (demo.util/->html
          (demo.util/page
           [:div.p-8
            [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
            [:div#datatable-container
             {:data-init (str "@get('" in-memory-data-url "')")}
             [:div#datatable]]]))})

(defn make-in-memory-data-handlers
  []
  (datatable/make-handlers
   {:id "datatable"
    :columns demo.datatable/columns
    :rows-fn (in-memory/rows-fn demo.datatable/philosophers)
    :count-fn (in-memory/count-fn demo.datatable/philosophers)
    :data-url in-memory-data-url
    :render-html-fn demo.util/->html
    :page-sizes [10 25 100 250]
    :selectable? true}))

(def in-memory-routes
  (let [{:keys [get post]} (make-in-memory-data-handlers)]
    [["/demo/datatable-in-memory" in-memory-page-handler]
     ["/demo/datatable-in-memory/data" {:get get
                                        :post post}]]))

(defn- scope->where-clauses
  [scope]
  (case scope
    "rome" [["region = ?" "Rome"]]
    "greece" [["region = ?" "Greece"]]
    nil nil
    [["1 = 0"]]))

(defn- visibility-fn
  [_query-signals request]
  (when-let [where-clauses (scope->where-clauses (get-in request [:query-params "scope"]))]
    {:where-clauses where-clauses}))

(defn- visibility-page-handler
  [req]
  (let [scope (get-in req [:query-params "scope"])
        data-url (if scope
                   (str visibility-data-url "?scope=" scope)
                   visibility-data-url)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (demo.util/->html
            (demo.util/page
             [:div.p-8
              [:h1.text-2xl.font-bold.mb-4 "Philosophers Visibility"]
              [:div#datatable-container
               {:data-init (str "@get('" data-url "')")}
               [:div#datatable]]]))}))

(defn- make-visibility-data-handlers
  [data-url]
  (let [ds (demo.datatable/get-datasource)
        execute! (fn [sqlvec]
                   (jdbc/execute! ds sqlvec
                                  {:builder-fn rs/as-unqualified-lower-maps}))
        table-search-rows (sqlq/rows-fn {:table-name "philosophers"
                                         :visibility-fn visibility-fn}
                                        execute!)
        table-count (sqlq/count-fn {:table-name "philosophers"
                                    :visibility-fn visibility-fn}
                                   execute!)
        stream-adapter! (fn [sqlvec on-row! on-complete!]
                          (let [row-count (volatile! 0)]
                            (reduce (fn [_ row]
                                      (vswap! row-count inc)
                                      (on-row! (into {} row)))
                                    nil
                                    (jdbc/plan ds sqlvec
                                               {:builder-fn rs/as-unqualified-lower-maps}))
                            (on-complete! {:row-count @row-count})))
        stream-rows! (sqlq/stream-rows-fn {:table-name "philosophers"
                                           :visibility-fn visibility-fn}
                                          stream-adapter!)]
    (datatable/make-handlers
     {:id "datatable"
      :columns demo.datatable/columns
      :rows-fn table-search-rows
      :count-fn table-count
      :export-stream-rows-fn stream-rows!
      :data-url data-url
      :render-html-fn demo.util/->html
      :render-table-search table/default-render-table-search
      :page-sizes [10 25 100 250]
      :selectable? true})))

(defn- visibility-data-handler
  [method req]
  (let [scope (get-in req [:query-params "scope"])
        data-url (if scope
                   (str visibility-data-url "?scope=" scope)
                   visibility-data-url)
        handlers (make-visibility-data-handlers data-url)]
    ((method handlers) req)))

(def visibility-routes
  [["/demo/datatable-visibility" visibility-page-handler]
   ["/demo/datatable-visibility/data" {:get (fn [req] (visibility-data-handler :get req))
                                       :post (fn [req] (visibility-data-handler :post req))}]])

(def ^:dynamic *state* nil)

(defn datatable-state-fixture
  [f]
  (let [rows demo.datatable/philosophers
        columns demo.datatable/columns
        rows-fn (in-memory/rows-fn rows)
        count-fn (in-memory/count-fn rows)
        state {:columns columns
               :rows rows
               :rows-fn rows-fn
               :count-fn count-fn
               :filters {}
               :sort []
               :group-by []
               :page {:size 10 :current 0}}]
    (binding [*state* state]
      (f))))

(defn datatable-db-fixture
  [f]
  (demo.datatable/init-db!)
  (demo.datatable/seed-data!)
  (f))
