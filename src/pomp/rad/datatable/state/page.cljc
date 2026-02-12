(ns pomp.rad.datatable.state.page)

(defn next-state
  [signals query-params]
  (let [action (get query-params "action")
        page-action (get query-params "page")
        new-size-str (get query-params "pageSize")
        current-size (:size signals 10)
        current-page (:current signals 0)
        size (if new-size-str
               #?(:clj (parse-long new-size-str)
                  :cljs (js/parseInt new-size-str 10))
               current-size)
        page (cond
               new-size-str 0
               (= action "global-search") 0
               (= page-action "first") 0
               (= page-action "prev") (max 0 (dec current-page))
               (= page-action "next") (inc current-page)
               (= page-action "last") nil
               :else current-page)]
    {:size size :current page}))
