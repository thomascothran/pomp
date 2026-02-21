(ns pomp.graph-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [pomp.graph :as graph]))

(defn- sse-response->string
  [resp]
  (let [out (java.io.ByteArrayOutputStream.)]
    (.write_body_to_stream (:body resp) resp out)
    (.toString out "UTF-8")))

(deftest canonical-node-id-test
  (testing "canonical node ids use type:id shape and are deterministic"
    (let [node {:type :project :id "apollo"}
          from-node (graph/canonical-node-id node)
          from-args (graph/canonical-node-id :project "apollo")]
      (is (= "project:apollo" from-node)
          "Canonical id should include the node type prefix")
      (is (= from-node from-args)
          "Map and two-arg arities should produce the same canonical id")
      (is (= from-node (graph/canonical-node-id node))
          "Canonical id should be deterministic for the same input"))))

(deftest dedupe-helpers-test
  (testing "dedupe-nodes keeps first occurrence of each id"
    (is (= [{:id "project:apollo" :label "Apollo"}
            {:id "story:auth-hardening" :label "Auth hardening"}]
           (graph/dedupe-nodes
            [{:id "project:apollo" :label "Apollo"}
             {:id "project:apollo" :label "Apollo duplicate"}
             {:id "story:auth-hardening" :label "Auth hardening"}
             {:label "missing id"}]))))
  (testing "dedupe-edges collapses repeated edge ids across edge shapes"
    (is (= [{:data {:id "project:apollo|project/story|story:auth-hardening"
                    :source "project:apollo"
                    :target "story:auth-hardening"}}
            {:id "story:auth-hardening|story/task|task:jwt-rotation"
             :source "story:auth-hardening"
             :target "task:jwt-rotation"}]
           (graph/dedupe-edges
            [{:data {:id "project:apollo|project/story|story:auth-hardening"
                     :source "project:apollo"
                     :target "story:auth-hardening"}}
             {:data {:id "project:apollo|project/story|story:auth-hardening"
                     :source "project:apollo"
                     :target "story:auth-hardening"}}
             {:id "story:auth-hardening|story/task|task:jwt-rotation"
              :source "story:auth-hardening"
              :target "task:jwt-rotation"}
             {:id "story:auth-hardening|story/task|task:jwt-rotation"
              :source "story:auth-hardening"
              :target "task:jwt-rotation"}
             {:source "missing" :target "id"}])))))

(deftest make-handlers-config-contract-test
  (let [init-request {:body-params {:graph {:graph-id "pm-graph-v1"
                                            :seed-node-id "project:apollo"
                                            :relation :graph/neighbors}}}
        expand-request {:body-params {:graph {:expandRequest {:graph-id "pm-graph-v1"
                                                              :node-id "project:apollo"
                                                              :relation :graph/neighbors}}}}
        response-ok? (fn [response]
                       (and (= 200 (:status response))
                            (map? (:headers response))
                            (some? (:body response))))]
    (testing "make-handlers defaults to built-in data behavior"
      (let [handlers (graph/make-handlers)]
        (is (response-ok? ((:init handlers) init-request)))
        (is (response-ok? ((:expand handlers) expand-request)))))

    (testing "make-handlers accepts legacy top-level data overrides"
      (let [init-calls (atom [])
            expand-calls (atom [])
            handlers (graph/make-handlers
                      {:initialize-graph (fn [query _request]
                                           (swap! init-calls conj query)
                                           {:graph-id (:graph-id query)
                                            :seed-node-id (:seed-node-id query)
                                            :nodes []
                                            :edges []})
                       :find-node-neighbors (fn [query _request]
                                              (swap! expand-calls conj query)
                                              {:graph-id (:graph-id query)
                                               :node-id (:node-id query)
                                               :nodes []
                                               :edges []})})]
        ((:init handlers) init-request)
        ((:expand handlers) expand-request)
        (is (= [{:graph-id "pm-graph-v1"
                 :seed-node-id "project:apollo"
                 :relation :graph/neighbors
                 :filters nil}]
               @init-calls))
        (is (= [{:graph-id "pm-graph-v1"
                 :node-id "project:apollo"
                 :relation :graph/neighbors
                 :filters nil}]
               @expand-calls))))

    (testing "make-handlers accepts config map data overrides"
      (let [init-calls (atom [])
            expand-calls (atom [])
            handlers (graph/make-handlers
                      {:data {:initialize-graph (fn [query _request]
                                                  (swap! init-calls conj query)
                                                  {:graph-id (:graph-id query)
                                                   :seed-node-id (:seed-node-id query)
                                                   :nodes []
                                                   :edges []})
                              :find-node-neighbors (fn [query _request]
                                                     (swap! expand-calls conj query)
                                                     {:graph-id (:graph-id query)
                                                      :node-id (:node-id query)
                                                      :nodes []
                                                      :edges []})}})]
        ((:init handlers) init-request)
        ((:expand handlers) expand-request)
        (is (= [{:graph-id "pm-graph-v1"
                 :seed-node-id "project:apollo"
                 :relation :graph/neighbors
                 :filters nil}]
               @init-calls))
        (is (= [{:graph-id "pm-graph-v1"
                 :node-id "project:apollo"
                 :relation :graph/neighbors
                 :filters nil}]
               @expand-calls))))

    (testing "make-handlers merges partial config data overrides with defaults"
      (let [expand-calls (atom [])
            handlers (graph/make-handlers
                      {:data {:find-node-neighbors (fn [query _request]
                                                     (swap! expand-calls conj query)
                                                     {:graph-id (:graph-id query)
                                                      :node-id (:node-id query)
                                                      :nodes []
                                                      :edges []})}})]
        (is (response-ok? ((:init handlers) init-request))
            "Init should still use default initialize-graph when only expand is overridden")
        ((:expand handlers) expand-request)
        (is (= [{:graph-id "pm-graph-v1"
                 :node-id "project:apollo"
                 :relation :graph/neighbors
                 :filters nil}]
               @expand-calls))))))

