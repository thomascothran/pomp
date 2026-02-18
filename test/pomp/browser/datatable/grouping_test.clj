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
  {:css "button[popovertarget='col-menu-school']"})

(def school-menu-button
  {:css "button[popovertarget='col-menu-school']"})

(def group-by-school-item
  {:xpath "//div[@id='col-menu-school']//a[contains(normalize-space(.), 'Group by')]"})

(def region-menu-button
  {:css "button[popovertarget='col-menu-region']"})

(def group-by-region-item
  {:xpath "//div[@id='col-menu-region']//a[contains(normalize-space(.), 'Group by')]"})

(def grouped-column-label
  {:xpath "//th[.//button[@popovertarget='col-menu-group']]//span[contains(@class,'font-semibold')]"})

(def page-size-select
  {:css "#datatable select.select-ghost.select-sm.font-medium"})

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

(defn- expected-school-count
  []
  (->> (:rows datatable/*state*)
       (map :school)
       distinct
       count))

(defn- expected-region-count
  []
  (->> (:rows datatable/*state*)
       (map :region)
       distinct
       count))

(defn- expected-school-region-group-count
  []
  (let [rows (:rows datatable/*state*)
        school-count (count (distinct (map :school rows)))
        region-groups-count (count (set (map (juxt :school :region) rows)))]
    (+ school-count region-groups-count)))

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

(deftest group-by-school-then-region-test
  (testing "multi-level grouping renders nested school and region groups"
    (open-datatable!)
    (e/click browser/*driver* school-menu-button)
    (e/wait-visible browser/*driver* group-by-school-item)
    (e/click browser/*driver* group-by-school-item)
    (e/wait-visible browser/*driver* group-row-selector)
    (e/click browser/*driver* region-menu-button)
    (e/wait-visible browser/*driver* group-by-region-item)
    (e/click browser/*driver* group-by-region-item)
    (e/wait-visible browser/*driver* grouped-column-label)
    (e/select browser/*driver* page-size-select "250")
    (e/wait-predicate #(= (expected-school-region-group-count)
                          (count (e/query-all browser/*driver* group-row-selector))))
    (let [grouped-label (e/get-element-text browser/*driver* grouped-column-label)
          visible-group-count (count (e/query-all browser/*driver* group-row-selector))
          expected-nested-count (expected-school-region-group-count)]
      (is (str/includes? grouped-label "Grouped by:")
          "Expected grouped header to show grouping chain")
      (is (str/includes? grouped-label "School")
          "Expected grouped header to include school")
      (is (str/includes? grouped-label "Region")
          "Expected grouped header to include region")
      (is (= expected-nested-count visible-group-count)
          "Expected nested grouping to render top and second-level synthetic rows")
      (is (> visible-group-count (expected-region-count))
          "Expected nested grouping to expose more grouped rows than single level"))))
