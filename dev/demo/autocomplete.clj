(ns demo.autocomplete
  "Demonstrates an autocomplete text field with:

  1. Dynamic server replies matching the text
  2. Debouncing"
  (:require [demo.util :refer [->html page get-signals]]
            [clojure.string :as str]))

(defn autocomplete-handler
  "Renders the autocomplete"
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->html
          (page
           [:div {:class "flex items-start justify-center min-h-screen w-full pt-[20vh]"}
            [:div#autocomplete-component {:class "w-[400px]"}
             [:input.input.w-full
              {:type "text"
               :placeholder "Type a fruit name..."
               :data-bind:query true
               :data-on:input__debounce.300ms "@get('/demo/autocomplete/options')"}]
             [:div#autocomplete-options {:data-show "$showOptions"}]]]))})

(comment
  (autocomplete-handler {}))

(defn options-handler
  "Renders the options. As the user types,
  the text is handled here, and the autocomplete
  options are patched via datastar."
  [req]
  (let [query (some-> req get-signals :query)
        fruits ["Apple" "Apricot" "Avocado" "Banana" "Blackberry" "Blueberry"
                "Cherry" "Coconut" "Cranberry" "Date" "Dragonfruit" "Elderberry"
                "Fig" "Grape" "Grapefruit" "Guava" "Kiwi" "Lemon" "Lime"
                "Mango" "Melon" "Orange" "Papaya" "Peach" "Pear" "Pineapple"
                "Plum" "Pomegranate" "Raspberry" "Strawberry" "Watermelon"]
        filtered (if (str/blank? query)
                   []
                   (filter #(str/includes? (str/lower-case %) (str/lower-case query))
                           fruits))]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (->html
            [:div#autocomplete-options
             (when (seq filtered)
               [:div {:data-signals:show-options "'true'"
                      :id (random-uuid)}
                [:ul.menu.bg-base-200.rounded-box.w-full.mt-2
                 {:data-show "$showOptions"
                  :data-on:click__outside "$showOptions = false;"}
                 (for [fruit filtered]
                   [:li
                    [:button
                     {:data-on:click (str "$query = '" fruit "'; $showOptions = false;")}
                     fruit]])]])])}))

(defn make-routes
  [_]
  [["/autocomplete" autocomplete-handler]
   ["/autocomplete/options" options-handler]])
