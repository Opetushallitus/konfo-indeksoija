(ns tarjonta-indeksoija-service.embedded-elastic
  (:import [pl.allegro.tech.embeddedelasticsearch EmbeddedElastic]))

(defn get-embedded-elastic
  []
  (let [server (-> (EmbeddedElastic/builder)
                   (.withElasticVersion "5.0.0")
                   (.build)
                   (.start))
        port (.getHttpPort server)]
    (println (str "Started embedded elasticsearch instance in port: " port))
    port))
