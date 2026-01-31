(ns dev.http
  (:require [ring.adapter.jetty9 :refer [run-jetty]]
            [reitit.ring :as ring]
            [demo.autocomplete :as dac]
            [demo.datatable :as ddt]
            [reitit.ring.middleware.dev :as dev]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [dev.logger :as log]
            [starfederation.datastar.clojure.api :as d*]))

(defn make-routes
  [config]
  [["/" (fn [req]
          {:status 200
           :body "hi"})]
   ["/hello-world" (constantly {:body "hi"
                                :status 200})]
   ["/demo"
    (dac/make-routes config)
    (ddt/make-routes config)]
   ["/assets/*" (ring/create-resource-handler)]])

(defn make-middleware
  []
  [parameters/parameters-middleware
   muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   exception/exception-middleware
   muuntaja/format-request-middleware
   coercion/coerce-response-middleware
   coercion/coerce-request-middleware
   multipart/multipart-middleware
   log/params-middleware])

(defn app
  [config]
  (ring/ring-handler
   (ring/router
    (make-routes config)
    {:data {:middleware (make-middleware)
            :muuntaja m/instance}
     :exception pretty/exception
     :reitit.middleware/transform dev/print-request-diffs})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))

(defonce !server
  (atom nil))

(defn start []
  (reset! !server
          (run-jetty (fn [req]
                       ((app {}) req))
                     {:port 3000, :join? false}))
  (println "server running in port 3000"))

(comment
  (start))
