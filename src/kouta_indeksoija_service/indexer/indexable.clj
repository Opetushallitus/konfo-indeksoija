(ns kouta-indeksoija-service.indexer.indexable
  (:require [kouta-indeksoija-service.elastic.tools :as tools]
            [clojure.tools.logging :as log]))

(defn- upsert-index
  [index-name docs]
  (when (not-empty docs)
    (tools/upsert-docs index-name docs)))

(defn do-index
  [index-name oids f]
  (when-not (empty? oids)
    (log/info (str "Indeksoidaan " (count oids) " indeksiin " index-name))
    (let [start (. System (currentTimeMillis))
          docs (remove nil? (f oids))]
      (upsert-index index-name docs)
      (log/info (str "Indeksointi " index-name " kesti " (- (. System (currentTimeMillis)) start) " ms."))
      docs)))

(defn get
  [index-name oid]
  (tools/get-doc index-name oid))