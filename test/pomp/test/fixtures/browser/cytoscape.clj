(ns pomp.test.fixtures.browser.cytoscape
  (:require [muuntaja.core :as m]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [scratch.cytoscape :as scratch.cytoscape]))

(def base-url "http://localhost:9393/scratch/cytoscape")

(def middlewares
  [parameters/parameters-middleware
   muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   muuntaja/format-request-middleware
   multipart/multipart-middleware])

(def router-data
  {:data {:muuntaja m/instance}})

(def routes
  [["/scratch/cytoscape" scratch.cytoscape/handler]
   ["/scratch/cytoscape/init" scratch.cytoscape/init-handler]
   ["/scratch/cytoscape/expand" scratch.cytoscape/expand-handler]])
