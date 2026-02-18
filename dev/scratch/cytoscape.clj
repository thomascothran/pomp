(ns scratch.cytoscape
  (:require [dev.onionpancakes.chassis.core :as c]
            [pomp.graph :as graph]))

(def graph-id "scratch-cytoscape")
(def seed-node-id "project:apollo")

(def ^:private graph-handlers
  (graph/make-handlers))

(def init-handler
  (:init graph-handlers))

(def expand-handler
  (:expand graph-handlers))

(defn page
  [& children]
  [:html {:data-theme "light"}
   [:head
    [:link {:href "/assets/output.css"
            :rel "stylesheet"}]
    [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.29.2/cytoscape.min.js"}]
    [:script {:type "module"
              :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"}]]
   [:body {:class "min-h-screen m-0 bg-base-200 text-base-content"}
    children]])

(defn handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body
   (c/html
    (page
     [:main {:class "mx-auto flex min-h-screen w-full min-w-[1100px] max-w-[1440px] flex-col gap-4 p-6"}
      [:header {:class "space-y-2"}
       [:h1 {:class "text-3xl font-semibold tracking-tight"} "Cytoscape Scratch Demo"]
       [:p {:class "text-sm text-base-content/70"}
        "Server-driven Cytoscape exploration with Datastar event wiring."]]
      [:section {:class "grid flex-1 grid-cols-[1fr_360px] gap-4"}
       [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
         [:div {:class "card-body p-4"}
          [:div {:id "scratch-cytoscape-host"
                 :class "relative h-[640px] rounded-box border border-base-300 bg-base-200"
                 :data-pomp-graph-id graph-id
                 :data-signals (str "{graph: {graphId: '" graph-id
                                    "', seedNodeId: '" seed-node-id
                                    "', relation: 'graph/neighbors', selectedNodeId: null, expandRequest: null, pendingByNodeId: {}, errorByNodeId: {}}}")
                 :data-init "@post('/scratch/cytoscape/init')"
                 :data-on:pomp-graph-node-select "$graph.selectedNodeId = (evt && evt.detail && evt.detail.nodeId) ? evt.detail.nodeId : null"
                  :data-on:pomp-graph-node-expand "const _detail = (evt && evt.detail) || {}; const _graphId = _detail.graphId || $graph.graphId; const _relation = _detail.relation || $graph.relation || 'graph/neighbors'; $graph.expandRequest = { graphId: _graphId, nodeId: _detail.nodeId || null, relation: _relation }; @post('/scratch/cytoscape/expand', { requestCancellation: 'auto' })"}
           [:div {:id "scratch-cytoscape-canvas"
                  :class "h-full w-full rounded-box"
                  :data-pomp-graph-canvas "true"}]
           [:div {:class "pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-base-content/45"}
            "Graph runtime loads on init if Cytoscape is available"]]]]
       [:aside {:class "space-y-4"}
        [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
         [:div {:class "card-body gap-3 p-4"}
          [:h2 {:class "card-title text-base"} "Details"]
          [:div {:class "rounded-box border border-base-300 bg-base-200/70 p-3"}
           [:div {:class "text-xs uppercase tracking-wide text-base-content/60"} "Selected node id"]
            [:div {:class "mt-1 font-mono text-sm"
                   :id "cy-selected-node-id"
                   :data-text "$graph.selectedNodeId || 'none'"}]]
           [:div {:class "rounded-box border border-base-300 bg-base-200/70 p-3"}
            [:div {:class "text-xs uppercase tracking-wide text-base-content/60"} "Expand request"]
            [:div {:class "mt-1 text-sm"
                   :id "cy-expand-request"
                   :data-text "$graph.expandRequest ? JSON.stringify($graph.expandRequest) : 'none'"}]]]]
        [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
         [:div {:class "card-body gap-3 p-4"}
          [:h2 {:class "card-title text-base"} "Legend + Status"]
          [:div {:class "flex flex-wrap gap-2"}
           [:span {:class "badge badge-outline"} "idle"]
           [:span {:class "badge badge-info badge-outline"} "selected"]
           [:span {:class "badge badge-warning badge-outline"} "pending"]
           [:span {:class "badge badge-error badge-outline"} "error"]]
          [:div {:class "rounded-box border border-base-300 bg-base-200/70 p-3 text-sm"}
           [:span {:class "text-base-content/70"} "Pending nodes: "]
           [:span {:class "font-semibold"
                   :id "cy-pending-count"
                    :data-text "Object.keys($graph.pendingByNodeId || {}).length"}]]]]]]]))})
