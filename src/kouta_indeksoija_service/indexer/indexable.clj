(ns kouta-indeksoija-service.indexer.indexable
  (:require [kouta-indeksoija-service.elastic.tools :as tools]
            [clojure.tools.logging :as log]))

(defn ->index-entry
  [id doc]
  (when doc (tools/->index-action id doc)))

(defn ->delete-entry
  [id]
  (tools/->delete-action id))

(defn- bulk
  [index-name actions]
  (when (not-empty actions)
    (tools/bulk index-name actions)))

(defn- eat-and-log-errors
  [oid f]
  (try (f oid)
     (catch Exception e
       (log/error e "Indeksoinnissa " oid " tapahtui virhe.")
       nil)))

(defn- create-actions
  [oids f]
  (flatten (doall (pmap #(eat-and-log-errors % f) oids))))

(defn do-index
  [index-name oids f]
  (when-not (empty? oids)
    (log/info (str "Indeksoidaan " (count oids) " indeksiin " index-name))
    (let [start (. System (currentTimeMillis))
          actions (remove nil? (create-actions oids f))]
      (bulk index-name actions)
      (log/info (str "Indeksointi " index-name " kesti " (- (. System (currentTimeMillis)) start) " ms."))
      (vec (remove nil? (map :doc actions))))))

(defn get
  [index-name oid]
  (tools/get-doc index-name oid))