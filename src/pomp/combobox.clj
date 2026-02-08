(ns pomp.combobox
  (:require [clojure.data.json :as json]
            [pomp.rad.combobox.handler :as handler]
            [pomp.rad.combobox.ui :as ui]
            [pomp.rad.combobox.util :as util]))

(defn render
  [{:keys [render-input-fn render-results-fn min-chars debounce-ms]
    :or {min-chars 2
         debounce-ms 250}
    :as config}]
  (let [ctx (util/base-context (assoc config :min-chars min-chars :debounce-ms debounce-ms))
        input-renderer (or render-input-fn ui/default-render-input)
        results-renderer (or render-results-fn ui/default-render-results)
        initial-signals (json/write-str
                         {:combobox {(keyword (:id config))
                                     {:query ""
                                      :selectedValue ""
                                      :selectedLabel ""
                                      :resultsOpen false
                                      :loadingOptions false
                                      :error nil}}})]
    [:div {:id (get-in ctx [:ids :wrapper])
           :class "relative w-full"
           :data-signals initial-signals}
      (input-renderer ctx)
      (results-renderer (assoc ctx :items [] :query "" :error nil))]))

(defn make-handler
  [config]
  (handler/make-handler config))
