# Report

`pomp.rad.report/make-report` composes a datatable and an analysis board behind one SSE endpoint so a single request can refresh both table content and charts.

## What it builds

`make-report` returns a descriptor map:

```clojure
{:datatable <datatable-config-with-data-url>
 :analysis  <analysis-board-descriptor>
 :handler   (fn [ring-req] ring-response)}
```

- `:handler` is the endpoint you mount for `:get` and `:post`.
- normal refresh requests emit datatable updates first, then analysis/chart updates.
- export requests (`?action=export`) emit only datatable export events.

## Quick start

```clojure
(ns myapp.report
  (:require [pomp.rad.analysis :as analysis]
            [pomp.rad.datatable.query.sql :as sqlq]
            [pomp.rad.report :as report]))

(def data-url "/reports/books/data")

(defn make-routes [execute!]
  (let [rows!  (sqlq/rows-fn  {:table-name "books"} execute!)
        count! (sqlq/count-fn {:table-name "books"} execute!)
        rpt    (report/make-report
                {:data-url data-url
                 :datatable
                 {:id "books-table"
                  :columns [{:key :id :label "ID" :type :number}
                            {:key :title :label "Title" :type :string}
                            {:key :genre :label "Genre" :type :string}]
                  :rows-fn rows!
                  :count-fn count!
                  :render-html-fn hiccup->html}
                 :analysis
                 {:analysis/id "books-analysis"
                  :analysis/filter-source-path [:datatable :books-table :filters]
                  :table-name "books"
                  :execute! execute!
                  :chart-definitions
                  {:genre (analysis/frequency-chart
                           {:id "genre-frequency"
                            :title "Genre frequency"
                            :bucket-column :genre})}
                  :board-items [{:chart-key :genre}]
                  :board-id "books-board"
                  :render-html-fn hiccup->html}})]
    [["/reports/books" page-handler]
     [data-url {:get (:handler rpt)
                :post (:handler rpt)}]]))
```

Page wiring pattern:

- board shell triggers initial load with `@post(data-url)`.
- datatable container triggers initial table load (often `@get(data-url)`).
- later datatable interactions post to the same `data-url`, and the report handler emits both table and chart updates in one SSE cycle.

## `make-report` API

Signature:

```clojure
(make-report config) => {:datatable ... :analysis ... :handler fn}
```

Top-level `config` keys:

| Key | Required | Description |
| --- | --- | --- |
| `:data-url` | Yes | Shared endpoint used by report refreshes. |
| `:datatable` | Yes | Datatable config passed to `pomp.rad.datatable/make-emitters` (with `:data-url` injected). |
| `:analysis` | Yes | Analysis board config passed to `pomp.rad.analysis/make-board` (with `:analysis-url` injected from `:data-url`). |

### Required vs optional keys in nested configs

`make-report` delegates validation and behavior to datatable and analysis constructors.

- for datatable config requirements/optionality, see `pomp.rad.datatable/make-emitters` in `src/pomp/rad/datatable.clj`.
- for analysis board requirements/optionality, see `pomp.rad.analysis/make-board` in `src/pomp/rad/analysis.clj` and internals in `src/pomp/rad/analysis/board.clj`.

Practical minimums for default behavior:

- datatable: `:id`, `:columns`, `:rows-fn`, `:render-html-fn`.
- analysis board: `:chart-definitions`, `:board-items`, `:render-html-fn` and (unless using custom `:make-chart-fn`) the normal analysis query keys (`:analysis/id`, `:analysis/filter-source-path`, `:table-name`, `:execute!`, plus chart query fields such as `:bucket-column`).

Common optional keys you will likely use:

- datatable optional keys: `:count-fn`, `:table-search-query`, `:page-sizes`, `:selectable?`, `:save-fn`, `:initial-signals-fn`, `:export-stream-rows-fn`, `:export-filename-fn`, `:render-table-search`, `:render-export`.
- analysis board optional keys: `:board-id`, `:board-class`, `:board-shell-class`, `:board-body-class`, `:default-wrapper-class-fn`, `:default-mount-class`, `:render-board-fn`, `:render-board-item-fn`, `:render-board-shell-fn`, `:data-on-signal-patch-filter`.

Notes:

- `make-report` always sets datatable `:data-url` to top-level `:data-url`.
- `make-report` always sets analysis `:analysis-url` to top-level `:data-url`.

## Emitter and SSE lifecycle

The flow lives in `src/pomp/rad/report.clj`:

1. `:handler` checks `?action=export`.
2. export path:
   - run datatable export emitter.
   - close SSE.
3. refresh path:
   - choose datatable emitter for request method (`:post` preferred, fallback to `:get`).
   - run datatable emitter.
   - run analysis board emitter (`:emit!` from `make-board`).
   - close SSE.

Important behavior:

- report-level emitters are called sequentially in one request thread (not parallel).
- the datatable query/render emitter internally overlaps row and count work with a `future`, then waits for completion before returning.
- because report closes SSE after both emitter calls return, connection close is the completion boundary for the composed update.

## Related references

- report composition: `src/pomp/rad/report.clj`
- datatable emitters: `src/pomp/rad/datatable.clj`
- datatable query/render internals: `src/pomp/rad/datatable/handler/query_render.clj`
- datatable export internals: `src/pomp/rad/datatable/handler/export.clj`
- analysis public API: `src/pomp/rad/analysis.clj`
- analysis board emitter internals: `src/pomp/rad/analysis/board.clj`
- runnable integration example: `dev/demo/datatable_charts.clj`