(deftest make-handlers-data-overrides-script-payload-test
  (let [init-request {:body-params {:graph {:graph-id "pm-graph-v1"
                                            :seed-node-id "project:apollo"
                                            :relation :graph/neighbors}}}
        expand-request {:body-params {:graph {:expandRequest {:graph-id "pm-graph-v1"
                                                              :node-id "project:apollo"
                                                              :relation :graph/neighbors}}}}
        init-marker "node:custom-init"
        expand-marker "node:custom-expand"
        handlers (graph/make-handlers
                  {:data {:initialize-graph (fn [query _request]
                                              {:graph-id (:graph-id query)
                                               :seed-node-id (:seed-node-id query)
                                               :nodes [{:id init-marker :label "Custom init node"}]
                                               :edges []})
                          :find-node-neighbors (fn [query _request]
                                                 {:graph-id (:graph-id query)
                                                  :node-id (:node-id query)
                                                  :nodes [{:id expand-marker :label "Custom expand node"}]
                                                  :edges []})}})
        init-body (sse-response->string ((:init handlers) init-request))
        expand-body (sse-response->string ((:expand handlers) expand-request))]
    (testing "custom init data changes emitted graph payload while keeping init bridge flow"
      (is (str/includes? init-body init-marker)
          "Init SSE payload should include graph content returned by custom :initialize-graph")
      (is (str/includes? init-body "window.pompInitGraph(result)")
          "Init SSE payload should still call the stable init bridge function"))
    (testing "custom expand data changes emitted graph payload while keeping expand bridge flow"
      (is (str/includes? expand-body expand-marker)
          "Expand SSE payload should include graph content returned by custom :find-node-neighbors")
      (is (str/includes? expand-body "window.pompApplyGraphPatch(result)")
          "Expand SSE payload should still call the stable expand bridge function"))))

