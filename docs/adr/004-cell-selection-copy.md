# ADR: Excel-like Cell Selection and Copy for Datatable

**Status:** Proposed
**Date:** 2025-12-15
**Context:** Pomp datatable component needs Excel-like cell range selection and clipboard copy functionality to improve data export workflows.

---

## Problem

Users working with tabular data frequently need to copy subsets of data to paste into spreadsheets (Excel, Google Sheets), text editors, or other applications. Currently, the datatable supports row selection via checkboxes, but this doesn't provide:

1. **Rectangular range selection** — selecting arbitrary cell ranges (e.g., rows 2-5 of columns B-D)
2. **Excel-compatible clipboard format** — tab-separated values that paste correctly into spreadsheet applications
3. **Keyboard shortcuts** — Ctrl+C to copy selected range

This is a standard feature in data grid components like AG Grid, and users expect it when working with tabular data.

---

## Expected Behaviors

Independent of implementation, the feature should support these behaviors:

### Selection Behaviors

| Behavior | Description |
|----------|-------------|
| **Click + Drag** | Click on a cell, drag to another cell, release to select rectangular range |
| **Shift + Click** | Click a cell, then Shift+click another cell to select range between them |
| **Click to Clear** | Clicking a single cell clears any existing range and starts a new potential selection |
| **Visual Feedback** | Selected cells are visually highlighted (background color, border, or both) |
| **Header Highlight** (optional) | Column/row headers for selected range are highlighted |

### Copy Behaviors

| Behavior | Description |
|----------|-------------|
| **Ctrl+C** | Copies selected range to clipboard |
| **Format** | Tab-separated values (`\t` between columns, `\n` between rows) for Excel compatibility |
| **Include Headers** (optional) | Option to include column headers in copied data |
| **Context Menu** (optional) | Right-click menu with "Copy" option |

### Scope Boundaries

| In Scope | Out of Scope (for now) |
|----------|------------------------|
| Single rectangular range selection | Multi-range selection (Ctrl+click to add ranges) |
| Copy to clipboard | Paste from clipboard |
| Visible cells only | Selection across paginated pages |
| Cell values as displayed | Custom copy formatting/processing |

---

## Implementation Options

### Option A: Datastar Signals + Server Rendering

**Approach:** Track selection state in Datastar signals; use `data-class` for highlighting; handle Ctrl+C in a small client-side script that reads cell values from DOM.

**Selection State (signals):**
```javascript
{
  datatable: {
    myTable: {
      selection: {
        start: { row: 2, col: 1 },  // or null
        end: { row: 5, col: 3 }     // or null
      }
    }
  }
}
```

**Highlighting:** Server renders cells with `data-class` that checks if cell is within selection range:
```clojure
{:data-class (str "{'bg-primary/20': $datatable.myTable.selection.start "
                  "&& rowIdx >= $datatable.myTable.selection.start.row "
                  "&& rowIdx <= $datatable.myTable.selection.end.row "
                  "&& colIdx >= $datatable.myTable.selection.start.col "
                  "&& colIdx <= $datatable.myTable.selection.end.col}")}
```

**Copy Handler:** Small JS snippet (inline or Squint component) listens for Ctrl+C, queries DOM for selected cells, formats as TSV, writes to clipboard.

**Pros:**
- Stays within existing Datastar architecture
- Selection state is reactive and inspectable
- Server controls what data attributes are available for copy

**Cons:**
- Complex `data-class` expressions for range checking
- Each cell needs row/col index data attributes
- Copy limited to what's in DOM (visible cells only)
- Mouse event handling (drag) may require custom JS anyway

---

### Option B: Client-Side Selection Manager (Squint Web Component)

**Approach:** Create a dedicated selection manager as a Squint-compiled web component that handles all selection logic client-side.

**Component Responsibilities:**
1. Track selection state (start/end cells)
2. Handle mouse events (mousedown, mousemove, mouseup)
3. Handle keyboard events (Shift+click, Ctrl+C)
4. Apply/remove highlight classes directly
5. Format and copy data to clipboard

