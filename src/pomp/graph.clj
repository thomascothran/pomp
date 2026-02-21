(ns pomp.graph
  (:require [pomp.rad.graph.handler :as handler]
            [pomp.rad.graph.state :as state]))

(def ^:private default-node-visual
  {:label "Node"
   :shape "round-rectangle"
   :color "#64748b"
   :border-color "#475569"})

(def ^:private default-node-types
  {:default default-node-visual
   :project {:label "project"
             :shape "ellipse"
             :color "#2563eb"
             :border-color "#1d4ed8"}
   :story {:label "story"
           :shape "round-rectangle"
           :color "#0f766e"
           :border-color "#115e59"}
   :task {:label "task"
          :shape "rectangle"
          :color "#ea580c"
          :border-color "#c2410c"}
   :subtask {:label "subtask"
             :shape "diamond"
             :color "#7c3aed"
             :border-color "#6d28d9"}
   :developer {:label "developer"
               :shape "hexagon"
               :color "#0891b2"
               :border-color "#0e7490"}
   :qa {:label "qa"
        :shape "vee"
        :color "#65a30d"
        :border-color "#4d7c0f"}
   :product-owner {:label "product-owner"
                   :shape "tag"
                   :color "#be123c"
                   :border-color "#9f1239"}})

(def ^:private default-edge-visual
  {:label "Related"
   :line-color "#8ca0b3"
   :arrow-color "#8ca0b3"
   :label-color "#1f2937"})

(defn- default-render-detail-card
  [{:keys [ids selected-node]}]
  (let [selected-node (or selected-node {})
        properties (:properties selected-node)]
    [:section.card.border.border-base-300.bg-base-100.shadow-sm
     [:div.card-body.gap-3.p-4
      [:h2.card-title.text-base (or (:label selected-node) "No node selected")]
      [:div {:id (:properties ids)}
       (if (seq properties)
         [:dl.space-y-2
          (mapv (fn [[k v]]
                  [:div.flex.justify-between.gap-4
                   [:dt.font-medium (name k)]
                   [:dd (str v)]])
                (sort-by (comp str key) properties))]
         [:p.text-sm.opacity-70 "No properties"])]]]))

(def ^:private default-details
  {:default-mode :read-only
   :render-detail-card default-render-detail-card})

(defn canonical-node-id
  ([node]
   (canonical-node-id (:type node) (:id node)))
  ([node-type local-id]
   (state/canonical-node-id node-type local-id)))

(def canonical-edge-id state/canonical-edge-id)
(def dedupe-nodes state/dedupe-nodes)

(defn dedupe-edges
  [edges]
  (:edges
   (reduce (fn [{:keys [seen edges] :as acc} edge]
             (let [edge-id (or (:id edge)
                               (get-in edge [:data :id]))]
               (if (or (nil? edge-id) (contains? seen edge-id))
                 acc
                 {:seen (conj seen edge-id)
                  :edges (conj edges edge)})))
           {:seen #{}
            :edges []}
           edges)))

(defn initialize-graph
  [query _request]
  (state/initialize-graph query))

(defn find-node-neighbors
  [query _request]
  (state/find-node-neighbors query))

(defn default-config
  []
  {:data {:initialize-graph initialize-graph
          :find-node-neighbors find-node-neighbors}
   :visual {:node-types default-node-types
            :edge-types {:default default-edge-visual}
            :legend-order [:project :story :task :subtask :developer :qa :product-owner]}
   :details default-details})

(defn- normalize-node-types
  [node-types]
  (let [node-types (or node-types {})
        with-default (assoc node-types
                            :default
                            (merge default-node-visual
                                   (get node-types :default {})))]
    (into {}
          (map (fn [[node-type visual]]
                 [node-type (merge default-node-visual visual)]))
          with-default)))

(defn- normalize-edge-types
  [edge-types]
  (let [edge-types (or edge-types {})
        with-default (assoc edge-types
                            :default
                            (merge default-edge-visual
                                   (get edge-types :default {})))]
    (into {}
          (map (fn [[edge-type visual]]
                 [edge-type (merge default-edge-visual visual)]))
          with-default)))

(defn normalize-config
  [config]
  (let [legacy-data? (or (contains? (or config {}) :initialize-graph)
                         (contains? (or config {}) :find-node-neighbors))
        data-input (if legacy-data?
                     {:initialize-graph (:initialize-graph config)
                      :find-node-neighbors (:find-node-neighbors config)}
                     (:data config))
        defaulted (default-config)
        visual-input (:visual config)]
    {:data {:initialize-graph (or (:initialize-graph data-input)
                                  (get-in defaulted [:data :initialize-graph]))
            :find-node-neighbors (or (:find-node-neighbors data-input)
                                     (get-in defaulted [:data :find-node-neighbors]))}
     :visual {:node-types (normalize-node-types (or (:node-types visual-input)
                                                    (get-in defaulted [:visual :node-types])))
              :edge-types (normalize-edge-types (or (:edge-types visual-input)
                                                    (get-in defaulted [:visual :edge-types])))
              :legend-order (vec (or (:legend-order visual-input)
                                     (get-in defaulted [:visual :legend-order])))}
     :details (merge (:details defaulted)
                     (:details config))}))

(defn- node-legend
  [node-types legend-order]
  (let [type-set (->> node-types keys (remove #{:default}) set)
        configured (vec (filter type-set legend-order))
        appended (->> (keys node-types)
                      (remove #{:default})
                      sort
                      (remove (set configured))
                      vec)
        ordered (into configured appended)]
    (mapv (fn [node-type]
            {:type node-type
             :label (get-in node-types [node-type :label])})
          ordered)))

(defn make-graph
  [config]
  (let [normalized (normalize-config config)]
    {:handlers (handler/make-handlers {:initialize-graph (get-in normalized [:data :initialize-graph])
                                       :find-node-neighbors (get-in normalized [:data :find-node-neighbors])
                                       :visual (get-in normalized [:visual])})
     :visual {:legend {:nodes (node-legend (get-in normalized [:visual :node-types])
                                           (get-in normalized [:visual :legend-order]))}}
     :details (:details normalized)
     :config normalized}))

(defn make-handlers
  "Creates Ring Datastar handlers for graph init and expansion.

   Returns {:init fn :expand fn}.
   You can override app functions via opts:
   - :initialize-graph
   - :find-node-neighbors"
  ([]
   (make-handlers {}))
  ([opts]
   (:handlers (make-graph opts))))
