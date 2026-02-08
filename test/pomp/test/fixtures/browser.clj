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

(def default-server-port 9393)
(def default-in-memory-server-port 9394)

(def ^:dynamic *driver* nil)
(def ^:dynamic *state* nil)

(defonce !server (atom nil))
(defonce !in-memory-server (atom nil))

(def default-app-handler
  (dev.http/app {}))

(defn stop-server
  []
  (when-let [server @!server]
    (.stop server)
    (reset! !server nil))
  nil)

(defn start-server
  [{:keys [app-handler port] :or {port default-server-port}}]
  (if @!server
    (stop-server)
    (reset! !server
            (run-jetty app-handler
                       {:port port
                        :join? false})))
  (demo.datatable/init-db!)
  (demo.datatable/seed-data!)
  nil)

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

(def default-in-memory-app-handler
  (in-memory-app))

(defn start-in-memory-server
  [{:keys [app-handler port] :or {port default-in-memory-server-port}}]
  (when-not @!in-memory-server
    (reset! !in-memory-server
            (run-jetty app-handler
                       {:port port
                        :join? false})))
  nil)

(defn stop-in-memory-server
  []
  (when-let [server @!in-memory-server]
    (.stop server)
    (reset! !in-memory-server nil))
  nil)

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
  [{:keys [app-handler port] :or {port default-server-port} :as config}]
  (when-not app-handler
    (throw (ex-info "server-fixture requires :app-handler" {:config config})))
  (fn [f]
    (start-server {:app-handler app-handler
                   :port port})
    (try
      (f)
      (finally
        (stop-server)))))

(defn in-memory-server-fixture
  [{:keys [app-handler port] :or {port default-in-memory-server-port} :as config}]
  (when-not app-handler
    (throw (ex-info "in-memory-server-fixture requires :app-handler" {:config config})))
  (fn [f]
    (start-in-memory-server {:app-handler app-handler
                             :port port})
    (try
      (f)
      (finally
        (stop-in-memory-server)))))

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
