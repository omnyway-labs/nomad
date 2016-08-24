(def VERSION (or (not-empty (System/getenv "SEMVER")) "0.0.1-SNAPSHOT"))

(defproject net.omnypay/nomad VERSION
  :encoding "utf-8"
  :description "Nomad is the OmnyPay schema migration library"
  :dependencies [[com.taoensso/timbre "4.3.1"]
                 [org.clojure/clojure "1.8.0"]
                 [prismatic/schema "1.1.0"]])
