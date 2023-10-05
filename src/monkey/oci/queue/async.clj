(ns monkey.oci.queue.async
  "Functions for integrating OCI queues with core.async channels."
  (:require [clojure.core.async :as ca]
            [clojure.tools.logging :as log]
            [monkey.oci.queue.core :as qc]))

(def ping? (partial = ::ping))

(defn queue->chan
  "Reads messages from a queue and puts them on a channel.  If no channel
   is provided, one will be created.  Returns the channel.  When the 
   channel is closed, the polling loop stops.  Because it's not possible
   to determine if a channel is closed, without reading from or writing to
   it, this function uses a ping value whenever the message fetcher returns
   without messages.  Otherwise we would not be able to stop the loop when
   the destination channel is closed, but the poller never returns new messages.
   If an `interval` of zero is passed in, the `get-messages` function will
   use long polling.  This means that, should the channel be closed in this
   case, the poll operation will still run and only return if externally
   interrupted, or a message is received."
  [ctx {:keys [queue-id channel interval get-messages] :or {interval 0}}]
  (let [ch (or channel (ca/chan 1 (remove ping?)))
        fetcher (or get-messages qc/get-messages)
        get-next (comp :body
                       deref
                       #(fetcher
                         ctx
                         {:queue-id queue-id
                          :timeout-in-seconds (float (/ interval 1000))}))
        put-all (fn [msg]
                  (log/debug "Read messages:" msg)
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
      (if (or (and (empty? messages)
                   (ca/>! ch ::ping))
              (and (not-empty messages)
                   (ca/<! (put-all messages))))
        (recur (get-next))
        (log/debug "Polling loop closed")))
    ch))
