(ns pomp.test.fixtures.browser
  (:require [etaoin.api :as e]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :refer [run-jetty]]))

(def default-server-port 9393)
(def default-in-memory-server-port 9394)

(def ^:dynamic *driver* nil)

(defn- make-handler
  [{:keys [routes middlewares router-data]}]
  (ring/ring-handler
   (ring/router
    routes
    (-> (or router-data {})
        (update :data #(merge {:middleware (or middlewares [])} (or % {})))))
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))

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
  [{:keys [routes port] :or {port default-server-port} :as config}]
  (when-not routes
    (throw (ex-info "server-fixture requires :routes" {:config config})))
  (fn [f]
    (let [server (run-jetty (make-handler config)
                            {:port port
                             :join? false})]
      (try
        (f)
        (finally
          (.stop server))))))

(defn in-memory-server-fixture
  [{:keys [routes port] :or {port default-in-memory-server-port} :as config}]
  (when-not routes
    (throw (ex-info "in-memory-server-fixture requires :routes" {:config config})))
  (fn [f]
    (let [server (run-jetty (make-handler config)
                            {:port port
                             :join? false})]
      (try
        (f)
        (finally
          (.stop server))))))
