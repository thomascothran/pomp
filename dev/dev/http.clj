(ns dev.http
  (:require [ring.adapter.jetty9 :refer [run-jetty]]
            [reitit.ring :as ring]
            [demo.autocomplete :as dac]))

(defn make-routes
  [config]
  [["/" (fn [req]
          {:status 200
           :body "hi"})]
   ["/hello-world" (constantly {:body "hi"
                                :status 200})]
   ["/demo"
    (dac/make-routes config)]])

(defn app
  [config]
  (ring/ring-handler
   (ring/router (make-routes config))
   (fn [_req]
     {:status 404})))

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
