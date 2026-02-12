(ns pomp.rad.datatable.ui.table-search-test
  (:require [clojure.test :refer [deftest is]]
            [pomp.rad.datatable.ui.table-search :as table-search]))

(deftest render-table-search-default-renderer-test
  (let [result (table-search/render-table-search {:data-url "/demo/datatable/data"
                                                  :table-id "datatable"
                                                  :global-table-search ""})
        attrs (second result)
        debounce-key :data-on:input__debounce.300ms
        debounce-handler (get attrs debounce-key)
        input-handler (:data-on:input attrs)
        bind-key :data-bind:datatable.datatable.globalTableSearch]
    (is (= :input (first result)))
    (is (= "input input-sm input-bordered w-56 focus:outline-none focus:outline-offset-0"
           (:class attrs))
        "Should apply daisy-first polished search classes")
    (is (= true (get attrs bind-key))
        "Should bind to datatable.<id>.globalTableSearch signal")
    (is (string? input-handler))
    (is (.contains input-handler "const _q = ((evt.target && evt.target.value) || '').trim();"))
    (is (.contains input-handler "$datatable.datatable.globalTableSearch = (_q.length >= 2 ? _q : '');")
        "Should enforce minimum 2 chars by setting effective search value to empty string")
    (is (string? debounce-handler))
    (is (.contains debounce-handler "@post('/demo/datatable/data?action=global-search')")
        "Should post to same data URL with action=global-search on debounce")))
