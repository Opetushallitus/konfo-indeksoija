(ns kouta-indeksoija-service.indexer.tools.search
  (:require [kouta-indeksoija-service.indexer.tools.general :refer :all]
            [kouta-indeksoija-service.indexer.tools.koodisto :refer :all]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache]]
            [kouta-indeksoija-service.indexer.tools.tyyppi :refer [remove-uri-version]]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.util.tools :refer [->distinct-vec]]
            [kouta-indeksoija-service.indexer.cache.eperuste :refer [get-eperuste-by-koulutuskoodi, get-eperuste-by-id]]
            [kouta-indeksoija-service.indexer.tools.tyyppi :refer [oppilaitostyyppi-uri-to-tyyppi]]))

(defn- clean-uris
  [uris]
  (vec (map remove-uri-version uris)))

(defn hit
  [& {:keys [koulutustyyppi
             koulutustyyppiUrit
             opetuskieliUrit
             tarjoajat
             tarjoajaOids
             oppilaitos
             koulutusalaUrit
             nimet
             asiasanat
             ammattinimikkeet
             tutkintonimikeUrit
             oppilaitosOid
             koulutusOid
             toteutusOid
             toteutusNimi
             onkoTuleva
             nimi
             kuva
             metadata]
      :or {koulutustyyppi nil
           koulutustyyppiUrit []
           opetuskieliUrit []
           tarjoajat []
           tarjoajaOids []
           oppilaitos []
           koulutusalaUrit []
           nimet []
           asiasanat []
           ammattinimikkeet []
           tutkintonimikeUrit []
           oppilaitosOid nil
           koulutusOid nil
           toteutusOid nil
           toteutusNimi {}
           onkoTuleva nil
           nimi {}
           kuva nil
           metadata {}}}]

  (let [tutkintonimikkeet (vec (map #(-> % get-koodi-nimi-with-cache :nimi) tutkintonimikeUrit))
        kunnat (remove nil? (distinct (map :kotipaikkaUri tarjoajat)))
        maakunnat (remove nil? (distinct (map #(:koodiUri (maakunta %)) kunnat)))

        terms (fn [lng-keyword] (distinct (remove nil? (concat (map lng-keyword nimet) ;HUOM! Älä tee tästä defniä, koska se ei enää ole thread safe!
                                                               (vector (-> oppilaitos :nimi lng-keyword))
                                                               (map #(-> % :nimi lng-keyword) tarjoajat)
                                                               (map lng-keyword asiasanat)
                                                               (map lng-keyword ammattinimikkeet)
                                                               (map lng-keyword tutkintonimikkeet)))))]

    (cond-> {:koulutustyypit (clean-uris (concat (vector koulutustyyppi) koulutustyyppiUrit))
             :opetuskielet (clean-uris opetuskieliUrit)
             :sijainti (clean-uris (concat kunnat maakunnat))
             :koulutusalat (clean-uris koulutusalaUrit)
             :terms {:fi (terms :fi)
                     :sv (terms :sv)
                     :en (terms :en)}
             :metadata (common/decorate-koodi-uris (merge metadata {:kunnat kunnat}))}

            (not (nil? koulutusOid))    (assoc :koulutusOid koulutusOid)
            (not (nil? toteutusOid))    (assoc :toteutusOid toteutusOid)
            (not (empty? toteutusNimi)) (assoc :toteutusNimi toteutusNimi)
            (not (nil? oppilaitosOid))  (assoc :oppilaitosOid oppilaitosOid)
            (not (nil? kuva))           (assoc :kuva kuva)
            (not (nil? onkoTuleva))     (assoc :onkoTuleva onkoTuleva)
            (not (empty? tarjoajaOids)) (assoc :tarjoajat tarjoajaOids)
            (not (empty? nimi))         (assoc :nimi nimi))))

(defn- get-koulutusalatasot-by-koulutus-koodi-uri
  [koulutusKoodiUri]
  (vec (concat (map :koodiUri (koulutusalat-taso1 koulutusKoodiUri))
               (map :koodiUri (koulutusalat-taso2 koulutusKoodiUri)))))

(defn- get-koulutusalatasot-for-amm-tutkinnon-osat
  [koulutus]
  (when-let [koulutusKoodiUrit (->> (get-in koulutus [:metadata :tutkinnonOsat])
                                    (map #(some-> % :koulutusKoodiUri remove-uri-version))
                                    (->distinct-vec))]
    (->distinct-vec (mapcat get-koulutusalatasot-by-koulutus-koodi-uri koulutusKoodiUrit))))

(defn koulutusalaKoodiUrit
  [koulutus]
  (if (any-ammatillinen? koulutus)
    (cond
      (amm-tutkinnon-osa? koulutus) (get-koulutusalatasot-for-amm-tutkinnon-osat koulutus)
      :default (get-koulutusalatasot-by-koulutus-koodi-uri (:koulutusKoodiUri koulutus))))

  (if (any-ammatillinen? koulutus)
    (let [koulutusKoodiUri (:koulutusKoodiUri koulutus)]
      (vec (concat (map :koodiUri (koulutusalat-taso1 koulutusKoodiUri))
                   (map :koodiUri (koulutusalat-taso2 koulutusKoodiUri)))))
    (get-in koulutus [:metadata :koulutusalaKoodiUrit])))

;TODO korvaa pelkällä get-eperuste-by-id, kun kaikki tuotantodata käyttää ePeruste id:tä
(defn- get-eperuste
  [koulutus]
  (let [eperuste-id (:ePerusteId koulutus)]
    (if eperuste-id
      (get-eperuste-by-id eperuste-id)
      (get-eperuste-by-koulutuskoodi (:koulutusKoodiUri koulutus)))))

(defn tutkintonimikeKoodiUrit
  [koulutus]
  (if (ammatillinen? koulutus)
    (when-let [eperuste (get-eperuste koulutus)]
      (->distinct-vec (map :tutkintonimikeUri (:tutkintonimikkeet eperuste))))
    (get-in koulutus [:metadata :tutkintonimikeKoodiUrit])))

(defn koulutustyyppiKoodiUrit
  [koulutus]
  (if (ammatillinen? koulutus)
    (vec (map :koodiUri (koulutustyypit (:koulutusKoodiUri koulutus))))
    []))

(defn- get-tutkinnon-osa-laajuudet
  [koulutus eperuste]
  (let [eperuste-tutkinnon-osat (:tutkinnonOsat eperuste)]
    (->> (for [tutkinnon-osa (some-> koulutus :metadata :tutkinnonOsat)
               :let [eperuste-osa (first (filter #(= (:id %) (:tutkinnonosaViite tutkinnon-osa)) eperuste-tutkinnon-osat))]]
           (some-> eperuste-osa :opintojenLaajuus :koodiUri))
         (vec))))

(defn- get-osaamisala-laajuus
  [eperuste koulutus]
  (when-let [osaamisalaKoodiUri (some-> koulutus :metadata :osaamisalaKoodiUri remove-uri-version)]
    (some->> (:osaamisalat eperuste)
             (filter #(= osaamisalaKoodiUri (some-> % :koodiUri remove-uri-version)))
             (first)
             :opintojenLaajuus
             :koodiUri)))

(defn opintojenlaajuusKoodiUri
  [koulutus]
  (cond
    (ammatillinen? koulutus)   (-> koulutus (get-eperuste) (get-in [:opintojenlaajuus :koodiUri]))
    (amm-osaamisala? koulutus) (-> koulutus (get-eperuste) (get-osaamisala-laajuus koulutus))
    :default                   (get-in koulutus [:metadata :opintojenLaajuusKoodiUri])))

(defn opintojenlaajuusyksikkoKoodiUri
  [koulutus]
  (cond
    (ammatillinen? koulutus)   (-> koulutus (get-eperuste) (get-in [:opintojenlaajuusyksikko :koodiUri]))
    (amm-osaamisala? koulutus) (-> koulutus (get-eperuste) (get-in [:opintojenlaajuusyksikko :koodiUri]))
    :default                   (get-in koulutus [:metadata :opintojenLaajuusKoodiUri])))

(defn tutkinnonOsaKoodiUrit
  [koulutus]
  (when (amm-tutkinnon-osa? koulutus)
    (-> (for [tutkinnon-osa (get-in koulutus [:metadata :tutkinnonOsat])
              :let [eperuste (get-eperuste-by-id (:ePerusteId tutkinnon-osa))
                    eperuste-tutkinnon-osa (first (filter #(= (:id %) (:tutkinnonosaViite tutkinnon-osa)) (:tutkinnonOsat eperuste)))]]
          {:eperuste (:ePerusteId tutkinnon-osa)
           :koulutus (:koulutusKoodiUri tutkinnon-osa)
           :opintojenLaajuus (some-> eperuste-tutkinnon-osa :opintojenLaajuus :koodiUri)
           :opintojenLaajuusyksikko (get-in eperuste [:opintojenlaajuusyksikko :koodiUri])
           :tutkinnonOsatKoodiUri (some-> eperuste-tutkinnon-osa :koodiUri)}))))

(defn koulutustyyppi-for-organisaatio
  [organisaatio]
  (when-let [oppilaitostyyppi (:oppilaitostyyppi organisaatio)]
    (oppilaitostyyppi-uri-to-tyyppi oppilaitostyyppi)))
