(ns pomp.graph
  (:require [pomp.rad.graph.handler :as handler]
            [pomp.rad.graph.state :as state]))

(defn canonical-node-id
  ([node]
   (canonical-node-id (:type node) (:id node)))
  ([node-type local-id]
   (state/canonical-node-id node-type local-id)))

(def canonical-edge-id state/canonical-edge-id)
(def dedupe-nodes state/dedupe-nodes)

(defn dedupe-edges
  [edges]
  (:edges
   (reduce (fn [{:keys [seen edges] :as acc} edge]
             (let [edge-id (or (:id edge)
                               (get-in edge [:data :id]))]
               (if (or (nil? edge-id) (contains? seen edge-id))
                 acc
                 {:seen (conj seen edge-id)
                  :edges (conj edges edge)})))
           {:seen #{}
            :edges []}
           edges)))

(defn initialize-graph
  [query _request]
  (state/initialize-graph query))

(defn find-node-neighbors
  [query _request]
  (state/find-node-neighbors query))

(defn make-handlers
  "Creates Ring Datastar handlers for graph init and expansion.

   Returns {:init fn :expand fn}.
   You can override app functions via opts:
   - :initialize-graph
   - :find-node-neighbors"
  ([]
   (make-handlers {}))
  ([opts]
   (handler/make-handlers
    {:initialize-graph (or (:initialize-graph opts) initialize-graph)
     :find-node-neighbors (or (:find-node-neighbors opts) find-node-neighbors)})))
