(ns url-shortener.core
  (:require [ring.adapter.jetty :as ring-jetty]
            [reitit.ring :as ring]
            [muuntaja.core :as m]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [clojure.set :as set]
            [taoensso.carmine :as car :refer [wcar]]
            [url-shortener.env :as env])
  (:gen-class))

(defonce my-conn-pool   (car/connection-pool {}))

(def my-conn-spec-1 {:uri env/redis-url})

(def my-wcar-opts
  {:pool my-conn-pool
   :spec my-conn-spec-1})

(defn reverse-str
  [my-str]
  (apply str (reverse my-str)))

(defn gen-id []
  (rand-int 350000000))

(defn hash-id
  [n]
  (let [symbolmap (zipmap (concat
                           (map char (range 48 58))
                           (map char (range 97 123))
                           (map char (range 65 91)))
                          (range 62))]
    (loop [decNumber n
           result []]
      (if (= decNumber 0)
        (reverse-str result)
        (recur (quot decNumber 62)
               (conj result ((set/map-invert symbolmap) (mod decNumber 62))))))))

(def surls (atom {}))

(defn shorten-url [{body :body-params}]
  (let [hash-key (hash-id (gen-id))]
    (when (not (some #(= % (get body :url)) (vals @surls)))
      surls (swap! surls assoc hash-key (get body :url))))
  {:status 200
   :body @surls})

(defn redirect-to-original [{{:keys [hash-id]} :path-params}]
  (let [original-url (get @surls hash-id)]
    {:status 301
     :headers {"Location" original-url}}))

(defn shorten-url-redis [{body :body-params}]
  (let [hash-key (hash-id (gen-id))]
    (wcar my-wcar-opts (car/set hash-key (get body :url)))
    {:status 200
     :body {:hash-key hash-key}}))

(defn redirect-to-original-redis [{{:keys [hash-id]} :path-params}]
  (let [original-url (wcar my-wcar-opts (car/get hash-id))]
    {:status 301
     :headers {"Location" original-url}}))

(defn string-handler [_]
  {:status 200
   :body "URL shortener"})

(def app
  (ring/ring-handler
   (ring/router
    ["/"
     ["shorten" {:post shorten-url-redis}]
     ["go/:hash-id" {:get redirect-to-original-redis}]
     ["" {:get string-handler}]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware]}})))

(defn start []
  (ring-jetty/run-jetty app {:port  3000
                             :join? false}))

(defn -main
  [& args]
  (start))
