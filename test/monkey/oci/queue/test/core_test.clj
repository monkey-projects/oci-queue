(ns monkey.oci.queue.test.core-test
  (:require [clojure.test :refer [deftest testing is with-test]]
            [monkey.oci.queue.core :as sut]
            [martian.test :as mt])
  (:import java.security.KeyPairGenerator))

(defn generate-key []
  (-> (doto (KeyPairGenerator/getInstance "RSA")
        (.initialize 2048))
      (.generateKeyPair)
      (.getPrivate)))

(def test-config {:user-ocid "test-user"
                  :tenancy-ocid "test-tenancy"
                  :private-key (generate-key)
                  :key-fingerprint "test-fingerprint"
                  :region "test-region"})

(def test-ctx (sut/make-context test-config))

(defn- test-call
  ([f route opts]
   (let [r (-> test-ctx
               (mt/respond-with-constant {route {:body "[]" :status 200}})
               (f opts)
               (deref))]
     (is (map? r))
     (is (= 200 (:status r)))))
  ([route opts]
   (let [f (some->> route symbol (ns-resolve 'monkey.oci.queue.core) var-get)]
     (is (fn? f) (str "no binding found for " route))
     (when f
       (test-call f route opts)))))

;; Since all tests are more or less the same, let's use a seq instead of copy/pasting.

(defn test-endpoints [ep]
  (doseq [[k v] ep]
    (testing (format "invokes `%s` endpoint" k)
      (test-call k v))))

(deftest queue-endpoints
  (test-endpoints {:list-queues  {:compartment-id "test-compartment"}
                   :create-queue {:compartment-id "new-compartment"
                                  :display-name "test-queue"}
                   :update-queue {:queue-id "test-queue"
                                  :freeform-tags {"key" "value"}}
                   :delete-queue {:queue-id "test-queue"}
                   :get-queue    {:queue-id "test-queue"}
                   :purge-queue  {:queue-id "test-queue"
                                  :purge-type :NORMAL}}))