**Integration:** Component attaches to table element, reads cell positions from data attributes:
```html
<table data-selection-manager>
  <tr data-row="0">
    <td data-col="0" data-value="Socrates">Socrates</td>
    <td data-col="1" data-value="5th BC">5th BC</td>
  </tr>
</table>
```

**Squint Component Sketch:**
```clojure
(defclass SelectionManager
  (field start-cell nil)
  (field end-cell nil)
  (field is-dragging false)

  (constructor [this]
    (.addEventListener this "mousedown" #(handle-mouse-down this %))
    (.addEventListener js/document "keydown" #(handle-keydown this %)))

  (method handle-mouse-down [this e]
    (when-let [cell (get-cell-from-event e)]
      (set! (.-start-cell this) cell)
      (set! (.-is-dragging this) true)))

  (method copy-to-clipboard [this]
    (let [data (get-selected-data this)
          tsv (format-as-tsv data)]
      (.writeText js/navigator.clipboard tsv))))
```

**Pros:**
- Clean separation of concerns
- Full control over UX (can match AG Grid exactly)
- No complex Datastar expressions
- Reusable component
- Can optimize for performance

**Cons:**
- More client-side code to maintain
- Selection state not in Datastar signals (harder to debug/inspect)
- Need to compile and bundle Squint component

---

### Option C: Hybrid Client Selection + Server Data Fetch

**Approach:** Client tracks selection coordinates; on copy, client requests data from server for those coordinates; server returns formatted data.

**Flow:**
1. Client selection manager tracks `{startRow, endRow, startCol, endCol}`
2. On Ctrl+C, client sends POST/GET to server: `/datatable/copy?startRow=2&endRow=5&startCol=1&endCol=3`
3. Server queries actual data (respecting current filters/sort), formats as TSV
4. Server returns TSV string; client writes to clipboard

**Server Endpoint:**
```clojure
(defn copy-handler [{:keys [query-params]}]
  (let [{:strs [startRow endRow startCol endCol]} query-params
        rows (fetch-rows-in-range startRow endRow)
        cols (get-columns-in-range startCol endCol)
        tsv (format-tsv rows cols)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body tsv}))
```

**Pros:**
- Server has access to all data (works across pagination)
- Server controls formatting (can handle special values, formatting)
- Can include data not visible in DOM (e.g., hidden columns, full precision numbers)
- Selection coordinates could be small payload

**Cons:**
- Network round-trip on every copy (latency)
- More complex architecture (client + server coordination)
- Need to handle offline/error cases
- Server needs to maintain context (current filters, sort, etc.) or client must send it

---

## Comparison Matrix

| Criterion | Option A (Signals) | Option B (Web Component) | Option C (Hybrid) |
|-----------|-------------------|-------------------------|-------------------|
| **Complexity** | Medium | Medium | High |
| **Datastar Native** | Yes | Partially | Partially |
| **Copy Across Pages** | No | No | Yes |
| **Latency on Copy** | None | None | Network RTT |
| **Client-Side Code** | Minimal | Moderate | Minimal |
| **Server-Side Code** | Minimal | None | Moderate |
| **Offline Support** | Yes | Yes | No |
| **Reusability** | Low | High | Medium |

---

## Recommendation

**Decision: Option A (Datastar Signals + Server Rendering)**

Option A aligns with our existing Datastar-first architecture. The implementation uses Datastar signals for state, `data-class` for highlighting, and small helper functions (called from Datastar expressions) for complex logic.

---

## Implementation Plan (Option A)

### Signal Structure

```javascript
datatable.myTable.cellSelectDragging = false    // true while mouse is down
datatable.myTable.cellSelectStart = null        // {row: 2, col: 1} or null
datatable.myTable.cellSelection = {}            // {"2-1": true, "3-1": true, ...}
```

Selection uses flat keys like `"2-1"` (row-col) for simple lookup in `data-class` expressions.

### Event Flow

| Event | Element | Handler |
|-------|---------|---------|
| `mousedown` | Each `<td>` | Inline expression: set dragging=true, start cell, initial selection |
| `mousemove` | `<table>` | Call `pompCellSelectMove()`, receive selection via custom event |
| `mouseup` | Window | Inline expression: set dragging=false |
| `keydown` | Window | Call `pompCellSelectCopy()` for Ctrl+C |

