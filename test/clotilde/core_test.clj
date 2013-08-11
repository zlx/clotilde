(ns clotilde.core-test
  (:use clojure.test
        clotilde.core
        clotilde.innards
        [matchure :only (when-match)]))

(defn safe-ops
  [time-out & exprs]
  (deref (future exprs) time-out nil))

(defn safe-op
  [time-out expr]
  (deref (future expr) time-out nil))

(deftest test-tools
  (testing safe-ops "safe-ops"
           (is (= '(nil) (safe-ops 10 (Thread/sleep 100))))
           (is (= '(nil :ok) (safe-ops 100 (Thread/sleep 10) :ok))))
  (testing safe-op "safe-op"
           (is (= nil (safe-op 10 (Thread/sleep 100))))
           (is (= :ok (safe-op 10 :ok)))))

(deftest test-initialize!
  (testing "New empty space."
           (initialize!)
           (is (= [] @-space) "Empty space?")
           (is (= [] @-waitq) "Empty queue?"))
  (testing "Clearing space and queue."
           (out! :x)
           (future (rd! :y))
           (Thread/sleep 100)
           (dosync 
             (is (= 1 (count @-space)) "Space not empty?")
             (is (= 1 (count @-waitq)) "Queue not empty?"))
           (initialize!)
           (dosync 
             (is (= [] @-space) "Emptied space?")
             (is (= [] @-waitq) "Emptied queue?"))))

(deftest test-out!
  (testing "(out! :x)."
           (initialize!)
           (out! :x)
           (dosync
             (is (= [[:x]] @-space) "Space contains [:x]?")))
  (testing "N-times (out! :x)"
           (initialize!)
           (out! :x)
           (out! :x)
           (dosync
             (is (= [[:x] [:x]] @-space) "Space contains [:x] twice?")))
  (testing "(out! (+ 1 2) (+ 2 3)"
           (initialize!)
           (out! (+ 1 2) (+ 2 3))
           (dosync
             (is (= [[3 5]] @-space) "Space contains [3 5]?"))))

(deftest test-eval!
  (testing "(eval! expr)"
           (initialize!)
           (eval! (+ 1 1))
           (Thread/sleep 100)
           (dosync
             (is (= [[2]] @-space) "Space contains [2]?")))
  (testing "(eval! expr1 expr2 :x)"
           (initialize!)
           (eval! 
               (+ 1 1)
               (+ 1 2)
               :x)
           (Thread/sleep 100)
           (dosync
             (is (= [[2 3 :x]] @-space) "Space contains [2 3 :x]?")))
  (testing "Evaluating N * (eval! expr1 expr2 :value) places N * [expr1-result expr2-result :value] into space."
           (initialize!)
           (eval! 
               (+ 1 1)
               (+ 1 2)
               :x)
           (eval! 
               (+ 1 1)
               (+ 1 2)
               :y)
           (eval! 
               (+ 1 1)
               (+ 1 2)
               :z)
           (Thread/sleep 100)
           (dosync
             (is (= [[2 3 :x] [2 3 :y] [2 3 :z]] @-space))))
  (testing "(eval! (out! (+ 1 0) (+ 1 1)) :x :y :z)"
           (initialize!)
           (eval! (out! (+ 1 0) (+ 1 1)) :x :y :z)
           (dosync
             (is (= [[1 2] [nil :x :y :z]] @-space)))))

(deftest test-match
  (testing ""
           ))

#_(deftest test-rd!
  (testing "(out! :x) then (rd! :x)."
           (initialize!)
           (out! :z)
           (out! :y)
           (out! :x)
           (is (= [:x] (safe-op (rd! :x))) "(rd! :x) evals to [:x]?")
           (is (= [[:z] [:y] [:x]] @-space) "Space still contains [:x] after (rd! :x)?"))
  (testing "Evaluating (eval! (rd! :x) :done) blocks untill (out! :x) is evaluated."
           (initialize!)
           (eval! (rd! :x) :done)
           (Thread/sleep 100)
           (dosync
             (is (= [] @-space) "Space is still empty because (rd! :x) blocks?")
             (is (= 1 (count @-waitq)) "Wait queue contains one element?"))
           (out! :z)
           (out! :y)
           (out! :x)
           (Thread/sleep 100)
           (dosync
             (is (= [[:z] [:y] [:x] [[:x] :done]] @-space) "Space contains [:x] (not removed by rd!) and [[:x] :done]?")
             (is (= [] @-waitq) "Wait queue is empty?"))))

#_(deftest test-in! 
  (testing "(out! :x) then (in! :x)."
           (initialize!)
           (out! :x)
           (is (= [:x] (safe-op (in! :x))) "(in! :x) evals to [:x]?")
           (is (= [] @-space) "Space is empty after (in! :x)?"))
    (testing "Evaluating (eval! (in! :x) :done) blocks untill (out! :x) is evaluated."
           (initialize!)
           (eval! (in! :x) :done)
           (Thread/sleep 100)
           (dosync
             (is (= [] @-space) "Space doesn't contain [:x :done] yet?")
             (is (= 1 (count @-waitq)) "Wait queue contains one element?"))
           (out! :x)
           (Thread/sleep 100)
           (dosync
             (is (= [[:z] [:y] [[:x] :done]] @-space) "Space contains only [:x :done] since in! has removed [:x]?")
             (is (= [] @-waitq) "Wait queue is empty?"))))

#_(deftest test-tuple-ops
  (testing "(out! & args)"
           (initialize!)
           (out! :x :y :z)
           (is (= [[:x :y :z]] @-space) "Space contains a tuple in the form [:x :y :z]?"))
  (testing "(in! & args)"
           (initialize!)
           (out! :x :y :y)
           (out! :x :y :z)
           (is (= [:x :y :z] (safe-op (in! :x :y :z))) "in! evals to [:x :y :z]?"))
  (testing "(rd! & args)"
           (initialize!)
           (out! :x :y :y)
           (out! :x :y :z)
           (is (= [:x :y :z] (safe-op (rd! :x :y :z))) "in! evals to [:x :y :z]?")))

#_(deftest test-wildcards
  (testing "Pattern with _ wildcards"
           (initialize!)
           (out! [:y 1 true])           
           (out! [:x 1 true])           
           (is (= [:x 1 true] (rd! :x _ _)) "Matched (rd! :x _ _)?")))