(deftest normalize-config-visual-fallback-test
  (let [normalize-config (resolve 'pomp.graph/normalize-config)]
    (is (some? normalize-config)
        "pomp.graph/normalize-config should exist so graph config can be normalized at the API boundary")
    (when normalize-config
      (let [normalized (normalize-config
                        {:visual {:node-types {:project {:label "Project"}}
                                  :edge-types {:project/story {:label "Has story"}}}})]
        (is (= {:label "Node"
                :shape "round-rectangle"
                :color "#64748b"
                :border-color "#475569"}
               (get-in normalized [:visual :node-types :default]))
            "Node visuals should always include deterministic default fallback values")
        (is (= {:label "Related"
                :line-color "#8ca0b3"
                :arrow-color "#8ca0b3"
                :label-color "#1f2937"}
               (get-in normalized [:visual :edge-types :default]))
            "Edge visuals should always include deterministic default fallback values")))))

(deftest make-graph-derives-legend-order-from-visual-config-test
  (let [make-graph (resolve 'pomp.graph/make-graph)]
    (is (some? make-graph)
        "pomp.graph/make-graph should exist so callers can consume normalized visual and legend outputs")
    (when make-graph
      (let [graph-system (make-graph
                          {:visual {:node-types {:default {:label "Node"}
                                                 :story {:label "Story"}
                                                 :task {:label "Task"}
                                                 :project {:label "Project"}}
                                    :legend-order [:task :project]}})]
        (is (= [:task :project :story]
               (mapv :type (get-in graph-system [:visual :legend :nodes])))
            "Legend should follow configured order first, then append unspecified node types deterministically")
        (is (= ["Task" "Project" "Story"]
               (mapv :label (get-in graph-system [:visual :legend :nodes])))
            "Legend labels should derive from visual node-type labels rather than static hardcoded legend entries")))))

(deftest make-graph-default-legend-order-test
  (let [make-graph (resolve 'pomp.graph/make-graph)]
    (is (some? make-graph)
        "pomp.graph/make-graph should exist so default legend behavior stays stable")
    (when make-graph
      (is (= [:ellipse :round-rectangle :rectangle :diamond :hexagon :vee :tag]
             (mapv :type (get-in (make-graph {}) [:visual :legend :nodes])))
          "Default legend should keep the existing node type order for no-config callers"))))

(deftest make-handlers-init-payload-includes-visual-config-test
  (let [init-request {:body-params {:graph {:graph-id "pm-graph-v1"
                                            :seed-node-id "project:apollo"
                                            :relation :graph/neighbors}}}
        handlers (graph/make-handlers
                  {:data {:initialize-graph (fn [query _request]
                                              {:graph-id (:graph-id query)
                                               :seed-node-id (:seed-node-id query)
                                               :nodes []
                                               :edges []})}
                   :visual {:node-types {:default {:label "Node"
                                                   :shape "round-rectangle"
                                                   :color "#64748b"
                                                   :border-color "#475569"}
                                         :project {:label "Project"
                                                   :shape "ellipse"
                                                   :color "#2563eb"
                                                   :border-color "#1d4ed8"}}
                            :legend-order [:project]}})
        init-body (sse-response->string ((:init handlers) init-request))]
    (is (str/includes? init-body "\"visual\"")
        "Init SSE payload should include visual config so the runtime can style from config")
    (is (str/includes? init-body "\"node-types\"")
        "Init SSE payload should carry node-type visual config")
    (is (str/includes? init-body "#2563eb")
        "Init SSE payload should include configured visual values, not runtime-only defaults")))

(deftest make-graph-default-detail-renderer-contract-test
  (let [make-graph (resolve 'pomp.graph/make-graph)]
    (is (some? make-graph)
        "pomp.graph/make-graph should exist so detail renderer defaults stay stable")
    (when make-graph
      (let [graph-system (make-graph {})
            details (:details graph-system)
            render-detail-card (:render-detail-card details)
            selected-node {:id "task:jwt-rotation"
                           :label "Rotate JWT"
                           :properties {:owner "auth" :priority "high"}}
            rendered-card (when (fn? render-detail-card)
                            (render-detail-card
                             {:graph-id "pm-graph-v1"
                              :ids {:root "pm-graph-v1-details"
                                    :properties "cy-selected-node-properties"}
                              :signals {:selected-node "graph.selectedNode"
                                        :selected-node-id "graph.selectedNodeId"}
                              :selected-node selected-node
                              :host-attrs {:data-pomp-graph-id "pm-graph-v1"}}))
            rendered-text (pr-str rendered-card)]
        (is (= :read-only (:default-mode details))
            "Default graph config should expose read-only details behavior")
        (is (fn? render-detail-card)
            "Default graph config should include a detail card renderer function")
        (is (vector? rendered-card)
            "Default detail renderer should return Hiccup")
        (is (str/includes? rendered-text "cy-selected-node-properties")
            "Default detail renderer should include the selected-node properties container")
        (is (str/includes? rendered-text "Rotate JWT")
            "Default detail renderer should render selected node values in read-only mode")))))

(deftest make-graph-custom-detail-renderer-ctx-contract-test
  (let [make-graph (resolve 'pomp.graph/make-graph)]
    (is (some? make-graph)
        "pomp.graph/make-graph should exist so custom detail rendering can be configured")
    (when make-graph
      (let [calls (atom [])
            custom-renderer (fn [ctx]
                              (swap! calls conj ctx)
                              [:section {:id (get-in ctx [:ids :root])}
                               (or (get-in ctx [:selected-node :label]) "No node selected")])
            graph-system (make-graph {:details {:render-detail-card custom-renderer}})
            render-detail-card (get-in graph-system [:details :render-detail-card])
            ctx {:graph-id "pm-graph-v1"
                 :ids {:root "pm-graph-v1-details"
                       :properties "cy-selected-node-properties"}
                 :signals {:selected-node "graph.selectedNode"
                           :selected-node-id "graph.selectedNodeId"}
                 :selected-node {:id "project:apollo" :label "Apollo"}
                 :host-attrs {:data-pomp-graph-id "pm-graph-v1"}}
            rendered (render-detail-card ctx)
            received (first @calls)]
        (is (fn? render-detail-card)
            "Custom detail renderer should be available in make-graph output")
        (is (every? #(contains? received %)
                    [:graph-id :ids :signals :selected-node :host-attrs])
            "Custom detail renderer should receive the stable ctx keys")
        (is (= (:selected-node ctx) (:selected-node received))
            "Custom detail renderer should receive selected-node context")
        (is (= [:section {:id "pm-graph-v1-details"} "Apollo"] rendered)
            "Custom detail renderer should be able to render selected-node-aware output")))))
