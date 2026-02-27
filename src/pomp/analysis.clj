(ns pomp.analysis
  "Public entrypoint for analysis chart handlers."
  (:require [pomp.rad.analysis.chart :as analysis.chart]
            [pomp.rad.analysis.board :as analysis.board]
            [pomp.rad.analysis.handler :as analysis.handler]))

(defn make-analysis-fn
  [& args]
  (apply analysis.chart/make-analysis-fn args))

(defn make-chart
  [& args]
  (apply analysis.chart/make-chart args))

(defn frequency-chart
  [& args]
  (apply analysis.chart/frequency-chart args))

(defn pie-chart
  [& args]
  (apply analysis.chart/pie-chart args))

(defn histogram-chart
  [& args]
  (apply analysis.chart/histogram-chart args))

(defn make-chart-handler
  [config]
  (analysis.handler/make-chart-handler config))

(defn make-board
  [& args]
  (apply analysis.board/make-board args))
