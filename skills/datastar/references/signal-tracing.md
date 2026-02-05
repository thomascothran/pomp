# Datastar Signal Tracing

## Client-side patch logging

Use `data-on-signal-patch` to observe patches as they arrive and apply filters to reduce noise.

```html
<div data-on-signal-patch="console.log('patch', patch)"></div>
<div data-on-signal-patch
     data-on-signal-patch-filter="{ include: 'user\..*', exclude: '.*password.*' }">
</div>
```

## On-screen inspection

Use `data-json-signals` to render live signal state for debugging.

```html
<pre data-json-signals></pre>
<pre data-json-signals__terse></pre>
<pre data-json-signals="{ include: 'form\..*' }"></pre>
```

## Server-side logging

Log outgoing patches in backend handlers right before calling `patch-elements!` or `patch-signals!` to capture what is being sent. Pair with client-side `data-on-signal-patch` logs to correlate the full round trip.
