# Datatable

A server-rendered datatable component for Clojure web applications using Datastar and DaisyUI. Provides filtering, sorting, pagination, column reordering, column visibility, and row grouping—all driven by server-side rendering with SSE updates.

## Features

- **Filtering**: Text filters with operators (contains, equals, starts-with, etc.)
- **Sorting**: Click column headers to sort ascending/descending
- **Pagination**: Configurable page sizes with navigation
- **Column reordering**: Drag-and-drop column headers
- **Column visibility**: Show/hide columns via menu
- **Row grouping**: Group rows by one or more columns (ordered)
- **Row selection**: Optional checkbox selection
- **Editable cells**: Inline editing with save on Enter/blur
- **Skeleton loading**: Smooth loading states

## Quick Start

```clojure
(ns myapp.routes
  (:require [pomp.rad.datatable.handler :as dt]
            [pomp.rad.datatable.in-memory-query :as imq]))

(def users
  [{:id 1 :name "Alice" :email "alice@example.com" :role "Admin"}
   {:id 2 :name "Bob" :email "bob@example.com" :role "User"}
   {:id 3 :name "Carol" :email "carol@example.com" :role "User"}])

(def users-table-handlers
  (let [{:keys [get post]}
        (dt/make-handlers {:query-fn (imq/query-fn users)
                           :columns [{:key :name :label "Name" :type :text}
                                     {:key :email :label "Email" :type :text}
                                     {:key :role :label "Role" :type :enum}]
                           :id "users-table"
                           :data-url "/api/users"})]
    {:get get
     :post post}))

;; Add to your routes
;; ["/api/users" {:get (get users-table-handlers :get)
;;                 :post (get users-table-handlers :post)}]
```

## API Reference

### `make-handlers`

Creates method-specific Ring handlers for datatable data updates. Handles filtering, sorting, pagination, column visibility, and grouping.

#### Signature

```clojure
(make-handlers opts) => {:get (fn [request] ring-response)
                         :post (fn [request] ring-response)}
```

#### Required options

| Key | Type | Description |
|-----|------|-------------|
| `:query-fn` | `fn` | Query function (see below) |
| `:columns` | `[column-spec]` | Column specifications |
| `:id` | `string` | Table element ID |
| `:data-url` | `string` | URL for data fetches |

#### Optional options

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `:page-sizes` | `[int]` | `[10 25 100]` | Available page size options |
| `:selectable?` | `boolean` | `false` | Enable row selection |
| `:row-id-fn` | `fn` | `:id` | Function to get unique row ID |
| `:signal-path` | `[keyword]` | `[:datatable]` | Path in signals where table state lives |
| `:skeleton-rows` | `int` | `10` | Number of skeleton rows on initial load |
| `:save-fn` | `fn` | `nil` | Save function for editable cells (see below) |
| `:initial-signals-fn` | `fn` | `nil` | Seeds first-load table signals when request carries no table signals |

#### `:initial-signals-fn`

Use `:initial-signals-fn` to seed datatable state on the first load only.

- Signature: `(fn [request] signals-map-or-nil)`
- Called only when the incoming request does not include table signals.
- Returned map should match datatable signal shape (for example `:filters`, `:sort`, `:page`, `:groupBy`, `:globalTableSearch`, `:columns`, `:columnOrder`).
- Request-provided signals win over seeded defaults.

Example: hide a column by default and seed pagination for a saved view.

```clojure
(dt/make-handlers
 {:id "users-table"
  :columns [{:key :name :label "Name" :type :text}
            {:key :internal-score :label "Internal score" :type :number}]
  :query-fn query-users
  :data-url "/api/users"
  :initial-signals-fn
  (fn [_req]
    {:page {:size 25 :current 0}
     :columns {:internal-score {:visible false}}})})
```

#### Column spec shape

```clojure
{:key :name
 :label "Name"
 :type :text      ;; :text or :enum
 :editable true}  ;; optional - enables inline editing
```

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `:key` | `keyword` | Yes | Column identifier, matches data key |
| `:label` | `string` | Yes | Display label in header |
| `:type` | `keyword` | Yes | `:text` or `:enum` |
| `:editable` | `boolean` | No | Enable inline editing for this column |

### `query-fn`

The `query-fn` is called by the datatable to fetch rows based on the current filter, sort, and pagination state. Use `in-memory-query/query-fn` for in-memory data, or implement your own for database queries.

#### Signature

```clojure
(query-fn signals ring-request) => {:rows [...] :total-rows n :page {...}}
```

#### Input: `signals` map

