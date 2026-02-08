(ns pomp.browser.combobox.library-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [demo.util :as demo.util]
            [dev.http :as dev.http]
            [etaoin.api :as e]
            [etaoin.keys :as keys]
            [muuntaja.core :as m]
            [pomp.combobox :as combobox]
            [pomp.test.fixtures.browser :as browser]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :refer [run-jetty]]))

(use-fixtures :once browser/driver-fixture)

(def test-port 9395)
(def base-url (str "http://localhost:" test-port "/test/combobox"))

(def input-a {:css "#combobox-fruit-a-input"})
(def input-b {:css "#combobox-fruit-b-input"})
(def panel-a {:css "#combobox-fruit-a-panel"})
(def option-apple-a {:xpath "//*[@id='combobox-fruit-a-panel']//button[contains(normalize-space(.), 'Apple')]"})
(def page-title {:css "h1"})

(defn- query-items
  [text]
  (let [query (some-> text str/trim str/lower-case)
        all-items ["Apple" "Apricot" "Banana" "Blueberry"]]
    (if (or (nil? query) (< (count query) 2))
      []
      (->> all-items
           (filter #(str/includes? (str/lower-case %) query))
           (map (fn [item] {:label item :value item}))))))

(defn- make-app
  []
  (let [handler-a (combobox/make-handler {:id "fruit-a"
                                          :query-fn (fn [text _req] (query-items text))
                                          :data-url "/test/combobox/options/a"
                                          :render-html-fn demo.util/->html})
        handler-b (combobox/make-handler {:id "fruit-b"
                                          :query-fn (fn [text _req] (query-items text))
                                          :data-url "/test/combobox/options/b"
                                          :render-html-fn demo.util/->html})]
    (ring/ring-handler
     (ring/router
      [["/test/combobox"
        (fn [req]
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (demo.util/->html
                  (demo.util/page
                   [:main {:class "mx-auto max-w-2xl p-6"}
                    [:h1 {:class "text-xl font-bold mb-4"} "Combobox Library Test Page"]
                    [:div {:class "space-y-4"}
                     (combobox/render {:id "fruit-a"
                                       :query-fn (fn [text _req] (query-items text))
                                       :data-url "/test/combobox/options/a"
                                       :render-html-fn demo.util/->html})
                     (combobox/render {:id "fruit-b"
                                       :query-fn (fn [text _req] (query-items text))
                                       :data-url "/test/combobox/options/b"
                                       :render-html-fn demo.util/->html})]]))})]
       ["/test/combobox/options/a" handler-a]
       ["/test/combobox/options/b" handler-b]]
      {:data {:middleware (dev.http/make-middleware)
              :muuntaja m/instance}})
     (ring/routes
      (ring/create-resource-handler {:path "/"})
      (ring/create-default-handler)))))

(defn- with-combobox-server
  [run-test!]
  (let [server (run-jetty (make-app) {:port test-port :join? false})]
    (try
      (run-test!)
      (finally
        (.stop server)))))

(defn- press-key!
  [selector key]
  (e/click browser/*driver* selector)
  (let [keyboard (-> (e/make-key-input)
                     (e/add-key-press key))]
    (e/perform-actions browser/*driver* keyboard)))

(deftest combobox-library-page-supports-core-interactions-test
  (with-combobox-server
    (fn []
      (e/go browser/*driver* base-url)
      (e/wait-visible browser/*driver* input-a)
      (testing "typing query opens anchored results and shows matching options"
        (e/fill browser/*driver* input-a "ap")
        (e/wait-visible browser/*driver* panel-a)
        (e/wait-visible browser/*driver* option-apple-a)
        (is (= "true" (e/get-element-attr browser/*driver* input-a "aria-expanded"))))
      (testing "clicking an option commits selection and closes menu"
        (e/click browser/*driver* option-apple-a)
        (e/wait-predicate #(and (= "Apple" (e/get-element-value browser/*driver* input-a))
                                (= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded"))))
        (is (= "Apple" (e/get-element-value browser/*driver* input-a)))
        (is (= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded"))))
      (testing "enter commits free text selection"
        (e/fill browser/*driver* input-a "Dragonfruit Jam")
        (press-key! input-a keys/enter)
        (e/wait-predicate #(and (= "Dragonfruit Jam" (e/get-element-value browser/*driver* input-a))
                                (= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded"))))
        (is (= "Dragonfruit Jam" (e/get-element-value browser/*driver* input-a)))
        (is (= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded"))))
      (testing "escape closes menu"
        (e/fill browser/*driver* input-a "ap")
        (e/wait-visible browser/*driver* option-apple-a)
        (press-key! input-a keys/escape)
        (e/wait-predicate #(= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded")))
        (is (= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded"))))
      (testing "blur closes menu"
        (e/fill browser/*driver* input-a "ap")
        (e/wait-visible browser/*driver* option-apple-a)
        (e/click browser/*driver* page-title)
        (e/wait-predicate #(= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded")))
        (is (= "false" (e/get-element-attr browser/*driver* input-a "aria-expanded")))))))

(deftest combobox-library-scopes-state-per-component-id-test
  (with-combobox-server
    (fn []
      (e/go browser/*driver* base-url)
      (e/screenshot browser/*driver* "wtaf.png")
      (e/wait-visible browser/*driver* input-a)
      (e/wait-visible browser/*driver* input-b)
      (e/fill browser/*driver* input-a "ap")
      (e/wait-visible browser/*driver* option-apple-a)
      (is (= "" (e/get-element-value browser/*driver* input-b))
          "Typing in first combobox should not mutate second combobox input")
      (is (= "false" (e/get-element-attr browser/*driver* input-b "aria-expanded"))
          "Second combobox should remain closed while first is active"))))
