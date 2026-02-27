(ns pomp.datatable.export.csv
  (:require [clojure.string :as str]))

(defn derive-export-columns
  [ordered-cols]
  (->> ordered-cols
       (map :key)
       distinct
       vec))

(defn- csv-cell
  [value]
  (let [cell (if (nil? value) "" (str value))]
    (if (re-find #"[\",\n\r]" cell)
      (str "\"" (str/replace cell "\"" "\"\"") "\"")
      cell)))

(defn csv-line
  [row columns]
  (str (str/join "," (map (fn [col-key]
                             (csv-cell (get row col-key)))
                           columns))
       "\n"))

(defn default-export-filename
  [table-id]
  (str table-id "-export.csv"))
