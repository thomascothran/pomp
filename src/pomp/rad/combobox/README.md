# Combobox

Server-rendered combobox component backed by Datastar requests.

## Public API

### `pomp.combobox/render`

Renders the combobox markup (wrapper + input + results panel) with default DaisyUI rendering.

### `pomp.combobox/make-handler`

Builds a Ring GET handler that reads Datastar query signals, calls `query-fn`, normalizes items, and returns rendered results HTML.

## Config

For full usage (`render` + `make-handler`), pass:

- `:id` unique combobox id string
- `:query-fn` query function
- `:data-url` endpoint used by input `@get`
- `:render-html-fn` hiccup-to-HTML function used by handler responses

Key optional config:

- `:min-chars` (default `2`)
- `:debounce-ms` (default `250`)
- `:max-results` (default `10`)
- `:render-input-fn` custom input renderer
- `:render-results-fn` custom results renderer

## `query-fn` contract

```clojure
(fn [text req] -> [{:label :value} ...])
```

- `text` is the current query text
- `req` is the current Ring request
- return entries with both `:label` and `:value` (handler normalizes values to strings)

## Default usage

```clojure
(ns myapp.combobox
  (:require [clojure.string :as str]
            [pomp.combobox :as combobox]))

(defn fruit-query [text _req]
  (for [fruit ["Apple" "Apricot" "Banana" "Cherry"]
        :when (str/includes?
               (str/lower-case fruit)
               (str/lower-case text))]
    {:label fruit :value fruit}))

(def fruit-handler
  (combobox/make-handler
   {:id "fruit-search"
    :data-url "/demo/combobox/options"
    :query-fn fruit-query
    :render-html-fn str}))

(defn fruit-view []
  (combobox/render
   {:id "fruit-search"
    :data-url "/demo/combobox/options"
    :query-fn fruit-query
    :render-html-fn str}))
```

## Custom renderer usage

```clojure
(combobox/render
 {:id "city-search"
  :data-url "/demo/cities/options"
  :query-fn city-query
  :render-html-fn str
  :render-input-fn
  (fn [{:keys [ids query-path data-url debounce-key]}]
    [:input {:id (:input ids)
             :class "input input-bordered w-full"
             :placeholder "Search cities"
             (str "data-bind:" query-path) true
             debounce-key (str "@get('" data-url "')")}])
  :render-results-fn
  (fn [{:keys [ids items]}]
    [:ul {:id (:listbox ids) :class "menu bg-base-100 rounded-box shadow"}
     (for [{:keys [label]} items]
       [:li {:key label} [:span label]])])})
```

## Naming note

The scratch spike in `dev/scratch/autocomplete.clj` still uses older `autocomplete` naming. Library docs and public API use `combobox`.
