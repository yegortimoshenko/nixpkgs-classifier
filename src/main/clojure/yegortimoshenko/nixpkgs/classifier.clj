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

(defn classify [title]
  (map first (filter-by-val (partial some (partial match-rule title)) (read-ruleset))))

(defn github-classify [token {:strs [issue pull_request]}]
  (let [{:strs [title url]} (or issue pull_request)]
    (http/request {:body (json/write-str (classify title))
                   :headers {:authorization [(str "token " token)]}}
                  (str url "/labels"))))

(defn new-ticket? [{:strs [action]}]
  (= action "opened"))

(defn valid-signature? [body secret signature] true)

(defn handle [{:strs [body headers]}]
  (let [{:strs [X-Hub-Signature]} headers
        {:strs [GITHUB_SECRET GITHUB_TOKEN]} (System/getenv)
        payload (json/read-str body)]
    (when (and (valid-signature? body GITHUB_SECRET X-Hub-Signature)
               (new-ticket? payload))
      (github-classify GITHUB_TOKEN payload))))

(defn -handleRequest [_ event ctx] (handle event))
