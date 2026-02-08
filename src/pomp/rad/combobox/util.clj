(ns pomp.rad.combobox.util
  (:require [clojure.string :as str]))

(defn element-id
  [id suffix]
  (str "combobox-" id suffix))

(defn signal-path
  [id k]
  (str "combobox." id "." k))

(defn signal-ref
  [id k]
  (str "$combobox['" id "']." k))

(defn kebab->camel
  [s]
  (->> (str/split (str s) #"-")
       (map-indexed (fn [idx segment]
                      (if (zero? idx)
                        segment
                        (str/capitalize segment))))
       (apply str)))

(defn kebab->camel-keyword
  [s]
  (keyword (kebab->camel s)))

(defn present-string
  [value]
  (when (and (some? value) (not (str/blank? (str value))))
    (str value)))

(defn base-context
  [{:keys [id data-url min-chars debounce-ms]}]
  (let [camel-id (kebab->camel id)
        paths {:query (signal-path id "query")
               :selected-value (signal-path id "selectedValue")
               :selected-label (signal-path id "selectedLabel")
               :results-open (signal-path id "resultsOpen")
               :loading-options (signal-path id "loadingOptions")
               :error (signal-path id "error")}
        refs {:query (signal-ref id "query")
              :query-alt (when (not= camel-id id)
                           (signal-ref camel-id "query"))
              :selected-value (signal-ref id "selectedValue")
              :selected-label (signal-ref id "selectedLabel")
              :results-open (signal-ref id "resultsOpen")
              :loading-options (signal-ref id "loadingOptions")
              :error (signal-ref id "error")}
        ids {:wrapper (element-id id "")
             :input (element-id id "-input")
             :panel (element-id id "-panel")
             :listbox (element-id id "-listbox")}
        debounce-key (str "data-on:input__debounce." debounce-ms "ms")]
    {:id id
     :ids ids
     :signal-paths paths
     :signal-refs refs
     :query-path (:query paths)
     :results-open-path (:results-open paths)
     :loading-options-path (:loading-options paths)
     :query-ref (:query refs)
     :results-open-ref (:results-open refs)
     :loading-options-ref (:loading-options refs)
     :selected-value-ref (:selected-value refs)
     :selected-label-ref (:selected-label refs)
     :data-url data-url
     :min-chars min-chars
     :debounce-ms debounce-ms
     :debounce-key debounce-key}))

(defn normalize-item
  [item]
  (when (map? item)
    (let [label (:label item)
          value (:value item)]
      (when (and (some? label) (some? value))
        {:label (if (keyword? label) (name label) (str label))
         :value (if (keyword? value) (name value) (str value))}))))
