(ns pomp.rad.graph.state
  (:require [clojure.string :as str]))

(def default-graph-id "pm-graph-v1")
(def default-seed-node-id "project:apollo")
(def default-relation :graph/neighbors)
(def error-relation :graph/error)

(defn canonical-node-id
  [node-type local-id]
  (str (name node-type) ":" local-id))

(defn canonical-edge-id
  [source-id relation target-id]
  (str source-id "|" (name relation) "|" target-id))

(def ^:private base-nodes
  [{:id (canonical-node-id :project "apollo") :type :project :label "Apollo Platform"}
   {:id (canonical-node-id :story "auth-hardening") :type :story :label "Auth hardening"}
   {:id (canonical-node-id :story "billing-pilot") :type :story :label "Billing pilot"}
   {:id (canonical-node-id :task "jwt-rotation") :type :task :label "Rotate JWT keys"}
   {:id (canonical-node-id :task "session-audit") :type :task :label "Audit session policy"}
   {:id (canonical-node-id :task "invoice-reconcile") :type :task :label "Reconcile invoices"}
   {:id (canonical-node-id :subtask "k8s-secret") :type :subtask :label "Update Kubernetes secret"}
   {:id (canonical-node-id :subtask "audit-log") :type :subtask :label "Backfill auth audit log"}
   {:id (canonical-node-id :developer "ava") :type :developer :label "Ava Chen"}
   {:id (canonical-node-id :developer "kai") :type :developer :label "Kai Morgan"}
   {:id (canonical-node-id :qa "mila") :type :qa :label "Mila Rossi"}
   {:id (canonical-node-id :product-owner "noah") :type :product-owner :label "Noah Patel"}])

(def ^:private base-relationships
  [{:source (canonical-node-id :project "apollo") :target (canonical-node-id :story "auth-hardening") :relation :project/story :label "has story"}
   {:source (canonical-node-id :project "apollo") :target (canonical-node-id :story "billing-pilot") :relation :project/story :label "has story"}
   {:source (canonical-node-id :story "auth-hardening") :target (canonical-node-id :task "jwt-rotation") :relation :story/task :label "has task"}
   {:source (canonical-node-id :story "auth-hardening") :target (canonical-node-id :task "session-audit") :relation :story/task :label "has task"}
   {:source (canonical-node-id :story "billing-pilot") :target (canonical-node-id :task "invoice-reconcile") :relation :story/task :label "has task"}
   {:source (canonical-node-id :task "jwt-rotation") :target (canonical-node-id :subtask "k8s-secret") :relation :task/subtask :label "has subtask"}
   {:source (canonical-node-id :task "session-audit") :target (canonical-node-id :subtask "audit-log") :relation :task/subtask :label "has subtask"}
   {:source (canonical-node-id :project "apollo") :target (canonical-node-id :product-owner "noah") :relation :project/product-owner :label "owned by"}
   {:source (canonical-node-id :task "jwt-rotation") :target (canonical-node-id :developer "ava") :relation :task/developer :label "implemented by"}
   {:source (canonical-node-id :task "session-audit") :target (canonical-node-id :developer "kai") :relation :task/developer :label "implemented by"}
   {:source (canonical-node-id :task "invoice-reconcile") :target (canonical-node-id :developer "ava") :relation :task/developer :label "implemented by"}
   {:source (canonical-node-id :task "jwt-rotation") :target (canonical-node-id :qa "mila") :relation :task/qa :label "verified by"}
   {:source (canonical-node-id :task "invoice-reconcile") :target (canonical-node-id :qa "mila") :relation :task/qa :label "verified by"}
   {:source (canonical-node-id :developer "ava") :target (canonical-node-id :story "auth-hardening") :relation :developer/story :label "working on"}
   {:source (canonical-node-id :story "billing-pilot") :target (canonical-node-id :story "auth-hardening") :relation :story/blocked-by :label "blocked by"}])

(def ^:private nodes-by-id
  (into {} (map (juxt :id identity) base-nodes)))

(def ^:private outgoing-by-source
  (group-by :source base-relationships))

(defn normalize-relation
  [relation]
  (cond
    (keyword? relation) relation
    (string? relation) (let [trimmed (str/trim relation)]
                         (when (seq trimmed)
                           (keyword trimmed)))
    :else nil))

