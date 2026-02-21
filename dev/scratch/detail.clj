(ns scratch.detail
  (:require [app :as app]
            [dev.onionpancakes.chassis.core :as c]
            [pomp.icons :as icons]))

(defn- life-detail-row
  [{:keys [label field input-type options]}]
  (let [detail-path (str "$lifeDetails." field)
        edit-path (str "$lifeEditing." field)
        editor-id (str "life-detail-editor-" field)
        hover-path (str "$lifeHover." field)
        open-edit-handler (str "$lifeEditing." field " = true; "
                               "document.getElementById('" editor-id "') && "
                               "(document.getElementById('" editor-id "').value = (" detail-path " ?? ''))")
        save-handler (str "document.getElementById('" editor-id "') && "
                          "($lifeDetails." field " = document.getElementById('" editor-id "').value); "
                          "$lifeEditing." field " = false")
        cancel-handler (str "$lifeEditing." field " = false")
        hover-on-handler (str "$lifeHover." field " = true")
        hover-off-handler (str "$lifeHover." field " = false")]
    [:div {:class "rounded-box border border-base-300/70 bg-base-100/80 p-3 sm:p-4"
           :data-on:mouseenter hover-on-handler
           :data-on:mouseleave hover-off-handler}
     [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4"}
      [:div {:class "min-w-36 text-sm font-medium text-base-content/70"} label]
      [:div {:class "w-full sm:max-w-xl"}
       [:div {:class "flex items-start gap-2"
              :data-show (str "!" edit-path)}
        [:span {:class "flex-1 text-base leading-6"
                :data-text detail-path}]
        [:button {:class "btn btn-ghost btn-xs opacity-0 transition-opacity duration-150"
                  :type "button"
                  :title "Edit"
                  :data-class (str "{'opacity-100': " hover-path ", 'opacity-0': !" hover-path "}")
                  :data-on:click open-edit-handler}
         icons/edit-pencil-icon]]
       [:div {:class "space-y-2"
              :data-show edit-path}
        (case input-type
          :select
          [:select {:id editor-id
                    :class "select select-bordered w-full"}
           (for [option options]
             [:option {:value option} option])]

          :textarea
          [:textarea {:id editor-id
                      :class "textarea textarea-bordered h-24 w-full"}]

          [:input {:id editor-id
                   :class "input input-bordered w-full"
                   :type "text"}])
        [:div {:class "flex justify-end gap-2"}
         [:button {:class "btn btn-ghost btn-sm"
                   :type "button"
                   :data-on:click cancel-handler}
          "Cancel"]
         [:button {:class "btn btn-primary btn-sm"
                   :type "button"
                   :data-on:click save-handler}
          icons/save-icon
          [:span "Save"]]]]]]]))

(defn- profile-property-row
  [{:keys [label field]}]
  (let [detail-path (str "$profileDetails." field)
        edit-path (str "$profileEditing." field)
        hover-path (str "$profileHover." field)
        editor-id (str "profile-editor-" field)
        open-edit-handler (str "$profileEditing." field " = true; "
                               "document.getElementById('" editor-id "') && "
                               "(document.getElementById('" editor-id "').value = (" detail-path " ?? ''))")
        save-handler (str "document.getElementById('" editor-id "') && "
                          "($profileDetails." field " = document.getElementById('" editor-id "').value); "
                          "$profileEditing." field " = false")
        cancel-handler (str "$profileEditing." field " = false")
        hover-on-handler (str "$profileHover." field " = true")
        hover-off-handler (str "$profileHover." field " = false")]
    [:tr {:class "align-top block border-b border-base-300/60 py-3 last:border-b-0 sm:table-row sm:border-b-0 sm:py-1"
          :data-on:mouseenter hover-on-handler
          :data-on:mouseleave hover-off-handler}
     [:th {:class "block w-full px-0 pb-1.5 text-[0.7rem] font-semibold uppercase tracking-wide text-base-content/60 sm:table-cell sm:w-44 sm:px-3 sm:py-2 sm:text-xs"}
      label]
     [:td {:class "block px-0 pt-1.5 sm:table-cell sm:px-3 sm:py-2"}
      [:div {:class "flex items-start gap-2"
             :data-show (str "!" edit-path)}
       [:span {:class "text-[0.95rem] leading-6 break-words sm:text-sm"
               :data-text detail-path}]
       [:button {:class "btn btn-ghost btn-xs opacity-0 transition-opacity duration-150"
                 :type "button"
                 :title "Edit"
                 :data-class (str "{'opacity-100': " hover-path ", 'opacity-0': !" hover-path "}")
                 :data-on:click open-edit-handler}
        icons/edit-pencil-icon]]
      [:div {:class "flex w-full flex-col gap-2 sm:join sm:flex-row"
             :data-show edit-path}
       [:input {:id editor-id
                :class "input input-bordered input-sm w-full sm:join-item"
                :type "text"}]
       [:button {:class "btn btn-ghost btn-sm w-full sm:w-auto sm:join-item"
                 :type "button"
                 :data-on:click cancel-handler}
        "Cancel"]
       [:button {:class "btn btn-primary btn-sm w-full sm:w-auto sm:join-item"
                 :type "button"
                 :data-on:click save-handler}
        "Save"]]]]))

