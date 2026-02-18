(ns scratch.node-based-ui
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as c]
            [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
            [starfederation.datastar.clojure.api :as d*]))

(def graph-id "scratch-node-ui")

(def ^:private init-payload
  {:graph-id graph-id
   :seed-node-id "node-a"
   :revision 1
   :nodes [{:id "node-a" :label "Gather Inputs" :x 44 :y 110 :w 256 :h 132 :state :idle}
           {:id "node-b" :label "Assemble Plan Packet" :x 560 :y 274 :w 256 :h 132 :state :idle}
           {:id "node-c" :label "Review Decisions" :x 860 :y 360 :w 256 :h 132 :state :idle}]
   :edges [{:id "edge-node-a-node-b" :source "node-a" :target "node-b" :source-anchor :right :target-anchor :left :style :cubic-bezier}
           {:id "edge-node-b-node-c" :source "node-b" :target "node-c" :source-anchor :bottom :target-anchor :left :style :cubic-bezier}]
   :viewport {:tx 0 :ty 0 :scale 1 :fit? true}
   :selected-node-id nil
   :warnings []})

(defn- open-payload
  [node-id]
  (let [safe-id (if (and (string? node-id) (seq (str/trim node-id)))
                  (str/trim node-id)
                  "node-a")
        child-id (str safe-id "-details")]
    {:graph-id graph-id
     :patch-id (str "open-" safe-id)
     :revision 2
     :nodes-upsert [{:id child-id
                     :label (str "Open: " safe-id)
                     :x 940
                     :y 120
                     :w 256
                     :h 132
                     :state :idle}]
     :nodes-remove []
     :edges-upsert [{:id (str "edge-" safe-id "-" child-id)
                     :source safe-id
                     :target child-id
                     :source-anchor :right
                     :target-anchor :left
                     :style :cubic-bezier}]
     :edges-remove []
     :warnings []}))

(defn- parse-json-string
  [value]
  (when (string? value)
    (let [trimmed (str/trim value)]
      (when (seq trimmed)
        (json/read-str trimmed {:key-fn keyword})))))

(defn- request-signals
  [req]
  (or (when (map? (:body-params req))
        (:body-params req))
      (parse-json-string (get-in req [:query-params "datastar"]))
      (let [signals-raw (d*/get-signals req)]
        (cond
          (map? signals-raw) signals-raw
          (string? signals-raw) (parse-json-string signals-raw)
          (some? signals-raw) (parse-json-string (slurp signals-raw))
          :else nil))
      {}))

(defn- pick-path
  [m paths]
  (some (fn [path]
          (let [value (get-in m path ::missing)]
            (when-not (= value ::missing)
              value)))
        paths))

(defn- open-node-id
  [req]
  (pick-path (request-signals req)
             [[:graph :openNodeId]
              [:graph :open-node-id]
              [:openNodeId]
              [:open-node-id]
              ["graph" "openNodeId"]
              ["graph" "open-node-id"]]))

(defn- script-event
  [js-function payload]
  (str "(function(){window.__pompNodeUiQueue=window.__pompNodeUiQueue||[];const payload="
       (json/write-str payload)
       ";if(typeof window!=='undefined'&&typeof window."
       js-function
       "==='function'){window."
       js-function
       "(payload);}else{window.__pompNodeUiQueue.push({fn:'"
       js-function
       "',payload:payload});}})();"))

(defn- one-shot-script-handler
  [req script]
  (->sse-response req
                  {on-open
                   (fn [sse]
                     (d*/execute-script! sse script)
                     (d*/close-sse! sse))}))

(defn initialize-graph
  [_req]
  init-payload)

(defn apply-open-patch
  [req]
  (open-payload (open-node-id req)))

(defn init-handler
  [req]
  (one-shot-script-handler req (script-event "pompNodeUiInit" (initialize-graph req))))

(defn open-handler
  [req]
  (one-shot-script-handler req (script-event "pompNodeUiApplyPatch" (apply-open-patch req))))

