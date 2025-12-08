# Datatable

## `query-fn`

The `query-fn` is called by the datatable to fetch rows based on the current filter, sort, and pagination state.

### Signature

```clojure
(query-fn signals) => {:rows [...] :total-rows n :page {...}}
```

### Input: `signals` map

| Key        | Type                        | Description                              |
|------------|-----------------------------|------------------------------------------|
| `:filters` | `{keyword filter-spec}`     | Map of column-key to filter specification |
| `:sort`    | `[{:column str :direction str}]` | Vector of sort specs (currently uses first only) |
| `:page`    | `{:size int :current int}`  | Pagination state |
| `:group-by`| `keyword` (optional)        | Column to group by |

#### Filter spec shape (`filter-spec`)

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

### Output: result map

| Key          | Type   | Description                                      |
|--------------|--------|--------------------------------------------------|
| `:rows`      | `[...]`| The rows for the current page after filtering/sorting |
| `:total-rows`| `int`  | Total count of rows **after filtering** (for pagination) |
| `:page`      | `map`  | `{:size int :current int}` - possibly clamped page state |

## `make-handler`

Creates a Ring handler for datatable data updates. Handles filtering, sorting, pagination, column visibility, and grouping.

### Signature

```clojure
(make-handler opts) => (fn [request] ring-response)
```

### Options

#### Required

| Key | Type | Description |
|-----|------|-------------|
| `:query-fn` | `fn` | Query function (see above) |
| `:columns` | `[column-spec]` | Column specifications |
| `:id` | `string` | Table element ID |
| `:data-url` | `string` | URL for data fetches |

#### Optional

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

### Example

```clojure
(def handler
  (make-handler {:query-fn (in-memory-query/query-fn my-data)
                 :columns [{:key :name :label "Name" :type :text}
                           {:key :status :label "Status" :type :enum}]
                 :id "my-table"
                 :data-url "/api/table/data"
                 :page-sizes [10 25 50]
                 :selectable? true}))
```
