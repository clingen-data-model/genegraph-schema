(ns genegraph.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [genegraph.schema :as schema]))

(deftest smoke-test
  (testing "namespace loads"
    (is (some? #'schema/-main))))
