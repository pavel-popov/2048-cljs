(ns main
  (:require [reagent.dom :as r.dom]
            [reagent.core :as r]
            [keybind.core :as key]
            [taoensso.timbre :as timbre :refer-macros [info error]]
            [cljs.core.match :refer-macros [match]]
            [cljs.core.async :refer (chan put! <! go go-loop timeout)]))


;; state handling

(defn with-index [coll]
  (partition 2 (interleave coll (range))))


(defn pop-random
  "Replace 1, 2 or 3 zeros with
   two, four or eight."
  [coll]
  (let [indexed (with-index coll)
        zeros (filter #(= 0 (first %)) indexed)
        picked (take (inc (rand-int 3)) (sort #(compare (rand-int 100)
                                                        (rand-int 100)) zeros))
        replaced (map (fn [[_ idx]] [(bit-shift-left 1 (inc (rand-int 3))) idx]) picked)
        picked-indices (set (map second picked))
        without-picked (filter #(not (picked-indices (second %))) indexed)
        with-replaced (concat without-picked replaced)]
    (->> with-replaced (sort #(compare (second %1)
                                       (second %2))) (map first) vec)))


(defn initial-state []
  (pop-random (take 16 (repeat 0))))


;; (initial-state)


(defonce state
  (r/atom {:points 0
           :field (initial-state)
           :lost false}))



(def event-queue (chan))


(defn transpose
  [[a0 a1 a2 a3
    b0 b1 b2 b3
    c0 c1 c2 c3
    d0 d1 d2 d3]]
  [d0 c0 b0 a0
   d1 c1 b1 a1
   d2 c2 b2 a2
   d3 c3 b3 a3])


