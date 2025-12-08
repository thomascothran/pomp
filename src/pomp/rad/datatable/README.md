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
- **Skeleton loading**: Smooth loading states

## Quick Start

```clojure
(ns myapp.table
  (:require [pomp.rad.datatable.table :as dt]
            [pomp.rad.datatable.in-memory-query :as imq]))

(def columns
  [{:key :name :label "Name" :type :text}
   {:key :email :label "Email" :type :text}
   {:key :role :label "Role" :type :enum}])

(def users
  [{:id 1 :name "Alice" :email "alice@example.com" :role "Admin"}
   {:id 2 :name "Bob" :email "bob@example.com" :role "User"}
   {:id 3 :name "Carol" :email "carol@example.com" :role "User"}])

(def query-fn (imq/query-fn users))

;; In your data handler, call the query function:
(let [{:keys [signals rows total-rows]} (dt/query current-signals query-params query-fn)]
  ;; Render the table with the results
  (dt/render {:id "users-table"
              :cols columns
              :rows rows
              :total-rows total-rows
              :page-size (get-in signals [:page :size])
              :page-current (get-in signals [:page :current])
              :page-sizes [10 25 50]
              :data-url "/api/users"
              :sort-state (:sort signals)
              :filters (:filters signals)}))
```

## API Reference

### `query-fn`

The `query-fn` is called by the datatable to fetch rows based on the current filter, sort, and pagination state.

#### Signature

```clojure
(query-fn signals) => {:rows [...] :total-rows n :page {...}}
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

### `make-handler`

Creates a Ring handler for datatable data updates. Handles filtering, sorting, pagination, column visibility, and grouping.

#### Signature

```clojure
(make-handler opts) => (fn [request] ring-response)
```

#### Required options

| Key | Type | Description |
|-----|------|-------------|
| `:query-fn` | `fn` | Query function (see above) |
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

#### Column spec shape

```clojure
{:key :name
 :label "Name"
 :type :text}  ;; :text or :enum
```

#### Example

```clojure
(def handler
  (make-handler {:query-fn (imq/query-fn my-data)
                 :columns [{:key :name :label "Name" :type :text}
                           {:key :status :label "Status" :type :enum}]
                 :id "my-table"
                 :data-url "/api/table/data"
                 :page-sizes [10 25 50]
                 :selectable? true}))
```
