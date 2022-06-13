(ns core
  (:require [execute]
            [evaluate.evaluate :as e]))

(defn route [x]
  (str "core: " (execute/exec) (e/evaluate) (* x x) "@"))