(def positive? #(< 0 %))


(defn actual-scorer [points]
  (put! event-queue [:scored points]))


(defn noop-scorer [points])


(defn merge-duplicates [[a b c d] & {:keys [scorer]}]
  (let [scorer (or scorer actual-scorer)]
    (cond
      (and (= a b) (= c d))
      (do (scorer (+ a b c d)) [(+ a b) (+ c d) 0 0])

      (and (= a b) (not= c d))
      (do (scorer (+ a b)) [(+ a b) c d 0])

      (= b c)
      (do (scorer (+ b c)) [a (+ b c) d 0])

      (= c d)
      (do (scorer (+ c d)) [a b (+ c d) 0])

      :else
      [a b c d])))


(defn merge-row [row & {:keys [scorer]}]
  (->>
   row
   (filter positive?)
   (#(concat % [0 0 0 0]))
   (take 4)
   (#(merge-duplicates % :scorer scorer))))


(defn merge-rows
  [[a0 a1 a2 a3
    b0 b1 b2 b3
    c0 c1 c2 c3
    d0 d1 d2 d3] & {:keys [scorer]}]
  (->> [(merge-row [a0 a1 a2 a3] :scorer scorer)
        (merge-row [b0 b1 b2 b3] :scorer scorer)
        (merge-row [c0 c1 c2 c3] :scorer scorer)
        (merge-row [d0 d1 d2 d3] :scorer scorer)]
       flatten))


(defn merge-left
  [field]
  (-> field merge-rows pop-random vec))


(defn merge-left-noscore
  [field]
  (-> field (#(merge-rows % :scorer noop-scorer)) vec))


;; (merge-row [0 0 8 8])


(defn mutate-state! [event payload]
  (info "Event" event "with payload" payload)
  (case event
    :scored
    (swap! state update-in [:points] #(+ payload %))

    :up
    (swap! state update-in [:field] (comp transpose merge-left transpose transpose transpose))

    :left
    (swap! state update-in [:field] merge-left)

    :right
    (swap! state update-in [:field] (comp transpose transpose merge-left transpose transpose))

    :down
    (swap! state update-in [:field] (comp transpose transpose transpose merge-left transpose))

    :transpose
    (swap! state update-in [:field] transpose)

    :merge
    (swap! state update-in [:field] merge)

    :reset
    (swap! state assoc :points 0 :field (initial-state) :lost false)

    (info "Nothing to do for" event))

  ;; checking if there are more moves available
  (let [field (:field @state)]
    (when (= 1 (count (set [(merge-left-noscore field)
                            ((comp transpose merge-left-noscore transpose transpose transpose) field)
                            ((comp transpose transpose merge-left-noscore transpose transpose) field)
                            ((comp transpose transpose transpose merge-left-noscore transpose) field)])))
      (swap! state assoc :lost true))))


(go-loop [[event payload] (<! event-queue)]
  (mutate-state! event payload)
  (recur (<! event-queue)))


;; (with-index [1 2 3 4])

;; (partition 2 (interleave [1 2 3] [3 4 5]))


(defn cell-background [cell]
  (case cell
    0 "bg-gray-400"
    2 "bg-indigo-300"
    4 "bg-indigo-400"
    8 "bg-indigo-500"
    16 "bg-indigo-600"
    32 "bg-pink-300"
    64 "bg-pink-400"
    128 "bg-pink-600"
    256 "bg-red-300"
    512 "bg-red-400"
    1024 "bg-red-500"
    2048 "bg-red-600"
    4096 "bg-yellow-600"
    8192 "bg-yellow-700"
    "bg-green-500"))


(defn field [cells]
  [:div {:class "rounded-t-xl overflow-hidden bg-gradient-to-r from-indigo-50 to-yellow-100 p-2"}
   [:div {:class "grid grid-cols-4 gap-2 h-64"}
    (doall
     (for [[cell idx] (with-index cells)]
       [:div {:class (str "rounded-md text-white text-2xl font-extrabold flex items-center justify-center py-2 "
                          (cell-background cell))
              :key (str "cell-" idx)} (if (= 0 cell) "\u00A0" cell)]))]])


;; utilities

(defn main-component []
  [:div {:class "min-h-screen bg-gray-100 py-6 flex flex-col justify-center sm:py-12"}
   [:div {:class "relative py-3 sm:max-w-xl sm:mx-auto"}
    [:div {:class "absolute inset-0 bg-gradient-to-r from-cyan-400 to-light-blue-500 shadow-lg transform -skew-y-6 sm:skew-y-0 sm:-rotate-6 sm:rounded-3xl"}]
    [:div {:class "relative px-4 py-10 bg-white shadow-lg sm:rounded-3xl sm:p-20"}
     [:div {:class "max-w-md mx-auto"}
      [:div {:class "divide-y divide-gray-200"}
       [:p "Points: " (:points @state) " " (when (:lost @state) [:span.font-extrabold.text-red-600 "You've lost, loser!"])]
       [:div                            ; buttons
        [:button {:on-click #(put! event-queue [:left nil]) :class "bg-blue-100 px-2 m-2"} "←"]
        [:button {:on-click #(put! event-queue [:up nil]) :class "bg-blue-100 px-2 m-2"} "↑"]
        [:button {:on-click #(put! event-queue [:down nil]) :class "bg-blue-100 px-2 m-2"} "↓"]
        [:button {:on-click #(put! event-queue [:right nil]) :class "bg-blue-100 px-2 m-2"} "→"]
        [:button {:on-click #(put! event-queue [:reset nil]) :class "bg-blue-100 px-2 m-2"} "reset"]]

       [field (:field @state)]]]]]])


(defn mount [c]
  (r.dom/render [c] (.getElementById js/document "app")))


(defn reload! []
  (mount main-component)
  (print "Hello reload!"))


(defn set-keybindings! []
  (key/bind! "h" ::left #(put! event-queue [:left nil]))
  (key/bind! "left" ::arrow-left #(put! event-queue [:left nil]))

  (key/bind! "j" ::down #(put! event-queue [:down nil]))
  (key/bind! "down" ::arrow-down #(put! event-queue [:down nil]))

  (key/bind! "k" ::up #(put! event-queue [:up nil]))
  (key/bind! "up" ::arrow-up #(put! event-queue [:up nil]))

  (key/bind! "l" ::right #(put! event-queue [:right nil]))
  (key/bind! "right" ::arrow-right #(put! event-queue [:right nil])))


(defn main! []
  (mount main-component)
  (set-keybindings!)
  (print "Hello Main"))
