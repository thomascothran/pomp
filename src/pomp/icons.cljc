(ns pomp.icons)

(def chevron-right
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "2"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m8.25 4.5 7.5 7.5-7.5 7.5"}]])

(def chevron-down
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "2"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m19.5 8.25-7.5 7.5-7.5-7.5"}]])

(def sort-icon-both
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "1.5"
         :stroke "currentColor"
         :class "w-3 h-3"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M3 7.5 7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5"}]])

(def sort-icon-asc
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "2"
         :stroke "currentColor"
         :class "w-3 h-3"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M4.5 15.75l7.5-7.5 7.5 7.5"}]])

(def sort-icon-desc
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "2"
         :stroke "currentColor"
         :class "w-3 h-3"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M19.5 8.25l-7.5 7.5-7.5-7.5"}]])

(def funnel-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "1.5"
         :stroke "currentColor"
         :class "w-4 h-4"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 0 1-.659 1.591l-5.432 5.432a2.25 2.25 0 0 0-.659 1.591v2.927a2.25 2.25 0 0 1-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 0 0-.659-1.591L3.659 7.409A2.25 2.25 0 0 1 3 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0 1 12 3Z"}]])

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

(def menu-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.5"
         :class "size-6"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M3.75 5.25h16.5m-16.5 4.5h16.5m-16.5 4.5h16.5m-16.5 4.5h16.5"}]])

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

(def columns-icon
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke-width "1.5"
    :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M9 4.5v15m6-15v15m-10.875 0h15.75c.621 0 1.125-.504 1.125-1.125V5.625c0-.621-.504-1.125-1.125-1.125H4.125C3.504 4.5 3 5.004 3 5.625v12.75c0 .621.504 1.125 1.125 1.125Z"}]])

(def boolean-true-icon
  [:svg.w-4.h-4.text-success {:xmlns "http://www.w3.org/2000/svg"
                              :fill "none"
                              :viewBox "0 0 24 24"
                              :stroke-width "2"
                              :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m4.5 12.75 6 6 9-13.5"}]])

(def boolean-false-icon
  [:svg.w-4.h-4.text-base-content.opacity-30 {:xmlns "http://www.w3.org/2000/svg"
                                              :fill "none"
                                              :viewBox "0 0 24 24"
                                              :stroke-width "2"
                                              :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M6 18 18 6M6 6l12 12"}]])

(def edit-pencil-icon
  [:svg.w-3.h-3 {:class "text-base-content/50"
                 :xmlns "http://www.w3.org/2000/svg"
                 :fill "none"
                 :viewBox "0 0 24 24"
                 :stroke-width "1.5"
                 :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m16.862 3.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L10.582 15.07a4.5 4.5 0 0 1-1.897 1.13L6 17l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Zm0 0L19.5 6.125"}]])

(def save-icon
  [:svg.w-4.h-4.text-success {:xmlns "http://www.w3.org/2000/svg"
                              :fill "none"
                              :viewBox "0 0 24 24"
                              :stroke-width "1.5"
                              :stroke "currentColor"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m4.5 12.75 6 6 9-13.5"}]])

(def theme-default-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"
         :stroke-width "1.5"
         :stroke "currentColor"
         :class "h-4 w-4"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m15 11.25 1.5 1.5.75-.75V8.758l2.276-.61a3 3 0 1 0-3.675-3.675l-.61 2.277H12l-.75.75 1.5 1.5M15 11.25l-8.47 8.47c-.34.34-.8.53-1.28.53s-.94.19-1.28.53l-.97.97-.75-.75.97-.97c.34-.34.53-.8.53-1.28s.19-.94.53-1.28L12.75 9M15 11.25 12.75 9"}]])

(def theme-chevron-down-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :class "h-3 w-3"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "m6 9 6 6 6-6"}]])
