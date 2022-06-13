(ns app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.dom :as rd]
            [reagent.core :as r]
            [haslett.client :as ws]
            [haslett.format :as fmt]
            [cljs.core.async :as a :refer [<! >! timeout]]
            core))

(declare init-socket)
(def stream (atom nil))
(def state (r/atom ""))

(defn send [data]
  (go (>! (:sink @stream) data)))

(defn receive []
  (go (try (loop []
             (let [message (<! (:source @stream))]
               (when message
                 (reset! state message)
                 (recur))))
           (catch js/Error e (js/console.log e)))
      (<! (timeout 1000))
      (init-socket)))

(defn close []
  (ws/close @stream))

(defn init-socket []
  (js/console.log "init-socket")
  (go (reset! stream (<! (ws/connect "ws://localhost:8080/" {:format fmt/edn})))
      (receive)))

(defn root []
  [:div 
   [:p "root: " (core/route 8)]
   [:button {:on-click #(send {:message "hello world"})}
    "Send"]
   [:p (str @state)]])

(defn mount []
  (rd/render [root]
             (.-body js/document)))

(defn ^:dev/after-load start []
  (init-socket)
  (mount))

(defn init []
  (start))