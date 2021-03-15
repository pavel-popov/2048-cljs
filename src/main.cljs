(ns main
  (:require [reagent.dom :as r.dom]
            [reagent.core :as r]
            [keybind.core :as key]
            [taoensso.timbre :as timbre :refer-macros [info]]
            [cljs.core.async :refer (chan put! <! go-loop)]))


(defn with-index
  "Return seq with every item of COLL turned into a list with second
  element being an index."
  [coll]
  (partition 2 (interleave coll (range))))


(defn sort-rand
  "Return randomly sorted collection."
  [coll]
  (sort #(compare (rand-int 100) (rand-int 100)) coll))


(defn pop-random
  "Replace 1 or 2  zeros with two, four or eight."
  [coll]
  (let [indexed (with-index coll)
        picked (->> indexed
                    (filter #(= 0 (first %))) ; keep zeros
                    sort-rand
                    (take (rand-int 3)))
        replaced (map (fn [[_ idx]] [(rand-nth [2 4 8]) idx]) picked)
        picked-indices (set (map second picked))
        without-picked (filter #(not (picked-indices (second %))) indexed)
        with-replaced (concat without-picked replaced)]

    (->> with-replaced
         (sort #(compare (second %1) (second %2))) ; sort by index
         (map first)
         vec)))


(defn initial-field
  "Return initial field.

It's guaranteed that there will be at least one non-zero element."
  []
  (loop []                            ; to prevent field full of zeros
    (let [field (pop-random (take 16 (repeat 0)))]
      (if (not= (set field) #{0})
        field
        (recur)))))


(defonce state
  (r/atom {:points 0
           :field (initial-field)
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
  (flatten
   [(merge-row [a0 a1 a2 a3] :scorer scorer)
    (merge-row [b0 b1 b2 b3] :scorer scorer)
    (merge-row [c0 c1 c2 c3] :scorer scorer)
    (merge-row [d0 d1 d2 d3] :scorer scorer)]))


(defn merge-left
  [field]
  (-> field merge-rows pop-random vec))


(defn merge-left-noscore
  [field]
  (-> field (#(merge-rows % :scorer noop-scorer)) vec))


(defn mutate-state! [event payload]
  (info "Event" event "with payload" payload)

  (case event
    :scored
    (swap! state update-in [:points] #(+ payload %))

    :left
    (swap! state update-in [:field] merge-left)

    :up
    (swap! state update-in [:field] (comp transpose merge-left transpose transpose transpose))

    :down
    (swap! state update-in [:field] (comp transpose transpose transpose merge-left transpose))

    :right
    (swap! state update-in [:field] (comp transpose transpose merge-left transpose transpose))

    :reset
    (swap! state assoc :points 0 :field (initial-field) :lost false)

    (info "Nothing to do for" event))

  ;; checking if there are more moves available
  (let [field (:field @state)]
    (when
        (= 1
           (count
            (set [(merge-left-noscore field)
                  ((comp transpose merge-left-noscore transpose transpose transpose) field)
                  ((comp transpose transpose merge-left-noscore transpose transpose) field)
                  ((comp transpose transpose transpose merge-left-noscore transpose) field)])))
        (swap! state assoc :lost true))))


(go-loop [[event payload] (<! event-queue)]
  (mutate-state! event payload)
  (recur (<! event-queue)))


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
       [:div {:class
              (cons (cell-background cell)
                    [:rounded-md :text-white :text-2xl :font-extrabold :flex :items-center :justify-center :py-2])
              :key (str "cell-" idx)} (if (= 0 cell) "\u00A0" cell)]))]])


(defn controls [dir]
  #(put! event-queue [dir nil]))


(defn main-component []
  [:div {:class "min-h-screen bg-gray-100 py-6 flex flex-col justify-center sm:py-12"}
   [:div {:class "relative py-3 sm:max-w-xl sm:mx-auto"}
    [:div {:class "absolute inset-0 bg-gradient-to-r from-cyan-400 to-light-blue-500 shadow-lg transform -skew-y-6 sm:skew-y-0 sm:-rotate-6 sm:rounded-3xl"}]
    [:div {:class "relative px-4 py-10 bg-white shadow-lg sm:rounded-3xl sm:p-20"}
     [:div {:class "max-w-md mx-auto"}
      [:div {:class "divide-y divide-gray-200"}
       [:p "Points: " (:points @state) " "
        (when (:lost @state) [:span.font-extrabold.text-red-600 "You've lost, loser!"])]

       ;; buttons
       [:div
        [:button {:on-click (controls :left) :class "bg-blue-100 px-2 m-2"} "←"]
        [:button {:on-click (controls :up) :class "bg-blue-100 px-2 m-2"} "↑"]
        [:button {:on-click (controls :down) :class "bg-blue-100 px-2 m-2"} "↓"]
        [:button {:on-click (controls :right) :class "bg-blue-100 px-2 m-2"} "→"]
        [:button {:on-click (controls :reset) :class "bg-blue-100 px-2 m-2"} "reset"]]

       [field (:field @state)]]]]]])


(defn mount [c]
  (r.dom/render [c] (.getElementById js/document "app")))


(defn reload! []
  (mount main-component)
  (print "Hello reload!"))


(defn set-keybindings! []
  (key/bind! "h" ::left (controls :left))
  (key/bind! "left" ::arrow-left (controls :left ))

  (key/bind! "j" ::down (controls :down))
  (key/bind! "down" ::arrow-down (controls :down))

  (key/bind! "k" ::up (controls :up))
  (key/bind! "up" ::arrow-up (controls :up))

  (key/bind! "l" ::right (controls :right))
  (key/bind! "right" ::arrow-right (controls :right)))


(defn main! []
  (mount main-component)
  (set-keybindings!)
  (print "Hello Main"))
