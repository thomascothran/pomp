(ns pomp.element.theme-picker)

(def default-themes
  ["light" "dark" "cupcake" "bumblebee" "emerald" "corporate" "synthwave"
   "retro" "cyberpunk" "valentine" "halloween" "garden" "forest" "aqua"
   "lofi" "pastel" "fantasy" "wireframe" "black" "luxury" "dracula" "cmyk"
   "autumn" "business" "acid" "lemonade" "night" "coffee" "winter" "dim"
   "nord" "sunset" "caramellatte" "abyss" "silk"])

(def default-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "1.5"
         :stroke "currentColor"
         :class "h-4 w-4"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m15 11.25 1.5 1.5.75-.75V8.758l2.276-.61a3 3 0 1 0-3.675-3.675l-.61 2.277H12l-.75.75 1.5 1.5M15 11.25l-8.47 8.47c-.34.34-.8.53-1.28.53s-.94.19-1.28.53l-.97.97-.75-.75.97-.97c.34-.34.53-.8.53-1.28s.19-.94.53-1.28L12.75 9M15 11.25 12.75 9"}]])

(defn theme-picker
  [{:keys [themes icon]
    :or {themes default-themes
         icon default-icon}}]
  [:div {:class "dropdown dropdown-end"}
   [:div {:tabindex 0
          :role "button"
          :class "btn btn-sm gap-1"}
    icon
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :viewBox "0 0 24 24"
           :fill "none"
           :stroke "currentColor"
           :stroke-width "2"
           :class "h-3 w-3"}
     [:path {:stroke-linecap "round"
             :stroke-linejoin "round"
             :d "m6 9 6 6 6-6"}]]]
   [:ul {:tabindex 0
         :class "dropdown-content menu menu-sm bg-base-100 rounded-box p-2 shadow"}
    (for [theme themes]
      [:li
       [:button {:type "button"
                 :data-on:click (str "document.documentElement.setAttribute('data-theme', '" theme "')")}
        theme]])]])
