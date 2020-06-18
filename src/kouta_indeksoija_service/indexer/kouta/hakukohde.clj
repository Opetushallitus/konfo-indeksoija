(ns kouta-indeksoija-service.indexer.kouta.hakukohde
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.indexable :as indexable]))

(def index-name "hakukohde-kouta")

(defn- assoc-valintaperuste
  [hakukohde valintaperuste]
  (cond-> (dissoc hakukohde :valintaperusteId)
          (some? (:valintaperusteId hakukohde)) (assoc :valintaperuste (-> valintaperuste
                                                                           (dissoc :metadata)
                                                                           (common/complete-entry)))))

(defn- assoc-toteutus
  [hakukohde toteutus]
  (assoc hakukohde :toteutus (-> toteutus
                                 (common/complete-entry)
                                 (common/toteutus->list-item))))

(defn- luonnos?
  [haku-tai-hakukohde]
  (= "tallennettu" (:tila haku-tai-hakukohde)))

(defn- korkeakoulutusta?
  [koulutus]
  (contains? #{"yo" "amk"} (:koulutustyyppi koulutus)))

(defn- johtaa-tutkintoon?
  [koulutus]
  (:johtaaTutkintoon koulutus))

(defn- alkamiskausi-kevat?
  [haku hakukohde]
  (-> (if (:kaytetaanHaunAlkamiskautta hakukohde) haku hakukohde)
      :alkamiskausiKoodiUri
      (clojure.string/starts-with? "kausi_k#")))

(defn- alkamisvuosi
  [haku hakukohde]
  (-> (if (:kaytetaanHaunAlkamiskautta hakukohde) haku hakukohde)
      :alkamisvuosi
      Integer/valueOf))

(defn- alkamiskausi-ennen-syksya-2016?
  [haku hakukohde]
  (let [alkamisvuosi (alkamisvuosi haku hakukohde)]
    (or (< alkamisvuosi 2016)
        (and (= alkamisvuosi 2016)
             (alkamiskausi-kevat? haku hakukohde)))))

(defn- some-kohdejoukon-tarkenne?
  [haku]
  (not (clojure.string/blank? (:kohdejoukonTarkenneKoodiUri haku))))

(defn- jatkotutkintohaku-tarkenne?
  [haku]
  (clojure.string/starts-with?
   (:kohdejoukonTarkenneKoodiUri haku)
   "haunkohdejoukontarkenne_3#"))

(defn- ->ei-yps
  [syy]
  {:voimassa false :syy syy})

(def ^:private yps
  {:voimassa true
   :syy      "Hakukohde on yhden paikan säännön piirissä"})

(defn- assoc-yps
  [hakukohde haku koulutus]
  (assoc
   hakukohde
   :yhdenPaikanSaanto
   (cond (luonnos? haku)
         (->ei-yps "Haku on luonnos tilassa")

         (luonnos? hakukohde)
         (->ei-yps "Hakukohde on luonnos tilassa")

         (not (korkeakoulutusta? koulutus))
         (->ei-yps "Ei korkeakoulutus koulutusta")

         (not (johtaa-tutkintoon? koulutus))
         (->ei-yps "Ei tutkintoon johtavaa koulutusta")

         (alkamiskausi-ennen-syksya-2016? haku hakukohde)
         (->ei-yps "Koulutuksen alkamiskausi on ennen syksyä 2016")

         (and (some-kohdejoukon-tarkenne? haku)
              (not (jatkotutkintohaku-tarkenne? haku)))
         (->ei-yps (str "Haun kohdejoukon tarkenne on "
                        (:kohdejoukonTarkenneKoodiUri haku)))

         :else
         yps)))

(defn- assoc-hakulomake-linkki
  [hakukohde haku]
  (let [link-holder (if (true? (:kaytetaanHaunHakulomaketta hakukohde)) haku hakukohde)]
    (conj hakukohde (common/create-hakulomake-linkki link-holder (:oid haku)))))

(defn create-index-entry
  [oid]
  (let [hakukohde      (kouta-backend/get-hakukohde oid)
        haku           (kouta-backend/get-haku (:hakuOid hakukohde))
        toteutus       (kouta-backend/get-toteutus (:toteutusOid hakukohde))
        koulutus       (kouta-backend/get-koulutus (:koulutusOid toteutus))
        valintaperuste (kouta-backend/get-valintaperuste (:valintaperusteId hakukohde))]
    (indexable/->index-entry oid
                             (-> hakukohde
                                 (assoc-yps haku koulutus)
                                 (common/complete-entry)
                                 (assoc-toteutus toteutus)
                                 (assoc-valintaperuste valintaperuste)
                                 (assoc-hakulomake-linkki haku)))))

(defn do-index
  [oids]
  (indexable/do-index index-name oids create-index-entry))

(defn get
  [oid]
  (indexable/get index-name oid))
