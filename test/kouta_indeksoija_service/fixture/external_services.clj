(ns kouta-indeksoija-service.fixture.external-services)

(def Koulutustoimija "1.2.246.562.10.11111111111")
(def Oppilaitos1 "1.2.246.562.10.54545454545")
(def Toimipiste1OfOppilaitos1 "1.2.246.562.10.54545454511")
(def Toimipiste2OfOppilaitos1 "1.2.246.562.10.54545454522")
(def Oppilaitos2 "1.2.246.562.10.55555555555")
(def Toimipiste1OfOppilaitos2 "1.2.246.562.10.55555555511")

(defn mock-organisaatio
  [oid]
  (locking oid
    (condp = oid
      Oppilaitos1 { :nimi { :fi "Kiva ammattikorkeakoulu" :sv "Kiva ammattikorkeakoulu sv"} :oid oid :kotipaikkaUri "kunta_091"}
      Toimipiste1OfOppilaitos1 { :nimi { :fi "Kiva ammattikorkeakoulu, Helsingin toimipiste" :sv "Kiva ammattikorkeakoulu, Helsingin toimipiste sv"} :oid oid :kotipaikkaUri "kunta_091" }
      Toimipiste2OfOppilaitos1 { :nimi { :fi "Kiva ammattikorkeakoulu, Kuopion toimipiste" :sv "Kiva ammattikorkeakoulu, Kuopion toimipiste sv"} :oid oid :kotipaikkaUri "kunta_297" }
      Oppilaitos2 { :nimi { :fi "Toinen kiva ammattikorkeakoulu"} :oid oid :kotipaikkaUri "kunta_532" }
      { :nimi { :fi (str "Nimi " oid " fi") :en (str "Nimi " oid " en")} :oid oid :kotipaikkaUri "kunta_091" } )))

(defn mock-koodisto
  ([koodisto koodi-uri]
   (locking koodi-uri
    (if koodi-uri
      { :koodiUri koodi-uri :nimi {:fi (str koodi-uri " nimi fi") :sv (str koodi-uri " nimi sv")}})))
  ([koodi-uri]
   (locking koodi-uri
     (if koodi-uri
       (mock-koodisto (subs koodi-uri 0 (clojure.string/index-of koodi-uri "_")) koodi-uri)))))

(defn mock-alakoodit
  [koodi-uri alakoodi-uri]
  (vector
   { :koodiUri (str alakoodi-uri "_01") :nimi {:fi (str alakoodi-uri "_01" " nimi fi") :sv (str alakoodi-uri "_01" " nimi sv")}}
   { :koodiUri (str alakoodi-uri "_02") :nimi {:fi (str alakoodi-uri "_02" " nimi fi") :sv (str alakoodi-uri "_02" " nimi sv")}}))

(defn mock-get-henkilo-nimi-with-cache
  [oid]
  (locking oid "Kalle Ankka"))

(defn- oppilaitos1-hierarkia?
  [oid]
  (or (= Oppilaitos1 oid) (= Toimipiste1OfOppilaitos1 oid) (= Toimipiste1OfOppilaitos2 oid)))

(defn- oppilaitos2-hierarkia?
  [oid]
  (or (= Oppilaitos2 oid) (= Toimipiste1OfOppilaitos2 oid)))

(defn- get-oids
  [oid]
  (if (oppilaitos1-hierarkia? oid)
    [Koulutustoimija Oppilaitos1 [Toimipiste1OfOppilaitos1 Toimipiste2OfOppilaitos1]]
    (if (oppilaitos2-hierarkia? oid)
      [Koulutustoimija Oppilaitos2 [Toimipiste1OfOppilaitos2]]
      [(str oid "55") oid [(str oid "1"), (str oid "2"), (str oid "3")]])))

(defn mock-organisaatio-hierarkia
  [oid & {:as params}]
  (locking oid ;with-redefs used in kouta-indexer-fixture is not thread safe
    (let [oids (get-oids oid)
          koulutustoimija-oid (first oids)
          oppilaitos-oid (second oids)
          oppilaitoksen-osa-oids (last oids)]
      {:numHits (+ 2 (count oppilaitoksen-osa-oids)),
       :organisaatiot [{:oid koulutustoimija-oid,
                        :alkuPvm 313106400000,
                        :parentOid "1.2.246.562.10.00000000001",
                        :parentOidPath (str koulutustoimija-oid  "/1.2.246.562.10.10101010100"),
                        :nimi { :fi (str "Koulutustoimija fi " koulutustoimija-oid), :sv (str "Koulutustoimija sv " koulutustoimija-oid)}
                        :kieletUris [ "oppilaitoksenopetuskieli_1#1" ],
                        :kotipaikkaUri "kunta_091",
                        :aliOrganisaatioMaara 1,
                        :organisaatiotyypit [ "organisaatiotyyppi_01" ],
                        :status "AKTIIVINEN"
                        :children [{:oid oppilaitos-oid,
                                    :alkuPvm 725839200000,
                                    :parentOid koulutustoimija-oid,
                                    :parentOidPath (str oppilaitos-oid "/"  koulutustoimija-oid  "/1.2.246.562.10.10101010100"),
                                    :oppilaitosKoodi "00000",
                                    :oppilaitostyyppi "oppilaitostyyppi_42#1",
                                    :toimipistekoodi "00000",
                                    :nimi { :fi (str "Oppilaitos fi " oppilaitos-oid), :sv (str "Oppilaitos sv " oppilaitos-oid)}
                                    :kieletUris [ "oppilaitoksenopetuskieli_1#1", "oppilaitoksenopetuskieli_2#1" ],
                                    :kotipaikkaUri "kunta_091",
                                    :aliOrganisaatioMaara (count oppilaitoksen-osa-oids),
                                    :organisaatiotyypit [ "organisaatiotyyppi_02" ],
                                    :status "AKTIIVINEN"
                                    :children (vec (map #(let [toimipiste-oid %] {:oid toimipiste-oid,
                                                                                  :alkuPvm 725839200000,
                                                                                  :parentOid oppilaitos-oid,
                                                                                  :parentOidPath (str toimipiste-oid "/" oppilaitos-oid "/"  koulutustoimija-oid  "/1.2.246.562.10.10101010100"),
                                                                                  :toimipistekoodi "00000",
                                                                                  :nimi { :fi (str "Toimipiste fi " toimipiste-oid), :sv (str "Toimipiste sv " toimipiste-oid)}
                                                                                  :kieletUris [ "oppilaitoksenopetuskieli_1#1", "oppilaitoksenopetuskieli_2#1" ],
                                                                                  :kotipaikkaUri "kunta_091",
                                                                                  :aliOrganisaatioMaara 0,
                                                                                  :organisaatiotyypit [ "organisaatiotyyppi_03" ],
                                                                                  :status "AKTIIVINEN"
                                                                                  :children []}) oppilaitoksen-osa-oids))}]}]})))