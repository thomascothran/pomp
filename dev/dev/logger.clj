(ns dev.logger
  (:require [com.brunobonacci.mulog :as u]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [starfederation.datastar.clojure.api :as d*]))

(defonce !publisher
  (atom nil))

(def file-path
  "logs/events.log")

(defn start!
  []
  (when-not @!publisher
    (try (io/delete-file file-path)
         (catch Exception _))
    (reset! !publisher
            (u/start-publisher! {:type :simple-file
                                 :filename file-path}))))

(defn params-middleware
  [handler]
  (fn [req]
    (let [is-d*? (get-in req [:headers "datastar-request"])
          raw-signals (when is-d*?
                        (d*/get-signals req))
          slurped-signals
          (when raw-signals
            (if (string? raw-signals)
              raw-signals
              (slurp raw-signals)))
          signals
          (when (and (string? slurped-signals)
                     (not= slurped-signals ""))
            (some-> slurped-signals
                    (json/read-str {:key-fn keyword})))]
      (u/log ::params-middleware
             :uri (get req :uri)
             :request-method (get req :method)
             :body-params (get req :body-params)
             :query-params (get req :query-params)
             :path-params (get req :path-params)
             :parsed-signals signals)

      (handler req))))

(defn exception-trace-middleware
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (binding [*out* *err*]
          (println "[request-exception]"
                   {:uri (:uri req)
                    :request-method (:request-method req)
                    :query-string (:query-string req)
                    :headers (select-keys (:headers req)
                                          ["datastar-request" "accept" "content-type" "host" "referer"])})
          (.printStackTrace t))
        (throw t)))))
