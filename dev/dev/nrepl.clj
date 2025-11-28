(ns dev.nrepl
  (:require [nrepl.server :as nrepl]))

(defn start-nrepl!
  [{:keys [port]
    :or {port 7888}
    :as c}]
  (spit ".nrepl-port" port)
  (println "starting nrepl on port " port)
  (assoc c :nrepl/server
         (nrepl/start-server
          :port port)))
