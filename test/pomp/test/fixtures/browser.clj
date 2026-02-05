(ns pomp.test.fixtures.browser
  (:require [demo.datatable :as demo.datatable]
            [dev.http :as dev.http]
            [etaoin.api :as e]
            [pomp.rad.datatable.query.in-memory :as in-memory]
            [ring.adapter.jetty9 :refer [run-jetty]]))

(def base-url "http://localhost:9393/demo/datatable")

(def ^:dynamic *driver* nil)
(def ^:dynamic *state* nil)

(defonce !server (atom nil))

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

(defn driver-fixture
  [f]
  (let [driver (e/chrome {:headless true})]
    (binding [*driver* driver]
      (try
        (e/set-window-size driver {:width 1280 :height 800})
        (f)
        (finally
          (e/quit driver))))))

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
