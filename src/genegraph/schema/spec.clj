(ns genegraph.schema.spec
  "Specs describing the shape of resources/schema.edn.

  The schema is a vector of entities, each a map keyed by an :id and
  discriminated by its :type:

    :rdfs/Class      -- a class, listing its attributes by cardinality
    :rdf/Property    -- a property, with a :range and optional :value-set
    :skos/Collection -- a value set, listing its members
    :skos/Concept    -- a term appearing as a member of some value set

  Descriptive keys (:note, :internal-note, ...) are advisory; they carry
  curation commentary and do not affect the meaning of an entity."
  (:require [clojure.spec.alpha :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; ---------------------------------------------------------------------------
;; Shared values

(s/def ::id qualified-keyword?)

(s/def ::type #{:rdfs/Class :rdf/Property :skos/Collection :skos/Concept})

(s/def ::maturity #{:cg/Draft :cg/Volatile :cg/Internal})

;; Term in an external vocabulary (SEPIO, Dublin Core) this entity aligns with.
(s/def ::reference qualified-keyword?)

;; Prose, for humans. :note is published; the rest are curation notes.
(s/def ::note string?)
(s/def ::internal-note string?)
(s/def ::description string?)

(s/def ::tag (s/coll-of qualified-keyword? :kind set?))

;; ---------------------------------------------------------------------------
;; Classes

;; Attribute lists, one per cardinality. Each names properties defined
;; elsewhere in the schema.
(s/def ::attribute-list (s/coll-of qualified-keyword? :kind vector? :distinct true))

(s/def ::oneOf ::attribute-list)          ; exactly one
(s/def ::oneOrMoreOf ::attribute-list)    ; at least one
(s/def ::zeroOrOneOf ::attribute-list)    ; optional, at most one
(s/def ::zeroOrMoreOf ::attribute-list)   ; optional, unbounded
(s/def ::zeroOrManyOf ::attribute-list)   ; variant spelling of :zeroOrMoreOf
(s/def ::attributes ::attribute-list)     ; cardinality not yet decided

(s/def ::class
  (s/keys :req-un [::id ::type ::oneOf]
          :opt-un [::maturity ::reference ::note ::internal-note ::description
                   ::oneOrMoreOf ::zeroOrOneOf ::zeroOrMoreOf ::zeroOrManyOf
                   ::attributes]))

;; ---------------------------------------------------------------------------
;; Properties

;; Either a primitive (:String, :Number, :Integer, :Boolean) or a class in
;; the schema (:cg/Cohort, :skos/Concept, :rdfs/Class, ...).
(s/def ::range keyword?)

;; Range as it appears in JSON, where resources degrade to strings.
(s/def ::json-type #{:String :Number :Integer :Boolean})

;; Names a :skos/Collection constraining the values of this property.
(s/def ::value-set qualified-keyword?)

(s/def ::internal-flag (s/coll-of simple-keyword? :kind set?))
(s/def ::internal-options (s/coll-of string? :kind vector?))
(s/def ::attention-before-final boolean?)

(s/def ::property
  (s/keys :req-un [::id ::type ::range]
          :opt-un [::maturity ::reference ::value-set ::json-type ::tag
                   ::note ::internal-note ::description
                   ::internal-flag ::internal-options ::attention-before-final]))

;; ---------------------------------------------------------------------------
;; Value sets and concepts

;; Qualified, since it comes from the SKOS vocabulary rather than this schema.
(s/def :skos/member (s/coll-of qualified-keyword? :kind vector?))

(s/def ::value-set-entity
  (s/keys :req-un [::id ::type]
          :req [:skos/member]
          :opt-un [::note ::internal-note ::description]))

(s/def ::concept
  (s/keys :req-un [::id ::type]
          :opt-un [::maturity ::reference ::note ::internal-note ::description]))

;; ---------------------------------------------------------------------------
;; The schema as a whole

(defn entity-type
  "Discriminator for schema entities."
  [entity]
  (:type entity))

(defmulti entity entity-type)

(defmethod entity :rdfs/Class [_] ::class)
(defmethod entity :rdf/Property [_] ::property)
(defmethod entity :skos/Collection [_] ::value-set-entity)
(defmethod entity :skos/Concept [_] ::concept)

(s/def ::entity (s/multi-spec entity :type))

;; Ids are not distinct: concepts belonging to more than one value set are
;; repeated once per value set.
(s/def ::schema (s/coll-of ::entity :kind vector?))

;; ---------------------------------------------------------------------------
;; Loading and checking

(defn read-schema
  "Read the schema from a classpath resource, \"schema.edn\" by default."
  ([] (read-schema "schema.edn"))
  ([resource-name]
   (-> resource-name io/resource slurp edn/read-string)))

(def known-keys
  "Keys recognized by the specs above, by entity type. s/keys ignores keys it
  does not know about, so `unknown-keys` uses this to catch misspellings."
  {:rdfs/Class #{:id :type :maturity :reference :note :internal-note :description
                 :oneOf :oneOrMoreOf :zeroOrOneOf :zeroOrMoreOf :zeroOrManyOf
                 :attributes}
   :rdf/Property #{:id :type :maturity :reference :range :value-set :json-type
                   :tag :note :internal-note :description :internal-flag
                   :internal-options :attention-before-final}
   :skos/Collection #{:id :type :skos/member :note :internal-note :description}
   :skos/Concept #{:id :type :maturity :reference :note :internal-note :description}})

(defn unknown-keys
  "Entities carrying keys the specs do not describe, as a seq of
  {:id ... :keys #{...}}. A non-empty result usually means a typo."
  [schema]
  (keep (fn [e]
          (let [unknown (remove (get known-keys (entity-type e) #{}) (keys e))]
            (when (seq unknown)
              {:id (:id e) :keys (set unknown)})))
        schema))

(defn dangling-references
  "References from one entity to an :id that the schema never defines, as a seq
  of {:id ... :refers-to ...}. Covers class attribute lists, property value
  sets, and value set members."
  [schema]
  (let [defined (into #{} (map :id) schema)]
    (for [e schema
          referent (concat (mapcat e [:oneOf :oneOrMoreOf :zeroOrOneOf
                                      :zeroOrMoreOf :zeroOrManyOf :attributes])
                           (:skos/member e)
                           (when-let [vs (:value-set e)] [vs]))
          :when (not (contains? defined referent))]
      {:id (:id e) :refers-to referent})))

(defn duplicate-ids
  "Ids carried by more than one entity, as a seq of
  {:id ... :count ... :types #{...} :identical? ...}. Repetition is expected
  for concepts belonging to more than one value set; those entries are
  identical copies. An entry with :identical? false, or with more than one
  :type, is a genuine collision -- two different entities laying claim to the
  same id, where a reference to that id no longer names one thing."
  [schema]
  (->> schema
       (group-by :id)
       (keep (fn [[id entities]]
               (when (< 1 (count entities))
                 {:id id
                  :count (count entities)
                  :types (into #{} (map entity-type) entities)
                  :identical? (apply = entities)})))))

(defn undescribed
  "Entities carrying no :description, as a seq of {:id ... :type ...}. Unlike
  `unknown-keys` and `dangling-references` a non-empty result is not an error,
  just the remaining documentation work. Ids appearing more than once in the
  schema are reported once."
  [schema]
  (distinct (keep #(when-not (:description %)
                     {:id (:id %) :type (entity-type %)})
                  schema)))

(comment
  ;; Validate the schema as it stands.
  (s/valid? ::schema (read-schema))

  ;; Entities that fail, with their problems.
  (->> (read-schema)
       (remove #(s/valid? ::entity %))
       (map #(vector (:id %) (::s/problems (s/explain-data ::entity %)))))

  ;; Misspelled keys and broken cross-references.
  (unknown-keys (read-schema))
  (dangling-references (read-schema))

  ;; Repeated ids, and just the ones that are actual collisions.
  (duplicate-ids (read-schema))
  (remove #(and (:identical? %) (= 1 (count (:types %))))
          (duplicate-ids (read-schema)))

  ;; What still needs a description, and how much is left per type.
  (->> (read-schema)
       undescribed
       #_(filter #(= :rdf/Property (:type %)))
       (map :id)
       (map name))

  (->> (read-schema) undescribed (group-by :type) (map (fn [[t es]] [t (count es)])))

  (->> (read-schema) undescribed (group-by :type))


  )
