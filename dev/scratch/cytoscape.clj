(ns scratch.cytoscape
  (:require [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as c]
            [pomp.graph :as graph]))

(def graph-id "scratch-cytoscape")
(def seed-node-id "project:apollo")

(def ^:private graph-system
  (graph/make-graph {}))

(def ^:private detail-card-ids
  {:root (str graph-id "-details")
   :properties "cy-selected-node-properties"})

(def ^:private detail-card-signals
  {:selected-node "graph.selectedNode"
   :selected-node-id "graph.selectedNodeId"})

(def ^:private detail-card-host-attrs
  {:data-pomp-graph-id graph-id})

(defn- render-detail-card
  [selected-node]
  (let [render-detail-card-fn (get-in graph-system [:details :render-detail-card])]
    (render-detail-card-fn {:graph-id graph-id
                            :ids detail-card-ids
                            :signals detail-card-signals
                            :selected-node selected-node
                            :host-attrs detail-card-host-attrs})))

(def ^:private marker-style-by-shape
  {"ellipse" "border-radius: 9999px;"
   "round-rectangle" "border-radius: 0.375rem;"
   "rectangle" "border-radius: 0.125rem;"
   "diamond" "border-radius: 0.125rem; transform: rotate(45deg);"
   "hexagon" "clip-path: polygon(25% 6%, 75% 6%, 100% 50%, 75% 94%, 25% 94%, 0 50%);"
   "vee" "clip-path: polygon(0 0, 50% 92%, 100% 0, 74% 0, 50% 44%, 26% 0);"
   "tag" "clip-path: polygon(0 0, 74% 0, 100% 50%, 74% 100%, 0 100%);"})

(defn- node-marker-style
  [visual]
  (str (get marker-style-by-shape (:shape visual) "border-radius: 0.125rem;")
       " background-color: " (:color visual) ";"
       " border-color: " (:border-color visual) ";"))

(def ^:private node-type-legend
  (let [node-types (get-in graph-system [:config :visual :node-types])]
    (mapv (fn [{:keys [type label]}]
            (let [type-key (keyword type)
                  visual (get node-types type-key)]
              {:label (or label (name type-key))
               :marker-style (node-marker-style visual)}))
          (get-in graph-system [:visual :legend :nodes]))))

(def ^:private graph-handlers
  (:handlers graph-system))

(def init-handler
  (:init graph-handlers))

(def expand-handler
  (:expand graph-handlers))

