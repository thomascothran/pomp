(ns pomp.rad.report
  (:require [pomp.rad.analysis :as analysis]
            [pomp.rad.datatable :as datatable]
            [clojure.string :as str]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [starfederation.datastar.clojure.api :as d*]))

(defn- export-action?
  [req]
  (= "export" (get-in req [:query-params "action"])))

(defn- datatable-handler-for-request
  [datatable-handlers req]
  (or (get datatable-handlers (:request-method req))
      (:post datatable-handlers)
      (:get datatable-handlers)))

(defn- sse-response->string
  [resp]
  (let [out (java.io.ByteArrayOutputStream.)
        _ (.write_body_to_stream (:body resp) resp out)
        bytes (.toByteArray out)
        content-encoding (or (get-in resp [:headers "Content-Encoding"])
                             (get-in resp [:headers "content-encoding"]))]
    (if (= "gzip" content-encoding)
      (with-open [in (java.util.zip.GZIPInputStream. (java.io.ByteArrayInputStream. bytes))]
        (slurp in))
      (String. bytes "UTF-8"))))

(defn- parse-sse-events
  [sse-body]
  (->> (str/split sse-body #"\n\n")
       (map str/trim)
       (remove str/blank?)
       (map (fn [block]
              (let [lines (str/split-lines block)]
                {:event (some (fn [line]
                                (when (str/starts-with? line "event: ")
                                  (subs line 7)))
                              lines)
                 :data (->> lines
                            (keep (fn [line]
                                    (when (str/starts-with? line "data: ")
                                      (subs line 6))))
                            vec)})))
       (remove (comp str/blank? :event))))

(defn- replay-sse-response!
  [sse resp]
  (doseq [{:keys [event data]} (parse-sse-events (sse-response->string resp))]
    (.send_event_BANG_ sse event data nil)))

(defn- report-refresh-handler
  [{:keys [datatable-handlers analysis-handler]}]
  (fn [req]
    (let [datatable-handler (datatable-handler-for-request datatable-handlers req)]
    (->sse-response req
                    {on-open
                     (fn [sse]
                         (replay-sse-response! sse (datatable-handler req))
                         (replay-sse-response! sse (analysis-handler req))
                         (d*/close-sse! sse))}))))

(defn- report-export-handler
  [{:keys [datatable-handlers]}]
  (fn [req]
    (let [datatable-handler (datatable-handler-for-request datatable-handlers req)]
    (->sse-response req
                    {on-open
                     (fn [sse]
                        (replay-sse-response! sse (datatable-handler req))
                        (d*/close-sse! sse))}))))

(defn make-report
  [{:keys [data-url datatable analysis] :as config}]
  (let [datatable-config (assoc datatable :data-url data-url)
        datatable-handlers (datatable/make-handlers datatable-config)
        datatable-descriptor (merge datatable-config datatable-handlers)
        analysis-config (assoc analysis :analysis-url data-url)
        analysis-descriptor (analysis/make-board analysis-config)
        coordinated-handler (report-refresh-handler {:datatable-handlers datatable-handlers
                                                     :analysis-handler (:handler analysis-descriptor)})
        export-handler (report-export-handler {:datatable-handlers datatable-handlers})]
    {:datatable datatable-descriptor
     :analysis analysis-descriptor
     :handler (fn [req]
                 (if (export-action? req)
                   (export-handler req)
                   (coordinated-handler req)))}))
