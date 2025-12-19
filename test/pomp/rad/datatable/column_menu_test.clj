(ns pomp.rad.datatable.column-menu-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [pomp.rad.datatable.ui.columns-menu :as columns-menu]))

(def test-inputs
  {:cols
   [{:key :name, :label "Name", :type :text}
    {:key :century, :label "Century", :type :text}
    {:key :school, :label "School", :type :enum}
    {:key :region, :label "Region", :type :enum}],
   :columns-state
   {:school {:visible true},
    :name {:visible true},
    :region {:visible true},
    :century {:visible true}}
   :table-id "datatable",
   :data-url "/demo/datatable/data"})

;; (def expected-result
;;   (some-> "test-resources/snapshots/pomp/rad/datatable/column-menu-test/rendered-result.edn"
;;           slurp
;;           edn/read-string))
;;
;; (deftest render-characterization-tests
;;   (is (= expected-result
;;          (columns-menu/render test-inputs))))
