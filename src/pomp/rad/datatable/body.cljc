(ns pomp.rad.datatable.body)

(defn render-row [cols row]
  [:tr
   (for [{:keys [key render]} cols]
     [:td (if render
            (render (get row key) row)
            (get row key))])])

(defn render [cols rows]
  [:tbody
   (for [row rows]
     (render-row cols row))])

(defn render-skeleton-row [cols]
  [:tr
   (for [_ cols]
     [:td [:div.skeleton.h-4.w-full]])])

(defn render-skeleton [cols n]
  [:tbody
   (for [_ (range n)]
     (render-skeleton-row cols))])
