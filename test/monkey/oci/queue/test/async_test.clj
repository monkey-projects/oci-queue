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
      (is (= "test message" (try-take ch))))))
