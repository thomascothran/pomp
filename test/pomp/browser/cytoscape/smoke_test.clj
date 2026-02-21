(ns pomp.browser.cytoscape.smoke-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]
            [pomp.test.fixtures.browser.cytoscape :as cytoscape]))

(use-fixtures :once
  (browser/server-fixture {:routes cytoscape/routes
                           :middlewares cytoscape/middlewares
                           :router-data cytoscape/router-data})
  browser/driver-fixture)

(defn- node-count
  []
  (or (e/js-execute browser/*driver*
                    "const g = window.pompGraphs && window.pompGraphs['scratch-cytoscape']; if (!g) return 0; const cy = g.cy || g; return cy && cy.nodes ? cy.nodes().length : 0;")
      0))

(defn- page-contains-text?
  [needle]
  (true?
   (e/js-execute browser/*driver*
                 (str "const text = (document.body && document.body.textContent) || '';"
                      "return text.includes(" (pr-str needle) ");"))))

(defn- legend-node-type-labels
  []
  (or (e/js-execute browser/*driver*
                    "return Array.from(document.querySelectorAll('[data-cy-node-type-label]')).map((el) => (el.textContent || '').trim()).filter(Boolean);")
      []))

(defn- emit-node-event!
  [node-id event-name]
  (e/js-execute browser/*driver*
                (str "const g = window.pompGraphs && window.pompGraphs['scratch-cytoscape'];"
                     "if (!g) return null;"
                     "const cy = g.cy || g;"
                     "const node = cy.getElementById('" node-id "');"
                     "if (!node || node.empty()) return null;"
                     "node.emit('" event-name "');"
                     "return node.id();")))

(defn- node-has-class?
  [node-id class-name]
  (boolean
   (e/js-execute browser/*driver*
                 (str "const g = window.pompGraphs && window.pompGraphs['scratch-cytoscape'];"
                      "if (!g) return false;"
                      "const cy = g.cy || g;"
                      "const node = cy.getElementById('" node-id "');"
                      "if (!node || node.empty()) return false;"
                      "return node.hasClass('" class-name "');"))))

(defn- node-ids
  []
  (or (e/js-execute browser/*driver*
                    "const g = window.pompGraphs && window.pompGraphs['scratch-cytoscape']; if (!g) return []; const cy = g.cy || g; return cy && cy.nodes ? cy.nodes().map((n) => n.id()) : [];")
      []))

(defn- expanded-nodes-overlap-source?
  [source-node-id before-node-ids]
  (true?
   (e/js-execute browser/*driver*
                 (str "const before = new Set(["
                      (str/join "," (map pr-str before-node-ids))
                      "]);"
                      "const g = window.pompGraphs && window.pompGraphs['scratch-cytoscape'];"
                      "if (!g) return false;"
                      "const cy = g.cy || g;"
                      "const source = cy.getElementById('" source-node-id "');"
                      "if (!source || source.empty()) return false;"
                      "const sourcePos = source.position();"
                      "const newlyAdded = cy.nodes().filter((n) => !before.has(n.id()));"
                      "return newlyAdded.some((n) => { const p = n.position(); return p && p.x === sourcePos.x && p.y === sourcePos.y; });"))))

(defn- node-rendered-position
  [node-id]
  (e/js-execute browser/*driver*
                (str "const g = window.pompGraphs && window.pompGraphs['scratch-cytoscape'];"
                     "if (!g) return null;"
                     "const cy = g.cy || g;"
                     "const node = cy.getElementById('" node-id "');"
                     "if (!node || node.empty()) return null;"
                     "const p = node.renderedPosition();"
                     "if (!p) return null;"
                     "return {x: p.x, y: p.y};")))

(defn- selected-node-properties-state
  []
  (e/js-execute browser/*driver*
                "const body = document.querySelector('#cy-selected-node-properties'); if (!body) return {exists: false, text: null}; return {exists: true, text: (body.textContent || '').trim()};"))

(defn- selected-node-property-value
  [property-key]
  (e/js-execute browser/*driver*
                (str "const key = " (pr-str property-key) ";"
                     "let rows = Array.from(document.querySelectorAll('#cy-selected-node-properties [data-cy-prop-key]'));"
                     "if (!rows.length) { const root = document.querySelector('#cy-selected-node-properties'); rows = root ? Array.from(root.children) : []; }"
                     "const row = rows.find((el) => { const label = el.querySelector('.text-xs'); return label && (label.textContent || '').trim() === key; });"
                     "const el = row ? (row.querySelector('[data-cy-prop-value]') || row.querySelector('.font-mono.text-sm')) : null;"
                     "return el ? (el.textContent || '').trim() : null;")))

(defn- edit-selected-node-property-enter!
  [property-key next-value]
  (e/js-execute browser/*driver*
                (str "const key = " (pr-str property-key) ";"
                     "const next = " (pr-str next-value) ";"
                     "let rows = Array.from(document.querySelectorAll('#cy-selected-node-properties [data-cy-prop-key]'));"
                     "if (!rows.length) { const root = document.querySelector('#cy-selected-node-properties'); rows = root ? Array.from(root.children) : []; }"
                     "const row = rows.find((el) => { const label = el.querySelector('.text-xs'); return label && (label.textContent || '').trim() === key; });"
                     "if (!row) return false;"
                     "const value = row.querySelector('[data-cy-prop-value]');"
                     "if (!value) return false;"
                     "value.dispatchEvent(new MouseEvent('dblclick', {bubbles: true, cancelable: true}));"
                     "const input = row.querySelector('input[data-cy-prop-input]');"
                     "if (!input) return false;"
                     "input.value = next;"
                     "input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}));"
                     "return true;")))

(defn- selected-node-property-edit-affordance
  [property-key]
  (e/js-execute browser/*driver*
                (str "const key = " (pr-str property-key) ";"
                     "let rows = Array.from(document.querySelectorAll('#cy-selected-node-properties [data-cy-prop-key]'));"
                     "if (!rows.length) { const root = document.querySelector('#cy-selected-node-properties'); rows = root ? Array.from(root.children) : []; }"
                     "const row = rows.find((el) => { const label = el.querySelector('.text-xs'); return label && (label.textContent || '').trim() === key; });"
                     "if (!row) return null;"
                     "const pencil = row.querySelector('[data-cy-prop-edit]');"
                     "if (!pencil) return null;"
                     "const className = pencil.getAttribute('class') || '';"
                     "const clickEvent = new MouseEvent('click', {bubbles: true, cancelable: true});"
                     "pencil.dispatchEvent(clickEvent);"
                     "const input = row.querySelector('input[data-cy-prop-input]');"
                     "return {"
                     "  hasPencil: true,"
                     "  hasHoverOnlyClass: className.includes('opacity-0') && className.includes('group-hover:opacity-100'),"
                     "  nonClickableClass: className.includes('pointer-events-none'),"
                     "  clickStartsEdit: !!input"
                     "};")))

(defn- rendered-position-drift
  [node-id previous-position]
  (when (and (map? previous-position)
             (number? (:x previous-position))
             (number? (:y previous-position)))
    (e/js-execute browser/*driver*
                  (str "const g = window.pompGraphs && window.pompGraphs['scratch-cytoscape'];"
                       "if (!g) return null;"
                       "const cy = g.cy || g;"
                       "const node = cy.getElementById('" node-id "');"
                       "if (!node || node.empty()) return null;"
                       "const p = node.renderedPosition();"
                       "if (!p) return null;"
                       "const dx = p.x - " (:x previous-position) ";"
                       "const dy = p.y - " (:y previous-position) ";"
                       "return Math.sqrt((dx * dx) + (dy * dy));"))))

(defn- init-custom-visual-graph!
  []
  (e/js-execute browser/*driver*
                (str "if (typeof window.pompInitGraph !== 'function') return null;"
                     "const graphId = 'smoke-visual-' + Date.now() + '-' + Math.floor(Math.random() * 1000000);"
                     "const hostId = graphId + '-host';"
                     "const canvasId = graphId + '-canvas';"
                     "const host = document.createElement('div');"
                     "host.id = hostId;"
                     "host.setAttribute('data-pomp-graph-id', graphId);"
                     "host.style.width = '640px';"
                     "host.style.height = '400px';"
                     "host.style.position = 'relative';"
                     "const canvas = document.createElement('div');"
                     "canvas.id = canvasId;"
                     "canvas.setAttribute('data-pomp-graph-canvas', 'true');"
                     "canvas.style.width = '100%';"
                     "canvas.style.height = '100%';"
                     "host.appendChild(canvas);"
                     "document.body.appendChild(host);"
                     "window.pompInitGraph({"
                     "  graphId: graphId,"
                     "  hostEl: host,"
                     "  canvasEl: canvas,"
                     "  viewport: {fit: false},"
                     "  nodes: ["
                     "    {id: 'node:known', label: 'Known', type: 'service'},"
                     "    {id: 'node:fallback', label: 'Fallback', type: 'unknown-type'},"
                     "    {id: 'node:target', label: 'Target', type: 'service'}"
                     "  ],"
                     "  edges: ["
                     "    {id: 'edge:known', source: 'node:known', target: 'node:target', relation: 'service/dependency', label: 'depends'},"
                     "    {id: 'edge:fallback', source: 'node:target', target: 'node:fallback', relation: 'unknown/relation', label: 'related'}"
                     "  ],"
                     "  visual: {"
                     "    nodeTypes: {"
                     "      default: {label: 'Node', shape: 'triangle', color: '#112233', borderColor: '#223344'},"
                     "      service: {label: 'Service', shape: 'hexagon', color: '#00aaee', borderColor: '#005577'}"
                     "    },"
                     "    edgeTypes: {"
                     "      default: {label: 'Related', lineColor: '#123456', arrowColor: '#123456', labelColor: '#654321'},"
                     "      'service/dependency': {label: 'Depends', lineColor: '#ff0066', arrowColor: '#ff0066', labelColor: '#660022'}"
                     "    }"
                     "  }"
                     "});"
                     "return graphId;")))

(defn- custom-visual-style-checks
  [graph-id]
  (e/js-execute browser/*driver*
                (str "const graphId = " (pr-str graph-id) ";"
                     "const g = window.pompGraphs && window.pompGraphs[graphId];"
                     "if (!g) return {ready: false};"
                     "const cy = g.cy || g;"
                     "if (!cy || !cy.getElementById) return {ready: false};"
                     "const knownNode = cy.getElementById('node:known');"
                     "const fallbackNode = cy.getElementById('node:fallback');"
                     "const knownEdge = cy.getElementById('edge:known');"
                     "const fallbackEdge = cy.getElementById('edge:fallback');"
                     "if (!knownNode || knownNode.empty() || !fallbackNode || fallbackNode.empty() || !knownEdge || knownEdge.empty() || !fallbackEdge || fallbackEdge.empty()) return {ready: false};"
                     "const normalize = (value) => String(value == null ? '' : value).toLowerCase().replace(/\\s+/g, '');"
                     "const hexToRgb = (hex) => {"
                     "  const cleaned = String(hex || '').replace('#', '');"
                     "  if (!/^[0-9a-fA-F]{6}$/.test(cleaned)) return normalize(hex);"
                     "  const r = parseInt(cleaned.slice(0, 2), 16);"
                     "  const gVal = parseInt(cleaned.slice(2, 4), 16);"
                     "  const b = parseInt(cleaned.slice(4, 6), 16);"
                     "  return normalize(`rgb(${r}, ${gVal}, ${b})`);"
                     "};"
                     "const knownNodeShape = String(knownNode.style('shape') || '');"
                     "const fallbackNodeShape = String(fallbackNode.style('shape') || '');"
                     "const knownEdgeLineColor = normalize(knownEdge.style('line-color'));"
                     "const fallbackEdgeLineColor = normalize(fallbackEdge.style('line-color'));"
                     "const expectedKnownEdgeLineColor = hexToRgb('#ff0066');"
                     "const expectedFallbackEdgeLineColor = hexToRgb('#123456');"
                     "return {"
                     "  ready: true,"
                     "  knownNodeShape: knownNodeShape,"
                     "  fallbackNodeShape: fallbackNodeShape,"
                     "  knownEdgeLineColor: knownEdgeLineColor,"
                     "  fallbackEdgeLineColor: fallbackEdgeLineColor,"
                     "  knownNodeMatches: knownNodeShape === 'hexagon',"
                     "  fallbackNodeMatches: fallbackNodeShape === 'triangle',"
                     "  knownEdgeMatches: knownEdgeLineColor === expectedKnownEdgeLineColor,"
                     "  fallbackEdgeMatches: fallbackEdgeLineColor === expectedFallbackEdgeLineColor,"
                     "  expectedKnownEdgeLineColor: expectedKnownEdgeLineColor,"
                     "  expectedFallbackEdgeLineColor: expectedFallbackEdgeLineColor"
                     "};")))

(deftest scratch-page-loads-and-host-present-test
  (testing "scratch cytoscape page initializes graph runtime"
    (e/go browser/*driver* cytoscape/base-url)
    (e/wait-visible browser/*driver* {:css "#scratch-cytoscape-host"})
    (e/wait-predicate #(pos? (node-count)))
    (is (pos? (node-count))
        "Expected Cytoscape graph to contain at least one node after init")
    (is (false? (page-contains-text? "Graph runtime loads on init if Cytoscape is available"))
        "Runtime overlay helper text should not be rendered")
    (is (= #{"project" "story" "task" "subtask" "developer" "qa" "product-owner"}
           (set (legend-node-type-labels)))
        "Legend should list all node types")))

(deftest select-and-expand-adds-neighbors-test
  (testing "select updates details and expand adds nodes without duplicates"
    (e/go browser/*driver* cytoscape/base-url)
    (e/wait-predicate #(pos? (node-count)))
    (let [selected-id (emit-node-event! "project:apollo" "click")]
      (is (= "project:apollo" selected-id)
          "Expected project seed node to be selectable")
      (e/wait-predicate #(let [{:keys [exists text]} (selected-node-properties-state)]
                           (and exists
                                (string? text)
                                (str/includes? text "domain")
                                (str/includes? text "platform"))))
      (let [{:keys [exists text] :as properties-state}
            (selected-node-properties-state)]
        (is exists
            (str "Selected properties container should exist: " (pr-str properties-state)))
        (is (and (string? text)
                 (str/includes? text "domain")
                 (str/includes? text "platform"))
            (str "Selected node details should include domain property value: " (pr-str properties-state)))))
    (let [before-expand (node-count)
          source-node-id "story:auth-hardening"
          source-rendered-pos-before (node-rendered-position source-node-id)]
      (is (map? source-rendered-pos-before)
          "Expected source node to have a rendered position before expansion")
      (let [before-node-ids (node-ids)]
        (emit-node-event! source-node-id "dbltap")
        (e/wait-predicate #(< before-expand (node-count)))
        (e/wait-predicate #(number? (rendered-position-drift source-node-id source-rendered-pos-before)))
        (is (<= (double (rendered-position-drift source-node-id source-rendered-pos-before)) 8.0)
            "Expanded layout should keep source node anchored within 8px rendered drift")
        (is (false? (expanded-nodes-overlap-source? source-node-id before-node-ids))
            "Expanded nodes should not be placed exactly on top of the source node"))
      (let [after-first-expand (node-count)]
        (emit-node-event! source-node-id "dbltap")
        (Thread/sleep 200)
        (is (= after-first-expand (node-count))
            "Repeated expansion should not duplicate existing nodes")))))

(deftest selected-node-property-is-inline-editable-test
  (testing "selected node property value can be edited inline"
    (e/go browser/*driver* cytoscape/base-url)
    (e/wait-predicate #(pos? (node-count)))
    (is (= "project:apollo" (emit-node-event! "project:apollo" "click"))
        "Expected project seed node to be selectable")
    (e/wait-predicate #(str/includes? (or (selected-node-property-value "domain") "") "platform"))
    (let [affordance (selected-node-property-edit-affordance "domain")]
      (is (= true (:hasPencil affordance))
          "Expected edit pencil affordance to be present")
      (is (= true (:hasHoverOnlyClass affordance))
          "Expected pencil affordance to be hover-only")
      (is (= true (:nonClickableClass affordance))
          "Expected pencil affordance to be non-clickable")
      (is (= false (:clickStartsEdit affordance))
          "Expected clicking pencil to not enter edit mode"))
    (is (true? (edit-selected-node-property-enter! "domain" "platform-ui"))
        "Expected double-click on value to enter inline edit mode")
    (e/wait-predicate #(str/includes? (or (selected-node-property-value "domain") "") "platform-ui"))
    (is (str/includes? (or (selected-node-property-value "domain") "") "platform-ui")
        "Expected edited domain value to be visible after commit")))

(deftest expand-error-is-local-and-non-fatal-test
  (testing "anomaly patch marks local node error and keeps graph interactive"
    (e/go browser/*driver* cytoscape/base-url)
    (e/wait-predicate #(pos? (node-count)))
    (is (true?
         (e/js-execute browser/*driver*
                       "if (!window.pompApplyGraphPatch) return false; window.pompApplyGraphPatch({graphId: 'scratch-cytoscape', nodeId: 'story:auth-hardening', 'anomaly/category': 'anomaly/unavailable', 'anomaly/message': 'Synthetic failure'}); return true;"))
        "Expected anomaly patch helper to be available")
    (e/wait-predicate #(node-has-class? "story:auth-hardening" "error"))
    (is (node-has-class? "story:auth-hardening" "error")
        "Target node should show local error class on anomaly")
    (emit-node-event! "project:apollo" "click")
    (e/wait-predicate #(let [{:keys [exists text]} (selected-node-properties-state)]
                         (and exists
                              (string? text)
                              (str/includes? text "domain")
                              (str/includes? text "platform"))))
    (is (let [{:keys [text]} (selected-node-properties-state)]
          (and (string? text)
               (str/includes? text "domain")
               (str/includes? text "platform")))
        "Graph should remain interactive after local expansion error")))

(deftest runtime-visual-config-applies-and-falls-back-test
  (testing "runtime styles apply configured visuals and deterministic fallbacks"
    (e/go browser/*driver* cytoscape/base-url)
    (e/wait-predicate #(pos? (node-count)))
    (let [graph-id (init-custom-visual-graph!)]
      (is (string? graph-id)
          "Expected custom graph init helper to return a graph id")
      (e/wait-predicate #(true? (:ready (custom-visual-style-checks graph-id))))
      (let [checks (custom-visual-style-checks graph-id)]
        (is (true? (:knownNodeMatches checks))
            (str "Configured node type should use configured shape: " (pr-str checks)))
        (is (true? (:fallbackNodeMatches checks))
            (str "Unknown node type should fall back to default node style: " (pr-str checks)))
        (is (true? (:knownEdgeMatches checks))
            (str "Configured edge relation should use configured line color: " (pr-str checks)))
        (is (true? (:fallbackEdgeMatches checks))
            (str "Unknown relation should fall back to default edge style: " (pr-str checks)))))))
