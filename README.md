# genegraph-schema

## Requirements

- [Clojure CLI](https://clojure.org/guides/install_clojure) (tools.deps)

## Usage

```bash
# Run the app
clojure -M:run

# Start an nREPL for editor-connected development
clojure -M:dev

# Run tests
clojure -M:test

# Build a standalone uberjar into target/
clojure -T:build uber
```

## Layout

```
src/genegraph/schema.clj   main namespace + entry point
test/genegraph/            tests
dev/user.clj              REPL scratch namespace (:dev alias)
resources/                non-code assets on the classpath
build.clj                 tools.build script
deps.edn                  dependencies and aliases
```
