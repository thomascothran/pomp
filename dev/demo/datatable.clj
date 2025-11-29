(ns demo.datatable
  (:require [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring
             :refer [->sse-response on-open]]
            [demo.util :refer [->html]]))

(comment
  (->html [:h1 "hello world"]))

(defn handler
  [req]
  {:status 500})
