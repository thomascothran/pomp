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
