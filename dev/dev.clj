(ns dev
  (:require [dev.nrepl]
            [dev.http]
            [dev.logger]))

(defn start-development!
  "Call from the cli to start the system"
  [{:keys [nrepl-port]
    :or   {nrepl-port 7999}}]
  (println "Starting development environment...")
  (dev.nrepl/start-nrepl! {:port nrepl-port})
  (dev.http/start)
  (dev.logger/start!)
  (println "Development environment ready!")
  @(promise))
