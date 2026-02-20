(ns pomp.rad.analysis.renderer.vega-lite
  (:require [clojure.data.json :as json]))

(def ^:private vega-url "https://cdn.jsdelivr.net/npm/vega@5/build/vega.min.js")
(def ^:private vega-lite-url "https://cdn.jsdelivr.net/npm/vega-lite@5/build/vega-lite.min.js")
(def ^:private vega-embed-url "https://cdn.jsdelivr.net/npm/vega-embed@6/build/vega-embed.min.js")

(def ^:private responsive-spec-attrs
  {"width" "container"
   "autosize" {"type" "fit-x"
               "contains" "padding"
               "resize" true}})

(defn bar-spec
  [{:keys [values title x-title y-title]}]
  (merge
   {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
    "description" (or title "Frequency chart")
    "data" {"values" (or values [])}
    "mark" {"type" "bar"}
    "encoding" {"x" {"field" "label"
                         "type" "nominal"
                         "title" (or x-title "Bucket")
                         "sort" "-y"}
                 "y" {"field" "value"
                         "type" "quantitative"
                         "title" (or y-title "Count")}
                 "tooltip" [{"field" "label" "type" "nominal" "title" (or x-title "Bucket")}
                             {"field" "value" "type" "quantitative" "title" (or y-title "Count")}]}}
   responsive-spec-attrs))

(defn pie-spec
  [{:keys [values title]}]
  (merge
   {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
    "description" (or title "Pie chart")
    "data" {"values" (or values [])}
    "mark" {"type" "arc"}
    "encoding" {"theta" {"field" "value" "type" "quantitative"}
                 "color" {"field" "label" "type" "nominal" "title" "Region"}
                 "tooltip" [{"field" "label" "type" "nominal" "title" "Region"}
                             {"field" "value" "type" "quantitative" "title" "Count"}]}}
   responsive-spec-attrs))

(defn histogram-spec
  [{:keys [values title subtitle x-title y-title]}]
  (merge
   {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"
    "description" (or title "Histogram")
    "title" {"text" (or title "Histogram")
               "subtitle" (or subtitle "")}
    "data" {"values" (or values [])}
    "mark" {"type" "bar"}
    "encoding" {"x" {"field" "label"
                         "type" "ordinal"
                         "title" (or x-title "Bucket")}
                 "y" {"field" "value"
                         "type" "quantitative"
                         "title" (or y-title "Count")}
                 "tooltip" [{"field" "label" "type" "ordinal" "title" (or x-title "Bucket")}
                             {"field" "value" "type" "quantitative" "title" (or y-title "Count")}]}}
   responsive-spec-attrs))

(defn render-script
  [{:keys [target-id spec]}]
  (str "(function(){"
       "const targetId=" (json/write-str target-id) ";"
       "const spec=" (json/write-str spec) ";"
       "const runtimeKey='__pompVegaRuntimePromise';"
       "const loadScript=function(url){return new Promise(function(resolve,reject){"
       "const existing=document.querySelector('script[data-pomp-vega-url=\"'+url+'\"]');"
       "if(existing){"
       "if(existing.dataset.loaded==='true'){resolve();return;}"
       "existing.addEventListener('load',function(){resolve();},{once:true});"
       "existing.addEventListener('error',function(){reject(new Error('Failed to load '+url));},{once:true});"
       "return;}"
       "const script=document.createElement('script');"
       "script.src=url;script.async=true;"
       "script.dataset.pompVegaUrl=url;"
       "script.onload=function(){script.dataset.loaded='true';resolve();};"
       "script.onerror=function(){reject(new Error('Failed to load '+url));};"
       "document.head.appendChild(script);"
       "});};"
       "const ensureRuntime=function(){"
       "if(window[runtimeKey]){return window[runtimeKey];}"
       "window[runtimeKey]=loadScript('" vega-url "')"
       ".then(function(){return loadScript('" vega-lite-url "');})"
       ".then(function(){return loadScript('" vega-embed-url "');});"
       "return window[runtimeKey];};"
       "const target=document.getElementById(targetId);"
       "if(!target){return;}"
       "ensureRuntime().then(function(){"
       "if(typeof window.vegaEmbed==='function'){"
       "window.vegaEmbed('#'+targetId,spec,{actions:false});"
       "}}).catch(function(err){if(window.console&&window.console.error){window.console.error('Pomp Vega runtime error',err);}});"
       "})();"))