### Cell Rendering

Each data cell gets these attributes:

```clojure
[:td {:data-row row-idx
      :data-col col-idx
      :data-value (str value)  ;; for copy script to read
      :data-class (str "{'bg-primary/20': $datatable." table-id ".cellSelection['" row-idx "-" col-idx "']}")
      :data-on:mousedown (str 
        "$datatable." table-id ".cellSelectDragging = true; "
        "$datatable." table-id ".cellSelectStart = {row: " row-idx ", col: " col-idx "}; "
        "$datatable." table-id ".cellSelection = {'" row-idx "-" col-idx "': true}")}
 value]
```

### Table-Level Handlers

```clojure
[:table.table.table-sm
 {:data-on:mousemove 
  (str "pompCellSelectMove(evt, '" table-id "', "
       "$datatable." table-id ".cellSelectDragging, "
       "$datatable." table-id ".cellSelectStart)")
  
  :data-on:pompcellselection 
  (str "$datatable." table-id ".cellSelection = evt.detail.selection")
  
  :data-on:mouseup__window 
  (str "$datatable." table-id ".cellSelectDragging = false")
  
  :data-on:keydown__window 
  (str "pompCellSelectCopy(evt, '" table-id "', $datatable." table-id ".cellSelection)")}
 ...]
```

### JavaScript Helper Functions

Rendered inline with the table (or in a shared script):

```javascript
function pompCellSelectMove(evt, tableId, isDragging, start) {
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
}

function pompCellSelectCopy(evt, tableId, cellSelection) {
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
      const cell = table.querySelector(`td[data-row="${r}"][data-col="${c}"]`);
      cells.push(cell ? (cell.dataset.value || cell.textContent) : '');
    }
    lines.push(cells.join('\t'));
  }
  
  navigator.clipboard.writeText(lines.join('\n'));
}
```

### Implementation Tasks

1. **Update signal initialization** — Add `cellSelectDragging`, `cellSelectStart`, `cellSelection` to datatable signals in `demo/datatable.clj` and wherever signals are initialized.

2. **Modify `row/render-cell`** — Add `data-row`, `data-col`, `data-value`, `data-class`, and `data-on:mousedown` attributes. Need to thread row-idx and col-idx through the rendering context.

3. **Modify `body/render`** — Pass row index to row rendering functions.

4. **Modify `table/render`** — Add table-level event handlers (`mousemove`, `mouseup__window`, `keydown__window`, `pompcellselection`).

5. **Add JavaScript helpers** — Create `pompCellSelectMove` and `pompCellSelectCopy` functions, either inline in a `<script>` tag or in a separate JS file.

6. **Handle grouped rows** — Skip group header rows (don't add `data-row` to them).

7. **Exclude checkbox column** — Don't add cell selection attributes to the selection checkbox column.

8. **Add CSS** — Ensure `bg-primary/20` or similar class exists for selection highlighting.

### Design Decisions Made

- **Flat selection keys** (`"2-1"`) vs nested map — Flat is simpler for `data-class` lookups
- **Window-level Ctrl+C** — With guard check for active selection; matches Excel/AG Grid behavior
- **No headers in copy** — For now; can add later with Shift modifier
- **Always enabled** — No feature flag; cell selection is always available
- **`pomp` prefix** — All JS functions prefixed to avoid collisions

---

## Open Questions

1. **Multi-range selection** — Should we support Ctrl+click to add additional ranges? (AG Grid does, but adds complexity)
2. **Column header inclusion** — Always include? User toggle? Shift+Ctrl+C variant?
3. **Row numbers** — Include row numbers in copy output?
4. **Touch support** — How should selection work on touch devices?
5. **Accessibility** — How to make range selection keyboard-accessible for screen readers?

---

## References

- [AG Grid Clipboard Documentation](https://www.ag-grid.com/javascript-data-grid/clipboard/)
- [AG Grid Cell Selection Documentation](https://www.ag-grid.com/javascript-data-grid/cell-selection/)
- [Clipboard API (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/Clipboard_API)
- [Squint Documentation](https://github.com/squint-cljs/squint)
