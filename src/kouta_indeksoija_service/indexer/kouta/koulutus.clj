(ns kouta-indeksoija-service.indexer.kouta.koulutus
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.cache.eperuste :refer [get-eperuste]]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache]]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.tools.general :refer [ammatillinen?]]
            [kouta-indeksoija-service.indexer.tools.koodisto :refer :all]))

(def index-name "koulutus-kouta")

(defn enrich-ammatillinen-metadata
  [koulutus]
  (if (ammatillinen? koulutus)
    (let [koulutusKoodi (get-in koulutus [:koulutus :koodiUri])
          eperuste (get-eperuste koulutusKoodi)]
      (-> koulutus
          (assoc-in [:metadata :tutkintonimike]          (vec (map (fn [x] {:koodiUri (:tutkintonimikeUri x) :nimi (:nimi x)}) (:tutkintonimikkeet eperuste))))
          (assoc-in [:metadata :opintojenLaajuus]        (:opintojenlaajuus eperuste))
          (assoc-in [:metadata :opintojenLaajuusyksikko] (:opintojenlaajuusyksikko eperuste))
          (assoc-in [:metadata :koulutusala]             (koulutusalat-taso1 koulutusKoodi))))
    koulutus))

(defn create-index-entry
  [oid]
  (let [koulutus (common/complete-entry (kouta-backend/get-koulutus oid))
        toteutukset (common/complete-entries (kouta-backend/get-toteutus-list-for-koulutus oid))]
    (indexable/->index-entry oid (-> koulutus
                                     (common/assoc-organisaatiot)
                                     (enrich-ammatillinen-metadata)
                                     (assoc :toteutukset (map common/toteutus->list-item toteutukset))))))

(defn do-index
  [oids]
  (indexable/do-index index-name oids create-index-entry))

(defn get
  [oid]
  (indexable/get index-name oid))