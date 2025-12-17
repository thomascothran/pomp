(ns pomp.datastar.impl-test
  (:require [clojure.test :refer [deftest is]]
            [pomp.datastar.impl :as impl]))

(deftest test-make-event-attrs-map-form
  (let [result
        (#'impl/make-event-attrs
          {:click {:get ["/hello" {:debounce [:ms 100]}]
                   :post "/hi"
                   :put {"/howdy" {:throttle [:ms 200]}}}
           #_#_:focus {:set-signal {:foo "bar"}}})]
      (is (= {:data-on-click__debounce.100ms "@get('/hello');",
              :data-on-click "@post('/hi')"
              :data-on-focus "@setAll('foo', 'bar')"}
             result))))
