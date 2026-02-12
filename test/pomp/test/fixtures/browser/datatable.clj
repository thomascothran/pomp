(ns pomp.test.fixtures.browser.datatable
  (:require [demo.datatable :as demo.datatable]
            [demo.util :as demo.util]
            [dev.http :as dev.http]
            [muuntaja.core :as m]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.query.in-memory :as in-memory]))

(def base-url "http://localhost:9393/demo/datatable")
(def in-memory-base-url "http://localhost:9394/demo/datatable-in-memory")

(def middlewares
  (dev.http/make-middleware))

(def router-data
  {:data {:muuntaja m/instance}})

(def routes
  [["/demo" (demo.datatable/make-routes {})]])

(def in-memory-data-url "/demo/datatable-in-memory/data")

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
