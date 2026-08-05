# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
clojure -M:run                                        # run the app
clojure -M:dev                                        # nREPL for editor-connected development
clojure -M:test                                       # all tests
clojure -M:test -n genegraph.schema-test              # one namespace
clojure -X:test :vars '[genegraph.schema-test/smoke-test]'   # one test var
clojure -T:build uber                                 # uberjar into target/
```

Note `-n` for a namespace; the test runner's `-v` takes a fully-qualified *var* and errors on a namespace name.

`.dir-locals.el` sets `cider-clojure-cli-aliases` to `:dev`, so CIDER jack-in picks up `dev/user.clj` (which wires up Portal via `tap>`). Development here is REPL-driven — `genegraph.schema.linkml` and the `comment` block at the bottom of `genegraph.schema.spec` are meant to be evaluated form-by-form, not run as a program. `genegraph.schema/-main` is still a placeholder.

## What this project is

A curated model of the ClinGen gene-validity data model, maintained as EDN. The deliverable is the data in `resources/schema.edn`; the Clojure code exists to validate it and to reconcile it against upstream vocabularies. There is no runtime application yet.

## resources/schema.edn

One flat vector of 218 entity maps, each with an `:id` (a namespaced keyword: `cg`, `dc`, `rdf`, `rdfs`, `skos`) and a `:type` that discriminates four shapes:

- `:rdfs/Class` — lists its attributes in **cardinality buckets** rather than as a single attribute list: `:oneOf` (exactly one), `:oneOrMoreOf`, `:zeroOrOneOf`, `:zeroOrMoreOf`, and `:attributes` (cardinality not yet decided). `:zeroOrManyOf` is a variant spelling of `:zeroOrMoreOf` that appears on two classes.
- `:rdf/Property` — has a `:range` (a primitive like `:String`/`:Number`, or a class id), optionally a `:value-set` naming a collection, and optionally `:json-type` where the JSON serialization differs from the RDF one (resources degrade to strings).
- `:skos/Collection` — a value set, with members in `:skos/member`.
- `:skos/Concept` — a term. Concepts are repeated once per value set they belong to, so **`:id` is not unique across the vector** (nine concepts appear twice).

The vector is entirely reference-based: classes name properties by keyword, properties name value sets by keyword, value sets name concepts by keyword. Nothing is nested, so an edit to one id must be matched at every reference site.

`:maturity` (`:cg/Draft`, `:cg/Volatile`, `:cg/Internal`) marks how settled an entity is; absent means unreviewed. `:reference` aligns an entity to an upstream term, mostly SEPIO. Prose keys are advisory and carry curation state, not meaning: `:note` and `:description` are publishable, while `:internal-note`, `:internal-flag`, `:internal-options`, and `:attention-before-final` are working notes. Inline `;` comments often record data counts observed in the source records ("124 without a description") — preserve them when editing.

Convention for value set ids is `:cg/<CamelCase>ValueSet`, and a property's `:value-set` must match its definition exactly.

## genegraph.schema.spec

Specs the shape of `schema.edn`. `::entity` is an `s/multi-spec` dispatching on `:type`; `::schema` is a vector of those, deliberately not requiring distinct ids.

Because `s/keys` ignores keys it does not recognize, validity alone does not mean the file is clean. Two extra checks cover what specs structurally cannot, and both should stay empty:

- `unknown-keys` — catches misspelled keys against the `known-keys` table. **Adding an optional key to a spec means adding it to `known-keys` too**, or the tool will report false positives.
- `dangling-references` — catches references to ids the schema never defines.

The `comment` block at the end of the namespace has ready-to-eval forms for all three checks. Run them after any edit to `schema.edn`.

Four references are known-dangling with no fix available: `:cg/InterpretationValueSet`, `:cg/MethodValueSet`, `:cg/ModelSystemValueSet`, and `:cg/PhaseStatusConfidenceValueSet` are referenced by properties but never defined, and their member terms exist nowhere in the repo.

## genegraph.schema.linkml

Loads `resources/sepio_classes.yaml` (the upstream SEPIO LinkML schema) for comparison against the model in `schema.edn` — this is the source behind the `:reference` keys. Currently a REPL scratch namespace that slurps the YAML and `tap>`s it; the "combine with Genegraph schema" half of its docstring is not implemented.

## Note on stray files

`resources/schema.edn~` and `src/genegraph/schema/linkml.clj~` are Emacs backups, not sources. They are neither gitignored nor tracked; do not edit them or treat them as current.
