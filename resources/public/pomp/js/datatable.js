if (!window.__pompDatatableCopyAttemptObserverInstalled) {
  window.addEventListener('keydown', function(evt) {
    const key = (evt.key || '').toLowerCase();
    if ((evt.ctrlKey || evt.metaKey) && key === 'c') {
      window.__pompDatatableCopyAttemptCount = (window.__pompDatatableCopyAttemptCount || 0) + 1;
    }
  }, true);
  window.__pompDatatableCopyAttemptObserverInstalled = true;
}

window.__pompDatatableExportSessions = window.__pompDatatableExportSessions || {};

window.pompDatatableExportBegin = function(payload) {
  const tableId = payload && payload.tableId;
  if (!tableId) return;

  const header = (payload && payload.header) || '';
  window.__pompDatatableExportSessions[tableId] = {
    filename: (payload && payload.filename) || (tableId + '-export.csv'),
    chunks: [header]
  };
};

window.pompDatatableExportAppend = function(payload) {
  const tableId = payload && payload.tableId;
  if (!tableId) return;

  const session = window.__pompDatatableExportSessions[tableId];
  if (!session) return;

  session.chunks.push((payload && payload.chunk) || '');
};

window.pompDatatableExportFinish = function(payload) {
  const tableId = payload && payload.tableId;
  if (!tableId) return;

  const session = window.__pompDatatableExportSessions[tableId];
  if (!session) return;

  const blob = new Blob(session.chunks, { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = (payload && payload.filename) || session.filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);

  delete window.__pompDatatableExportSessions[tableId];
};

window.pompDatatableExportFail = function(payload) {
  const tableId = payload && payload.tableId;
  if (tableId) {
    delete window.__pompDatatableExportSessions[tableId];
  }
  const message = (payload && payload.message) || 'Datatable export failed';
  console.error(message);
};

// Cell selection helper functions for datatable
window.pompCellMouseDown = function(evt, tableSignals, options) {
  if (evt.target.closest('input, button, select, textarea')) {
    return;
  }

  const opts = options || {};

  tableSignals.cellSelection = [opts.cellSelectionKey];
  tableSignals._cellSelectDragging = true;
  tableSignals._cellSelectStart = { row: opts.row, col: opts.col };
};

window.pompCellSelectMove = function(evt, tableId, isDragging, start) {
  if (!isDragging || !start) return;

  const cell = evt.target.closest('td[data-row]');
  if (!cell) return;

  const row = parseInt(cell.dataset.row);
  const col = parseInt(cell.dataset.col);

  if (row === start.row && col === start.col) return;

  // Build rectangular selection
  const selection = [];
  const minRow = Math.min(start.row, row);
  const maxRow = Math.max(start.row, row);
  const minCol = Math.min(start.col, col);
  const maxCol = Math.max(start.col, col);

  for (let r = minRow; r <= maxRow; r++) {
    for (let c = minCol; c <= maxCol; c++) {
      selection.push(`${r}-${c}`);
    }
  }

  evt.target.dispatchEvent(new CustomEvent('pompcellselection', {
    bubbles: true,
    detail: { selection }
  }));
};

window.pompCellSelectCopy = function(evt, tableId, cellSelection) {
  // Only handle Ctrl+C / Cmd+C
  const key = (evt.key || '').toLowerCase();
  if (!(evt.ctrlKey || evt.metaKey) || key !== 'c') return;
  if (!cellSelection || cellSelection.length === 0) return;

  evt.preventDefault();

  // Find selection bounds
  const coords = cellSelection.map(k => k.split('-').map(Number));
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
    lines.push(cells.join('\t'));
  }

  const tsvData = lines.join('\n');

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
};
