(ns pomp.analysis
  "Public entrypoint for analysis chart handlers."
  (:require [pomp.rad.analysis.handler :as analysis.handler]))

(defn make-chart-handler
  [config]
  (analysis.handler/make-chart-handler config))
