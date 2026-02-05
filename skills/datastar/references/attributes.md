# Datastar Frontend Attributes & Actions

## Signals and expressions

Signals are Datastar's reactive state values. They are accessed in expressions using the `$` prefix (for example `$name`, `$user.email`, `$items.length`). Expressions inside `data-*` attributes are JavaScript expressions, so you can use operators, function calls, and template literals.

### Nested signals
Signals can hold nested objects. Access nested values with standard JavaScript dot or bracket notation.

```html
<div data-signals="{ user: { name: 'Ava', prefs: { theme: 'dark' } } }"></div>
<div data-text="$user.name"></div>
<div data-text="$user.prefs.theme"></div>
<div data-text="$user['prefs']['theme']"></div>
```

When patching on the server, nested objects are merge-patched, so you can update a subset of keys (for example, update only `user.prefs.theme`).

Examples:

```html
<!-- Bind text directly to a signal -->
<div data-text="$name"></div>

<!-- Use JavaScript template literal for interpolation -->
<div data-text="`Hello, ${$name}!`"></div>
```

Tip: for static text, just put it in the element body instead of `data-text`.

## Core bindings

### data-signals
Patch signals on element load. Use `__ifmissing` to avoid overwriting.
Example: `<div data-signals__ifmissing="{ count: 0 }"></div>`

### data-bind
Two-way bind element values to signals across inputs, checkboxes, radios, selects, textareas, and files.
Example: `<input type="text" data-bind:username />`

### data-computed
Create computed signals that update when dependencies change.
Example: `<div data-computed:total="$price * $quantity"></div>`

### data-effect
Run side effects on load and whenever dependencies change.
Example: `<div data-effect="console.log('Count', $count)"></div>`

### data-ref
Store a DOM element reference in a signal.
Example: `<input data-ref:searchInput />`

## Display and attributes

### data-text
Bind `textContent` to a JavaScript expression.
Example: `<div data-text="$name"></div>`

### data-show
Toggle visibility using `display: none` based on a boolean expression.
Example: `<div data-show="$isVisible">Content</div>`

### data-class
Add/remove classes based on expressions.
Example: `<div data-class="{ 'text-red': $hasError }"></div>`

### data-style
Bind inline styles from expressions.
Example: `<div data-style:color="$themeColor"></div>`

### data-attr
Bind arbitrary HTML attributes and data/ARIA attributes.
Example: `<button data-attr:disabled="$isLoading">Submit</button>`

## Events and lifecycle

### data-on
Attach event handlers with modifiers (`prevent`, `stop`, `once`, `window`, `outside`, `debounce`, `capture`, `passive`).
Example: `<form data-on:submit__prevent="@post('/save')"></form>`

### data-init
Run an expression when an element mounts. Supports `__delay` and `__viewtransition`.
Example: `<div data-init__delay.1s="@get('/api/data')"></div>`

### data-on-intersect
Run an expression when an element enters the viewport. Supports `__once`, `__full`, `__half`.
Example: `<div data-on-intersect__once="@get('/api/initial')"></div>`

### data-on-interval
Run an expression on a timer. Use `__duration.5s` and optional `.leading`.
Example: `<div data-on-interval__duration.5s.leading="@get('/api/poll')"></div>`

### data-indicator
Create a boolean signal that is true while a request is in flight.
Example: `<button data-on:click="@post('/save')" data-indicator:saving></button>`

## Debugging

### data-on-signal-patch
Run when signals are patched; supports `data-on-signal-patch-filter` for include/exclude patterns.
Example: `<div data-on-signal-patch="console.log(patch)"></div>`

### data-json-signals
Render signals as JSON for debugging. Supports `__terse` and include/exclude filters.
Example: `<pre data-json-signals__terse></pre>`

## Actions

### @get / @post / @put / @patch / @delete
Send SSE requests and stream server updates. Options include `contentType`, `selector`, `filterSignals`, `headers`, and `requestCancellation`.
Example: `<button data-on:click="@post('/save', { contentType: 'form' })"></button>`

### @setAll
Set matching signals to the same value.
Example: `<button data-on:click="@setAll('', { include: 'form\..*' })"></button>`

### @toggleAll
Toggle matching boolean signals.
Example: `<button data-on:click="@toggleAll({ include: 'flag\..*' })"></button>`

### @peek
Read a signal without creating a reactive dependency.
Example: `<div data-effect="const current = @peek(() => $count);"></div>`
