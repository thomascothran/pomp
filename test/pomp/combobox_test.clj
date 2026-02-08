(ns pomp.combobox-test
  (:require [clojure.data.json :as json]
             [clojure.string :as str]
             [clojure.test :refer [deftest is testing]]
             [pomp.combobox :as combobox]))

(defn- hiccup-attr-maps
  [hiccup]
  (->> (tree-seq coll? seq hiccup)
       (filter map?)))

(defn- hiccup-attr-strings
  [hiccup]
  (->> (hiccup-attr-maps hiccup)
       (mapcat vals)
       (filter string?)))

(def base-config
  {:id "fruit-search"
   :data-url "/test/combobox/options"
   :render-html-fn pr-str
   :query-fn (fn [_ _] [])})

(deftest combobox-public-api-contract-test
  (testing "library exposes render and make-handler entrypoints"
    (is (fn? combobox/render)
        "Expected pomp.combobox/render to exist and be callable")
    (is (fn? combobox/make-handler)
        "Expected pomp.combobox/make-handler to exist and be callable")))

(deftest combobox-render-scopes-ids-and-signals-test
  (let [hiccup (combobox/render base-config)
        ids (->> (hiccup-attr-maps hiccup)
                 (keep :id)
                 set)
        attr-strings (hiccup-attr-strings hiccup)]
    (testing "component ids are scoped by id"
      (is (contains? ids "combobox-fruit-search"))
      (is (contains? ids "combobox-fruit-search-input"))
      (is (contains? ids "combobox-fruit-search-panel"))
      (is (contains? ids "combobox-fruit-search-listbox")))
    (testing "signal paths are scoped by component id"
      (is (some #(str/includes? % "combobox.fruit-search.query") attr-strings))
      (is (some #(str/includes? % "combobox.fruit-search.resultsOpen") attr-strings))
      (is (some #(str/includes? % "combobox.fruit-search.loadingOptions") attr-strings)))))

(deftest combobox-render-allows-renderer-overrides-test
  (let [input-ctx (atom nil)
        results-ctx (atom nil)
        hiccup (combobox/render (assoc base-config
                                 :render-input-fn (fn [ctx]
                                                    (reset! input-ctx ctx)
                                                    [:div {:id "custom-combobox-input"} "input hook"])
                                 :render-results-fn (fn [ctx]
                                                      (reset! results-ctx ctx)
                                                      [:div {:id "custom-combobox-results"} "results hook"])))
        ids (->> (hiccup-attr-maps hiccup)
                 (keep :id)
                 set)]
    (is (map? @input-ctx)
        "Expected :render-input-fn to receive a context map")
    (is (map? @results-ctx)
        "Expected :render-results-fn to receive a context map")
    (is (contains? ids "custom-combobox-input")
        "Expected render output to include custom input hook markup")
    (is (contains? ids "custom-combobox-results")
        "Expected render output to include custom results hook markup")))

(deftest combobox-handler-parses-datastar-query-and-normalizes-items-test
  (let [seen-query (atom nil)
        seen-results-ctx (atom nil)
        handler (combobox/make-handler
                 {:id "fruit-search"
                  :data-url "/test/combobox/options"
                  :render-html-fn pr-str
                  :query-fn (fn [text _req]
                              (reset! seen-query text)
                              [{:label "Apple" :value :apple}
                               {:label 42 :value 42}
                               {:value "missing-label"}
                               {:label "missing-value"}
                               "bad-item"])
                  :render-results-fn (fn [ctx]
                                       (reset! seen-results-ctx ctx)
                                       [:div {:id "hooked-results"}])})
        _response (handler {:headers {"datastar-request" "true"}
                            :query-params {"datastar" (json/write-str {:query "ap"})}})]
    (is (= "ap" @seen-query)
        "Expected query text to be parsed from query-params datastar JSON")
    (is (= [{:label "Apple" :value "apple"}
            {:label "42" :value "42"}]
           (:items @seen-results-ctx))
        "Expected query results to normalize to {:label :value} string maps")))
