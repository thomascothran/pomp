(ns scratch.cytoscape
  (:require [dev.onionpancakes.chassis.core :as c]
            [pomp.graph :as graph]))

(def graph-id "scratch-cytoscape")
(def seed-node-id "project:apollo")

(def ^:private node-type-legend
  [{:type "project"
    :marker-style "border-radius: 9999px; background-color: #2563eb; border-color: #1d4ed8;"}
   {:type "story"
    :marker-style "border-radius: 0.375rem; background-color: #0f766e; border-color: #115e59;"}
   {:type "task"
    :marker-style "border-radius: 0.125rem; background-color: #ea580c; border-color: #c2410c;"}
   {:type "subtask"
    :marker-style "border-radius: 0.125rem; transform: rotate(45deg); background-color: #7c3aed; border-color: #6d28d9;"}
   {:type "developer"
    :marker-style "clip-path: polygon(25% 6%, 75% 6%, 100% 50%, 75% 94%, 25% 94%, 0 50%); background-color: #0891b2; border-color: #0e7490;"}
   {:type "qa"
    :marker-style "clip-path: polygon(0 0, 50% 92%, 100% 0, 74% 0, 50% 44%, 26% 0); background-color: #65a30d; border-color: #4d7c0f;"}
   {:type "product-owner"
    :marker-style "clip-path: polygon(0 0, 74% 0, 100% 50%, 74% 100%, 0 100%); background-color: #be123c; border-color: #9f1239;"}])

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
                 :style "height: 640px;"
                 :data-pomp-graph-id graph-id
                   :data-signals (str "{graph: {graphId: '" graph-id
                                      "', seedNodeId: '" seed-node-id
                                      "', relation: 'graph/neighbors', selectedNode: null, selectedNodePropertiesText: 'none', expandRequest: null, pendingByNodeId: {}, errorByNodeId: {}}}")
                  :data-init "@post('/scratch/cytoscape/init')"
                  :data-on:pomp-graph-node-select "const _detail = (evt && evt.detail) || {}; const _node = _detail.node || null; const _rawProperties = _node && _node.properties; let _properties = null; if (_rawProperties && typeof _rawProperties === 'string') { try { const _parsed = JSON.parse(_rawProperties); if (_parsed && typeof _parsed === 'object' && !Array.isArray(_parsed)) { _properties = _parsed; } } catch (_e) {} } else if (_rawProperties && typeof _rawProperties === 'object' && !Array.isArray(_rawProperties)) { _properties = _rawProperties; } const _escapeHtml = (value) => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;').replace(/'/g, '&#39;'); const _labelClass = 'text-xs uppercase tracking-wide text-base-content/60'; const _valueClass = 'font-mono text-sm'; let _rows = ''; if (_properties) { const _keys = Object.keys(_properties).sort(); for (let i = 0; i < _keys.length; i += 1) { const _key = _keys[i]; _rows += \"<div class='space-y-0.5'><div class='\" + _labelClass + \"'>\" + _escapeHtml(_key) + \"</div><div class='\" + _valueClass + \"'>\" + _escapeHtml(_properties[_key]) + \"</div></div>\"; } } if (!_rows && typeof _detail.nodePropertiesText === 'string') { const _lines = _detail.nodePropertiesText.split('\\n'); for (let i = 0; i < _lines.length; i += 1) { const _line = _lines[i].trim(); if (!_line) continue; const _idx = _line.indexOf('->'); if (_idx < 0) continue; const _key = _line.slice(0, _idx).trim(); if (!_key) continue; const _value = _line.slice(_idx + 2).trim(); _rows += \"<div class='space-y-0.5'><div class='\" + _labelClass + \"'>\" + _escapeHtml(_key) + \"</div><div class='\" + _valueClass + \"'>\" + _escapeHtml(_value) + \"</div></div>\"; } } if (!_rows) { _rows = \"<div class='space-y-0.5'><div class='text-xs uppercase tracking-wide text-base-content/60'>No properties</div><div class='font-mono text-sm'>none</div></div>\"; } $graph.selectedNodeId = _detail.nodeId || null; $graph.selectedNode = _node; const _el = document.getElementById('cy-selected-node-properties'); if (_el) { _el.innerHTML = _rows; }"
                  :data-on:pomp-graph-node-expand "const _detail = (evt && evt.detail) || {}; const _graphId = _detail.graphId || $graph.graphId; const _relation = _detail.relation || $graph.relation || 'graph/neighbors'; $graph.expandRequest = { graphId: _graphId, nodeId: _detail.nodeId || null, relation: _relation }; @post('/scratch/cytoscape/expand', { requestCancellation: 'auto' })"}
           [:div {:id "scratch-cytoscape-canvas"
                  :class "h-full w-full rounded-box"
                  :data-pomp-graph-canvas "true"}]]]]
        [:aside {:class "space-y-4"}
         [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
           [:div {:class "card-body gap-2 p-4"}
            [:h2 {:class "card-title text-base"} "Details"]
             [:div {:class "mt-2 max-h-48 space-y-2 overflow-auto"
                    :id "cy-selected-node-properties"}
              [:div {:class "space-y-0.5"}
               [:div {:class "text-xs uppercase tracking-wide text-base-content/60"} "No properties"]
               [:div {:class "font-mono text-sm"} "none"]]]]]
         [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
          [:div {:class "card-body gap-3 p-4"}
           [:h2 {:class "card-title text-base"} "Node Type Legend + Status"]
           [:div {:class "grid grid-cols-2 gap-x-4 gap-y-2 text-sm"}
            (for [{:keys [type marker-style]} node-type-legend]
              [:div {:class "flex items-center gap-2"
                     :key type}
               [:span {:class "inline-block h-4 w-4 border border-base-content/35"
                       :style marker-style}]
               [:span {:data-cy-node-type-label "true"} type]])]
           [:div {:class "rounded-box border border-base-300 bg-base-200/70 p-3 text-sm"}
            [:span {:class "text-base-content/70"} "Pending nodes: "]
            [:span {:class "font-semibold"
                    :id "cy-pending-count"
                    :data-text "Object.keys($graph.pendingByNodeId || {}).length"}]]]]]]]))})
