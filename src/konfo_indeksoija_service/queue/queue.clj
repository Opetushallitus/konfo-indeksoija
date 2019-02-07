(ns konfo-indeksoija-service.queue.queue
  (:require [amazonica.aws.sqs :as sqs]
            [clojure.tools.logging :as log]
            [clj-log.error-log :refer [with-error-logging]]
            [cheshire.core :as json]
            [clojure.algo.generic.functor :refer [fmap]]
            [konfo-indeksoija-service.util.conf :refer [env]]
            [konfo-indeksoija-service.queue.sqs :as queue-sqs]
            [konfo-indeksoija-service.queue.state :as state]
            [konfo-indeksoija-service.util.collections :as coll]))


(defn- handle-index
  [messages])
  ;; TODO Do indexing

(defn queue [priority] (sqs/find-queue (get (:queue env) priority)))

(defn receive [{:keys [q f]}] (assoc (f q) :queue q))

(defn receive-messages-from-queues
  []
  (coll/collect-first
    receive
    #(seq (:messages %))
    [{:q (queue :priority) :f queue-sqs/long-poll}
     {:q (queue :fast) :f queue-sqs/short-poll}
     {:q (queue :slow) :f queue-sqs/short-poll}]))


(defn body-json->map [msg] (json/parse-string (:body msg) true))

(defn handle-messages-from-queues
  "Receive messages from queues and call handler function on them. If handling is
  successful delete messages from queue."
  ([handler] (handle-messages-from-queues handler body-json->map))
  ([handler unwrap]
   (let [received (receive-messages-from-queues)
         messages (:messages received)]
     (when (seq messages)
       (handler (map unwrap messages))
       (doseq
         [msg (map :receipt-handle messages)]
         (sqs/delete-message :queue-url (:queue received)
                             :receipt-handle msg))))))


(defn index-from-queue!
  "Start future to receive messages from queues and index them. On errors just
  prints log message and continues receiving."
  []
  (log/info "Start listening on queues.")
  (future
    (loop []
      (try
        (handle-messages-from-queues
          (fn
            [messages]
            (doseq [step [#(state/set-states! ::state/started %)
                          handle-index
                          #(state/set-states! ::state/indexed %)]]
              (step messages))))
        (catch Exception e (log/error e "Error in receiving indexing messages. Continuing polling.")))
      (recur)))
  (log/warn "Stopped listening on queues."))


(defn handle-failed
  "Handle messages from DLQ. Mark message states to failed."
  []
  (if-let [failed (seq (:messages (queue-sqs/short-poll (queue :dlq))))]
    (state/set-states! ::state/failed failed)))