(ns pomp.rad.datatable.handler.save
  (:require [clojure.data.json :as json]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [starfederation.datastar.clojure.api :as d*]))

(defn- save-open-fn
  [{:keys [req id raw-signals save-fn extract-cell-edit-fn]}]
  (let [cell-edit (extract-cell-edit-fn raw-signals)]
    (when cell-edit
      (save-fn (assoc cell-edit :req req)))
    (fn [sse]
      (when cell-edit
        (let [{:keys [row-id col-key]} cell-edit
              editing-path {(keyword row-id) {col-key false}}]
          (d*/patch-signals! sse
                             (json/write-str
                              {:datatable {(keyword id) {:_editing editing-path
                                                         :cells nil}}})))))))

(defn emit-save-action!
  [{:keys [sse] :as opts}]
  ((save-open-fn opts) sse))

(defn handle-save-action!
  [{:keys [req] :as opts}]
  (let [open-fn (save-open-fn opts)]
    (->sse-response req
                    {on-open
                     (fn [sse]
                       (open-fn sse)
                       (d*/close-sse! sse))})))
