if (!window.__pompDatatableCopyAttemptObserverInstalled) {
  window.addEventListener('keydown', function(evt) {
    const key = (evt.key || '').toLowerCase();
    if ((evt.ctrlKey || evt.metaKey) && key === 'c') {
      window.__pompDatatableCopyAttemptCount = (window.__pompDatatableCopyAttemptCount || 0) + 1;
    }
  }, true);
  window.__pompDatatableCopyAttemptObserverInstalled = true;
}
