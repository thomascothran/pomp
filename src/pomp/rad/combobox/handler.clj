(ns pomp.rad.combobox.handler
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [pomp.rad.combobox.ui :as ui]
            [pomp.rad.combobox.util :as util]))

(defn- parse-datastar-query
  [req id]
  (let [raw (get-in req [:query-params "datastar"])
        parsed (when (seq raw)
                 (try
                   (json/read-str raw {:key-fn keyword})
                   (catch Throwable _
                     nil)))
        id-key (keyword id)
        camel-id-key (util/kebab->camel-keyword id)
        query (or (util/present-string (:query parsed))
                  (util/present-string (get-in parsed [:combobox camel-id-key :query]))
                  (util/present-string (get-in parsed [:combobox id-key :query]))
                  (util/present-string (get-in req [:params :query]))
                  "")
        selected-label (or (util/present-string (get-in parsed [:combobox id-key :selectedLabel]))
                           (util/present-string (get-in parsed [:combobox camel-id-key :selectedLabel]))
                           "")]
    (if (and (seq selected-label)
             (str/starts-with? query selected-label)
             (> (count query) (count selected-label)))
      (subs query (count selected-label))
      query)))

(defn make-handler
  [{:keys [id query-fn render-html-fn render-results-fn min-chars max-results]
    :or {min-chars 2
         max-results 10}
    :as config}]
  (let [results-renderer (or render-results-fn ui/default-render-results)]
    (fn [req]
      (let [ctx (util/base-context (assoc config :min-chars min-chars :debounce-ms (or (:debounce-ms config) 250)))
            query (parse-datastar-query req id)
            trimmed-query (str/trim (or query ""))
            raw-items (if (>= (count trimmed-query) min-chars)
                        (query-fn query req)
                        [])
            items (->> raw-items
                       (keep util/normalize-item)
                       (take max-results)
                       vec)
            body (render-html-fn (results-renderer (assoc ctx
                                                     :items items
                                                     :query query
                                                     :error nil)))]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body body}))))
