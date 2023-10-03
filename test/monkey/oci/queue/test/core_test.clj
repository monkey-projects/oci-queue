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
  ([ctx f route opts]
   (let [r (-> ctx
               (mt/respond-with-constant {route {:body "[]" :status 200}})
               (f opts)
               (deref))]
     (is (map? r))
     (is (= 200 (:status r)))))
  ([ctx route opts]
   (let [f (some->> route symbol (ns-resolve 'monkey.oci.queue.core) var-get)]
     (is (fn? f) (str "no binding found for " route))
     (when f
       (test-call ctx f route opts)))))

;; Since all tests are more or less the same, let's use a seq instead of copy/pasting.

(defn test-endpoints
  ([ctx ep]
   (doseq [[k v] ep]
     (testing (format "invokes `%s` endpoint" k)
       (test-call ctx k v))))
  ([ep]
   (test-endpoints test-ctx ep)))

(deftest queue-overview-endpoints
  (test-endpoints {:list-queues   {:compartment-id "test-compartment"}
                   :create-queue  {:compartment-id "new-compartment"
                                   :display-name "test-queue"}
                   :update-queue  {:queue-id "test-queue"
                                   :freeform-tags {"key" "value"}}
                   :delete-queue  {:queue-id "test-queue"}
                   :get-queue     {:queue-id "test-queue"}
                   :purge-queue   {:queue-id "test-queue"
                                   :purge-type :NORMAL}
                   :list-channels {:queue-id "test-queue"}}))

(deftest make-queue-context
  (testing "creates context object for queue using messages endpoint"
    (is (map? (sut/make-queue-context {} "http://messages")))))

(def queue-ctx (sut/make-queue-context test-config "http://test-queue"))

(deftest queue-endpoints
  (test-endpoints queue-ctx
                  {:get-stats       {:queue-id "test-queue"}
                   :get-messages    {:queue-id "test-queue"}
                   :delete-messages {:queue-id "test-queue"
                                     :delete {:entries [{:receipt "test-rcpt"}]}}
                   :delete-message  {:queue-id "test-queue"
                                     :message-receipt "test-rcpt"}
                   :put-messages    {:queue-id "test-queue"
                                     :messages [{:content "test message"}]}}))