(defn handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body
   (c/html
    (app/with-app-layout
      {:drawer-id "detail-drawer"
       :nav-title "Aristotle Detail"}
      [:div {:class "mx-auto w-full max-w-6xl space-y-7"}
       [:div {:class "breadcrumbs text-sm"}
        [:ul
         [:li [:a {:href "/"} "Home"]]
         [:li [:a {:href "/scratch/app-skeleton"} "Scratch"]]
         [:li "Aristotle"]]]
       [:section {:class "hero rounded-box border border-base-300 bg-gradient-to-br from-base-100 via-base-100 to-base-200/60 shadow-lg"}
        [:div {:class "hero-content w-full flex-wrap items-start justify-between gap-6 px-6 py-8 max-sm:flex-col max-sm:items-start"}
         [:div {:class "min-w-0 flex-1 space-y-3"}
          [:p {:class "badge badge-outline badge-lg"} "Classical Greece"]
          [:h1 {:class "text-3xl font-bold tracking-tight sm:text-4xl md:text-5xl"} "Aristotle"]
          [:p {:class "max-w-2xl text-base-content/70"}
           "Student of Plato, tutor of Alexander, and architect of a durable framework for logic, metaphysics, ethics, and natural philosophy."]
          [:div {:class "flex flex-wrap gap-2"}
           [:span {:class "badge badge-primary"} "Logic"]
           [:span {:class "badge badge-secondary"} "Ethics"]
           [:span {:class "badge badge-accent"} "Metaphysics"]
           [:span {:class "badge badge-info"} "Biology"]]]
         [:div {:class "stats stats-vertical w-full max-w-xs border border-base-300 bg-base-200/80 shadow-sm"}
          [:div {:class "stat min-w-0"}
           [:div {:class "stat-title"} "Born"]
           [:div {:class "stat-value text-2xl"} "384 BCE"]
           [:div {:class "stat-desc whitespace-normal break-words"} "Stagira"]]
          [:div {:class "stat min-w-0"}
           [:div {:class "stat-title"} "Died"]
           [:div {:class "stat-value text-2xl"} "322 BCE"]
           [:div {:class "stat-desc whitespace-normal break-words"} "Chalcis"]]
          [:div {:class "stat min-w-0"}
           [:div {:class "stat-title"} "School"]
           [:div {:class "stat-value text-2xl"} "Lyceum"]
           [:div {:class "stat-desc whitespace-normal break-words"} "Peripatetic tradition"]]]]]
       [:div {:class "grid gap-6 lg:grid-cols-12 lg:items-start"}
        [:section {:class "card border border-base-300 bg-gradient-to-br from-base-100 to-base-200/40 shadow lg:col-span-7"}
         [:div {:class "card-body gap-4"
                :data-signals "{lifeDetails:{birthYear:'384 BCE',birthPlace:'Stagira, Chalcidice',deathYear:'322 BCE',school:'Peripatetic',biographicalNote:'Founded the Lyceum in Athens and developed a method grounded in observation and classification.'},lifeEditing:{birthYear:false,birthPlace:false,deathYear:false,school:false,biographicalNote:false},lifeHover:{birthYear:false,birthPlace:false,deathYear:false,school:false,biographicalNote:false}}"}
          [:div {:class "flex items-center justify-between gap-4"}
           [:h2 {:class "card-title"} "Life Details"]
           [:span {:class "badge badge-success badge-outline"} "Editable"]]
          [:p {:class "text-sm text-base-content/70"}
           "Hover a row to reveal the pencil, then edit and save each life detail inline."]
          [:div {:class "space-y-2"}
           (life-detail-row {:label "Birth year"
                             :field "birthYear"
                             :input-type :text})
           (life-detail-row {:label "Birth place"
                             :field "birthPlace"
                             :input-type :text})
           (life-detail-row {:label "Death year"
                             :field "deathYear"
                             :input-type :text})
           (life-detail-row {:label "School"
                             :field "school"
                             :input-type :select
                             :options ["Peripatetic" "Platonist" "Other"]})
           (life-detail-row {:label "Biographical note"
                             :field "biographicalNote"
                             :input-type :textarea})]
          [:div {:class "sr-only"} "Reset"]
          [:div {:class "sr-only"} "Save life details"]]]
        [:div {:class "space-y-6 lg:col-span-5"}
         [:section {:class "card border border-base-300 bg-base-100 shadow"}
          [:div {:class "card-body gap-4"}
           [:h2 {:class "card-title"} "Philosophical Positions"]
           [:ul {:class "list rounded-box divide-y divide-base-300/60 border border-base-300 bg-base-100/90"}
            [:li {:class "flex min-w-0 flex-col gap-1.5 px-6 py-3 sm:grid sm:grid-cols-[9rem_1fr] sm:gap-3 sm:px-8"}
             [:div {:class "text-sm font-semibold leading-snug break-normal whitespace-normal sm:text-base"} "Hylomorphism"]
             [:div {:class "text-sm text-base-content/70"} "Substances are composites of matter and form."]]
            [:li {:class "flex min-w-0 flex-col gap-1.5 px-6 py-3 sm:grid sm:grid-cols-[9rem_1fr] sm:gap-3 sm:px-8"}
             [:div {:class "text-sm font-semibold leading-snug break-normal whitespace-normal sm:text-base"} "Four Causes"]
             [:div {:class "text-sm text-base-content/70"} "Material, formal, efficient, and final explanations work together."]]
            [:li {:class "flex min-w-0 flex-col gap-1.5 px-6 py-3 sm:grid sm:grid-cols-[9rem_1fr] sm:gap-3 sm:px-8"}
             [:div {:class "text-sm font-semibold leading-snug break-normal whitespace-normal sm:text-base"} "Virtue Ethics"]
             [:div {:class "text-sm text-base-content/70"} "Character and habituation anchor practical wisdom."]]
            [:li {:class "flex min-w-0 flex-col gap-1.5 px-6 py-3 sm:grid sm:grid-cols-[9rem_1fr] sm:gap-3 sm:px-8"}
             [:div {:class "text-sm font-semibold leading-snug break-normal whitespace-normal sm:text-base"} "Non-Contradiction"]
             [:div {:class "text-sm text-base-content/70"} "A proposition cannot be both true and false in the same respect."]]]]
          [:section {:class "card border border-base-300 bg-base-100 shadow-md"}
           [:div {:class "card-body gap-3"}
            [:h2 {:class "card-title"} "Scholastic Reception"]
            [:ul {:class "steps steps-vertical steps-sm w-full"}
             [:li {:class "step step-primary"}
              [:div {:class "space-y-1 pl-2"}
               [:div {:class "text-sm font-medium leading-5"} "Arabic Commentators"]
               [:p {:class "text-xs leading-5 text-base-content/70"} "Averroes transmits and systematizes Aristotle for Latin readers."]]]
             [:li {:class "step step-primary"}
              [:div {:class "space-y-1 pl-2"}
               [:div {:class "text-sm font-medium leading-5"} "University Adoption"]
               [:p {:class "text-xs leading-5 text-base-content/70"} "Paris and Bologna absorb Aristotelian logic and metaphysics."]]]
             [:li {:class "step"}
              [:div {:class "space-y-1 pl-2"}
               [:div {:class "text-sm font-medium leading-5"} "Neo-Aristotelian Revival"]
               [:p {:class "text-xs leading-5 text-base-content/70"} "Virtue ethics and teleology re-enter modern debates."]]]]]]]]]
       [:section {:class "card border border-base-300 bg-base-100 shadow"
                  :data-signals "{profileDetails:{curriculumFocus:'Practical wisdom and civic friendship',method:'Dialectic plus empirical observation',virtueModel:'Habit first, rule second',logicLegacy:'Syllogistic structure',polityPreference:'Mixed constitution',causalLens:'Final causes orient inquiry',knowledgeAim:'Demonstrative understanding',teleologyScope:'Nature and politics'},profileEditing:{curriculumFocus:false,method:false,virtueModel:false,logicLegacy:false,polityPreference:false,causalLens:false,knowledgeAim:false,teleologyScope:false},profileHover:{curriculumFocus:false,method:false,virtueModel:false,logicLegacy:false,polityPreference:false,causalLens:false,knowledgeAim:false,teleologyScope:false}}"}
        [:div {:class "card-body gap-4"}
         [:div {:class "flex flex-wrap items-start justify-between gap-2 sm:items-center"}
          [:h2 {:class "card-title"} "Aristotelian Profile Matrix"]
          [:span {:class "badge badge-info badge-outline shrink-0"} "Compact editable"]]
         [:p {:class "text-sm text-base-content/70"}
          "Each value stays read-only text until you put that row into edit mode."]
         [:div {:class "w-full"}
          [:table {:class "table w-full text-sm sm:table-sm"}
           [:tbody
            (profile-property-row {:label "Curriculum focus" :field "curriculumFocus"})
            (profile-property-row {:label "Method" :field "method"})
            (profile-property-row {:label "Virtue model" :field "virtueModel"})
            (profile-property-row {:label "Logic legacy" :field "logicLegacy"})
            (profile-property-row {:label "Polity preference" :field "polityPreference"})
            (profile-property-row {:label "Causal lens" :field "causalLens"})
            (profile-property-row {:label "Knowledge aim" :field "knowledgeAim"})
            (profile-property-row {:label "Teleology scope" :field "teleologyScope"})]]]]]
       [:div {:class "grid min-w-0 gap-6 xl:grid-cols-2"}
        [:section {:class "card min-w-0 border border-base-300 bg-base-100/95 shadow-md"}
         [:div {:class "card-body min-w-0"}
          [:h2 {:class "card-title"} "Major Texts"]
          [:div {:class "w-full max-w-full overflow-x-auto"}
           [:table {:class "table table-zebra w-full"}
            [:thead
             [:tr
              [:th "Work"]
              [:th "Domain"]
              [:th "Notes"]]]
            [:tbody
             [:tr
              [:td "Nicomachean Ethics"]
              [:td "Ethics"]
              [:td "Eudaimonia and the doctrine of the mean."]]
             [:tr
              [:td "Metaphysics"]
              [:td "Ontology"]
              [:td "Being qua being and substance."]]
             [:tr
              [:td "Posterior Analytics"]
              [:td "Logic"]
              [:td "Demonstration and scientific knowledge."]]
             [:tr
              [:td "Politics"]
              [:td "Political theory"]
              [:td "Constitutions and civic flourishing."]]]]]]]
        [:section {:class "card min-w-0 border border-base-300 bg-base-100/95 shadow-md"}
         [:div {:class "card-body min-w-0"}
          [:h2 {:class "card-title"} "Influenced Philosophers"]
          [:ul {:class "timeline timeline-vertical timeline-compact"}
           [:li
            [:div {:class "timeline-middle"} "1"]
            [:div {:class "timeline-end timeline-box w-full min-w-0"}
             [:div {:class "font-semibold"} "Averroes"]
             [:p {:class "text-sm text-base-content/70 break-words"} "Systematic medieval commentaries on Aristotle."]]]
           [:li
            [:div {:class "timeline-middle"} "2"]
            [:div {:class "timeline-end timeline-box w-full min-w-0"}
             [:div {:class "font-semibold"} "Thomas Aquinas"]
             [:p {:class "text-sm text-base-content/70 break-words"} "Integrated Aristotelian metaphysics with scholastic theology."]]]
           [:li
            [:div {:class "timeline-middle"} "3"]
            [:div {:class "timeline-end timeline-box w-full min-w-0"}
             [:div {:class "font-semibold"} "Alasdair MacIntyre"]
             [:p {:class "text-sm text-base-content/70 break-words"} "Revived virtue ethics in contemporary moral philosophy."]]]]]]]
       [:div {:class "grid min-w-0 gap-6 xl:grid-cols-2 2xl:grid-cols-3"}
        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md"}
         [:div {:class "card-body min-w-0 gap-4"}
          [:h2 {:class "card-title"} "Interpretive Tabs"]
          [:p {:class "text-sm text-base-content/70"}
           "Switch between editorial slices without scrolling."]
          [:div {:role "tablist"
                 :class "tabs tabs-box w-full bg-base-200/60"}
           [:input {:type "radio" :name "aristotle-tabs" :role "tab" :class "tab" :aria-label "Primary" :checked "checked"}]
           [:div {:role "tabpanel" :class "tab-content min-h-20 rounded-lg border border-base-300 bg-base-100 p-3 text-sm text-base-content/80"}
            "Core concepts grouped by logic, ethics, and natural philosophy."]
           [:input {:type "radio" :name "aristotle-tabs" :role "tab" :class "tab" :aria-label "Textual"}]
           [:div {:role "tabpanel" :class "tab-content min-h-20 rounded-lg border border-base-300 bg-base-100 p-3 text-sm text-base-content/80"}
            "Compare translations, paraphrases, and manuscript traditions."]
           [:input {:type "radio" :name "aristotle-tabs" :role "tab" :class "tab" :aria-label "Debates"}]
           [:div {:role "tabpanel" :class "tab-content min-h-20 rounded-lg border border-base-300 bg-base-100 p-3 text-sm text-base-content/80"}
            "See key objections and later responses in scholarly commentary."]]]]

        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md"}
         [:div {:class "card-body min-w-0 gap-4"}
          [:h2 {:class "card-title"} "Accordion Notes"]
          [:p {:class "text-sm text-base-content/70"}
           "One-open collapse keeps dense notes compact on mobile."]
          [:div {:class "space-y-2"}
           [:div {:class "collapse collapse-plus border border-base-300 bg-base-200/40"}
            [:input {:type "radio" :name "aristotle-notes" :checked "checked"}]
            [:div {:class "collapse-title text-sm font-medium"} "Early life"]
            [:div {:class "collapse-content text-sm text-base-content/70"}
             "Born in 384 BCE in Stagira, Aristotle trained under Plato in Athens and later taught at the Lyceum."]]
           [:div {:class "collapse collapse-plus border border-base-300 bg-base-200/40"}
            [:input {:type "radio" :name "aristotle-notes"}]
            [:div {:class "collapse-title text-sm font-medium"} "Method"]
            [:div {:class "collapse-content text-sm text-base-content/70"}
             "His method blends analytic deduction with empirical observation across biology, logic, and ethics."]]
           [:div {:class "collapse collapse-plus border border-base-300 bg-base-200/40"}
            [:input {:type "radio" :name "aristotle-notes"}]
            [:div {:class "collapse-title text-sm font-medium"} "Influence"]
            [:div {:class "collapse-content text-sm text-base-content/70"}
             "Later traditions reinterpret his framework from medieval scholasticism to modern virtue ethics."]]]]]

        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md"}
         [:div {:class "card-body min-w-0 gap-4"}
          [:h2 {:class "card-title"} "Actions Menu"]
          [:p {:class "text-sm text-base-content/70"}
           "Context actions stay near content while avoiding clutter."]
          [:details {:class "dropdown dropdown-end"}
           [:summary {:class "btn btn-outline btn-sm w-full sm:w-auto"}
            "Workflow"]
           [:ul {:class "dropdown-content menu menu-sm z-10 mt-2 w-56 rounded-box border border-base-300 bg-base-100 p-2 shadow"}
            [:li [:button {:type "button" :class "justify-start"} "Duplicate profile"]]
            [:li [:button {:type "button" :class "justify-start"} "Export notes"]]
            [:li [:button {:type "button" :class "justify-start"} "Archive entry"]]]]]]

        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md"}
         [:div {:class "card-body min-w-0 gap-4"}
          [:h2 {:class "card-title"} "Variant Dialogue Modal"]
          [:p {:class "text-sm text-base-content/70"}
           "A modal keeps optional comparison details focused and accessible."]
          [:button {:type "button"
                    :class "btn btn-primary btn-sm"
                    :data-on:click "document.getElementById('aristotle-variant-modal').showModal()"}
           "Review source variant"]
          [:dialog {:id "aristotle-variant-modal"
                    :class "modal"
                    :tabindex "-1"}
           [:div {:class "modal-box"}
            [:h3 {:class "text-lg font-bold"} "Variant Passage Review"]
            [:p {:class "mt-2 text-sm text-base-content/80"}
             "Compare competing readings and decide which wording should anchor the editorial note."]
            [:div {:class "modal-action"}
             [:form {:method "dialog"}
              [:button {:type "submit" :class "btn"} "Done"]]]]
           [:form {:method "dialog" :class "modal-backdrop"}
            [:button {:type "submit" :aria-label "close"} "close"]]]]]

        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md xl:col-span-2 2xl:col-span-1"}
         [:div {:class "card-body min-w-0 gap-4"}
          [:h2 {:class "card-title"} "Source vs Translation"]
          [:p {:class "text-sm text-base-content/70"}
           "Diffs quickly show where wording diverges."]
          [:div {:class "space-y-3 sm:hidden"}
           [:article {:class "rounded-box border border-base-300 bg-base-200/40 p-3"}
            [:h3 {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"}
             "Source"]
            [:ul {:class "list-disc space-y-1 pl-4 text-sm text-base-content/80"}
             [:li "[Greek tradition]"]
             [:li "Final cause marks purpose as an end."]
             [:li "Ethics centers on habituated practical reason."]]]
           [:article {:class "rounded-box border border-base-300 bg-base-200/40 p-3"}
            [:h3 {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"}
             "Translation"]
            [:ul {:class "list-disc space-y-1 pl-4 text-sm text-base-content/80"}
             [:li "[Modern framing]"]
             [:li "Translation foregrounds method and causation."]
             [:li "Commentary stresses historical context."]]]]
          [:figure {:class "diff hidden h-24 w-full overflow-hidden rounded-lg border border-base-300 bg-base-200/70 sm:block"}
           [:div {:class "diff-item-1 p-2"}
            [:div {:class "flex h-full items-center justify-center rounded-box border border-base-300 bg-base-100/90"}
             [:span {:class "text-xs font-semibold uppercase tracking-wide text-base-content/70"} "Source"]]]
           [:div {:class "diff-item-2 p-2"}
            [:div {:class "flex h-full items-center justify-center rounded-box border border-base-300 bg-base-100/90"}
             [:span {:class "text-xs font-semibold uppercase tracking-wide text-base-content/70"} "Translation"]]]
           [:div {:class "diff-resizer"}]]
          [:div {:class "hidden grid-cols-2 gap-3 sm:grid"}
           [:article {:class "rounded-box border border-base-300 bg-base-200/40 p-3"}
            [:h3 {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"}
             "Source details"]
            [:ul {:class "list-disc space-y-1 pl-4 text-sm text-base-content/80"}
             [:li "[Greek tradition]"]
             [:li "Final cause marks purpose as an end."]
             [:li "Ethics centers on habituated practical reason."]]]
           [:article {:class "rounded-box border border-base-300 bg-base-200/40 p-3"}
            [:h3 {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"}
             "Translation details"]
            [:ul {:class "list-disc space-y-1 pl-4 text-sm text-base-content/80"}
             [:li "[Modern framing]"]
             [:li "Translation foregrounds method and causation."]
             [:li "Commentary stresses historical context."]]]]]]

        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md"}
         [:div {:class "card-body min-w-0 gap-4"}
          [:h2 {:class "card-title"} "Persona / Status"]
          [:p {:class "text-sm text-base-content/70"}
           "Avatar, indicator, and status components form reviewer identity blocks."]
          [:div {:class "space-y-3"}
           [:div {:class "flex items-center gap-3"}
            [:div {:class "indicator"}
             [:span {:class "indicator-item indicator-bottom indicator-right status status-success status-sm" :title "online"}]
             [:div {:class "avatar"}
              [:div {:class "w-12 rounded-full bg-primary text-primary-content"}
               [:span "AC"]]]]
            [:div
             [:p {:class "text-sm font-semibold"} "Alex Carter"]
             [:p {:class "text-xs text-base-content/70"} "Editorial lead"]]]
           [:div {:class "flex items-center gap-3"}
            [:div {:class "indicator"}
             [:span {:class "indicator-item indicator-bottom indicator-right status status-warning status-sm" :title "reviewing"}]
             [:div {:class "avatar avatar-placeholder"}
              [:div {:class "w-12 rounded-full bg-neutral text-neutral-content"}
               [:span "NR"]]]]
            [:div
             [:p {:class "text-sm font-semibold"} "Noah Reed"]
             [:p {:class "text-xs text-base-content/70"} "Review stage"]]]]]]

        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md"}
         [:div {:class "card-body min-w-0 gap-4"}
          [:h2 {:class "card-title"} "Publication Controls"]
          [:fieldset {:class "fieldset rounded-box border border-base-300 bg-base-200/40 p-3"}
           [:legend {:class "fieldset-legend"} "Draft controls"]
           [:label {:class "label cursor-pointer justify-start gap-2"}
            [:input {:type "checkbox"
                     :class "checkbox checkbox-primary"
                     :checked "checked"}]
            [:span {:class "label-text"} "Mark as reviewed"]]
           [:label {:class "label cursor-pointer justify-start gap-2"}
            [:input {:type "checkbox"
                     :class "toggle toggle-secondary"}]
            [:span {:class "label-text"} "Show draft-only fields"]]]
          [:fieldset {:class "fieldset mt-2 rounded-box border border-base-300 bg-base-200/40 p-3"}
           [:legend {:class "fieldset-legend"} "Audience mode"]
           [:label {:class "label cursor-pointer justify-start gap-2"}
            [:input {:type "radio" :name "audience-mode" :class "radio radio-accent" :checked "checked"}]
            [:span {:class "label-text"} "Academic audience"]]
           [:label {:class "label cursor-pointer justify-start gap-2"}
            [:input {:type "radio" :name "audience-mode" :class "radio radio-accent"}]
            [:span {:class "label-text"} "General audience"]]]]]

        [:section {:class "card min-w-0 border border-base-300 bg-base-100 shadow-md xl:col-span-2 2xl:col-span-2"}
         [:div {:class "card-body"}
          [:h2 {:class "text-lg font-semibold"} "Research Snapshot"]
          [:div {:class "mt-4 grid gap-4 md:grid-cols-3"}
           [:div {:class "stat rounded-box border border-base-300 bg-base-200/70"}
            [:div {:class "stat-title"} "Commentary density"]
            [:div {:class "stat-value text-2xl"} "78%"]
            [:div {:class "stat-desc"} "Metaphysics and ethics dominate."]]
           [:div {:class "stat rounded-box border border-base-300 bg-base-200/70"}
            [:div {:class "stat-title"} "Courses citing Aristotle"]
            [:div {:class "stat-value text-2xl"} "42"]
            [:div {:class "stat-desc"} "Across philosophy and political theory."]]
           [:div {:class "stat rounded-box border border-base-300 bg-base-200/70"}
            [:div {:class "stat-title"} "Core translations"]
            [:div {:class "stat-value text-2xl"} "16"]
            [:div {:class "stat-desc"} "Frequently assigned in seminars."]]]
          [:div {:class "mt-4 space-y-2"}
           [:progress {:class "progress progress-primary w-full" :value "72" :max "100"}]
           [:progress {:class "progress progress-secondary w-full" :value "61" :max "100"}]
           [:progress {:class "progress progress-accent w-full" :value "84" :max "100"}]]]]
        [:section {:class "card border border-base-300 bg-gradient-to-r from-base-100 to-base-200/50 shadow"}
         [:div {:class "card-body"}
          [:h2 {:class "card-title"} "Secondary Literature"]
          [:div {:class "grid gap-3 md:grid-cols-2"}
           [:article {:class "alert"}
            [:div
             [:div {:class "font-semibold"} "Jonathan Barnes"]
             [:div {:class "text-sm"} "Aristotle: A Very Short Introduction"]]]
           [:article {:class "alert"}
            [:div
             [:div {:class "font-semibold"} "Martha Nussbaum"]
             [:div {:class "text-sm"} "The Fragility of Goodness"]]]
           [:article {:class "alert"}
            [:div
             [:div {:class "font-semibold"} "Terence Irwin"]
             [:div {:class "text-sm"} "Aristotle's First Principles"]]]
           [:article {:class "alert"}
            [:div
             [:div {:class "font-semibold"} "Sarah Broadie"]
             [:div {:class "text-sm"} "Ethics with Aristotle"]]]]]]]]))})
