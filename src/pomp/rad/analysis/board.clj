(ns pomp.rad.analysis.board
  "Internal board builder for the public `pomp.rad.analysis/make-board` API.

  Purpose:
  - Build a reusable analysis board descriptor from chart definitions and board items.
  - Provide one board-level SSE handler that recomputes all board items on filter changes.

  Architecture:
  - The shell element is stable and keeps Datastar trigger wiring (`data-init`,
    `data-on-signal-patch`).
  - Only the body (`<board-id>__body`) is patched on refresh, so updates do not replace
    shell-level trigger attributes.
  - Item identity is normalized from `:instance-id` (if present) or `:chart-key` and then
    expanded into predictable ids (`__wrapper`, `__card`, `__mount-point`) plus identity
    class suffixes.

  Usage model:
  - Consumers typically call `pomp.rad.analysis/make-board`.
  - This namespace is the implementation for that API and is still safe to call directly
    when low-level customization is needed."
  (:require [clojure.string :as str]
            [pomp.rad.analysis.chart :as analysis.chart]
            [pomp.rad.analysis.handler :as analysis.handler]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [starfederation.datastar.clojure.api :as d*]))

(def ^:private default-card-class "card bg-base-100 border border-base-300 shadow-sm")
(def ^:private default-card-body-class "card-body")
(def ^:private default-board-id "chart-grid")
(def ^:private default-board-class "flex flex-wrap gap-6")
(def ^:private default-board-shell-class nil)
(def ^:private default-wrapper-class-value "w-full max-w-3xl")
(def ^:private default-mount-class-value "min-h-48 w-full")

(defn- normalize-instance-id
  [instance-id chart-key]
  (let [raw-id (or instance-id (name chart-key))]
    (-> (if (keyword? raw-id)
          (name raw-id)
          (str raw-id))
        (str/trim)
        (not-empty)
        (or (name chart-key)))))

(defn- identity-class-suffix
  [instance-id chart-key]
  (-> (normalize-instance-id instance-id chart-key)
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"(^-+|-+$)" "")
      (not-empty)
      (or "chart")))

(defn- with-identity-class
  [base-class class-prefix instance-id chart-key]
  (str base-class
       " "
       class-prefix
       "--"
       (identity-class-suffix instance-id chart-key)))

(defn default-render-board-item
  [{:keys [wrapper-id wrapper-class rendered-card]}]
  [:div {:id wrapper-id
         :class wrapper-class}
   rendered-card])

(defn board-body-id
  [board-id]
  (str board-id "__body"))

(defn- datatable-filter-source-path?
  [source-path]
  (and (= 3 (count source-path))
       (= :datatable (keyword (first source-path)))
       (= :filters (keyword (nth source-path 2)))))

(defn- default-signal-patch-filter
  [analysis-filter-source-path]
  (when (datatable-filter-source-path? analysis-filter-source-path)
    (let [table-id (name (keyword (second analysis-filter-source-path)))]
      (str "{ include: '^datatable\\." table-id "\\.filters(\\.|$)' }"))))

(defn default-render-board-shell
  [{:keys [board-id board-shell-class analysis-url body data-on-signal-patch-filter]}]
  [:div (merge {:id board-id
                :data-init (str "@post('" analysis-url "')")
                :data-on-signal-patch (str "@post('" analysis-url "')")}
               (when (seq board-shell-class)
                 {:class board-shell-class})
               (when data-on-signal-patch-filter
                 {:data-on-signal-patch-filter data-on-signal-patch-filter}))
   body])

(defn default-render-board
  [{:keys [board-id board-class board-body-class board-items render-board-item-fn render-board-shell-fn]
    :as board-context}]
  (render-board-shell-fn
   (assoc board-context
          :body [:div {:id (board-body-id board-id)
                       :class (or board-body-class board-class)}
                  (mapv render-board-item-fn board-items)])))

