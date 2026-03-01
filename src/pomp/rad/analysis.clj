(ns pomp.rad.analysis
  "Public API for Datastar-driven analysis charts and boards.

  Use this namespace when you want to:
  - build chart specs from concise chart maps
  - build chart and board descriptors with SSE handlers
  - keep chart definitions declarative and composable"
  (:require [pomp.rad.analysis.board :as analysis.board]
            [pomp.rad.analysis.chart :as analysis.chart]
            [pomp.rad.analysis.handler :as analysis.handler]))

(defn make-analysis-fn
  "Returns a function that computes chart data for one request context.

  Required keys in `shared-context`:
  - `:execute!` (fn that executes SQL and returns rows)
  - `:table-name` (unless `chart-spec` provides `:table-name`)

  Optional keys in `shared-context`:
  - `:sql/frequency-fn`
  - `:sql/histogram-fn`
  - `:sql/null-count-fn`

  Required keys in `chart-spec` for default SQL flow:
  - one of `:query/type` or `:chart/type`
  - `:bucket-column`
  - `:bucket-size` when histogram query needs bucketing

  Optional keys in `chart-spec`:
  - `:table-name` (overrides shared table)
  - `:rows->values-fn`
  - `:values->buckets-fn`
  - `:build-spec-fn`
  - `:result-fn`
  - `:include-null-count?`
  - `:chart/spec` and `:chart/spec-config`

  The returned function has shape `(fn [context] {:chart/spec ... :chart/buckets ...})`."
  [shared-context chart-spec]
  (analysis.chart/make-analysis-fn shared-context chart-spec))

(defn make-chart
  "Builds a chart descriptor map with analysis, renderers, and handler.

  Arity 1, `[config]`, expects the full chart config map.
  Arity 2, `[shared-context chart-config]`, merges shared context with
  chart-specific config before building.

  Required keys for default end-to-end behavior:
  - `:analysis/id`
  - `:analysis/filter-source-path`
  - `:chart/id`
  - `:render-html-fn`
  - either `:analysis-fn` OR enough data to build one:
    - `:execute!`
    - one of `:query/type` or `:chart/type`
    - `:bucket-column`
    - `:table-name`

  Optional keys:
  - `:render-card-fn`
  - `:render-chart-fn`
  - `:render-script-fn`
  - `:chart/target-id`
  - `:chart/target-class`
  - SQL builder overrides (`:sql/frequency-fn`, `:sql/histogram-fn`, `:sql/null-count-fn`)
  - chart transform hooks accepted by `make-analysis-fn`

  Returned map includes keys such as `:analysis-fn`, `:render-card-fn`,
  `:render-script-fn`, and `:handler`."
  ([shared-context chart-config]
   (analysis.chart/make-chart shared-context chart-config))
  ([config]
   (analysis.chart/make-chart config)))

(defn frequency-chart
  "Builds a frequency chart spec from a concise chart map.

  Required keys in `concise-chart` for default query flow:
  - `:id` (or pass through `:chart/id`)
  - `:bucket-column`

  Optional keys in `concise-chart`:
  - `:title` (or `:chart/title`)
  - `:target-id` (or `:chart/target-id`)
  - `:spec` (or `:chart/spec-config`)
  - any additional `make-chart` / `make-analysis-fn` chart keys

  `concise-chart` is shorthand input. These short keys are translated:
  `:id` -> `:chart/id`, `:title` -> `:chart/title`,
  `:target-id` -> `:chart/target-id`, `:spec` -> `:chart/spec-config`.
  Any other keys pass through unchanged.

  `overrides` is an optional map merged last, so it wins over defaults and
  over keys provided in `concise-chart`.

  Defaults from this constructor:
  `:chart/type :frequency`, `:query/type :frequency`,
  `:chart/value-sort :frequency`.

  Example:
  `(frequency-chart {:id :school-frequency
                     :title :school-frequency-title
                     :bucket-column :school}
                    {:chart/target-class :full-width})`"
  ([concise-chart]
   (analysis.chart/frequency-chart concise-chart))
  ([concise-chart overrides]
   (analysis.chart/frequency-chart concise-chart overrides)))

