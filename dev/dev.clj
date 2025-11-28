(ns dev
  (:require [dev.nrepl]))

(defn start-development!
  "Call from the cli to start the system"
  [{:keys [nrepl-port]
    :or   {nrepl-port 7888}}]
  (println "Starting development environment...")
  (dev.nrepl/start-nrepl! {:port nrepl-port})
  (println "Development environment ready!")
  @(promise))