(defn make-board-handler
   [{:keys [board-id
            board-class
            board-shell-class
            board-body-class
            board-items
             render-board-fn
             render-board-item-fn
            render-board-shell-fn
            data-on-signal-patch-filter
            analysis-url
            render-html-fn]}]
  (fn [req]
    (->sse-response
     req
     {on-open
      (fn [sse]
        (let [results (mapv (fn [{:keys [chart-instance]}]
                              (let [context (analysis.handler/build-context req chart-instance)
                                    result (merge (select-keys context [:analysis/id
                                                                        :chart/id
                                                                        :chart/type
                                                                        :chart/renderer])
                                                  ((:analysis-fn chart-instance) context))
                                    rendered-card ((:render-card-fn chart-instance) result)]
                                {:rendered-card rendered-card
                                 :script ((:render-script-fn chart-instance) result)}))
                            board-items)
              board-body-html (render-html-fn
                               (render-board-fn {:board-id board-id
                                                  :board-class board-class
                                                  :board-shell-class board-shell-class
                                                  :board-body-class board-body-class
                                                  :board-items (mapv merge board-items results)
                                                  :render-board-item-fn render-board-item-fn
                                                  :render-board-shell-fn (fn [{:keys [body]}] body)
                                                 :analysis-url analysis-url
                                                 :data-on-signal-patch-filter data-on-signal-patch-filter}))]
          (d*/patch-elements! sse board-body-html)
          (doseq [{:keys [script]} results]
            (when (and (string? script)
                       (not (str/blank? script)))
              (d*/execute-script! sse script)))
          (d*/close-sse! sse)))})))

(defn- normalize-board-item
  [{:keys [chart-definitions
            make-chart-fn
            default-wrapper-class-fn
            default-mount-class]}
   {:keys [chart-key] :as item}]
  (when-not chart-key
    (throw (ex-info "Board item requires :chart-key" {:item item})))
  (let [base-chart-config (get chart-definitions chart-key)
        _ (when-not base-chart-config
            (throw (ex-info "Board item references unknown :chart-key"
                            {:chart-key chart-key
                             :known-chart-keys (keys chart-definitions)})))
        instance-id (normalize-instance-id (:instance-id item) chart-key)
        wrapper-id (str instance-id "__wrapper")
        card-id (str instance-id "__card")
        mount-id (str instance-id "__mount-point")
        wrapper-class (with-identity-class (or (:wrapper-class item)
                                               (default-wrapper-class-fn chart-key))
                        "chart-wrapper"
                        instance-id
                        chart-key)
        mount-class (with-identity-class (or (:mount-class item)
                                             default-mount-class)
                      "chart-mount"
                      instance-id
                      chart-key)
        chart-config (-> base-chart-config
                         (assoc :chart/card-id card-id
                                :chart/card-class default-card-class
                                :chart/card-body-class default-card-body-class
                                :chart/target-id mount-id
                                :chart/target-class mount-class)
                         (update :chart/spec-config merge (:spec-config item)))]
    (assoc item
           :instance-id instance-id
           :wrapper-id wrapper-id
           :card-id card-id
           :mount-id mount-id
           :wrapper-class wrapper-class
           :mount-class mount-class
           :chart-config chart-config
           :chart-instance (make-chart-fn chart-config))))

