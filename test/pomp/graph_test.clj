(ns pomp.graph-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.graph :as graph]))

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
