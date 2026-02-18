(ns pomp.rad.graph.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.graph.state :as state]))

(deftest normalize-relation-test
  (testing "relation normalization coerces and trims supported values"
    (is (= :all (state/normalize-relation "all"))
        "String relation should normalize to keyword")
    (is (= :all (state/normalize-relation " all "))
        "String relation should be trimmed before keyword coercion")
    (is (= :graph/neighbors (state/normalize-relation :graph/neighbors))
        "Keyword relation should remain canonical")
    (is (nil? (state/normalize-relation "   "))
        "Blank string relation should normalize to nil")
    (is (nil? (state/normalize-relation nil))
        "Nil relation should normalize to nil")))

(deftest initialize-graph-anomaly-test
  (testing "missing required keys return :anomaly/incorrect"
    (is (= {:seed-node-id "project:apollo"
            :anomaly/category :anomaly/incorrect
            :anomaly/message "Missing required :graph-id"
            :anomaly/data {:missing :graph-id}}
           (state/initialize-graph {:seed-node-id "project:apollo"})))
    (is (= {:graph-id "pm-graph-v1"
            :anomaly/category :anomaly/incorrect
            :anomaly/message "Missing required :seed-node-id"
            :anomaly/data {:missing :seed-node-id}}
           (state/initialize-graph {:graph-id "pm-graph-v1"})))))

(deftest unknown-node-anomaly-test
  (testing "unknown seed node returns :anomaly/not-found"
    (is (= {:graph-id "g-1"
            :seed-node-id "project:missing"
            :anomaly/category :anomaly/not-found
            :anomaly/message "Unknown seed node: project:missing"
            :anomaly/data {:seed-node-id "project:missing"}}
           (state/initialize-graph {:graph-id "g-1"
                                    :seed-node-id "project:missing"}))))
  (testing "unknown expansion node returns :anomaly/not-found"
    (is (= {:graph-id "g-1"
            :node-id "story:nope"
            :anomaly/category :anomaly/not-found
            :anomaly/message "Unknown node: story:nope"
            :anomaly/data {:node-id "story:nope"}}
           (state/find-node-neighbors {:graph-id "g-1"
                                       :node-id "story:nope"}))))
  (testing "missing expansion node-id returns :anomaly/incorrect"
    (is (= {:graph-id "g-1"
            :anomaly/category :anomaly/incorrect
            :anomaly/message "Missing required :node-id"
            :anomaly/data {:missing :node-id}}
           (state/find-node-neighbors {:graph-id "g-1"})))))

(deftest synthetic-expand-error-test
  (testing "graph/error relation returns a local, non-retryable anomaly"
    (is (= {:graph-id "g-1"
            :node-id "story:auth-hardening"
            :anomaly/category :anomaly/unavailable
            :anomaly/message "Synthetic expansion failure for local error-state validation"
            :anomaly/data {:node-id "story:auth-hardening"
                           :relation :graph/error
                           :retryable? false}}
           (state/find-node-neighbors {:graph-id "g-1"
                                       :node-id "story:auth-hardening"
                                       :relation "graph/error"})))))
