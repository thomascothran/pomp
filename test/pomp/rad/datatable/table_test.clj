(ns pomp.rad.datatable.table-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [pomp.rad.datatable.core :as dt]))

(def test-table-data
  {:group-by [],
   :filters {},
   :total-rows 15,
   :page-size 10,
   :page-current 0,
   :sort-state [],
   :page-sizes [10 25 100 250],
   :selectable? true,
   :groups nil
   :rows
   [{:id 1,
     :name "Socrates",
     :century "5th BC",
     :school "Classical Greek",
     :region "Greece"}
    {:id 2,
     :name "Plato",
     :century "4th BC",
     :school "Platonism",
     :region "Greece"}]
   :cols
   [{:key :name, :label "Name", :type :text}
    {:key :century, :label "Century", :type :text}
    {:key :school, :label "School", :type :enum}
    {:key :region, :label "Region", :type :enum}]
   :id "datatable",
   :data-url "/demo/datatable/data"})

(def expected-result
  (some-> "test-resources/snapshots/pomp/rad/datatable/table-test/rendered-table.edn"
          slurp
          edn/read-string))

(deftest table-render-characterization-test
  (is (= expected-result
         (dt/render test-table-data))))
