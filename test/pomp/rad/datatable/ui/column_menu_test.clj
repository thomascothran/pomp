(ns pomp.rad.datatable.ui.column-menu-test
  (:require [clojure.test :refer [deftest is testing]]
            [pomp.rad.datatable.ui.column-menu :as column-menu]))

(defn- find-menu-items
  "Extracts the menu item labels from a column-menu render result.
   Returns a set of strings like #{\"Sort ascending\" \"Group by School\" ...}"
  [rendered]
  (let [dropdown (second rendered) ; The dropdown div is the second element
        ul (last dropdown) ; The ul is the last child of dropdown
        lis (rest ul)] ; Skip the :ul.menu... tag
    (->> lis
         (map (fn [li]
                (let [a (second li) ; [:li [:a ...]]
                      contents (drop 2 a)] ; Skip tag and attrs
                  ;; Join all string content (icons are vectors, text is strings)
                  (->> contents
                       (filter string?)
                       (apply str)))))
         set)))

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
