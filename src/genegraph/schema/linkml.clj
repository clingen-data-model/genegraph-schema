(ns genegraph.schema.linkml
  "Ingest LinkML schema and combine with Genegraph schema."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [yamlstar.core :as yaml])
  (:import [java.io PushbackReader]))

#_(with-open [r (-> "sepio_classes.yaml" io/resource io/reader PushbackReader.)] 
  (->> (edn/read r)
       count)
     )
(def sepio-linkml
  (-> "sepio_classes.yaml"
      io/resource
      slurp
      yaml/load))

(tap> sepio-linkml)
