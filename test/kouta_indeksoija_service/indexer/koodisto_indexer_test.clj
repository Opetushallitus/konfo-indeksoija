(ns kouta-indeksoija-service.indexer.koodisto-indexer-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.test-tools :refer :all]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer [json]]
            [clj-test-utils.elasticsearch-mock-utils :refer :all]
            [clj-test-utils.s3-mock-utils :refer :all]
            [kouta-indeksoija-service.indexer.koodisto.koodisto :as koodisto]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [mocks.externals-mock :as mock]
            [clj-s3.s3-connect :as s3]
            [kouta-indeksoija-service.test-tools :refer :all]
            [kouta-indeksoija-service.util.conf :refer [env]]))

(use-fixtures :each (fn [test] (test) (reset-test-data false)))

(deftest koodisto-index-test
  (testing "do index koodisto"
    (with-redefs [kouta-indeksoija-service.rest.koodisto/get-koodit #(json "test/resources/koodisto/" %)]
      (i/index-koodistot ["maakunta"])
      (let [result (koodisto/get "maakunta")]
        (is (= "maakunta" (:koodisto result)))
        (is (= 21 (count (distinct (remove nil? (map :koodiUri (:koodit result)))))))
        (doseq [nimi (map :nimi (:koodit result))]
          (is (contains? nimi :fi))
          (is (contains? nimi :sv)))))))