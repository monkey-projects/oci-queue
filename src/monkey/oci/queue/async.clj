(ns monkey.oci.queue.async
  "Functions for integrating OCI queues with core.async channels."
  (:require [camel-snake-kebab.core :as csk]
            [clojure.core.async :as ca]
            [clojure.tools.logging :as log]
            [monkey.oci.queue.core :as qc]))

(def ping? (partial = ::ping))

(def option-keys (->> qc/GetMessageOptions
                      (keys)
                      (map (comp first vals))
                      (map (comp csk/->kebab-case-keyword name))
                      (cons :queue-id)))

(defn queue->chan
  "Reads messages from a queue and puts them on a channel.  If no channel
   is provided, one will be created.  Returns the channel.  When the 
   channel is closed, the polling loop stops.  Because it's not possible
   to determine if a channel is closed, without reading from or writing to
   it, this function uses a ping value whenever the message fetcher returns
   without messages.  Otherwise we would not be able to stop the loop when
   the destination channel is closed, but the poller never returns new messages.

   This function mimics the arguments of the `core/get-messages` function,
   with the context as first argument and the options as second.  You can pass
   in the `:limit`, `:timeout-in-seconds`, `:channel-filter`, etc...

   If a `timeout-in-seconds` larger than zero is passed in, the `get-messages` 
   function will use long polling.  This means that, should the channel be 
   closed in this case, the poll operation will still run and only return if 
   externally interrupted, or a message is received, or it times out."
  [ctx {:keys [queue-id channel timeout-in-seconds get-messages] :or {timeout-in-seconds 30000} :as opts}]
  (let [ch (or channel (ca/chan 1 (remove ping?)))
        fetcher (or get-messages qc/get-messages)
        get-next (comp :body
                       deref
                       ;; TODO Pagination
                       #(fetcher
                         ctx
                         (select-keys opts option-keys)))
        put-all (fn [msg]
                  ;; Can't use onto-chan! because it gives no indication of whether target
                  ;; channel is closed or not.
                  (ca/go-loop [s (seq msg)]
                    (if (empty? s)
                      ;; Done
                      true
                      (if (ca/>! ch (first s))
                        (recur (next s))
                        ;; Destination closed
                        false))))]
    (ca/go-loop [{:keys [messages] :as r} (get-next)]
      ;; Send a ping if no messages have been received.  This is necessary to
      ;; detect if the output channel was closed.  Otherwise this could go on
      ;; forever, if the fetcher does not return any messages.
      (when (or (and (empty? messages)
                     (ca/>! ch ::ping))
                (and (not-empty messages)
                     (ca/<! (put-all messages))))
        (recur (get-next))))
    ch))

(defn chan->queue
  "Takes messages from a channel, and sends them to the given queue.
   This actually invokes `put-messages`, which uses the default implementation
   if not specified, and formats the arguments as for the `put-messages` endpoint.
   The messages on the channel should have the structure expected by the backend.
   This also allows you to specify a destination channel.
   Stops when `channel` is closed."
  [ctx {:keys [queue-id channel put-messages]}]
  ;; TODO Add the possibility to batch messages and send them in one call, using a
  ;; timeout
  (let [put (or put-messages qc/put-messages)]
    (ca/go-loop [msg (ca/<! channel)]
      (when msg
        (put ctx {:queue-id queue-id
                  :put {:messages [msg]}}))))
  channel)
