(benchmark abmixed2.smt
:source {
Test formula which needs AB-mixed interpolators
Desired Interpolant: (and (<= y x1) (implies (>= y x1) (< (f x1) x2)))
}
:status unsat
:difficulty { 0 }
:logic AUFLIRA
:extrafuns ((a1 Real))
:extrafuns ((a2 Real))
:extrafuns ((b1 Real))
:extrafuns ((b2 Real))
:extrafuns ((x1 Real))
:extrafuns ((x2 Real))
:extrafuns ((y Real))
:extrafuns ((f Real Real Real))
:assumption
(and (<= a1 x1) (and (= (f a1 (+ a1 5.0)) a2) (and (<= y a1) (< a2 x2))))
:formula
(and (<= x1 (- b1 12.0)) (and (= (f (- b1 12.0) (- b1 7.0)) b2) (and (<= (- b1 12.0) y) (< x2 b2)))))
