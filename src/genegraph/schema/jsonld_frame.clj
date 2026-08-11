(ns genegraph.schema.jsonld-frame
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [charred.api :as charred])
  (:import [java.io PushbackReader]))

(defn multiary-props [s]
  (set (concat (:oneOrMoreOf s) (:zeroOrMoreOf s))))


(do
  (def primitives #{:Boolean :Integer :Number :String})

  (defn context-key [id]
    (let [key-ns (namespace id)]
      (if (= "cg" key-ns)
        (name id)
        (str key-ns ":" (name id)))))
  
  (defn property->context-tuple [{:keys [id value-set range] :as property} multi-props]
    [(context-key id)
     (cond-> {}
       value-set (assoc "@type" "@vocab")
       (multi-props id) (assoc "@container" "@set")
       (not (or (primitives range) value-set)) (assoc "@type" "@id"))])

  (defn construct-context-map [schema]
    (let [schema-classes (filterv #(= :rdfs/Class (:type %)) schema)
          multi-props (reduce set/union (mapv multiary-props schema-classes))]
      (->> schema
           (filterv #(= :rdf/Property (:type %)))
           (mapv #(property->context-tuple % multi-props))
           (remove #(= 0 (count (second %))))
           (into {}))))

  (defn schema->jsonld-context [schema]
    {"@context"
     (-> (construct-context-map schema)
         (dissoc "rdf:type")
         (assoc "@vocab" "https://genegraph.clinicalgenome.org/terms/"
                "id" "@id"
	        "type" "@type"
                "cg" "https://genegraph.clinicalgenome.org/terms/",
	        "dc" "http://purl.org/dc/terms/"
                "rdfs" "http://www.w3.org/2000/01/rdf-schema#"))
     "@type" "Statement"})

  (defn write-jsonld-context []
    (with-open [r (-> "schema.edn" io/resource io/reader PushbackReader.)
                w (io/writer "target/genegraph-frame.json")]
      (->> (edn/read r)
           (into [])
           schema->jsonld-context
           (charred/write-json w))))
  
  (write-jsonld-context)
   
  )


(do
  (defn unary-props [s]
    (set (concat (:oneOf s) (:zeroOrOneOf s))))



  (with-open [r (-> "schema.edn" io/resource io/reader PushbackReader.)]
    (let [schema-classes (filterv #(= :rdfs/Class (:type %)) (edn/read r))
          unary (reduce set/union (mapv unary-props schema-classes))
          multi (reduce set/union (mapv multiary-props schema-classes))]
      (set/intersection unary multi)))
  )

(comment
  
  
  
  )
