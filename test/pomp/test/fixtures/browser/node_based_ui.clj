(ns pomp.test.fixtures.browser.node-based-ui
  (:require [dev.http :as dev.http]
            [muuntaja.core :as m]
            [scratch.node-based-ui :as scratch.node-based-ui]))

(def base-url "http://localhost:9393/scratch/node-based-ui")

(def middlewares
  (dev.http/make-middleware))

(def router-data
  {:data {:muuntaja m/instance}})

(def routes
  [["/scratch/node-based-ui" scratch.node-based-ui/handler]
   ["/scratch/node-based-ui/init" scratch.node-based-ui/init-handler]
   ["/scratch/node-based-ui/open" scratch.node-based-ui/open-handler]])
