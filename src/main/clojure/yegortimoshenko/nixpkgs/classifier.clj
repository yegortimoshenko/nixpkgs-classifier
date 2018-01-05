(ns yegortimoshenko.nixpkgs.classifier
  (:gen-class :implements [com.amazonaws.services.lambda.runtime.RequestHandler])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [yegortimoshenko.io.http :as http]))

(defn expand-rule [rule]
  (if (string? rule)
    (re-pattern (str "(?i)(\\W|^)" (str/lower-case rule) "(\\W|$)"))
    (identity rule)))

(defn match-rule [title rule]
  (re-find (expand-rule rule) title))

(defn read-ruleset []
  (read-string (slurp (io/resource "ruleset.edn"))))

(defn filter-by-val [pred m]
  (filter (fn [[k v]] (pred v)) m))

(defn classify [s]
  (map first (filter-by-val (partial some (partial match-rule s)) (read-ruleset))))

(defn label-issue [token url labels]
  (http/request {:body (json/write-str labels)
                 :headers {:authorization [(format "token %s" token)]}}
                (str url "/labels")))

(defn classify-issue [token {:strs [issue pull_request]}]
  (let [{:strs [title issue_url url]} (or issue pull_request)]
    (label-issue token (or issue_url url) (classify title))))

(defn new-issue? [{:strs [action]}]
  (= action "opened"))

(defn handle [payload]
  (let [{:strs [GITHUB_TOKEN]} (System/getenv)]
    (when (new-issue? payload)
      (classify-issue GITHUB_TOKEN payload))))

(defn -handleRequest [_ input ctx]
  (handle input))
