(ns pomp.rad.datatable.ui.pagination
  (:require [pomp.rad.datatable.util :as util]))

(defn render
  [{:keys [total-rows page-size page-current page-sizes data-url]}]
  (let [total-pgs (util/total-pages total-rows page-size)
        unknown-total? (nil? total-rows)
        start (if (and (not unknown-total?) (zero? total-rows))
                0
                (+ 1 (* page-current page-size)))
        end (if unknown-total?
              (* (+ page-current 1) page-size)
              (min (* (+ page-current 1) page-size) total-rows))
        on-first? (= page-current 0)
        on-last? (or (and (not unknown-total?)
                          (zero? total-rows))
                     (and total-pgs
                          (>= (+ page-current 1) total-pgs)))]
    [:div.mt-4.flex.flex-col.gap-3.text-sm.opacity-70.sm:flex-row.sm:items-center.sm:justify-between
     [:div.flex.items-center.gap-3.self-start
       #_(when (util/has-active-filters? filters)
           [:button.btn.btn-sm.btn-ghost.text-error.opacity-100
            {:data-on:click (str "@post('" data-url "?clearFilters=1')")}
            "✕ Clear filters"])
       [:div.flex.flex-wrap.items-center.gap-2
        [:select.select.select-ghost.select-sm.font-medium
         {:data-on:change (str "@post('" data-url "?pageSize=' + evt.target.value)")}
         (for [size page-sizes]
           [:option {:value size :selected (= size page-size)} size])]
        [:span.whitespace-nowrap "per page"]]]
     [:div.self-start.whitespace-nowrap.sm:self-auto (if unknown-total?
              (str start "–" end)
              (str start "–" end " of " total-rows))]
     [:div.flex.flex-wrap.items-center.gap-1.self-start.sm:self-auto
       [:button.btn.btn-ghost.btn-sm.btn-square
        {:data-on:click (str "@post('" data-url "?page=first')")
         :disabled on-first?}
        "«"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click (str "@post('" data-url "?page=prev')")
        :disabled on-first?}
       "‹"]
      [:button.btn.btn-ghost.btn-sm.btn-square
       {:data-on:click (str "@post('" data-url "?page=next')")
        :disabled (and (not unknown-total?) on-last?)}
       "›"]
      (when-not unknown-total?
        [:button.btn.btn-ghost.btn-sm.btn-square
         {:data-on:click (str "@post('" data-url "?page=last')")
          :disabled on-last?}
         "»"])]]))
