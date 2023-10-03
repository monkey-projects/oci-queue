(ns user
  (:require [config.core :refer [env]]
            [monkey.oci.common.utils :as u]
            [monkey.oci.queue.core :as c]))

(def conf (-> env
              (select-keys [:user-ocid :tenancy-ocid :key-fingerprint :private-key :region])
              (update :private-key u/load-privkey)))

(def cid (:compartment-ocid env))

(def ctx (c/make-context conf))
