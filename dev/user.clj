(ns user
  "Loaded automatically at REPL startup when the :dev alias is active."
  (:require [genegraph.schema :as schema]))

(comment
  ;; Scratch space for REPL-driven development.
  (schema/-main))
