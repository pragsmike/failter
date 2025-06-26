(ns failter.frontmatter-test
  (:require [clojure.test :refer :all]
            [failter.frontmatter :as fm]))

(deftest parse-file-content-test
  (testing "Parsing content with valid frontmatter"
    (let [content "---\ntitle: Test\nauthor: Me\n---\nThis is the body."
          result (fm/parse-file-content content)]
      ;; --- FIX: Assert against keyword keys ---
      (is (= {:title "Test" :author "Me"} (:frontmatter result)))
      (is (= "This is the body." (:body result)))))

  (testing "Parsing content without frontmatter"
    (let [content "This is just a body."
          result (fm/parse-file-content content)]
      (is (= {} (:frontmatter result)))
      (is (= "This is just a body." (:body result)))))

  (testing "Parsing content with malformed frontmatter"
    (let [content "---\ntitle: Test\nauthor\n---\nBody."
          result (fm/parse-file-content content)]
      (is (= {} (:frontmatter result)) "Should return empty map for malformed YAML")
      (is (= content (:body result)) "Should return original content for malformed YAML"))))

(deftest serialize-and-roundtrip-test
  (testing "Serializing with frontmatter"
    (let [frontmatter {:key "value"}
          body "The body."
          serialized (fm/serialize frontmatter body)]
      (is (clojure.string/includes? serialized "key: value"))
      (is (clojure.string/ends-with? serialized body))))

  (testing "Serializing without frontmatter"
    (let [frontmatter {}
          body "Just the body."
          serialized (fm/serialize frontmatter body)]
      (is (= "Just the body." serialized))))

  (testing "Full round-trip"
    (let [;; --- FIX: Use keywords in the original map ---
          original-frontmatter {:tags ["a" "b"], :published true}
          original-body "This is the body content.\nWith multiple lines."
          serialized (fm/serialize original-frontmatter original-body)
          parsed (fm/parse-file-content serialized)]
      (is (= original-frontmatter (:frontmatter parsed)))
      (is (= original-body (:body parsed))))))
