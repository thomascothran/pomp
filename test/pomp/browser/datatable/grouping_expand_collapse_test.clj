(ns pomp.browser.datatable.grouping-expand-collapse-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once browser/driver-fixture browser/datatable-state-fixture)

(def first-name-cell
  {:css "#datatable td[data-row='0'][data-col='0']"})

(def school-menu-button
  {:css "button[popovertarget='col-menu-school']"})

(def group-by-school-item
  {:xpath "//div[@id='col-menu-school']//a[contains(normalize-space(.), 'Group by')]"})

(def stoicism-group-toggle
  {:xpath "//tr[contains(@class,'bg-base-200')][.//span[contains(normalize-space(.), 'Stoicism')]]//button"})

(def stoicism-row
  {:xpath "//tr[.//td[contains(normalize-space(.), 'Zeno of Citium')]]"})

(def platonism-row
  {:xpath "//tr[.//td[contains(normalize-space(.), 'Plato')]]"})

(defn- open-datatable!
  []
  (e/go browser/*driver* browser/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- group-by-school!
  []
  (e/click browser/*driver* school-menu-button)
  (e/wait-visible browser/*driver* group-by-school-item)
  (e/click browser/*driver* group-by-school-item)
  (e/wait-visible browser/*driver* stoicism-group-toggle))

(deftest group-expand-collapse-toggles-rows-test
  (testing "toggling a group flips child row visibility"
    (open-datatable!)
    (group-by-school!)
    (let [initial-visible (e/visible? browser/*driver* stoicism-row)]
      (e/click browser/*driver* stoicism-group-toggle)
      (e/wait browser/*driver* 1)
      (is (not= initial-visible (e/visible? browser/*driver* stoicism-row))
          "Expected group row visibility to flip on toggle")
      (e/click browser/*driver* stoicism-group-toggle)
      (e/wait browser/*driver* 1)
      (is (= initial-visible (e/visible? browser/*driver* stoicism-row))
          "Expected group row visibility to flip back on second toggle"))))

(deftest group-expand-does-not-affect-other-groups-test
  (testing "toggling one group does not affect other groups"
    (open-datatable!)
    (group-by-school!)
    (let [platonism-visible (e/visible? browser/*driver* platonism-row)]
      (e/click browser/*driver* stoicism-group-toggle)
      (e/wait browser/*driver* 1)
      (is (= platonism-visible (e/visible? browser/*driver* platonism-row))
          "Expected other group visibility unchanged"))))
