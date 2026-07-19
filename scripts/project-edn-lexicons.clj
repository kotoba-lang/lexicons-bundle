#!/usr/bin/env bb
(ns project-edn-lexicons
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(defn doc-id [doc] (or (:id doc) (get doc "id")))

(defn target-for [repo doc]
  (let [parts (.split ^String (doc-id doc) "\\.")]
    (if (fs/directory? (fs/path repo "wire/lex"))
      (str (fs/path repo "wire/lex" (str (last parts) ".json")))
      (str (fs/path repo "lexicons" (apply fs/path (butlast parts))
                    (str (last parts) ".json"))))))

(defn sources [repo]
  (let [wire (sort (fs/glob (fs/path repo "data/lex") "*.wire.edn"))]
    (if (seq wire)
      wire
      (let [data (sort (fs/glob (fs/path repo "data/lex") "*.edn"))]
        (if (seq data) data (sort (fs/glob (fs/path repo "lex") "*.edn")))))))

(defn outputs [repo]
  (for [source (sources repo)
        :let [doc (edn/read-string (slurp (str source)))]]
    {:source (str source)
     :target (target-for repo doc)
     :body (str (json/generate-string doc {:pretty true}) "\n")}))

(defn run! [{:keys [repo check]}]
  (when-not repo
    (binding [*out* *err*] (println "usage: project-edn-lexicons.clj --repo PATH [--check]"))
    (System/exit 2))
  (let [rows (vec (outputs repo))
        stale (filter #(or (not (fs/exists? (:target %)))
                           (not= (:body %) (slurp (:target %)))) rows)]
    (when (empty? rows)
      (binding [*out* *err*] (println "no canonical lex/*.edn inputs:" repo))
      (System/exit 2))
    (if check
      (when (seq stale)
        (doseq [{:keys [target]} stale]
          (binding [*out* *err*] (println "stale generated lexicon:" target)))
        (System/exit 1))
      (doseq [{:keys [target body]} rows]
        (fs/create-dirs (fs/parent target))
        (spit target body)
        (println "generated" target)))))

(run! (cli/parse-opts *command-line-args* {:coerce {:check :boolean}}))