| Key        | Type                        | Description                              |
|------------|-----------------------------|------------------------------------------|
| `:filters` | `{keyword filter-spec}`     | Map of column-key to filter specification |
| `:sort`    | `[{:column str :direction str}]` | Vector of sort specs (currently uses first only) |
| `:page`    | `{:size int :current int}`  | Pagination state |
| `:group-by`| `[keyword ...]` (optional)  | Ordered columns to group by |
| `:project-columns` | `[keyword ...]` (optional) | Server-derived projection hint for adapters that support column projection |

`:project-columns` is additive and optional. SQL-backed queries use it to project only required columns when present, and safely fall back to `SELECT *` when absent. Non-SQL adapters may ignore it.

#### Filter spec shape

```clojure
{:type "text"
 :op   "contains" | "not-contains" | "equals" | "not-equals" | "starts-with" | "ends-with" | "is-empty"
 :value "search string"}
```

#### Sort spec shape

```clojure
{:column "column-name"
 :direction "asc" | "desc"}
```

#### Page shape

```clojure
{:size 10      ;; rows per page
 :current 0}   ;; 0-indexed page number
```

#### Output: result map

| Key          | Type   | Description                                      |
|--------------|--------|--------------------------------------------------|
| `:rows`      | `[...]`| The rows for the current page after filtering/sorting |
| `:total-rows`| `int`  | Total count of rows **after filtering** (for pagination) |
| `:page`      | `map`  | `{:size int :current int}` - possibly clamped page state |

### Grouping behavior

- `:group-by` is ordered. For example, `[:century :school]` renders a group tree of `century -> school -> rows`.
- In grouped mode, sorting is controlled by the first grouped column.
- Pagination semantics depend on grouping depth:
  - single grouped column: pagination is by top-level group count
  - multiple grouped columns: pagination is by leaf row count (synthetic group rows are context)

### `save-fn`

The `save-fn` is called when a user edits a cell and saves (via Enter key or blur). Use `sql/save-fn` for SQL databases, or implement your own for custom persistence.

#### Signature

```clojure
(save-fn {:row-id row-id :col-key col-key :value value :req request}) => {:success true}
```

#### Input map

| Key | Type | Description |
|-----|------|-------------|
| `:row-id` | `string` | The row identifier (from `:row-id-fn`) |
| `:col-key` | `keyword` | The column key being edited |
| `:value` | `any` | The new value |
| `:req` | `map` | The Ring request (for context) |

#### SQL save function

The built-in SQL save function generates UPDATE statements:

```clojure
(require '[pomp.rad.datatable.query.sql :as sqlq])

(def execute! (fn [sqlvec] (jdbc/execute! my-datasource sqlvec)))

(def save! (sqlq/save-fn {:table "users"
                          :id-column :id}   ;; :id-column defaults to :id
                         execute!))
```

| Argument | Type | Description |
|----------|------|-------------|
| `config` | `map` | Configuration map (see below) |
| `execute!` | `fn` | `(fn [sqlvec] ...)` - executes SQL (same as query-fn) |

| Config Option | Type | Default | Description |
|---------------|------|---------|-------------|
| `:table` | `string` | required | Database table name |
| `:id-column` | `keyword` | `:id` | Column for WHERE clause |

## Editable Cells Example

Complete example with inline editing and SQL persistence:

```clojure
(ns myapp.routes
  (:require [pomp.datatable :as dt]
            [pomp.rad.datatable.query.sql :as sqlq]
            [next.jdbc :as jdbc]))

(def execute! (fn [sqlvec] (jdbc/execute! my-ds sqlvec)))

(def users-table-handlers
  (dt/make-handlers
   {:query-fn (sqlq/query-fn {:table-name "users"} execute!)
    :save-fn (sqlq/save-fn {:table "users"} execute!)
    :columns [{:key :name :label "Name" :type :text :editable true}
              {:key :email :label "Email" :type :text :editable true}
              {:key :role :label "Role" :type :enum}]
    :id "users-table"
    :data-url "/api/users"}))
```

### Editing behavior

- **Double-click** a cell to enter edit mode
- **Enter** saves and exits edit mode
- **Escape** cancels and reverts to original value
- **Blur** (clicking outside) saves and exits edit mode
- Non-editable columns ignore double-click

### Signal structure

For advanced customization, the editing state is stored in signals:

```javascript
{
  datatable: {
    "table-id": {
      editing: { rowId: "123", colKey: "name" },  // Currently editing
      submitInProgress: false,                     // Prevents double-submit
      cells: { "123": { "name": "New Value" } }   // Pending edits
    }
  }
}
```