(defn make-board
  "Builds an analysis board descriptor map with a single SSE refresh endpoint.

  The returned descriptor includes:
  - `:handler`: one board-level SSE endpoint that recomputes and patches all board items.
  - `:render-board-fn`: shell/body renderer where shell stays stable and body is patchable.
  - `:board-items`: normalized item configs with derived ids and chart instances.

  Identity derivation:
  - Base identity uses `:instance-id` when provided, otherwise `(name :chart-key)`.
  - Derived ids are deterministic:
    - wrapper: `<base>__wrapper`
    - card: `<base>__card`
    - mount: `<base>__mount-point`

  Override hooks:
  - `:render-board-fn` controls full board rendering.
  - `:render-board-item-fn` controls each item wrapper/card composition.
  - `:render-board-shell-fn` controls shell trigger wrapper rendering.

  Examples:

  (def board
    (make-board
     {:analysis/id :library-analysis
      :analysis/filter-source-path [:datatable :library :filters]
      :chart-definitions
      {:genre {:chart/id :genre-frequency
               :chart/type :frequency
               :query/type :frequency
               :bucket-column :genre}}
      :board-items [{:chart-key :genre}]
      :table-name :books
      :sql/frequency-fn sql/frequency
      :execute! db/execute!
      :render-html-fn hiccup->html}))

  ;; explicit per-item instance identity
  (make-board
   {:analysis/id :library-analysis
    :analysis/filter-source-path [:datatable :library :filters]
    :chart-definitions {:genre {:chart/id :genre-frequency}}
    :board-items [{:chart-key :genre
                   :instance-id :genre-main}]
    :make-chart-fn (fn [_] {:analysis-fn (constantly {:chart/buckets []})
                            :render-card-fn (constantly [:div \"card\"])
                            :render-script-fn (constantly nil)})
    :render-html-fn pr-str})

  ;; customize shell and item rendering hooks
  (make-board
   {:analysis/id :library-analysis
    :analysis/filter-source-path [:datatable :library :filters]
    :chart-definitions {:genre {:chart/id :genre-frequency}}
    :board-items [{:chart-key :genre}]
    :render-board-shell-fn (fn [{:keys [board-id body]}]
                             [:section {:id board-id} body])
    :render-board-item-fn (fn [{:keys [wrapper-id rendered-card]}]
                            [:article {:id wrapper-id} rendered-card])
    :render-board-fn default-render-board
    :make-chart-fn (fn [_] {:analysis-fn (constantly {:chart/buckets []})
                            :render-card-fn (constantly [:div \"card\"])
                            :render-script-fn (constantly nil)})
    :render-html-fn pr-str})"
  ([shared-context board-spec]
   (make-board (merge shared-context board-spec)))
  ([{:keys [chart-definitions
            board-items
            board-id
            board-class
            board-shell-class
            board-body-class
            default-wrapper-class-fn
            default-mount-class
            render-board-fn
            render-board-item-fn
            render-board-shell-fn]
      :as config}]
   (let [shared-chart-config (select-keys config [:analysis/id
                                                   :analysis/filter-source-path
                                                   :render-html-fn
                                                   :table-name
                                                   :execute!
                                                   :sql/frequency-fn
                                                   :sql/histogram-fn
                                                   :sql/null-count-fn])
         make-chart-fn (or (:make-chart-fn config)
                           (fn [chart-config]
                             (analysis.chart/make-chart
                              (merge shared-chart-config chart-config))))
         default-wrapper-class-fn* (or default-wrapper-class-fn
                                       (constantly default-wrapper-class-value))
         default-mount-class* (or default-mount-class
                                  default-mount-class-value)
          render-board-item-fn* (or render-board-item-fn
                                    default-render-board-item)
           render-board-shell-fn* (or render-board-shell-fn
                                      default-render-board-shell)
           render-board-fn* (or render-board-fn
                                default-render-board)
           board-body-class* (or board-body-class
                                 board-class
                                 default-board-class)
           board-shell-class* (or board-shell-class
                                  default-board-shell-class)
           data-on-signal-patch-filter* (or (:data-on-signal-patch-filter config)
                                            (default-signal-patch-filter (:analysis/filter-source-path config)))
           board-items* (mapv (partial normalize-board-item {:chart-definitions chart-definitions
                                                            :make-chart-fn make-chart-fn
                                                           :default-wrapper-class-fn default-wrapper-class-fn*
                                                           :default-mount-class default-mount-class*})
                            board-items)
          board-config (merge config
                              {:board-id (or board-id default-board-id)
                               :board-class board-body-class*
                               :board-body-class board-body-class*
                               :board-shell-class board-shell-class*
                               :default-wrapper-class-fn default-wrapper-class-fn*
                               :default-mount-class default-mount-class*
                                :render-board-fn render-board-fn*
                               :render-board-item-fn render-board-item-fn*
                               :render-board-shell-fn render-board-shell-fn*
                               :data-on-signal-patch-filter data-on-signal-patch-filter*
                               :board-items board-items*})]
     (assoc board-config
            :handler (make-board-handler board-config)))))
