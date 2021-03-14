(ns main
  (:require [reagent.dom :as r.dom]
            [reagent.core :as r]
            [taoensso.timbre :as timbre :refer-macros [info error]]
            [cljs.core.async :refer (chan put! <! go go-loop timeout)]))


;; state handling

(defonce state
  (r/atom {:counter 0}))


(def event-queue (chan))


(defn mutate-state! [event payload]
  (info "Event" event "with payload" payload)
  (case event
    :clicked
    (swap! state update-in [:counter] #(+ payload %))))


(go-loop [[event payload] (<! event-queue)]
  (mutate-state! event payload)
  (recur (<! event-queue)))

;; utilities

(defn main-component []
  [:div {:class "min-h-screen bg-gray-100 py-6 flex flex-col justify-center sm:py-12"}
   [:div {:class "relative py-3 sm:max-w-xl sm:mx-auto"}
    [:div {:class "absolute inset-0 bg-gradient-to-r from-cyan-400 to-light-blue-500 shadow-lg transform -skew-y-6 sm:skew-y-0 sm:-rotate-6 sm:rounded-3xl"}]
    [:div {:class "relative px-4 py-10 bg-white shadow-lg sm:rounded-3xl sm:p-20"}
     [:div {:class "max-w-md mx-auto"}
      [:div {:class "divide-y divide-gray-200"}

       [:div
        [:h1 (str "Clicked " (:counter @state) " times.")]
        [:button
         {:on-click #(put! event-queue [:clicked 1])
          :class    "bg-blue-100 px-2 m-2"} "click me"]
        [:button
         {:on-click #(put! event-queue [:clicked 2])
          :class    "bg-blue-200 px-2 m-2"} "click me x2"]]]]]]])


(defn mount [c]
  (r.dom/render [c] (.getElementById js/document "app")))


(defn reload! []
  (mount main-component)
  (print "Hello reload!"))


(defn main! []
  (mount main-component)
  (print "Hello Main"))
