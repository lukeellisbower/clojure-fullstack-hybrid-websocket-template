 (ns backend.core
   (:require
    [immutant.web             :as web]
    [immutant.web.async       :as async]
    [immutant.web.middleware  :as web-middleware]
    [compojure.route          :as route]
    [environ.core             :refer (env)]
    [compojure.core           :refer (ANY GET defroutes)]
    [ring.util.response       :refer (response redirect content-type)]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [xtdb.api :as xt]
    [shared.core :as shared])
   (:gen-class))

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "data/dev/tx-log")
      :xtdb/document-store (kv-store "data/dev/doc-store")
      :xtdb/index-store (kv-store "data/dev/index-store")})))

(def xtdb-node (start-xtdb!))

(defn stop-xtdb! []
  (.close xtdb-node))

(defn test-db []
  (xt/submit-tx xtdb-node [[::xt/put
                            {:xt/id "hi2u"
                             :user/name "zig"}]])
  (xt/q (xt/db xtdb-node) '{:find [e]
                            :where [[e :user/name "zig"]]}))


(def sockets (atom #{}))

(defn on-message [ch edn-data]
  (let [data (edn/read-string edn-data)]
    (async/send! ch (prn-str {:messge (apply str (reverse (:message data)))
                              :data (shared/route 99)}))))

(def websocket-callbacks
  "WebSocket callback functions"
  {:on-open   (fn [channel]
                (swap! sockets conj channel)
                (async/send! channel (prn-str "server ready")))
   :on-close   (fn [channel {:keys [code reason]}]
                 (println "close code:" code "reason:" reason))
   :on-message on-message})

(defroutes routes
  (GET "/" {c :context} (redirect (str c "/index.html")))
  (route/resources "/"))

(def server (atom nil))

(defn stop-server []
  (web/stop (deref server)))

(defn start-server [args]
  (reset! server
          (web/run
           (-> routes
               (web-middleware/wrap-session {:timeout 20})
               (web-middleware/wrap-websocket websocket-callbacks))
           (merge {"host" (or (env :demo-web-host) "0.0.0.0")
                   "port" (or (env :demo-web-port) "8080")}
                  args))))

(defn restart-server []
  (stop-server)
  (require 'backend.core :reload)
  (start-server {}))

(restart-server)

(defn -main [& {:as args}]
  (start-server args))

