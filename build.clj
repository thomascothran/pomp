(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'tech.thomascothran/pomp)
; alternatively, use MAJOR.MINOR.COMMITS:
(def version (format "3.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")

(defn- pom-template [version]
  [[:description "pomp: Bthreads for Clojure(Script)"]
   [:url "https://github.com/thomascothran/pomp"]
   [:licenses
    [:license
     [:name "Eclipse Public License"]
     [:url "http://www.eclipse.org/legal/epl-v10.html"]]]
   [:developers
    [:developer
     [:name "Thomas Cothran"]]]
   [:scm
    [:url "https://github.com/thomascothran/pomp"]
    [:connection "scm:git:https://github.com/thomascothran/pomp.git"]
    [:developerConnection "scm:git:ssh:git@github.com:thomascothran/pomp.git"]
    [:tag (str "v" version)]]])

(defn- jar-opts [opts]
  (assoc opts
         :lib lib   :version version
         :jar-file  (format "target/%s-%s.jar" lib version)
         :basis     (b/create-basis {})
         :class-dir class-dir
         :target    "target"
         :src-dirs  ["src"]
         :pom-data  (pom-template version)))

(defn- sh! [& args]
  (let [{:keys [exit out err]} (apply shell/sh args)]
    (when (seq out)
      (print out))
    (when (seq err)
      (binding [*out* *err*]
        (print err)))
    (when-not (zero? exit)
      (throw (ex-info "External command failed"
                      {:args args
                       :exit exit
                       :err err})))
    {:out out :err err}))

(defn- generate-tailwind-manifest! []
  (println "\nGenerating Tailwind class manifest...")
  (sh! "npm" "run" "gen:tailwind-classes")
  (let [manifest (io/file "resources/pomp-tailwind-classes.txt")]
    (when-not (.exists manifest)
      (throw (ex-info "Tailwind manifest was not generated"
                       {:path manifest}))))
  (println "Tailwind class manifest ready."))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  ;(test opts)
  (b/delete {:path "target"})
  (let [opts (jar-opts opts)]
    (generate-tailwind-manifest!)
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println "\nBuilding JAR..." (:jar-file opts))
    (b/jar opts))
  opts)

(defn install "Install the JAR locally." [opts]
  (let [opts (jar-opts opts)]
    (b/install opts))
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
