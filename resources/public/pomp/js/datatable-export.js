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
