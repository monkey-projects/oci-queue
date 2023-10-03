(ns monkey.oci.queue.core
  "Core functionality for the OCI queue API client.  The functions here
   mostly just delegate to Martian for performing the actual http calls."
  (:require [martian.core :as martian]
            [monkey.oci.common
             [martian :as cm]
             [utils :as u]]
            [schema.core :as s]))

(def json #{"application/json"})

(def common-queue-details
  {(s/optional-key :channelConsumptionLimit) s/Int
   (s/optional-key :customEncryptionKeyId) s/Str
   (s/optional-key :deadLetterQueueDeliveryCount) s/Int
   (s/optional-key :definedTags) s/Any
   (s/optional-key :freeformTags) s/Any
   (s/optional-key :timeoutInSeconds) s/Int
   (s/optional-key :visibilityInSeconds) s/Int})

(s/defschema CreateQueueDetails
  (assoc common-queue-details
         :compartmentId s/Str
         :displayName s/Str))

(s/defschema UpdateQueueDetails
  (assoc common-queue-details
         (s/optional-key :displayName) s/Str))

(def queue-routes
  [{:route-name :list-queues
    :method :get
    :path-parts ["/queues"]
    :query-schema {:compartmentId s/Str
                   (s/optional-key :displayName) s/Str
                   (s/optional-key :id) s/Str}
    :produces json}

   {:route-name :create-queue
    :method :post
    :path-parts ["/queues"]
    :body-schema {:queue CreateQueueDetails}
    :consumes json
    :produces json}

   {:route-name :update-queue
    :method :put
    :path-parts ["/queues/" :queue-id]
    :path-schema {:queue-id s/Str}
    :body-schema {:queue UpdateQueueDetails}
    :consumes json
    :produces json}

   {:route-name :delete-queue
    :method :delete
    :path-parts ["/queues/" :queue-id]
    :path-schema {:queue-id s/Str}
    :consumes json}

   {:route-name :get-queue
    :method :get
    :path-parts ["/queues/" :queue-id]
    :path-schema {:queue-id s/Str}
    :consumes json}

   {:route-name :purge-queue
    :method :post
    :path-parts ["/queues/" :queue-id "/actions/purge"]
    :path-schema {:queue-id s/Str}
    :body-schema {:details {(s/optional-key :channelIds) [s/Str]
                            :purgeType (s/enum :NORMAL :DLQ :BOTH)}}
    :consumes json}])

(def routes (concat queue-routes))

(def host (comp (partial format "https://messaging.%s.oraclecloud.com/20210201") :region))

(defn make-context
  "Creates Martian context for the given configuration.  This context
   should be passed to subsequent requests."
  [conf]
  (cm/make-context conf host routes))

(def send-request martian/response-for)

(u/define-endpoints *ns* routes martian/response-for)
