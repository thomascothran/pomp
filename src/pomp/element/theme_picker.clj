(ns pomp.element.theme-picker
  (:require [pomp.icons :as icons]))

(def default-themes
  ["light" "dark" "cupcake" "bumblebee" "emerald" "corporate" "synthwave"
   "retro" "cyberpunk" "valentine" "halloween" "garden" "forest" "aqua"
   "lofi" "pastel" "fantasy" "wireframe" "black" "luxury" "dracula" "cmyk"
   "autumn" "business" "acid" "lemonade" "night" "coffee" "winter" "dim"
   "nord" "sunset" "caramellatte" "abyss" "silk"])

(def default-icon icons/theme-default-icon)

(defn theme-picker
  [{:keys [themes icon]
    :or {themes default-themes
         icon default-icon}}]
  [:div {:class "dropdown dropdown-end"}
   [:div {:tabindex 0
          :role "button"
          :class "btn btn-sm gap-1"}
    icon
    icons/theme-chevron-down-icon]
   [:ul {:tabindex 0
         :class "dropdown-content menu menu-sm bg-base-100 rounded-box p-2 shadow"}
    (for [theme themes]
      [:li
       [:button {:type "button"
                 :data-on:click (str "document.documentElement.setAttribute('data-theme', '" theme "')")}
        theme]])]])