(defn- relation-matches?
  [requested relation]
  (or (nil? requested)
      (= requested default-relation)
      (= requested relation)))

(defn dedupe-by-id
  [items]
  (:items
   (reduce (fn [{:keys [seen items] :as acc} item]
             (let [id (:id item)]
               (if (or (nil? id) (contains? seen id))
                 acc
                 {:seen (conj seen id)
                  :items (conj items item)})))
           {:seen #{}
            :items []}
           items)))

(defn dedupe-nodes
  [nodes]
  (dedupe-by-id nodes))

(defn dedupe-edges
  [edges]
  (dedupe-by-id edges))

(defn- outgoing-edges
  [node-id relation]
  (->> (get outgoing-by-source node-id [])
       (filter (fn [{edge-relation :relation}]
                 (relation-matches? relation edge-relation)))
       vec))

(defn- node-expandable?
  [node-id]
  (boolean (seq (get outgoing-by-source node-id))))

(defn- canonical-node
  [node]
  (assoc node :expandable? (node-expandable? (:id node))))

(defn- canonical-edge
  [{:keys [source target relation label]}]
  {:id (canonical-edge-id source relation target)
   :source source
   :target target
   :label label
   :relation relation})

(defn- invalid-query
  [query key-name message]
  (assoc (select-keys query [:graph-id :seed-node-id :node-id])
         :anomaly/category :anomaly/incorrect
         :anomaly/message message
         :anomaly/data {:missing key-name}))

(defn initialize-graph
  [{:keys [graph-id seed-node-id relation] :as query}]
  (cond
    (str/blank? graph-id)
    (invalid-query query :graph-id "Missing required :graph-id")

    (str/blank? seed-node-id)
    (invalid-query query :seed-node-id "Missing required :seed-node-id")

    :else
    (if-let [seed-node (get nodes-by-id seed-node-id)]
      (let [requested-relation (normalize-relation relation)
            edges (->> (outgoing-edges seed-node-id requested-relation)
                       (map canonical-edge)
                       dedupe-edges)
            neighbor-nodes (->> edges
                                (map :target)
                                (keep nodes-by-id)
                                (map canonical-node)
                                dedupe-nodes)
            nodes (dedupe-nodes (into [(canonical-node seed-node)] neighbor-nodes))]
        {:graph-id graph-id
         :seed-node-id seed-node-id
         :nodes nodes
         :edges edges
         :selected-node-id seed-node-id
         :viewport {:fit? true}
         :warnings []})
      {:graph-id graph-id
       :seed-node-id seed-node-id
       :anomaly/category :anomaly/not-found
       :anomaly/message (str "Unknown seed node: " seed-node-id)
       :anomaly/data {:seed-node-id seed-node-id}})))

(defn find-node-neighbors
  [{:keys [graph-id node-id relation] :as query}]
  (cond
    (str/blank? graph-id)
    (invalid-query query :graph-id "Missing required :graph-id")

    (str/blank? node-id)
    (invalid-query query :node-id "Missing required :node-id")

    :else
    (if (contains? nodes-by-id node-id)
      (let [requested-relation (normalize-relation relation)]
        (if (= requested-relation error-relation)
          {:graph-id graph-id
           :node-id node-id
           :anomaly/category :anomaly/unavailable
           :anomaly/message "Synthetic expansion failure for local error-state validation"
           :anomaly/data {:node-id node-id
                          :relation error-relation
                          :retryable? false}}
          (let [relation-key (or requested-relation default-relation)
                edges (->> (outgoing-edges node-id requested-relation)
                           (map canonical-edge)
                           dedupe-edges)
                nodes (->> edges
                           (map :target)
                           (keep nodes-by-id)
                           (map canonical-node)
                           dedupe-nodes)]
            {:graph-id graph-id
             :node-id node-id
             :nodes nodes
             :edges edges
             :relation-meta {:relation relation-key}
             :warnings []})))
      {:graph-id graph-id
       :node-id node-id
       :anomaly/category :anomaly/not-found
       :anomaly/message (str "Unknown node: " node-id)
       :anomaly/data {:node-id node-id}})))