(defn pie-chart
  "Builds a pie chart spec from a concise chart map.

  Required keys in `concise-chart` for default query flow:
  - `:id` (or pass through `:chart/id`)
  - `:bucket-column`

  Optional keys in `concise-chart`:
  - `:title` (or `:chart/title`)
  - `:target-id` (or `:chart/target-id`)
  - `:spec` (or `:chart/spec-config`)
  - any additional `make-chart` / `make-analysis-fn` chart keys

  `concise-chart` and `overrides` follow the same semantics as
  `frequency-chart`.

  Defaults from this constructor:
  `:chart/type :pie`, `:query/type :frequency`, `:chart/value-sort :frequency`.

  Example:
  `(pie-chart {:id :region-pie
               :title :region-share
               :bucket-column :region})`"
  ([concise-chart]
   (analysis.chart/pie-chart concise-chart))
  ([concise-chart overrides]
   (analysis.chart/pie-chart concise-chart overrides)))

(defn histogram-chart
  "Builds a histogram chart spec from a concise chart map.

  Required keys in `concise-chart` for default query flow:
  - `:id` (or pass through `:chart/id`)
  - `:bucket-column`
  - `:bucket-size` (required by default histogram SQL builder)

  Optional keys in `concise-chart`:
  - `:title` (or `:chart/title`)
  - `:target-id` (or `:chart/target-id`)
  - `:spec` (or `:chart/spec-config`)
  - `:include-null-count?`
  - any additional `make-chart` / `make-analysis-fn` chart keys

  `concise-chart` and `overrides` follow the same semantics as
  `frequency-chart`.

  Defaults from this constructor:
  `:chart/type :histogram`, `:query/type :histogram`,
  `:chart/value-shape :histogram-range`, `:chart/value-sort :bucket-asc`.

  Example:
  `(histogram-chart {:id :influence-histogram
                     :title :influence-distribution
                     :bucket-column :influence
                     :bucket-size 10}
                    {:include-null-count? true})`"
  ([concise-chart]
   (analysis.chart/histogram-chart concise-chart))
  ([concise-chart overrides]
   (analysis.chart/histogram-chart concise-chart overrides)))

(defn make-chart-handler
  "Builds a chart-level SSE handler from a chart config map.

  Required keys in `config`:
  - `:analysis-fn`
  - `:render-chart-fn`
  - `:render-html-fn`

  Optional but typically provided for metadata/filter extraction:
  - `:analysis/id`
  - `:analysis/filter-source-path`
  - `:chart/id`
  - `:chart/type`
  - `:render-script-fn`

  This is the low-level handler entrypoint used by `make-chart`."
  [config]
  (analysis.handler/make-chart-handler config))

(defn make-board
  "Builds an analysis board descriptor map with a board-level `:handler`.

  Arity 1, `[config]`, accepts a fully merged board config.
  Arity 2, `[shared-context board-config]`, merges shared context with board
  config.

  Required keys in `board-config`:
  - `:chart-definitions` map (`chart-key` -> chart spec)
  - `:board-items` vector where each item includes `:chart-key`
  - `:render-html-fn`

  Required for default shell refresh wiring:
  - `:analysis-url`

  Required unless you provide `:make-chart-fn`:
  - keys needed by `make-chart` (`:analysis/id`, `:analysis/filter-source-path`,
    `:table-name`, `:execute!`, and chart query keys)

  Optional keys:
  - `:board-id`, `:board-class`, `:board-shell-class`, `:board-body-class`
  - `:default-wrapper-class-fn`, `:default-mount-class`
  - `:render-board-fn`, `:render-board-item-fn`, `:render-board-shell-fn`
  - `:data-on-signal-patch-filter`
  - per-item overrides (`:instance-id`, `:wrapper-class`, `:mount-class`, `:spec-config`)

  Example:
  `(make-board {:analysis/id :philosophers
                :analysis/filter-source-path [:datatable :philosophers :filters]
                :chart-definitions {:school (frequency-chart {:id :school
                                                              :bucket-column :school})}
                :board-items [{:chart-key :school}]})`"
  ([shared-context board-config]
   (analysis.board/make-board shared-context board-config))
  ([config]
   (analysis.board/make-board config)))