(defn page
  [& children]
  [:html {:data-theme "light"}
   [:head
    [:link {:href "/assets/output.css"
            :rel "stylesheet"}]
    [:script {:type "module"
              :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"}]]
   [:body {:class "m-0 min-h-screen bg-base-200 text-base-content"}
    children]])

(defn- node-card
  [{:keys [node-id label top left width height]}]
  [:article {:id (str "scratch-node-ui-node-" node-id)
             :data-node-id node-id
             :class "card pointer-events-auto absolute cursor-pointer border border-base-300 bg-base-100 shadow-sm transition hover:-translate-y-0.5 hover:shadow"
             :style (str "position:absolute;top:" top "px;left:" left "px;width:" width "px;height:" height "px;")
             :onclick (str "const host=this.closest('#scratch-node-ui-host');"
                           "if(host){host.dispatchEvent(new CustomEvent('pomp-graph-node-select',{bubbles:true,detail:{graphId:'"
                           graph-id
                           "',nodeId:'"
                           node-id
                           "',source:'inline-click'}}));}"
                           "const selected=document.getElementById('scratch-node-ui-selected-value');"
                           "if(selected){selected.textContent='"
                           node-id
                           "';}")
             :ondblclick (str "const host=this.closest('#scratch-node-ui-host');"
                              "if(host){host.dispatchEvent(new CustomEvent('pomp-graph-node-open',{bubbles:true,detail:{graphId:'"
                              graph-id
                              "',nodeId:'"
                              node-id
                              "',trigger:'inline-dblclick'}}));}"
                              "const opened=document.getElementById('scratch-node-ui-open-value');"
                              "if(opened){opened.textContent='"
                              node-id
                              "';}"
                              "const layer=document.getElementById('scratch-node-ui-node-layer');"
                              "const svg=document.getElementById('scratch-node-ui-canvas');"
                              "const detailNodeId='scratch-node-ui-node-"
                              node-id
                              "-details';"
                              "const detailLocalId='"
                              node-id
                              "-details';"
                              "const detailLeft=940;const detailTop=120;const detailW=256;const detailH=132;"
                              "if(layer && !document.getElementById(detailNodeId)){"
                              "const card=document.createElement('article');"
                              "card.id=detailNodeId;card.dataset.nodeId=detailLocalId;"
                              "card.className='card pointer-events-auto absolute cursor-pointer border border-base-300 bg-base-100 shadow-sm';"
                              "card.style.position='absolute';"
                              "card.style.left=detailLeft+'px';card.style.top=detailTop+'px';card.style.width=detailW+'px';card.style.height=detailH+'px';"
                              "card.innerHTML='<div class=\"card-body gap-2 p-4\"><div class=\"badge badge-outline badge-sm\">'+detailLocalId+'</div><h3 class=\"text-sm font-semibold\">Open: "
                              node-id
                              "</h3><p class=\"text-xs text-base-content/65\">Generated by double-click.</p></div>';"
                              "layer.appendChild(card);"
                              "}"
                              "const edgeId='scratch-node-ui-edge-"
                              node-id
                              "-details';"
                              "if(svg && !document.getElementById(edgeId)){"
                              "const startX=this.offsetLeft+this.offsetWidth;"
                              "const startY=this.offsetTop+(this.offsetHeight/2);"
                              "const endX=detailLeft;"
                              "const endY=detailTop+(detailH/2);"
                              "const dx=Math.max(64,Math.abs(endX-startX)*0.35);"
                              "const path=document.createElementNS('http://www.w3.org/2000/svg','path');"
                              "path.id=edgeId;path.setAttribute('fill','none');path.setAttribute('stroke','#0ea5e9');path.setAttribute('stroke-width','3');path.setAttribute('stroke-linecap','round');path.setAttribute('stroke-dasharray','7 7');"
                              "path.setAttribute('d','M '+startX+' '+startY+' C '+(startX+dx)+' '+startY+', '+(endX-dx)+' '+endY+', '+endX+' '+endY);"
                              "svg.appendChild(path);"
                              "}")}
   [:div {:class "card-body gap-2 p-4"}
    [:div {:class "badge badge-outline badge-sm"
           :data-node-ui-id true}
     node-id]
    [:h3 {:class "text-sm font-semibold"
          :data-node-ui-label true}
     label]
    [:p {:class "text-xs text-base-content/65"}
     "Double-click opens a detail node and connector."]]])

(defn handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body
   (c/html
    (page
     [:main {:class "mx-auto flex min-h-screen w-full max-w-[1500px] flex-col gap-4 p-4 lg:p-6"}
      [:header {:class "space-y-1"}
       [:h1 {:class "text-2xl font-semibold tracking-tight"} "Node-Based UI Scratch Shell"]
       [:p {:class "text-sm text-base-content/70"}
        "HTML nodes + SVG edges. Double-click a node to open a detail node and connector."]]
      [:section {:class "grid flex-1 gap-4 lg:grid-cols-[1fr_340px]"}
       [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
        [:div {:class "card-body overflow-auto p-3 sm:p-4"}
         [:div {:id "scratch-node-ui-host"
                :class "relative overflow-hidden rounded-box border border-base-300 bg-base-200"
                :style "position:relative;width:1200px;height:560px;overflow:hidden;"
                :data-signals (str "{graph:{graphId:'" graph-id "',selectedNodeId:null,openNodeId:null}}")
                :data-init "@post('/scratch/node-based-ui/init')"
                :data-on:pomp-graph-node-select "$graph.selectedNodeId = (evt && evt.detail && evt.detail.nodeId) ? evt.detail.nodeId : null"
                :data-on:pomp-graph-node-open "const _detail = (evt && evt.detail) || {}; $graph.openNodeId = _detail.nodeId || null; @post('/scratch/node-based-ui/open', { requestCancellation: 'auto' })"}
          [:svg {:id "scratch-node-ui-canvas"
                 :class "pointer-events-none absolute"
                 :style "position:absolute;inset:0;width:1200px;height:560px;"
                 :viewBox "0 0 1200 560"
                 :width "1200"
                 :height "560"
                 :fill "none"
                 :xmlns "http://www.w3.org/2000/svg"
                 :aria-hidden "true"}
           [:path {:id "scratch-node-ui-edge-node-a-node-b"
                   :d "M 300 176 C 420 176, 440 340, 560 340"
                   :stroke "#2563eb"
                   :stroke-width "3"
                   :stroke-linecap "round"
                   :fill "none"}]
           [:path {:id "scratch-node-ui-edge-node-b-node-c"
                   :d "M 688 406 C 760 406, 788 426, 860 426"
                   :stroke "#2563eb"
                   :stroke-width "3"
                   :stroke-linecap "round"
                   :fill "none"}]]
          [:div {:id "scratch-node-ui-node-layer"
                 :class "absolute inset-0"
                 :style "position:absolute;inset:0;width:1200px;height:560px;"}
           (node-card {:node-id "node-a"
                       :label "Gather Inputs"
                       :top 110
                       :left 44
                       :width 256
                       :height 132})
           (node-card {:node-id "node-b"
                       :label "Assemble Plan Packet"
                       :top 274
                       :left 560
                       :width 256
                       :height 132})
           (node-card {:node-id "node-c"
                       :label "Review Decisions"
                       :top 360
                       :left 860
                       :width 256
                       :height 132})]]]]
       [:aside {:id "scratch-node-ui-details"
                :class "space-y-4"}
        [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
         [:div {:class "card-body gap-3 p-4"}
          [:h2 {:class "card-title text-base"} "Selection"]
          [:div {:id "scratch-node-ui-selected"
                 :class "rounded-box border border-base-300 bg-base-200/70 p-3"}
           [:div {:class "text-xs uppercase tracking-wide text-base-content/60"}
            "Selected node id"]
           [:div {:id "scratch-node-ui-selected-value"
                  :class "mt-1 text-sm font-mono"
                  :data-text "$graph.selectedNodeId || 'none'"}
            "none"]]
          [:div {:id "scratch-node-ui-open"
                 :class "rounded-box border border-base-300 bg-base-200/70 p-3"}
           [:div {:class "text-xs uppercase tracking-wide text-base-content/60"}
            "Opened node id"]
           [:div {:id "scratch-node-ui-open-value"
                  :class "mt-1 text-sm font-mono"
                  :data-text "$graph.openNodeId || 'none'"}
            "none"]]]]
        [:section {:class "card border border-base-300 bg-base-100 shadow-sm"}
         [:div {:class "card-body gap-3 p-4 text-sm text-base-content/75"}
          [:h2 {:class "card-title text-base"} "Interaction notes"]
          [:p "Click a node card to select it."]
          [:p "Double-click a node to add a detail node and connector."]]]]]]))})
