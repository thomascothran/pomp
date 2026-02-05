# Datastar Clojure SDK (Ring)

## Quick start

Require the API and adapter:

```clojure
(require '[starfederation.datastar.clojure.api :as d*]
         '[starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open on-close]])
```

Create a one-shot SSE response:

```clojure
(defn handler [req]
  (->sse-response req
                  {on-open
                   (fn [sse]
                     (d*/patch-elements! sse "<div>Updated</div>")
                     (d*/close-sse! sse))}))
```

## Datastar request handling

When a route returns both HTML and Datastar responses, detect the header:

```clojure
(if (get-in req [:headers "datastar-request"])
  (->sse-response req {on-open (fn [sse] ...)})
  (render-full-page req))
```

In this project, requests are parsed by `muuntaja/format-request-middleware` (see `dev/dev/http.clj`). Prefer `:body-params` for non-GET Datastar requests, and `:query-params` for GET requests where Datastar sends `datastar=<json>`.

```clojure
(defn datastar-signals [req]
  (when (get-in req [:headers "datastar-request"])
    (or (:body-params req)
        (some-> (get-in req [:query-params "datastar"])
                (json/read-str {:key-fn keyword}))
        {})))
```

Only fall back to `d*/get-signals` if you are outside the middleware stack or need to support raw request bodies.

## Sending updates

Use `d*/patch-signals!` to set or update signals from the backend. Datastar uses JSON Merge Patch semantics: nested maps merge, and `nil` removes a key.

```clojure
(d*/patch-signals! sse (json/write-str {:user {:name "Ava"
                                              :prefs {:theme "dark"}}}))
;; Update a nested field only
(d*/patch-signals! sse (json/write-str {:user {:prefs {:theme "light"}}}))
;; Remove a signal
(d*/patch-signals! sse (json/write-str {:temp nil}))
```

- `d*/patch-elements!` sends HTML fragments for morphing.
- `d*/patch-signals!` sends JSON merge patches (stringified JSON).
- `d*/execute-script!` sends a script for execution.
- `d*/close-sse!` ends a one-shot SSE stream.

## Long-lived streams

Store generators if the adapter supports it:

```clojure
(defonce !connections (atom #{}))

(defn sse-handler [req]
  (->sse-response req
                  {on-open (fn [sse] (swap! !connections conj sse))
                   on-close (fn [sse _status] (swap! !connections disj sse))}))

(defn broadcast! [html]
  (doseq [sse @!connections]
    (d*/patch-elements! sse html)))
```
