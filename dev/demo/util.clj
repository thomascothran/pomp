(ns demo.util
  (:require [dev.onionpancakes.chassis.core :as c]
            [clojure.data.json :as json]
            [starfederation.datastar.clojure.api :as d*]))

(defn ->html
  [hiccup]
  (c/html hiccup))

(defn page
  [& children]
  [:html {:data-theme "dracula"}
   [:head
    [:link {:href "/assets/output.css"
            :rel "stylesheet"}]
    [:script {:type "module"
              :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"}]
    [:script
     (c/raw
      "// Cell selection helper functions for datatable
window.pompCellSelectMove = function(evt, tableId, isDragging, start) {
  if (!isDragging || !start) return;
  
  const cell = evt.target.closest('td[data-row]');
  if (!cell) return;
  
  const row = parseInt(cell.dataset.row);
  const col = parseInt(cell.dataset.col);
  
  // Build rectangular selection
  const selection = {};
  const minRow = Math.min(start.row, row);
  const maxRow = Math.max(start.row, row);
  const minCol = Math.min(start.col, col);
  const maxCol = Math.max(start.col, col);
  
  for (let r = minRow; r <= maxRow; r++) {
    for (let c = minCol; c <= maxCol; c++) {
      selection[`${r}-${c}`] = true;
    }
  }
  
  evt.target.dispatchEvent(new CustomEvent('pompcellselection', {
    bubbles: true,
    detail: { selection }
  }));
};

window.pompCellSelectCopy = function(evt, tableId, cellSelection) {
  // Only handle Ctrl+C / Cmd+C
  if (!(evt.ctrlKey || evt.metaKey) || evt.key !== 'c') return;
  if (!cellSelection || Object.keys(cellSelection).length === 0) return;
  
  evt.preventDefault();
  
  // Find selection bounds
  const keys = Object.keys(cellSelection);
  const coords = keys.map(k => k.split('-').map(Number));
  const minRow = Math.min(...coords.map(c => c[0]));
  const maxRow = Math.max(...coords.map(c => c[0]));
  const minCol = Math.min(...coords.map(c => c[1]));
  const maxCol = Math.max(...coords.map(c => c[1]));
  
  // Build TSV from DOM
  const table = document.getElementById(tableId);
  const lines = [];
  
  for (let r = minRow; r <= maxRow; r++) {
    const cells = [];
    for (let c = minCol; c <= maxCol; c++) {
      const cell = table.querySelector(`td[data-row='${r}'][data-col='${c}']`);
      cells.push(cell ? (cell.dataset.value || cell.textContent) : '');
    }
    lines.push(cells.join('\\t'));
  }
  
  const tsvData = lines.join('\\n');
  
  // Use Clipboard API if available (secure context), otherwise fallback
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(tsvData);
  } else {
    // Fallback for non-secure contexts (HTTP)
    const textArea = document.createElement('textarea');
    textArea.value = tsvData;
    textArea.style.position = 'fixed';
    textArea.style.opacity = '0';
    document.body.appendChild(textArea);
    textArea.select();
    try {
      document.execCommand('copy');
    } finally {
      document.body.removeChild(textArea);
    }
  }
};")]]
   [:body.min-h-screen.m-0
    children]])

(comment
  (page [:div [:h1 "Hello"] [:p "World"]])
  (println (page [:div#app [:h1 "Test Page"]])))

(defn get-signals
  [req]
  (if (get-in req [:headers "datastar-request"])
    (some-> (d*/get-signals req)
            (json/read-str {:key-fn keyword}))
    req))
