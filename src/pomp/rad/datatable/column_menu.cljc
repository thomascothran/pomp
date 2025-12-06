(ns pomp.rad.datatable.column-menu)

(def dots-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M12 6.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5ZM12 12.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5ZM12 18.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5Z"}]])

(def arrow-up-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M4.5 10.5 12 3m0 0 7.5 7.5M12 3v18"}]])

(def arrow-down-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3"}]])

(def list-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0ZM3.75 12h.007v.008H3.75V12Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm-.375 5.25h.007v.008H3.75v-.008Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z"}]])

(def eye-slash-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M3.98 8.223A10.477 10.477 0 0 0 1.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.451 10.451 0 0 1 12 4.5c4.756 0 8.773 3.162 10.065 7.498a10.522 10.522 0 0 1-4.293 5.774M6.228 6.228 3 3m3.228 3.228 3.65 3.65m7.894 7.894L21 21m-3.228-3.228-3.65-3.65m0 0a3 3 0 1 0-4.243-4.243m4.242 4.242L9.88 9.88"}]])

(defn render
  [{:keys [col-key col-label data-url table-id]}]
  (let [col-name (name col-key)
        popover-id (str "col-menu-" col-name)
        anchor-name (str "--col-menu-" col-name)
        hide-popover (str "document.getElementById('" popover-id "').hidePopover(); ")]
    (list
     [:button.btn.btn-ghost.btn-xs.px-1.opacity-50.hover:opacity-100
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      dots-icon]
     [:div.dropdown-content.bg-base-100.rounded-box.shadow-lg.p-2
      {:popover "auto"
       :id popover-id
       :style {:position-anchor anchor-name
               :position "absolute"
               :top "anchor(bottom)"
               :left "anchor(right)"
               :translate "-100% 0"
               :font-weight "normal"}}
      [:ul.menu.menu-sm.w-44
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "@get('" data-url "?sortCol=" col-name "&sortDir=asc')")}
         arrow-up-icon "Sort ascending"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "@get('" data-url "?sortCol=" col-name "&sortDir=desc')")}
         arrow-down-icon "Sort descending"]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "@get('" data-url "?groupBy=" col-name "')")}
         list-icon "Group by " col-label]]
       [:li
        [:a.flex.items-center.gap-2 {:data-on:click (str hide-popover "$" table-id ".columns." col-name ".visible = false; @get('" data-url "')")}
         eye-slash-icon "Hide column"]]]])))

(defn render-group-column
  [{:keys [data-url]}]
  (let [popover-id "col-menu-group"
        anchor-name "--col-menu-group"]
    (list
     [:button.btn.btn-ghost.btn-xs.px-1.opacity-50.hover:opacity-100
      {:popovertarget popover-id
       :style {:anchor-name anchor-name}}
      dots-icon]
     [:div.dropdown-content.bg-base-100.rounded-box.shadow-lg.p-2
      {:popover "auto"
       :id popover-id
       :style {:position-anchor anchor-name
               :position "absolute"
               :top "anchor(bottom)"
               :left "anchor(right)"
               :translate "-100% 0"
               :font-weight "normal"}}
      [:ul.menu.menu-sm.w-44
       [:li
        [:a {:data-on:click (str "document.getElementById('" popover-id "').hidePopover(); @get('" data-url "?ungroup=true')")}
         "Remove grouping"]]]])))
