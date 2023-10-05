(ns monkey.oci.queue.test.async-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.core.async :as ca]
            [monkey.oci.queue
             [async :as sut]
             [core :as c]]))

(defn- try-take [ch & [timeout timeout-val]]
  (let [t (ca/timeout (or timeout 1000))
        [r p] (ca/alts!! [t ch])]
    (if (= t p)
      (or timeout-val ::timeout)
      r)))

(defn- dummy-fetcher [& _]
  (Thread/sleep 1000)
  (future {:body {:messages []}}))

(defmacro with-chan [[k v] & body]
  `(let [~k ~v]
     (try
       ~@body
       (finally (ca/close! ~k)))))

(deftest option-keys
  (testing "is a map with the message option keys for the call"
    (is (seq? sut/option-keys))
    (is (contains? (set sut/option-keys) :timeout-in-seconds))))

(deftest queue->chan
  (testing "creates new channel, returns it"
    (with-chan [ch (sut/queue->chan {} {:get-messages dummy-fetcher})]
      (is (some? ch))))

  (testing "returns input channel if provided"
    (with-chan [ch (ca/chan)]
      (is (= ch (sut/queue->chan {} {:channel ch
                                     :get-messages dummy-fetcher})))))

  (testing "invokes `get-messages` endpoint"
    (with-chan [ch (sut/queue->chan {} {:get-messages (fn [& _]
                                                        (Thread/sleep 100)
                                                        (future {:body {:messages ["test message"]}}))})]
      (is (= "test message" (try-take ch)))))

  (testing "passes context and message options"
    (with-chan [ch (sut/queue->chan :test-ctx
                                    {:get-messages (fn [ctx opts]
                                                     (Thread/sleep 100)
                                                     (future {:body {:messages [ctx opts]}}))
                                     :queue-id "test-queue"
                                     :timeout-in-seconds 10
                                     :other "not allowed"})]
      (is (= :test-ctx (try-take ch)))
      (is (= {:queue-id "test-queue"
              :timeout-in-seconds 10} (try-take ch))))))

(deftest chan->queue
  (testing "puts message to queue"
    (let [sent (ca/chan 10)
          put-msg (fn [_ opts]
                    ;; Fake put function that pipes the message back to a channel
                    (ca/go (ca/onto-chan! sent (get-in opts [:put :messages]))))]
      (with-chan [ch (ca/chan)]
        (is (= ch (sut/chan->queue {} {:put-messages put-msg
                                       :channel ch})))
        (is (= ch (second (ca/alts!! [[ch "test message"] (ca/timeout 1000)]))))
        (is (= "test message" (try-take sent)))))))
