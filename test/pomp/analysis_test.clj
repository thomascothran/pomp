(ns pomp.analysis-test
  (:require [clojure.test :refer [deftest is]]))

(deftest make-chart-handler-entrypoint-test
  (let [public-maker (requiring-resolve 'pomp.analysis/make-chart-handler)]
    (is (var? public-maker)
        "Expected public make-chart-handler entrypoint")
    (when (var? public-maker)
      (let [handler ((var-get public-maker)
                     {:analysis/id "analysis"
                      :chart/id "chart"
                      :chart/type :bar
                      :analysis/filter-source-path [:datatable :demo :filters]
                      :analysis-fn (fn [_] {:chart/buckets []})
                      :render-chart-fn identity
                      :render-html-fn (constantly "<div id=\"chart\"></div>")})]
        (is (fn? handler)
            "Expected public entrypoint to delegate to handler factory")))))
