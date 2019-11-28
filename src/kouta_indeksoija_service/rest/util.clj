(ns kouta-indeksoija-service.rest.util
  (:require [clj-http.client :as client]
            [clojure.string :refer [upper-case]]
            [clojure.tools.logging :as log]))

(defn add-callerinfo [options]
  (update-in options [:headers] assoc
             "Caller-Id" "1.2.246.562.10.00000000001.kouta-indeksoija"))

(defn get [url opts]
  (let [options (add-callerinfo opts)]
    ;(log/info "Making get call to " url " with options: " options)
    (client/get url options)))

(defn put [url opts]
  (let [options (add-callerinfo opts)]
    ;(log/info "Making put call to " url " with options: " options)
    (client/put url options)))

(defn post [url opts]
  (let [options (add-callerinfo opts)]
    (client/post url options)))

(defn request [opts]
  (-> opts
      add-callerinfo
      client/request))

(defn handle-error
  [url method-name response]
  (let [status   (:status response)
        body     (:body response)]
    (case status
      200 body
      404 (do (log/warn  "Got " status " from " method-name ": " url " with body " body) nil)
          (do (log/error "Got " status " from " method-name ": " url " with response " response) nil))))

(defn ->json-body-with-error-handling
  [url method opts]
  (let [method-name (upper-case (str method))
        f           (case method :post post :put put :get get)]
    (log/debug method-name " => " url)
    (let [response (f url (merge opts {:throw-exceptions false :as :json}))]
      (handle-error url method-name response))))

(defn get->json-body
  ([url query-params]
   (->json-body-with-error-handling url :get {:query-params query-params}))
  ([url]
    (get->json-body url {})))

(defn post->json-body
  ([url body content-type]
   (->json-body-with-error-handling url :post {:body body :content-type (keyword content-type)}))
  ([url body]
   (post->json-body url body :json)))