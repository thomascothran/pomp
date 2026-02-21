(ns pomp.rad.graph.handler
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [pomp.rad.graph.state :as state]))

(defn- parse-json-string
  [value]
  (when (string? value)
    (let [trimmed (str/trim value)]
      (when (seq trimmed)
        (json/read-str trimmed {:key-fn keyword})))))

(defn- request-signals
  [req]
  (or (when (map? (:body-params req))
        (:body-params req))
      (parse-json-string (get-in req [:query-params "datastar"]))
      (let [signals-raw (d*/get-signals req)]
        (cond
          (map? signals-raw) signals-raw
          (string? signals-raw) (parse-json-string signals-raw)
          (some? signals-raw) (parse-json-string (slurp signals-raw))
          :else nil))
      {}))

(defn- pick-path
  [m paths]
  (some (fn [path]
          (let [value (get-in m path ::missing)]
            (when-not (= value ::missing)
              value)))
        paths))

(defn- normalize-id
  [value]
  (cond
    (string? value) (let [trimmed (str/trim value)]
                      (when (seq trimmed)
                        trimmed))
    (keyword? value) (if-let [kw-ns (namespace value)]
                       (str kw-ns "/" (name value))
                       (name value))
    :else value))

(defn normalize-relation
  [relation]
  (state/normalize-relation relation))

(defn- normalize-anomaly-category
  [category]
  (cond
    (keyword? category) category
    (string? category) (keyword category)
    :else :anomaly/fault))

(defn- normalize-anomaly
  [context result]
  (-> (merge context result)
      (update :anomaly/category normalize-anomaly-category)
      (update :anomaly/message #(or % "Graph request failed"))
      (update :anomaly/data #(or % {}))))

(defn- invoke-app
  [app-fn query req context]
  (try
    (let [result (app-fn query req)]
      (cond
        (and (map? result)
             (:anomaly/category result))
        (normalize-anomaly context result)

        (map? result)
        result

        :else
        (normalize-anomaly context
                           {:anomaly/category :anomaly/fault
                            :anomaly/message "Graph function must return a map"
                            :anomaly/data {:result-type (str (type result))}})))
    (catch Throwable ex
      (normalize-anomaly context
                         {:anomaly/category :anomaly/fault
                          :anomaly/message (or (ex-message ex) "Unhandled graph failure")
                          :anomaly/data {:exception (str (class ex))}}))))

(defn- graph-runtime-bootstrap-script
  []
  (or (some-> (io/resource "public/pomp/js/graph.js")
              slurp)
      ""))

(defn- script-event
  [js-function result]
  (str "(function(){"
       (graph-runtime-bootstrap-script)
       "const result="
       (json/write-str result)
       ";if(typeof window !== 'undefined' && typeof window."
       js-function
       " === 'function'){window."
       js-function
       "(result);}"
       "})();"))

(defn- init-query
  [req]
  (let [signals (request-signals req)
        graph-id (normalize-id (pick-path signals
                                          [[:graph :graph-id]
                                           [:graph :graphId]
                                           [:graph-id]
                                           [:graphId]
                                           [:graph :id]
                                           [:graph "graph-id"]
                                           [:graph "graphId"]
                                           ["graph" "graph-id"]
                                           ["graph" "graphId"]]))
        seed-node-id (normalize-id (pick-path signals
                                              [[:graph :seed-node-id]
                                               [:graph :seedNodeId]
                                               [:seed-node-id]
                                               [:seedNodeId]
                                               [:graph :node-id]
                                               [:graph :nodeId]]))
        relation (normalize-relation (pick-path signals
                                                [[:graph :relation]
                                                 [:relation]
                                                 ["graph" "relation"]
                                                 ["relation"]]))
        filters (pick-path signals
                           [[:graph :filters]
                            [:filters]
                            ["graph" "filters"]
                            ["filters"]])]
    {:graph-id graph-id
     :seed-node-id seed-node-id
     :relation relation
     :filters filters}))

(defn- expand-query
  [req]
  (let [signals (request-signals req)
        expand-request (or (pick-path signals
                                      [[:graph :expandRequest]
                                       [:graph :expand-request]
                                       ["graph" "expandRequest"]
                                       ["graph" "expand-request"]])
                           {})
        graph-id (normalize-id
                  (or (pick-path expand-request
                                 [[:graph-id] [:graphId] ["graph-id"] ["graphId"]])
                      (pick-path signals
                                 [[:graph :graph-id]
                                  [:graph :graphId]
                                  [:graph-id]
                                  [:graphId]])))
        node-id (normalize-id
                 (or (pick-path expand-request
                                [[:node-id] [:nodeId] ["node-id"] ["nodeId"]])
                     (pick-path signals
                                [[:graph :node-id]
                                 [:graph :nodeId]
                                 [:node-id]
                                 [:nodeId]])))
        relation (normalize-relation
                  (or (pick-path expand-request
                                 [[:relation] ["relation"]])
                      (pick-path signals
                                 [[:graph :relation]
                                  [:relation]])))
        filters (or (pick-path expand-request
                               [[:filters] ["filters"]])
                    (pick-path signals
                               [[:graph :filters]
                                [:filters]]))]
    {:graph-id graph-id
     :node-id node-id
     :relation relation
     :filters filters}))

(defn- one-shot-script-handler
  [req script]
  (->sse-response req
                  {on-open
                   (fn [sse]
                     (d*/execute-script! sse script)
                     (d*/close-sse! sse))}))

(defn make-handlers
  [{:keys [initialize-graph find-node-neighbors visual]}]
  {:init (fn [req]
           (let [query (init-query req)
                 context (select-keys query [:graph-id :seed-node-id])
                 result (-> initialize-graph
                            (invoke-app query req context)
                            (assoc :visual visual))]
             (one-shot-script-handler req (script-event "pompInitGraph" result))))

   :expand (fn [req]
             (let [query (expand-query req)
                   context (select-keys query [:graph-id :node-id])
                   result (invoke-app find-node-neighbors query req context)]
               (one-shot-script-handler req (script-event "pompApplyGraphPatch" result))))})
