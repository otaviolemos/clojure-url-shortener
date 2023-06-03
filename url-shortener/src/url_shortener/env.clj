(ns url-shortener.env)

(def redis-url (System/getenv "REDIS_URI"))
