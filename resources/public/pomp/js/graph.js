;(function() {
  const NODE_SELECT_EVENT = 'pomp-graph-node-select';
  const NODE_EXPAND_EVENT = 'pomp-graph-node-expand';
  const DEFAULT_RELATION = 'graph/neighbors';
  const LISTENER_STATE_KEY = '__pompGraphListenerState';
  const RESIZE_STATE_KEY = '__pompGraphResizeState';
  const EXPAND_DEDUPE_WINDOW_MS = 250;

  function ensureRegistry() {
    if (!window.pompGraphs || typeof window.pompGraphs !== 'object') {
      window.pompGraphs = {};
    }

    return window.pompGraphs;
  }

  function asString(value) {
    if (value === null || value === undefined) {
      return null;
    }

    return String(value);
  }

  function relationFrom(payload) {
    return asString(payload && payload.relation) || DEFAULT_RELATION;
  }

  function graphIdFrom(payload) {
    return asString(
      payload && (payload.graphId || payload['graph-id'] || payload.graph_id)
    );
  }

  function nodeIdFrom(payload) {
    return asString(
      payload && (payload.nodeId || payload['node-id'] || payload.node_id)
    );
  }

  function anomalyCategoryFrom(payload) {
    return payload && (
      payload['anomaly/category'] ||
      payload.anomalyCategory ||
      payload['anomaly-category']
    );
  }

  function resolveHostElement(graphId, payload) {
    if (payload && payload.hostEl && payload.hostEl.dispatchEvent) {
      return payload.hostEl;
    }

    if (payload && payload.hostId) {
      const elById = document.getElementById(payload.hostId);
      if (elById) {
        return elById;
      }
    }

    if (payload && payload.hostSelector) {
      const bySelector = document.querySelector(payload.hostSelector);
      if (bySelector) {
        return bySelector;
      }
    }

    if (graphId) {
      const byDataAttr = document.querySelector('[data-pomp-graph-id="' + graphId + '"]');
      if (byDataAttr) {
        return byDataAttr;
      }
    }

    return null;
  }

  function resolveCanvasElement(hostEl, payload) {
    if (payload && payload.canvasEl) {
      return payload.canvasEl;
    }

    if (payload && payload.canvasId) {
      const byId = document.getElementById(payload.canvasId);
      if (byId) {
        return byId;
      }
    }

    if (payload && payload.canvasSelector) {
      const bySelector = document.querySelector(payload.canvasSelector);
      if (bySelector) {
        return bySelector;
      }
    }

    if (hostEl) {
      const withinHost = hostEl.querySelector('[data-pomp-graph-canvas]');
      if (withinHost) {
        return withinHost;
      }
    }

    return hostEl;
  }

  function dispatchGraphEvent(hostEl, eventName, detail) {
    if (!hostEl || !hostEl.dispatchEvent) {
      return;
    }

    hostEl.dispatchEvent(new CustomEvent(eventName, {
      bubbles: true,
      detail: detail
    }));
  }

  function normalizeProperties(properties) {
    if (!properties) {
      return null;
    }

    if (typeof properties === 'string') {
      try {
        const parsed = JSON.parse(properties);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          return parsed;
        }
      } catch (_e) {
        return null;
      }

      return null;
    }

    if (typeof properties !== 'object' || Array.isArray(properties)) {
      return null;
    }

    return properties;
  }

  function formatNodeProperties(properties) {
    const normalized = normalizeProperties(properties);
    if (!normalized) {
      return 'none';
    }

    const keys = Object.keys(normalized).filter(Boolean).sort();
    if (keys.length === 0) {
      return 'none';
    }

    return keys
      .map(function(key) {
        const value = normalized[key];
        if (value === null || value === undefined) {
          return key + ' -> null';
        }

        return key + ' -> ' + String(value);
      })
      .join('\n');
  }

  function parseClasses(raw) {
    if (!raw) {
      return [];
    }

    if (Array.isArray(raw)) {
      return raw
        .map(asString)
        .filter(Boolean);
    }

    return asString(raw)
      .split(/\s+/)
      .map(function(x) { return x.trim(); })
      .filter(Boolean);
  }

  function nodeStatePatch(rawNode) {
    const patch = {};

    if (!rawNode || typeof rawNode !== 'object') {
      return patch;
    }

    if (typeof rawNode.loading === 'boolean') {
      patch.loading = rawNode.loading;
    }

    if (typeof rawNode.expanded === 'boolean') {
      patch.expanded = rawNode.expanded;
    }

    if (typeof rawNode.error === 'boolean') {
      patch.error = rawNode.error;
    }

    if (rawNode.state === 'loading') {
      patch.loading = true;
    }

    if (rawNode.state === 'expanded') {
      patch.expanded = true;
      patch.loading = false;
    }

    if (rawNode.state === 'error') {
      patch.error = true;
      patch.loading = false;
    }

    return patch;
  }

  function normalizeNode(rawNode) {
    if (!rawNode || typeof rawNode !== 'object') {
      return null;
    }

    const data = rawNode.data && typeof rawNode.data === 'object'
      ? Object.assign({}, rawNode.data)
      : {};

    if (!rawNode.data || typeof rawNode.data !== 'object') {
      Object.keys(rawNode).forEach(function(key) {
        if (key === 'classes' || key === 'class' || key === 'className' ||
            key === 'group' || key === 'loading' || key === 'expanded' ||
            key === 'error' || key === 'state') {
          return;
        }

        data[key] = rawNode[key];
      });
    }

    const id = asString(data.id || data.nodeId || data['node-id']);
    if (!id) {
      return null;
    }

    data.id = id;

    const classes = parseClasses(rawNode.classes || rawNode.class || rawNode.className);
    const statePatch = nodeStatePatch(rawNode);

    return {
      id: id,
      data: data,
      classes: classes,
      statePatch: statePatch
    };
  }

  function normalizeEdge(rawEdge) {
    if (!rawEdge || typeof rawEdge !== 'object') {
      return null;
    }

    const data = rawEdge.data && typeof rawEdge.data === 'object'
      ? Object.assign({}, rawEdge.data)
      : {};

    if (!rawEdge.data || typeof rawEdge.data !== 'object') {
      Object.keys(rawEdge).forEach(function(key) {
        if (key === 'classes' || key === 'class' || key === 'className' || key === 'group') {
          return;
        }

        data[key] = rawEdge[key];
      });
    }

    const id = asString(data.id || data.edgeId || data['edge-id']);
    if (!id) {
      return null;
    }

    data.id = id;

    const source = asString(data.source);
    const target = asString(data.target);
    if (!source || !target) {
      return null;
    }

    data.source = source;
    data.target = target;

    return {
      id: id,
      data: data,
      classes: parseClasses(rawEdge.classes || rawEdge.class || rawEdge.className)
    };
  }

  function applyNodeState(cy, nodeId, patch) {
    if (!nodeId) {
      return;
    }

    const node = cy.getElementById(nodeId);
    if (!node || node.empty()) {
      return;
    }

    if (patch.loading === true) {
      node.addClass('loading');
    } else if (patch.loading === false) {
      node.removeClass('loading');
    }

    if (patch.expanded === true) {
      node.addClass('expanded');
    } else if (patch.expanded === false) {
      node.removeClass('expanded');
    }

    if (patch.error === true) {
      node.addClass('error');
    } else if (patch.error === false) {
      node.removeClass('error');
    }
  }

  function applyPatchDedup(cy, payload) {
    const nodeSet = new Set(
      cy.nodes().map(function(node) {
        return node.id();
      })
    );

    const edgeSet = new Set(
      cy.edges().map(function(edge) {
        return edge.id();
      })
    );

    const toAdd = [];
    const addedNodeIds = [];
    const nodes = Array.isArray(payload && payload.nodes) ? payload.nodes : [];
    const edges = Array.isArray(payload && payload.edges) ? payload.edges : [];

    nodes.forEach(function(rawNode) {
      const node = normalizeNode(rawNode);
      if (!node) {
        return;
      }

      if (!nodeSet.has(node.id)) {
        nodeSet.add(node.id);
        addedNodeIds.push(node.id);
        toAdd.push({
          group: 'nodes',
          data: node.data,
          classes: node.classes.join(' ')
        });
      } else {
        const existing = cy.getElementById(node.id);
        if (existing && !existing.empty()) {
          existing.data(node.data);
          if (node.classes.length > 0) {
            existing.addClass(node.classes.join(' '));
          }
        }
      }

      applyNodeState(cy, node.id, node.statePatch);
    });

    edges.forEach(function(rawEdge) {
      const edge = normalizeEdge(rawEdge);
      if (!edge) {
        return;
      }

      if (!edgeSet.has(edge.id)) {
        edgeSet.add(edge.id);
        toAdd.push({
          group: 'edges',
          data: edge.data,
          classes: edge.classes.join(' ')
        });
      } else {
        const existing = cy.getElementById(edge.id);
        if (existing && !existing.empty()) {
          existing.data(edge.data);
          if (edge.classes.length > 0) {
            existing.addClass(edge.classes.join(' '));
          }
        }
      }
    });

    if (toAdd.length > 0) {
      cy.add(toAdd);
    }

    return {
      addedCount: toAdd.length,
      addedNodeIds: addedNodeIds
    };
  }

  function layoutOptions(payload, phase) {
    const requestedLayout = payload && payload.layout && typeof payload.layout === 'object'
      ? payload.layout
      : {};
    const initFitRequested = !!(payload && payload.viewport && payload.viewport.fit);
    const isExpansion = phase === 'expand';
    const fallback = {
      name: 'cose',
      animate: false,
      fit: isExpansion ? false : initFitRequested,
      padding: isExpansion ? 72 : 96,
      randomize: isExpansion ? false : true,
      spacingFactor: 1.6,
      nodeRepulsion: 120000,
      idealEdgeLength: 140,
      edgeElasticity: 80,
      gravity: 0.2,
      numIter: 1200
    };

    const merged = Object.assign({}, fallback, requestedLayout, {
      animate: false,
      fit: isExpansion
        ? false
        : (requestedLayout.fit !== undefined ? !!requestedLayout.fit : initFitRequested),
      padding: requestedLayout.padding !== undefined
        ? requestedLayout.padding
        : fallback.padding,
      randomize: requestedLayout.randomize !== undefined
        ? !!requestedLayout.randomize
        : fallback.randomize
    });

    return merged;
  }

  function sourceRenderedPosition(cy, nodeId) {
    if (!nodeId) {
      return null;
    }

    const source = cy.getElementById(nodeId);
    if (!source || source.empty()) {
      return null;
    }

    const rendered = source.renderedPosition();
    if (!rendered || typeof rendered.x !== 'number' || typeof rendered.y !== 'number') {
      return null;
    }

    return {
      x: rendered.x,
      y: rendered.y
    };
  }

  function anchorSourceRenderedPosition(cy, nodeId, previousRenderedPosition) {
    if (!previousRenderedPosition || !nodeId) {
      return;
    }

    const currentRenderedPosition = sourceRenderedPosition(cy, nodeId);
    if (!currentRenderedPosition) {
      return;
    }

    const dx = previousRenderedPosition.x - currentRenderedPosition.x;
    const dy = previousRenderedPosition.y - currentRenderedPosition.y;
    if (dx === 0 && dy === 0) {
      return;
    }

    const currentPan = cy.pan();
    if (!currentPan || typeof currentPan.x !== 'number' || typeof currentPan.y !== 'number') {
      return;
    }

    cy.pan({
      x: currentPan.x + dx,
      y: currentPan.y + dy
    });
  }

  function runExpansionLayout(cy, payload) {
    if (!cy || typeof cy.layout !== 'function') {
      return;
    }

    const nodes = Array.isArray(payload && payload.nodes) ? payload.nodes : [];
    const edges = Array.isArray(payload && payload.edges) ? payload.edges : [];
    if (nodes.length === 0 && edges.length === 0) {
      return;
    }

    const sourceNodeId = nodeIdFrom(payload);
    const previousSourceRenderedPosition = sourceRenderedPosition(cy, sourceNodeId);
    const layout = cy.layout(layoutOptions(payload, 'expand'));

    if (!layout || typeof layout.run !== 'function') {
      return;
    }

    let anchored = false;
    function anchorAfterLayout() {
      if (anchored) {
        return;
      }

      anchored = true;
      anchorSourceRenderedPosition(cy, sourceNodeId, previousSourceRenderedPosition);
    }

    if (previousSourceRenderedPosition && typeof layout.one === 'function') {
      layout.one('layoutstop', anchorAfterLayout);
    }

    layout.run();

    if (!previousSourceRenderedPosition) {
      return;
    }

    if (typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(anchorAfterLayout);
    } else {
      window.setTimeout(anchorAfterLayout, 0);
    }
  }

  function defaultStyle() {
    return [
      {
        selector: 'node',
        style: {
          label: 'data(label)',
          'background-color': '#64748b',
          shape: 'round-rectangle',
          'border-width': 2,
          'border-color': '#475569',
          color: '#0f172a',
          'font-size': 12,
          'text-wrap': 'wrap',
          'text-max-width': 140,
          'text-background-color': '#ffffff',
          'text-background-opacity': 0.94,
          'text-background-shape': 'roundrectangle',
          'text-background-padding': 4,
          'text-margin-y': -1
        }
      },
      {
        selector: 'node[type = "project"]',
        style: {
          shape: 'ellipse',
          'background-color': '#2563eb',
          'border-color': '#1d4ed8'
        }
      },
      {
        selector: 'node[type = "story"]',
        style: {
          shape: 'round-rectangle',
          'background-color': '#0f766e',
          'border-color': '#115e59'
        }
      },
      {
        selector: 'node[type = "task"]',
        style: {
          shape: 'rectangle',
          'background-color': '#ea580c',
          'border-color': '#c2410c'
        }
      },
      {
        selector: 'node[type = "subtask"]',
        style: {
          shape: 'diamond',
          'background-color': '#7c3aed',
          'border-color': '#6d28d9'
        }
      },
      {
        selector: 'node[type = "developer"]',
        style: {
          shape: 'hexagon',
          'background-color': '#0891b2',
          'border-color': '#0e7490'
        }
      },
      {
        selector: 'node[type = "qa"]',
        style: {
          shape: 'vee',
          'background-color': '#65a30d',
          'border-color': '#4d7c0f'
        }
      },
      {
        selector: 'node[type = "product-owner"]',
        style: {
          shape: 'tag',
          'background-color': '#be123c',
          'border-color': '#9f1239'
        }
      },
      {
        selector: 'edge',
        style: {
          width: 2,
          label: 'data(label)',
          'line-color': '#8ca0b3',
          'target-arrow-color': '#8ca0b3',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier',
          color: '#1f2937',
          'font-size': 10,
          'text-background-color': '#ffffff',
          'text-background-opacity': 0.9,
          'text-background-shape': 'roundrectangle',
          'text-background-padding': 2
        }
      },
      {
        selector: 'node.loading',
        style: {
          'border-color': '#f59e0b',
          'border-width': 4
        }
      },
      {
        selector: 'node.expanded',
        style: {
          'border-color': '#16a34a',
          'border-width': 4
        }
      },
      {
        selector: 'node.error',
        style: {
          'background-color': '#dc2626',
          'border-color': '#7f1d1d',
          'border-width': 4
        }
      }
    ];
  }

  function registerGraphBridge(cy, graphId, hostEl, payload) {
    const existingState = cy.scratch(LISTENER_STATE_KEY);
    if (existingState && existingState.registered) {
      return;
    }

    const state = {
      registered: true,
      lastExpandAtByNodeId: {}
    };

    cy.scratch(LISTENER_STATE_KEY, state);

    cy.on('click', 'node', function(evt) {
      const targetNodeId = evt && evt.target && evt.target.id ? asString(evt.target.id()) : null;
      if (!targetNodeId) {
        return;
      }

      const nodeData = evt && evt.target && typeof evt.target.data === 'function'
        ? Object.assign({}, evt.target.data())
        : null;

      dispatchGraphEvent(hostEl, NODE_SELECT_EVENT, {
        graphId: graphId,
        nodeId: targetNodeId,
        node: nodeData,
        nodePropertiesText: formatNodeProperties(nodeData && nodeData.properties)
      });
    });

    function emitExpand(evt) {
      const targetNodeId = evt && evt.target && evt.target.id ? asString(evt.target.id()) : null;
      if (!targetNodeId) {
        return;
      }

      const now = Date.now();
      const previous = state.lastExpandAtByNodeId[targetNodeId] || 0;
      if (now - previous < EXPAND_DEDUPE_WINDOW_MS) {
        return;
      }

      state.lastExpandAtByNodeId[targetNodeId] = now;

      applyNodeState(cy, targetNodeId, {
        loading: true,
        error: false
      });

      dispatchGraphEvent(hostEl, NODE_EXPAND_EVENT, {
        graphId: graphId,
        nodeId: targetNodeId,
        relation: relationFrom(payload)
      });
    }

    cy.on('dbltap', 'node', emitExpand);
    cy.on('dblclick', 'node', emitExpand);
  }

  function registerResizeSync(cy, hostEl, canvasEl) {
    if (!cy || typeof cy.resize !== 'function') {
      return;
    }

    const existingState = cy.scratch(RESIZE_STATE_KEY);
    if (existingState && existingState.registered) {
      return;
    }

    const resizeNow = function() {
      try {
        cy.resize();
      } catch (_e) {
        // no-op
      }
    };

    const state = {
      registered: true,
      observer: null
    };

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      const onWindowResize = function() {
        resizeNow();
      };

      window.addEventListener('resize', onWindowResize, { passive: true });
      state.onWindowResize = onWindowResize;
    }

    if (typeof window !== 'undefined' && typeof window.ResizeObserver === 'function') {
      const targetEl = hostEl || canvasEl || (typeof cy.container === 'function' ? cy.container() : null);
      if (targetEl) {
        const observer = new window.ResizeObserver(function() {
          resizeNow();
        });
        observer.observe(targetEl);
        state.observer = observer;
      }
    }

    cy.scratch(RESIZE_STATE_KEY, state);

    if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(resizeNow);
    } else {
      window.setTimeout(resizeNow, 0);
    }
  }

  function initGraph(payload) {
    ensureRegistry();

    const graphId = graphIdFrom(payload);
    if (!graphId) {
      console.warn('[pomp graph] Missing graph id; skipped init.');
      return null;
    }

    if (typeof window.cytoscape !== 'function') {
      console.warn('[pomp graph] Cytoscape global missing; skipped init for graph ' + graphId + '.');
      return null;
    }

    const hostEl = resolveHostElement(graphId, payload);
    const canvasEl = resolveCanvasElement(hostEl, payload);
    if (!canvasEl) {
      console.warn('[pomp graph] Missing host/canvas element; skipped init for graph ' + graphId + '.');
      return null;
    }

    const registry = ensureRegistry();
    let cy = registry[graphId];

    if (!cy || (typeof cy.destroyed === 'function' && cy.destroyed())) {
      cy = window.cytoscape({
        container: canvasEl,
        elements: [],
        layout: layoutOptions(payload, 'init'),
        style: payload && payload.style ? payload.style : defaultStyle()
      });

      registry[graphId] = cy;
    }

    registerGraphBridge(cy, graphId, hostEl, payload || {});
    registerResizeSync(cy, hostEl, canvasEl);

    const initPatchResult = applyPatchDedup(cy, {
      nodes: payload && payload.nodes,
      edges: payload && payload.edges
    });

    if (initPatchResult.addedCount > 0 && typeof cy.layout === 'function') {
      cy.layout(layoutOptions(payload, 'init')).run();
    }

    const requestedNodeId = nodeIdFrom(payload) || asString(payload && payload.seedNodeId) || asString(payload && payload['seed-node-id']);
    if (requestedNodeId) {
      applyNodeState(cy, requestedNodeId, { expanded: true, loading: false, error: false });
    }

    if (payload && payload.viewport && payload.viewport.fit) {
      try {
        cy.fit();
      } catch (_e) {
        // no-op
      }
    }

    return cy;
  }

  function applyGraphPatch(payload) {
    ensureRegistry();

    const graphId = graphIdFrom(payload);
    if (!graphId) {
      console.warn('[pomp graph] Missing graph id; skipped patch.');
      return null;
    }

    const cy = ensureRegistry()[graphId];
    if (!cy) {
      console.warn('[pomp graph] No graph instance for id ' + graphId + '; skipped patch.');
      return null;
    }

    const requestedNodeId = nodeIdFrom(payload);
    if (anomalyCategoryFrom(payload)) {
      if (requestedNodeId) {
        applyNodeState(cy, requestedNodeId, { loading: false, error: true });
      }

      return {
        graphId: graphId,
        addedCount: 0,
        anomaly: true
      };
    }

    const result = applyPatchDedup(cy, payload || {});

    runExpansionLayout(cy, payload || {});

    if (requestedNodeId) {
      applyNodeState(cy, requestedNodeId, { loading: false, expanded: true, error: false });
    }

    return {
      graphId: graphId,
      addedCount: result.addedCount,
      anomaly: false
    };
  }

  ensureRegistry();
  window.pompInitGraph = initGraph;
  window.pompApplyGraphPatch = applyGraphPatch;
})();
