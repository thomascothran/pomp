(ns pomp.browser.datatable.grouping-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [etaoin.api :as e]
            [pomp.test.fixtures.browser :as browser]))

(use-fixtures :once browser/driver-fixture browser/datatable-state-fixture)

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
  (e/go browser/*driver* browser/base-url)
  (e/wait-visible browser/*driver* first-name-cell))

(defn- group-row-texts
  []
  (let [rows (e/query-all browser/*driver* group-row-selector)]
    (mapv #(e/get-element-text-el browser/*driver* %) rows)))

(defn- expected-group-count
  [group-value]
  (->> (:rows browser/*state*)
       (filter #(= group-value (:school %)))
       count))

(deftest group-by-school-test
  (testing "grouping by school creates grouped rows"
    (open-datatable!)
    (e/click browser/*driver* school-menu-button)
    (e/wait-visible browser/*driver* group-by-school-item)
    (e/click browser/*driver* group-by-school-item)
    (e/wait-visible browser/*driver* group-row-selector)
    (let [expected-count (expected-group-count "Stoicism")
          texts (group-row-texts)
          stoic-row (some #(when (str/includes? % "Stoicism") %) texts)]
      (is stoic-row "Expected Stoicism group row")
      (is (str/includes? stoic-row (str "(" expected-count ")"))
          "Expected group count in row"))))