(def ^:private on-node-select-script
  (str/join
   " "
   ["const _detail = (evt && evt.detail) || {};"
    "const _node = _detail.node || null;"
    "const _graphId = ($graph && $graph.graphId) || 'scratch-cytoscape';"
    "const _container = document.querySelector('#cy-selected-node-properties');"
    "if (!_container) return;"
    "const _escapeHtml = (value) => String(value == null ? '' : value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;').replace(/'/g, '&#39;');"
    "const _escapeAttr = (value) => String(value == null ? '' : value).replace(/&/g, '&amp;').replace(/\"/g, '&quot;').replace(/'/g, '&#39;');"
    "const _labelClass = 'text-xs uppercase tracking-wide text-base-content/60';"
    "const _valueClass = 'font-mono text-sm';"
    "const _parseObject = (value) => { if (!value) return null; if (typeof value === 'string') { try { const parsed = JSON.parse(value); return (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) ? parsed : null; } catch (_e) { return null; } } if (typeof value === 'object' && !Array.isArray(value)) return value; return null; };"
    "const _propertiesFromText = (text) => { if (typeof text !== 'string') return null; const result = {}; const lines = text.split('\\n'); for (let i = 0; i < lines.length; i += 1) { const line = lines[i].trim(); if (!line) continue; const idx = line.indexOf('->'); if (idx < 0) continue; const key = line.slice(0, idx).trim(); if (!key) continue; result[key] = line.slice(idx + 2).trim(); } return Object.keys(result).length ? result : null; };"
    "let _properties = _parseObject(_node && _node.properties);"
    "if (!_properties) _properties = _propertiesFromText(_detail.nodePropertiesText);"
    "if (!_properties) _properties = {};"
    "_container.__cySelectedNodeId = _detail.nodeId || null;"
    "_container.__cyProperties = Object.assign({}, _properties);"
    "_container.__cyRender = function() { const props = _container.__cyProperties || {}; const keys = Object.keys(props).sort(); const rows = []; for (let i = 0; i < keys.length; i += 1) { const key = keys[i]; rows.push(\"<div class='group space-y-0.5' data-cy-prop-key='\" + _escapeAttr(key) + \"'><div class='\" + _labelClass + \"'>\" + _escapeHtml(key) + \"</div><div class='flex items-center gap-1'><div class='\" + _valueClass + \"' data-cy-prop-value='true'>\" + _escapeHtml(props[key]) + \"</div><button type='button' tabindex='-1' class='btn btn-ghost btn-xs h-5 min-h-0 px-1 text-base-content/55 pointer-events-none opacity-0 transition-opacity group-hover:opacity-100' data-cy-prop-edit='true' aria-hidden='true'>&#9998;</button></div></div>\"); } if (!rows.length) { rows.push(\"<div class='space-y-0.5'><div class='text-xs uppercase tracking-wide text-base-content/60'>No properties</div><div class='font-mono text-sm'>none</div></div>\"); } _container.innerHTML = rows.join(''); $graph.selectedNodePropertiesText = (_container.textContent || '').trim() || 'none'; };"
    "_container.__cyCommit = function(key, nextValue) { const nodeId = _container.__cySelectedNodeId; if (!nodeId) return; const props = _container.__cyProperties || {}; props[key] = nextValue; _container.__cyProperties = props; const g = window.pompGraphs && window.pompGraphs[_graphId]; const cy = g && (g.cy || g); const selected = cy && cy.getElementById ? cy.getElementById(nodeId) : null; if (selected && !selected.empty()) { let selectedProps = _parseObject(selected.data('properties')); if (!selectedProps) selectedProps = {}; selectedProps[key] = nextValue; selected.data('properties', selectedProps); } _container.__cyRender(); };"
    "_container.__cyStartEdit = function(row) { if (!row || row.getAttribute('data-cy-editing') === 'true') return; const key = row.getAttribute('data-cy-prop-key'); if (!key) return; const props = _container.__cyProperties || {}; const current = Object.prototype.hasOwnProperty.call(props, key) ? props[key] : ''; const valueWrap = row.querySelector('[data-cy-prop-value]'); if (!valueWrap) return; row.setAttribute('data-cy-editing', 'true'); valueWrap.innerHTML = \"<input type='text' data-cy-prop-input='true' class='input input-bordered input-xs w-full font-mono text-sm' />\"; const input = valueWrap.querySelector('input[data-cy-prop-input]'); if (!input) { row.removeAttribute('data-cy-editing'); return; } input.value = String(current == null ? '' : current); input.focus(); input.select(); let finished = false; const finish = (cancelled) => { if (finished) return; finished = true; row.removeAttribute('data-cy-editing'); if (cancelled) { _container.__cyRender(); return; } _container.__cyCommit(key, input.value); }; input.addEventListener('keydown', (event) => { if (event.key === 'Enter') { event.preventDefault(); finish(false); } else if (event.key === 'Escape') { event.preventDefault(); finish(true); } }); input.addEventListener('blur', () => finish(false)); };"
    "if (!_container.dataset.cyPropEditingBound) { _container.dataset.cyPropEditingBound = 'true'; _container.addEventListener('dblclick', (event) => { const value = event.target && event.target.closest('[data-cy-prop-value]'); if (!value) return; const row = value.closest('[data-cy-prop-key]'); _container.__cyStartEdit(row); }); }"
    "_container.__cyRender();"
    "$graph.selectedNodeId = _detail.nodeId || null;"
    "$graph.selectedNode = _node || null;"]))

(def ^:private on-node-expand-script
  "const _detail = (evt && evt.detail) || {}; const _graphId = _detail.graphId || $graph.graphId; const _relation = _detail.relation || $graph.relation || 'graph/neighbors'; $graph.expandRequest = { graphId: _graphId, nodeId: _detail.nodeId || null, relation: _relation }; @post('/scratch/cytoscape/expand', { requestCancellation: 'auto' })")

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
      [:section {:class "grid h-full flex-1 grid-cols-[1fr_360px] gap-4"
                 :style "min-height: 640px;"}
       [:section {:class "card h-full border border-base-300 bg-base-100 shadow-sm"}
        [:div {:class "card-body h-full p-4"}
         [:div {:id "scratch-cytoscape-host"
                :class "relative h-full rounded-box border border-base-300 bg-base-200"
                :style "height: 100%; min-height: 640px;"
                :data-pomp-graph-id graph-id
                :data-signals (str "{graph: {graphId: '" graph-id
                                   "', seedNodeId: '" seed-node-id
                                   "', relation: 'graph/neighbors', selectedNode: null, selectedNodePropertiesText: 'none', expandRequest: null, errorByNodeId: {}}}")
                :data-init "@post('/scratch/cytoscape/init')"
                :data-on:pomp-graph-node-select on-node-select-script
                :data-on:pomp-graph-node-expand on-node-expand-script}
          [:div {:id "scratch-cytoscape-canvas"
                 :class "h-full w-full rounded-box"
                 :data-pomp-graph-canvas "true"}]]]]
       [:aside {:class "space-y-4"}
        (render-detail-card nil)
        [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
         [:div {:class "card-body gap-3 p-4"}
          [:h2 {:class "card-title text-base"} "Node Type Legend"]
          [:div {:class "grid grid-cols-2 gap-x-4 gap-y-2 text-sm"}
           (for [{:keys [label marker-style]} node-type-legend]
             [:div {:class "flex items-center gap-2"
                    :key label}
              [:span {:class "inline-block h-4 w-4 border border-base-content/35"
                      :style marker-style}]
              [:span {:data-cy-node-type-label "true"} label]])]]]]]]))})
