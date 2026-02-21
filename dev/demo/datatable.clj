(ns demo.datatable
  (:require [app :as app]
            [demo.util :refer [->html]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [pomp.datatable :as datatable]
            [pomp.rad.datatable.ui.table :as table]
            [pomp.rad.datatable.query.sql :as sqlq]))

(defn format-century
  "Formats a numeric century as a human-readable string.
   Negative values are BC, positive are AD.
   e.g., -5 -> \"5th BC\", 17 -> \"17th AD\""
  [row]
  (let [n (:century row)
        abs-n (abs n)
        suffix (case (mod abs-n 10)
                 1 (if (= (mod abs-n 100) 11) "th" "st")
                 2 (if (= (mod abs-n 100) 12) "th" "nd")
                 3 (if (= (mod abs-n 100) 13) "th" "rd")
                 "th")
        era (if (neg? n) " BC" " AD")]
    (str abs-n suffix era)))

(def columns
  [{:key :name :label "Name" :type :string :editable true :global-search? true}
   {:key :century :label "Century" :type :number :display-fn format-century :editable true}
   {:key :school :label "School" :type :enum :groupable true :editable true :global-search? true
    :options ["Classical Greek" "Platonism" "Peripatetic" "Confucianism" "Taoism"
              "Epicureanism" "Stoicism" "Christian Platonism" "Scholasticism"
              "Rationalism" "Empiricism" "German Idealism" "Existentialism"]}
   {:key :region :label "Region" :type :enum :groupable true}
   {:key :influence :label "Influence" :type :number}
   {:key :verified :label "Verified" :type :boolean :editable true}
   {:key :id :label "ID" :type :number}])

(def philosophers
  "Seed data for the philosophers table."
  (mapv (fn [philosopher]
          (assoc philosopher :influence (+ 10 (mod (* 7 (:id philosopher)) 91))))
        [{:id 1 :name "Socrates" :century -5 :school "Classical Greek" :region "Greece" :verified true}
         {:id 2 :name "Plato" :century -4 :school "Platonism" :region "Greece" :verified true}
         {:id 3 :name "Aristotle" :century -4 :school "Peripatetic" :region "Greece" :verified true}
         {:id 4 :name "Confucius" :century -5 :school "Confucianism" :region "China" :verified true}
         {:id 5 :name "Laozi" :century -6 :school "Taoism" :region "China" :verified false}
         {:id 6 :name "Epicurus" :century -3 :school "Epicureanism" :region "Greece" :verified true}
         {:id 7 :name "Zeno of Citium" :century -3 :school "Stoicism" :region "Greece" :verified true}
         {:id 8 :name "Marcus Aurelius" :century 2 :school "Stoicism" :region "Rome" :verified true}
         {:id 9 :name "Seneca" :century 1 :school "Stoicism" :region "Rome" :verified true}
         {:id 10 :name "Augustine" :century 4 :school "Christian Platonism" :region "North Africa" :verified true}
         {:id 11 :name "Thomas Aquinas" :century 13 :school "Scholasticism" :region "Italy" :verified true}
         {:id 12 :name "René Descartes" :century 17 :school "Rationalism" :region "France" :verified true}
         {:id 13 :name "John Locke" :century 17 :school "Empiricism" :region "England" :verified false}
         {:id 14 :name "Immanuel Kant" :century 18 :school "German Idealism" :region "Germany" :verified true}
         {:id 15 :name "Friedrich Nietzsche" :century 19 :school "Existentialism" :region "Germany" :verified false}
         {:id 16 :name "Plotinus" :century 3 :school "Platonism" :region "Egypt" :verified true}
         {:id 17 :name "Proclus" :century 5 :school "Platonism" :region "Greece" :verified true}
         {:id 18 :name "Al-Farabi" :century 10 :school "Peripatetic" :region "Central Asia" :verified true}
         {:id 19 :name "Avicenna" :century 11 :school "Peripatetic" :region "Persia" :verified true}
         {:id 20 :name "Mencius" :century -4 :school "Confucianism" :region "China" :verified true}
         {:id 21 :name "Xunzi" :century -3 :school "Confucianism" :region "China" :verified true}
         {:id 22 :name "Zhuangzi" :century -4 :school "Taoism" :region "China" :verified true}
         {:id 23 :name "Wang Bi" :century 3 :school "Taoism" :region "China" :verified false}
         {:id 24 :name "Lucretius" :century -1 :school "Epicureanism" :region "Rome" :verified true}
         {:id 25 :name "Epictetus" :century 1 :school "Stoicism" :region "Greece" :verified true}
         {:id 26 :name "Musonius Rufus" :century 1 :school "Stoicism" :region "Rome" :verified false}
         {:id 27 :name "Boethius" :century 6 :school "Christian Platonism" :region "Italy" :verified true}
         {:id 28 :name "Pseudo-Dionysius" :century 6 :school "Christian Platonism" :region "Syria" :verified false}
         {:id 29 :name "Anselm of Canterbury" :century 11 :school "Scholasticism" :region "England" :verified true}
         {:id 30 :name "Duns Scotus" :century 13 :school "Scholasticism" :region "Scotland" :verified true}
         {:id 31 :name "Baruch Spinoza" :century 17 :school "Rationalism" :region "Netherlands" :verified true}
         {:id 32 :name "Gottfried Wilhelm Leibniz" :century 17 :school "Rationalism" :region "Germany" :verified true}
         {:id 33 :name "George Berkeley" :century 18 :school "Empiricism" :region "Ireland" :verified true}
         {:id 34 :name "David Hume" :century 18 :school "Empiricism" :region "Scotland" :verified true}
         {:id 35 :name "Johann Gottlieb Fichte" :century 18 :school "German Idealism" :region "Germany" :verified false}
         {:id 36 :name "G.W.F. Hegel" :century 19 :school "German Idealism" :region "Germany" :verified true}
         {:id 37 :name "Soren Kierkegaard" :century 19 :school "Existentialism" :region "Denmark" :verified true}
         {:id 38 :name "Jean-Paul Sartre" :century 20 :school "Existentialism" :region "France" :verified true}
         {:id 39 :name "Simone de Beauvoir" :century 20 :school "Existentialism" :region "France" :verified true}
         {:id 40 :name "Albert Camus" :century 20 :school "Existentialism" :region "Algeria" :verified true}
         {:id 41 :name "Antisthenes" :century -4 :school "Classical Greek" :region "Greece" :verified true}
         {:id 42 :name "Isocrates" :century -4 :school "Classical Greek" :region "Greece" :verified true}]))

;; =============================================================================
;; H2 Database Setup
;; =============================================================================

(def db-spec
  "H2 in-memory database spec."
  {:dbtype "h2:mem" :dbname "demo"})

(defonce ^:private datasource
  (delay (jdbc/get-datasource db-spec)))

(defn get-datasource
  "Returns the H2 datasource, creating it if needed."
  []
  @datasource)

(defn create-schema!
  "Creates the philosophers table if it doesn't exist."
  []
  (let [ds (get-datasource)]
    (jdbc/execute! ds
                   ["CREATE TABLE IF NOT EXISTS philosophers (
                     id INT PRIMARY KEY,
                     name VARCHAR(100),
                     century INT,
                     school VARCHAR(100),
                     region VARCHAR(100),
                     influence INT,
                     verified BOOLEAN)"])
    (jdbc/execute! ds ["ALTER TABLE philosophers ADD COLUMN IF NOT EXISTS influence INT"])))

(defn seed-data!
  "Inserts the philosophers data into the table.
   Clears existing data first."
  []
  (let [ds (get-datasource)]
    (jdbc/execute! ds ["DELETE FROM philosophers"])
    (doseq [p philosophers]
      (jdbc/execute! ds ["INSERT INTO philosophers (id, name, century, school, region, influence, verified) VALUES (?, ?, ?, ?, ?, ?, ?)"
                         (:id p) (:name p) (:century p) (:school p) (:region p) (:influence p) (:verified p)]))))

(defonce db-initialized? (atom false))

(defn init-db!
  "Initializes the H2 database with schema and seed data.
   Only runs once per JVM session."
  []
  (when-not @db-initialized?
    (create-schema!)
    (seed-data!)
    (reset! db-initialized? true)))

(def datatable-component-url "/demo/datatable/data")

(defn page-handler [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (app/with-app-layout
            {:nav-title "Pomp Demo"}
            [:div.p-8
             [:h1.text-2xl.font-bold.mb-4 "Philosophers"]
             [:div#datatable-container
              {:data-init (str "@get('" datatable-component-url "')")}
              [:div#datatable]]]))})

(defn make-data-handlers
  []
  (let [ds (get-datasource)
        execute! (fn [sqlvec]
                   (jdbc/execute! ds sqlvec
                                  {:builder-fn rs/as-unqualified-lower-maps}))
        table-search-rows (sqlq/rows-fn {:table-name "philosophers"} execute!)
        table-count (sqlq/count-fn {:table-name "philosophers"} execute!)
        stream-adapter! (fn [sqlvec on-row! on-complete!]
                          (let [row-count (volatile! 0)]
                            (reduce (fn [_ row]
                                      (vswap! row-count inc)
                                      (on-row! (into {} row)))
                                    nil
                                    (jdbc/plan ds sqlvec
                                               {:builder-fn rs/as-unqualified-lower-maps}))
                            (on-complete! {:row-count @row-count})))
        stream-rows! (sqlq/stream-rows-fn {:table-name "philosophers"} stream-adapter!)]
    (datatable/make-handlers
     {:id "datatable"
      :columns columns
      :rows-fn table-search-rows
      :export-stream-rows-fn stream-rows!
      :count-fn table-count
      :table-search-query table-search-rows
      :save-fn (sqlq/save-fn {:table "philosophers"} execute!)
      :data-url datatable-component-url
      :render-html-fn ->html
      :render-table-search table/default-render-table-search
      :page-sizes [10 25 100 250]
      :initial-signals-fn (fn [_]
                            {:columns {:influence {:visible false}
                                       :id {:visible false}}})
      :selectable? true})))

(defn make-routes [_]
  (init-db!)
  (let [{:keys [get post]} (make-data-handlers)]
    [["/datatable" page-handler]
     ["/datatable/data" {:get get
                         :post post}]]))

(comment
  (require '[demo.datatable :as dt] :reload)

  ;; Initialize the database
  (dt/init-db!)

  ;; Check data in the database
  (jdbc/execute! (dt/get-datasource) ["SELECT * FROM philosophers"])

  ;; Test the page handler
  (:body (page-handler {})))
