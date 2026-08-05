(ns user
  "Loaded automatically at REPL startup when the :dev alias is active."
  (:require [genegraph.schema :as schema]
            [portal.api :as portal]))

(comment
  (do
    (def p (portal/open))
    (add-tap #'portal/submit))
  )

(comment
  ;; Scratch space for REPL-driven development.
  (schema/-main))
