(ns pomp.rad.datatable.ui.column-menu-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.ui.column-menu :as column-menu]))

(defn- find-menu-items
  "Extracts menu item labels from a rendered column menu.
  Returns a set of strings like #{\"Sort ascending\" \"Group by School\" ...}."
  [rendered]
  (let [dropdown (second rendered)
        ul (last dropdown)
        lis (rest ul)]
    (set
     (map (fn [li]
            (let [link (second li)
                  contents (drop 2 link)]
              (->> contents
                   (filter string?)
                   (apply str))))
          lis))))

(defn- find-group-menu-handlers
  "Extracts data-on:click handlers from a rendered column menu."
  [rendered]
  (let [dropdown (second rendered)
        ul (last dropdown)
        lis (rest ul)]
    (set
     (keep (comp :data-on:click second second) lis))))

(def base-input
  {:col-key :school
   :col-label "School"
   :data-url "/demo/datatable/data"
   :table-id "datatable"})

(deftest group-by-option-shown-when-groupable
  (testing "Group by menu option is shown when :groupable? is true"
    (let [rendered (column-menu/render (assoc base-input :groupable? true))
          menu-items (find-menu-items rendered)]
      (is (contains? menu-items "Group by School")
          "Expected 'Group by School' to be present in menu"))))

(deftest group-by-option-hidden-when-not-groupable
  (testing "Group by menu option is hidden when :groupable? is false"
    (let [rendered (column-menu/render (assoc base-input :groupable? false))
          menu-items (find-menu-items rendered)]
      (is (not (contains? menu-items "Group by School"))
          "Expected 'Group by School' to NOT be present in menu")))

  (testing "Group by menu option is hidden when :groupable? is not specified"
    (let [rendered (column-menu/render base-input)
          menu-items (find-menu-items rendered)]
      (is (not (contains? menu-items "Group by School"))
          "Expected 'Group by School' to NOT be present in menu when :groupable? is omitted"))))

(deftest grouped-column-menu-shows-remove-and-clear-actions
  (testing "Grouped column menu shows ungroup and clear all groups actions"
    (let [rendered (column-menu/render-group-column {:data-url "/demo/datatable/data"
                                                     :group-col-key :school})
          menu-items (find-menu-items rendered)
          menu-handlers (find-group-menu-handlers rendered)]
      (is (some #(str/includes? % "Ungroup") menu-items)
          "Expected label to indicate removing a single grouped level")
      (is (some #(str/includes? % "Clear") menu-items)
          "Expected label to indicate clearing all group levels")
      (is (some #(str/includes? % "@post('/demo/datatable/data?ungroup=true") menu-handlers)
          "Expected one affordance to use an ungroup action")
      (is (some #(str/includes? % "@post('/demo/datatable/data?clearGroups=true") menu-handlers)
          "Expected one affordance to clear all grouped levels"))))
