(ns pomp.rad.datatable.pagination
  (:require [pomp.rad.datatable.util :as util]))

(defn paginate-data [rows signals]
  (let [{:keys [size current]} signals]
    (->> rows
         (drop (* current size))
         (take size))))

(defn next-state
  [signals query-params total-rows]
  (let [page-action (get query-params "page")
        new-size-str (get query-params "pageSize")
        current-size (:size signals 10)
        current-page (:current signals 0)
        size (if new-size-str
               #?(:clj (parse-long new-size-str)
                  :cljs (js/parseInt new-size-str 10))
               current-size)
        total-pgs (util/total-pages total-rows size)
        page (cond
               new-size-str 0
               (= page-action "first") 0
               (= page-action "prev") (max 0 (dec current-page))
               (= page-action "next") (min (dec total-pgs) (inc current-page))
               (= page-action "last") (dec total-pgs)
               :else (min current-page (max 0 (dec total-pgs))))]
    {:size size :current page}))

(defn render
  [{:keys [total-rows page-size page-current filters page-sizes data-url]}]
  (let [total-pgs (util/total-pages total-rows page-size)
        start (if (zero? total-rows) 0 (+ 1 (* page-current page-size)))
        end (min (* (+ page-current 1) page-size) total-rows)
        on-first? (= page-current 0)
        on-last? (or (zero? total-rows) (>= (+ page-current 1) total-pgs))]
    [:div.flex.items-center.justify-between.mt-4.text-sm.opacity-70
     [:div.flex.items-center.gap-4
      (when (util/has-active-filters? filters)
        [:button.btn.btn-sm.btn-ghost.text-error.opacity-100
         {:data-on:click (str "@get('" data-url "?clearFilters=1')")}
         "✕ Clear filters"])
      [:div.flex.items-center.gap-1
       [:select.select.select-ghost.select-sm.font-medium
        {:data-on:change (str "@get('" data-url "?pageSize=' + evt.target.value)")}
        (for [size page-sizes]
          [:option {:value size :selected (= size page-size)} size])]
       [:span.whitespace-nowrap "per page"]]]
     [:div (str start "–" end " of " total-rows)]
     [:div.flex.items-center.gap-1
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click (str "@get('" data-url "?page=first')")
        :disabled on-first?}
       "«"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click (str "@get('" data-url "?page=prev')")
        :disabled on-first?}
       "‹"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click (str "@get('" data-url "?page=next')")
        :disabled on-last?}
       "›"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click (str "@get('" data-url "?page=last')")
        :disabled on-last?}
       "»"]]]))
