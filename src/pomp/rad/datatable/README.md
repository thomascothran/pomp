# Datatable

A server-rendered datatable component for Clojure web applications using Datastar and DaisyUI. Provides filtering, sorting, pagination, column reordering, column visibility, and row grouping—all driven by server-side rendering with SSE updates.

## Features

- **Filtering**: Text filters with operators (contains, equals, starts-with, etc.)
- **Sorting**: Click column headers to sort ascending/descending
- **Pagination**: Configurable page sizes with navigation
- **Column reordering**: Drag-and-drop column headers
- **Column visibility**: Show/hide columns via menu
- **Row grouping**: Group rows by any column
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

(def users-table-handler
  (dt/make-handler {:query-fn (imq/query-fn users)
                    :columns [{:key :name :label "Name" :type :text}
                              {:key :email :label "Email" :type :text}
                              {:key :role :label "Role" :type :enum}]
                    :id "users-table"
                    :data-url "/api/users"}))

;; Add to your routes
;; ["/api/users" users-table-handler]
```

## API Reference

### `make-handler`

Creates a Ring handler for datatable data updates. Handles filtering, sorting, pagination, column visibility, and grouping.

#### Signature

```clojure
(make-handler opts) => (fn [request] ring-response)
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
| `:group-by`| `keyword` (optional)        | Column to group by |

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

(def users-table-handler
  (dt/make-handler
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
