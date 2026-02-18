(ns pomp.rad.graph.ui)

(def node-select-event-name "pomp-graph-node-select")
(def node-expand-event-name "pomp-graph-node-expand")
(def default-expand-relation "graph/neighbors")

(def selected-node-id-signal "graph.selectedNodeId")
(def expand-request-signal "graph.expandRequest")
(def pending-by-node-id-signal "graph.pendingByNodeId")
(def error-by-node-id-signal "graph.errorByNodeId")

(def graph-host-signals
  {:selected-node-id selected-node-id-signal
   :expand-request expand-request-signal
   :pending-by-node-id pending-by-node-id-signal
   :error-by-node-id error-by-node-id-signal})

(def graph-id-data-attr :data-pomp-graph-id)
(def graph-canvas-data-attr :data-pomp-graph-canvas)

(def node-select-data-on-attr
  (keyword (str "data-on:" node-select-event-name)))

(def node-expand-data-on-attr
  (keyword (str "data-on:" node-expand-event-name)))

(defn data-on-attr
  [event-name expression]
  {(keyword (str "data-on:" event-name)) expression})

(defn host-event-attrs
  [{:keys [on-select on-expand]}]
  (merge
   (when on-select
     (data-on-attr node-select-event-name on-select))
   (when on-expand
     (data-on-attr node-expand-event-name on-expand))))

(defn host-data-attrs
  [graph-id]
  {graph-id-data-attr graph-id})

(defn canvas-data-attrs
  []
  {graph-canvas-data-attr "true"})
