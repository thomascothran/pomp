(ns pomp.browser.datatable.grouping-expand-collapse-test
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

(def first-group-toggle
  {:xpath "(//tr[contains(@class,'bg-base-200')]//button)[1]"})

(defn- group-toggle
  [group-name]
  {:xpath (str "//tr[contains(@class,'bg-base-200')][.//span[contains(normalize-space(.), '" group-name "')]]//button")})

(defn- philosopher-row
  [philosopher-name]
  {:xpath (str "//tr[.//td[contains(normalize-space(.), '" philosopher-name "')]]")})

(defn- open-datatable!
  []
  (e/go browser/*driver* datatable/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- group-by-school!
  []
  (e/click browser/*driver* school-menu-button)
  (e/wait-visible browser/*driver* group-by-school-item)
  (e/click browser/*driver* group-by-school-item)
  (e/wait-visible browser/*driver* first-group-toggle))

(defn- group-row-texts
  []
  (let [rows (e/query-all browser/*driver* group-row-selector)]
    (mapv #(e/get-element-text-el browser/*driver* %) rows)))

(defn- parse-group-name
  [text]
  (some-> (re-find #"^(.*?)\s*\(\d+\)$" (str/trim text)) second))

(defn- school->first-philosopher
  [school]
  (->> (:rows datatable/*state*)
       (filter #(= school (:school %)))
       (map :name)
       first))

(deftest group-expand-collapse-toggles-rows-test
  (testing "toggling a group flips child row visibility"
    (open-datatable!)
    (group-by-school!)
    (let [group-name (some-> (group-row-texts) first parse-group-name)
          representative (some-> group-name school->first-philosopher)
          row-selector (philosopher-row representative)
          toggle-selector (group-toggle group-name)
          initial-visible (e/visible? browser/*driver* row-selector)]
      (is group-name "Expected a visible grouped section")
      (is representative "Expected grouped section to have a representative philosopher")
      (e/click browser/*driver* toggle-selector)
      (e/wait browser/*driver* 1)
      (is (not= initial-visible (e/visible? browser/*driver* row-selector))
          "Expected group row visibility to flip on toggle")
      (e/click browser/*driver* toggle-selector)
      (e/wait browser/*driver* 1)
      (is (= initial-visible (e/visible? browser/*driver* row-selector))
          "Expected group row visibility to flip back on second toggle"))))

(deftest group-expand-does-not-affect-other-groups-test
  (testing "toggling one group does not affect other groups"
    (open-datatable!)
    (group-by-school!)
    (let [group-names (->> (group-row-texts) (map parse-group-name) (remove nil?) vec)
          toggled-group (first group-names)
          other-group (second group-names)
          toggled-toggle (group-toggle toggled-group)
          other-row (philosopher-row (school->first-philosopher other-group))
          other-visible (e/visible? browser/*driver* other-row)]
      (is toggled-group "Expected one visible group to toggle")
      (is other-group "Expected at least two visible groups")
      (e/click browser/*driver* toggled-toggle)
      (e/wait browser/*driver* 1)
      (is (= other-visible (e/visible? browser/*driver* other-row))
          "Expected other group visibility unchanged"))))
