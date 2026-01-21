## Usage

### Basic Concepts

By convention SDK adapters provide a single `->sse-response` function. This
function returns a valid ring response tailored to work with the ring
adapter it is made for. This function will receive an implementation of
`SSEGenerator` protocol also tailored to the ring adapter used.

You then use the Datastar SDK functions with the SSE generator.

### Short example

Start by requiring the main API and an adapter. With Http-kit for instance:

```clojure
(require '[starfederation.datastar.clojure.api :as d*]
         '[starfederation.datastar.clojure.adapter.ring
           :refer [->sse-response on-open]])

```

Using the adapter you create ring responses in your handlers:

```clojure
(defn sse-handler [request]
  (->sse-response request
    {on-open
     (fn [sse-gen]
       (d*/patch-elements! sse-gen "<div>test</div>")
       (d*/close-sse! sse-gen))}))

```

In the callback we use the SSE generator `sse-gen` with the Datastar SDK functions.

Depending on the adapter you use, you can keep the SSE generator open by storing
it somewhere and use it later:

```clojure
(def !connections (atom #{}))


(defn sse-handler [request]
  (->sse-response request
    {on-open
     (fn [sse-gen]
       (swap! !connections conj sse-gen))

     on-close
     (fn [sse-gen status]
       (swap! !connections disj sse-gen))}))


(defn broadcast-elements! [elements]
  (doseq [c @!connections]
    (d*/patch-elements! c elements)))

```

### Advanced features

This SDK is essentially a tool to manage SSE connections with helpers to format
events the way the Datastar framework expects them on the front end.

It provides advanced functionality for managing several aspects of SSE.

You can find more information in several places:

- the docstings for the `->sse-response` function you are using.
- the [SSE design notes document](/doc/SSE-design-notes.md) details
  what considerations are taken into account in the SDK.
- the [write profiles document](/doc/Write-profiles.md) details the
  tools the SDK provides to control the buffering behaviors of a SSE stream and
  how to use compression.
- the [adapter implementation guide](/doc/implementing-adapters.md)
  lists the conventions by which SDK adapters are implemented if the need to
  implement your own ever arises.
