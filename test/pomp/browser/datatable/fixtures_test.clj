(ns pomp.browser.datatable.fixtures-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]
            [pomp.test.fixtures.browser.datatable :as datatable]))

(use-fixtures :once browser/driver-fixture datatable/datatable-state-fixture)

(deftest fixtures-bind-driver-and-state-test
    (testing "browser fixtures bind driver and state"
      (is (= :chrome (e/driver-type browser/*driver*))
          "Expected a headless Chrome driver")
    (is (map? datatable/*state*) "Expected datatable fixture state")
    (is (seq (:columns datatable/*state*)) "Expected fixture columns")
    (is (seq (:rows datatable/*state*)) "Expected fixture rows")
    (is (fn? (:rows-fn datatable/*state*)) "Expected rows-fn in fixture state")
    (is (fn? (:count-fn datatable/*state*)) "Expected count-fn in fixture state")))
