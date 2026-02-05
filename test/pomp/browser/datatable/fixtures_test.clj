(ns pomp.browser.datatable.fixtures-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once browser/driver-fixture browser/datatable-state-fixture)

(deftest fixtures-bind-driver-and-state-test
  (testing "browser fixtures bind driver and state"
    (is (= :chrome (e/driver-type browser/*driver*))
        "Expected a headless Chrome driver")
    (is (map? browser/*state*) "Expected datatable fixture state")
    (is (seq (:columns browser/*state*)) "Expected fixture columns")
    (is (seq (:rows browser/*state*)) "Expected fixture rows")
    (is (fn? (:query-fn browser/*state*)) "Expected query-fn in fixture state")))
