(ns pomp.rad.datatable.handler.signals-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.handler.signals :as signals]))

(deftest effective-signals-test
  (testing "first load seeds signals and request signals win on conflicts"
    (let [seed-calls (atom 0)
          req {:query-params {}}
          result (signals/effective-signals {:raw-signals {}
                                             :initial-signals-fn (fn [_]
                                                                   (swap! seed-calls inc)
                                                                   {:page {:size 25 :current 2}
                                                                    :columns {:age {:visible false}}})
                                             :req req})]
      (is (= 1 @seed-calls))
      (is (= {:page {:size 25 :current 2}
              :columns {:age {:visible false}}}
             result))))

  (testing "signal-bearing requests do not call initial-signals-fn"
    (let [seed-calls (atom 0)
          result (signals/effective-signals {:raw-signals {:page {:size 10 :current 1}}
                                             :initial-signals-fn (fn [_]
                                                                   (swap! seed-calls inc)
                                                                   {:page {:size 99 :current 9}})
                                             :req {}})]
      (is (zero? @seed-calls))
      (is (= {:page {:size 10 :current 1}} result)))))

(deftest normalize-group-by-test
  (testing "normalizes groupBy signals into keyword group-by vector"
    (is (= {:groupBy ["school" :region]
            :group-by [:school :region]}
           (signals/normalize-group-by {:groupBy ["school" :region]}))))

  (testing "missing groupBy yields empty group-by vector"
    (is (= {:group-by []}
           (signals/normalize-group-by {})))))
