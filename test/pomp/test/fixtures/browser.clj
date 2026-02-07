(ns pomp.test.fixtures.browser
  (:require [demo.datatable :as demo.datatable]
            [demo.util :as demo.util]
            [dev.http :as dev.http]
            [etaoin.api :as e]
            [muuntaja.core :as m]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.query.in-memory :as in-memory]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :refer [run-jetty]]))

(def base-url "http://localhost:9393/demo/datatable")
(def in-memory-base-url "http://localhost:9394/demo/datatable-in-memory")

(def ^:dynamic *driver* nil)
(def ^:dynamic *state* nil)

(defonce !server (atom nil))
(defonce !in-memory-server (atom nil))

(defn start-server
  [test-plan]
  (when-not @!server
    (reset! !server
            (run-jetty (fn [req]
                         ((dev.http/app {}) req))
                       {:port 9393
                        :join? false})))
  (demo.datatable/init-db!)
  (demo.datatable/seed-data!)
  test-plan)

(defn stop-server
  [test-plan]
  (when-let [server @!server]
    (.stop server)
    (reset! !server nil))
  test-plan)

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

(defn make-in-memory-data-handler
  []
  (datatable/make-handler
   {:id "datatable"
    :columns demo.datatable/columns
    :query-fn (in-memory/query-fn demo.datatable/philosophers)
    :data-url in-memory-data-url
    :render-html-fn demo.util/->html
    :page-sizes [10 25 100 250]
    :selectable? true}))

(defn in-memory-app
  []
  (ring/ring-handler
   (ring/router
    [["/demo/datatable-in-memory" in-memory-page-handler]
     ["/demo/datatable-in-memory/data" (make-in-memory-data-handler)]]
    {:data {:middleware (dev.http/make-middleware)
            :muuntaja m/instance}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))

(defn start-in-memory-server
  [test-plan]
  (when-not @!in-memory-server
    (reset! !in-memory-server
            (run-jetty (in-memory-app)
                       {:port 9394
                        :join? false})))
  test-plan)

(defn stop-in-memory-server
  [test-plan]
  (when-let [server @!in-memory-server]
    (.stop server)
    (reset! !in-memory-server nil))
  test-plan)

(defn driver-fixture
  [f]
  (let [driver (e/chrome {:headless true})]
    (binding [*driver* driver]
      (try
        (e/set-window-size driver {:width 1280 :height 800})
        (f)
        (finally
          (e/quit driver))))))

(defn server-fixture
  [f]
  (start-server nil)
  (try
    (f)
    (finally
      (stop-server nil))))

(defn in-memory-server-fixture
  [f]
  (start-in-memory-server nil)
  (try
    (f)
    (finally
      (stop-in-memory-server nil))))

(defn datatable-state-fixture
  [f]
  (let [rows demo.datatable/philosophers
        columns demo.datatable/columns
        query-fn (in-memory/query-fn rows)
        state {:columns columns
               :rows rows
               :query-fn query-fn
               :filters {}
               :sort []
               :group-by []
               :page {:size 10 :current 0}}]
    (binding [*state* state]
      (f))))
