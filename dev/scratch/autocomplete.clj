(ns scratch.autocomplete
  (:require [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as c]
            [pomp.combobox :as combobox]
            [pomp.element.navbar :as navbar]
            [pomp.element.theme-picker :as theme-picker]))

(def items
  ["Apple" "Apricot" "Avocado" "Banana" "Blackberry" "Blueberry"
   "Cherry" "Coconut" "Cranberry" "Date" "Dragonfruit" "Fig"
   "Grape" "Grapefruit" "Guava" "Kiwi" "Lemon" "Lime"
   "Mango" "Melon" "Orange" "Papaya" "Peach" "Pear"
   "Pineapple" "Plum" "Pomegranate" "Raspberry" "Strawberry" "Watermelon"])

(defn page
  [& children]
  [:html {:data-theme "light"}
   [:head
    [:link {:href "/assets/output.css"
            :rel "stylesheet"}]
    [:script {:type "module"
              :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"}]]
   [:body {:class "min-h-screen m-0 bg-base-200"}
    children]])

(def combobox-id "scratch-autocomplete")

(defn- render-input
  [{:keys [ids]}]
  [:div {:class "relative"}
   [:input {:id (:input ids)
            :type "text"
            :class "input input-bordered w-full pr-10"
            :placeholder "Type to search..."
            :data-bind:query true
            :data-indicator:loadingoptions true
            :data-on:click "if (evt.target && evt.target.select) { evt.target.select(); }"
            :data-on:input "$resultsOpen = (($query || '').trim().length >= 2);"
            :data-on:input__debounce.250ms "@get('/scratch/autocomplete/options')"
            :data-on:keydown "if (evt.key === 'Enter') { evt.preventDefault(); const _raw = ((((evt.target && evt.target.value) || ''))).trim(); const _selected = ($selectedItem || ''); const _q = (_selected && _raw.startsWith(_selected) && _raw.length > _selected.length) ? _raw.slice(_selected.length) : _raw; if (evt.target) { evt.target.value = _q; } $query = _q; $selectedItem = _q; $resultsOpen = false; } if (evt.key === 'Escape') { $resultsOpen = false; }"
            :data-on:blur "setTimeout(() => { $resultsOpen = false; }, 120);"
            :autocomplete "off"
            :role "combobox"
            :aria-autocomplete "list"
            :aria-haspopup "listbox"
            :aria-controls "scratch-autocomplete-results-listbox"
            :data-attr:aria-expanded "$resultsOpen ? 'true' : 'false'"}]
   [:div {:class "pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3"
          :data-show "$loadingoptions"
          :role "status"
          :aria-live "polite"
          :aria-hidden "true"}
    [:span {:class "loading loading-spinner loading-sm"}]]])

(defn- option-button
  [item]
  [:li
   [:button {:type "button"
             :class "btn btn-ghost justify-start"
             :role "option"
             :data-on:click (str "$query = '" item "'; $selectedItem = '" item "'; $resultsOpen = false;")}
    item]])

(defn- render-results
  [{:keys [ids items query]}]
  (let [trimmed-query (str/trim (or query ""))
        no-match-query? (and (>= (count trimmed-query) 2)
                             (empty? items))]
    [:div {:id "scratch-autocomplete-results"
           :class "absolute left-0 top-full z-20 w-full"
           :data-show "$resultsOpen"}
     [:div {:id (:panel ids)
            :class "w-full"
            :data-show "$resultsOpen"}
      (cond
        no-match-query?
        [:div {:class "mt-2 rounded-box border border-base-300 bg-base-100 p-2 shadow-lg"}
         [:div {:class "alert alert-info"
                :role "status"
                :aria-live "polite"}
          [:span "No results found"]]
         [:ul {:id (:listbox ids)
               :class "hidden"
               :aria-hidden "true"}]]

        (seq items)
        [:div {:class "mt-2 rounded-box border border-base-300 bg-base-100 p-1 shadow-lg"}
         [:ul {:id "scratch-autocomplete-results-listbox"
               :class "menu w-full"
               :role "listbox"
               :aria-live "polite"}
          (for [{:keys [label]} items]
            (option-button label))]
         [:ul {:id (:listbox ids)
               :class "hidden"
               :aria-hidden "true"}]]

        :else
        [:ul {:id (:listbox ids)
              :class "hidden"
              :aria-hidden "true"}])]]))

(defn- render-error
  []
  (c/html
   [:div {:id "scratch-autocomplete-results"
          :class "absolute left-0 top-full z-20 w-full"
          :data-show "$resultsOpen"}
    [:div {:id "combobox-scratch-autocomplete-panel"
           :class "w-full"
           :data-show "$resultsOpen"}
     [:div {:class "mt-2 rounded-box border border-base-300 bg-base-100 p-2 shadow-lg"}
      [:div {:class "alert alert-error"
             :role "alert"
             :aria-live "assertive"}
       [:span "Unable to load results"]]]]]))

(defn handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body
   (c/html
    (page
     [:div {:class "min-h-screen flex flex-col"}
      (navbar/navbar
       {:attrs {:class "bg-base-100 border-b border-base-300"
                :role "navigation"}
        :left-group [:a {:href "/scratch/autocomplete"
                         :class "btn btn-ghost text-lg"}
                     "Scratch Autocomplete"]
        :right-group (theme-picker/theme-picker {})})
       [:main {:class "mx-auto w-full max-w-2xl p-4 sm:p-6"}
        [:div {:class "card bg-base-100 shadow"}
         [:div {:class "card-body gap-4"}
          [:label {:class "form-control w-full"}
           [:span {:class "label-text mb-2"} "Search"]
           (combobox/render
            {:id combobox-id
             :data-url "/scratch/autocomplete/options"
             :render-input-fn render-input
             :render-results-fn render-results
             :render-html-fn c/html})]
          [:div {:class "text-sm"}
           [:span {:class "font-semibold"} "Selected: "]
           [:span {:data-text "$selectedItem || 'None'"}]]]]]]))})

(defn- filtered-items
  [query]
  (let [q (some-> query str/trim)]
    (if (or (nil? q) (< (count q) 2))
      []
      (filter #(str/includes? (str/lower-case %) (str/lower-case q)) items))))

(def options-response
  (combobox/make-handler
   {:id combobox-id
    :data-url "/scratch/autocomplete/options"
    :query-fn (fn [query _req]
                (mapv (fn [item]
                        {:label item
                         :value item})
                      (filtered-items query)))
    :render-results-fn render-results
    :render-html-fn c/html}))

(defn options-handler
  [req]
  (let [query (or (some-> req (get-in [:params :query])) "")
        error-query? (= "error" (some-> query str/trim str/lower-case))]
    (if error-query?
      {:status 500
       :headers {"Content-Type" "text/html"}
       :body (render-error)}
      (options-response req))))
