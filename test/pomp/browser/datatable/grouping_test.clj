(ns pomp.browser.datatable.grouping-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]
            [pomp.test.fixtures.browser.datatable :as datatable]))

(use-fixtures :once
  (browser/server-fixture {:routes datatable/routes
                           :middlewares datatable/middlewares
                           :router-data datatable/router-data})
  browser/driver-fixture
  datatable/datatable-db-fixture
  datatable/datatable-state-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def school-menu-button
  {:css "button[popovertarget='col-menu-school']"})

(def group-by-school-item
  {:xpath "//div[@id='col-menu-school']//a[contains(normalize-space(.), 'Group by')]"})

(def group-row-selector
  {:css "#datatable tr.bg-base-200"})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- group-row-texts
  []
  (let [rows (e/query-all browser/*driver* group-row-selector)]
    (mapv #(e/get-element-text-el browser/*driver* %) rows)))

(defn- parse-group-row
  [text]
  (when-let [[_ group-value count-text] (re-find #"^(.*?)\s*\((\d+)\)$" (str/trim text))]
    {:group-value group-value
     :count (Long/parseLong count-text)}))

(defn- expected-group-count
  [group-value]
  (->> (:rows datatable/*state*)
       (filter #(= group-value (:school %)))
       count))

(deftest group-by-school-test
  (testing "grouping by school creates grouped rows"
    (open-datatable!)
    (e/click browser/*driver* school-menu-button)
    (e/wait-visible browser/*driver* group-by-school-item)
    (e/click browser/*driver* group-by-school-item)
    (e/wait-visible browser/*driver* group-row-selector)
    (let [group (some-> (group-row-texts) first parse-group-row)
          expected-count (some-> group :group-value expected-group-count)]
      (is group "Expected at least one visible group row")
      (is (some? expected-count) "Expected visible group row to include group name")
      (is (= expected-count (:count group))
          "Expected visible group count to match backing rows"))))
