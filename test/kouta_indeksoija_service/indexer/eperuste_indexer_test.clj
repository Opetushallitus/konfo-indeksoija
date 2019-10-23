(ns kouta-indeksoija-service.indexer.eperuste-indexer-test
  (:require [clojure.test :refer :all]
            [clj-test-utils.elasticsearch-mock-utils :refer :all]
            [clj-test-utils.s3-mock-utils :refer :all]
            [kouta-indeksoija-service.indexer.eperuste.eperuste :as eperuste]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [mocks.externals-mock :as mock]
            [clj-s3.s3-connect :as s3]
            [kouta-indeksoija-service.test-tools :refer :all]
            [kouta-indeksoija-service.util.conf :refer [env]]))

(defn empty-s3 []
  (let [keys (s3/list-keys)]
    (if (not (empty? keys)) (s3/delete keys))))

(use-fixtures :once (fn [tests] (init-s3-mock) (tests) (stop-s3-mock)))
(use-fixtures :each (fn [test] (test) (reset-test-data false) (empty-s3)))

(deftest eperuste-index-test
  (testing "do index eperuste"
    (with-redefs [kouta-indeksoija-service.rest.eperuste/get-doc mock/get-eperuste-doc
                  kouta-indeksoija-service.rest.eperuste/get-osaamisalakuvaukset (fn [x] [])]
      (i/index-eperusteet ["3397334"])
      (let [indexed-eperuste (eperuste/get "3397334")
            picture-list (s3/list-keys)]
        (is (= 0 (count picture-list)))
        (is (= "koulutustyyppi_12" (get indexed-eperuste :koulutustyyppi)))))))
