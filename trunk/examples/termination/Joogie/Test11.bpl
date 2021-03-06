type ref;
type realVar;
type classConst;
// type Field x;
// var $HeapVar : <x>[ref, Field x]x;

const unique $null : ref ;
const unique $intArrNull : [int]int ;
const unique $realArrNull : [int]realVar ;
const unique $refArrNull : [int]ref ;

const unique $arrSizeIdx : int;
var $intArrSize : [int]int;
var $realArrSize : [realVar]int;
var $refArrSize : [ref]int;

var $stringSize : [ref]int;

//built-in axioms 
axiom ($arrSizeIdx == -1);

//note: new version doesn't put helpers in the perlude anymore//Prelude finished 



var java.lang.String$lp$$rp$$Random$args254 : [int]ref;
var int$Random$index0 : int;


// procedure is generated by joogie.
function {:inline true} $neref(x : ref, y : ref) returns (__ret : int) {
if (x != y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $realarrtoref($param00 : [int]realVar) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $modreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $leref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $modint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $gtref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqrealarray($param00 : [int]realVar, $param11 : [int]realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $addint(x : int, y : int) returns (__ret : int) {
(x + y)
}


// procedure is generated by joogie.
function {:inline true} $subref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $inttoreal($param00 : int) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $shrint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $negReal($param00 : realVar) returns (__ret : int);



	 //  @line: 5
// <Random: int random()>
procedure int$Random$random$2231() returns (__ret : int)
  modifies $stringSize, int$Random$index0;
 {
var $i422 : int;
var $i321 : int;
var $i016 : int;
var $i117 : int;
var $r219 : [int]ref;
var r020 : ref;
var $i523 : int;
var $i218 : int;
var $r115 : [int]ref;
	 //  @line: 6
Block29:
	 //  @line: 6
	$i117 := int$Random$index0;
	 //  @line: 6
	$r115 := java.lang.String$lp$$rp$$Random$args254;
	 //  @line: 6
	$i016 := $refArrSize[$r115[$arrSizeIdx]];
	 goto Block30;
	 //  @line: 6
Block30:
	 goto Block31, Block33;
	 //  @line: 6
Block31:
	 assume ($ltint(($i117), ($i016))==1);
	 goto Block32;
	 //  @line: 6
Block33:
	 //  @line: 6
	 assume ($negInt(($ltint(($i117), ($i016))))==1);
	 //  @line: 7
	__ret := 0;
	 return;
	 //  @line: 9
Block32:
	 //  @line: 9
	$r219 := java.lang.String$lp$$rp$$Random$args254;
	 goto Block34;
	 //  @line: 9
Block34:
	 //  @line: 9
	$i218 := int$Random$index0;
	 assert ($geint(($i218), (0))==1);
	 assert ($ltint(($i218), ($refArrSize[$r219[$arrSizeIdx]]))==1);
	 //  @line: 9
	r020 := $r219[$i218];
	 //  @line: 10
	$i321 := int$Random$index0;
	 //  @line: 10
	$i422 := $addint(($i321), (1));
	 //  @line: 10
	int$Random$index0 := $i422;
	$i523 := $stringSize[r020];
	 //  @line: 11
	__ret := $i523;
	 return;
}


// procedure is generated by joogie.
function {:inline true} $ushrint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $refarrtoref($param00 : [int]ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $divref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $mulref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $neint(x : int, y : int) returns (__ret : int) {
if (x != y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $ltreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $reftorefarr($param00 : ref) returns (__ret : [int]ref);



// procedure is generated by joogie.
function {:inline true} $gtint(x : int, y : int) returns (__ret : int) {
if (x > y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $reftoint($param00 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $addref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $xorreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $andref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $cmpreal(x : realVar, y : realVar) returns (__ret : int) {
if ($ltreal((x), (y)) == 1) then 1 else if ($eqreal((x), (y)) == 1) then 0 else -1
}


// procedure is generated by joogie.
function {:inline true} $addreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $gtreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqreal(x : realVar, y : realVar) returns (__ret : int) {
if (x == y) then 1 else 0
}


	 //  @line: 2
// <Random: void <clinit>()>
procedure void$Random$$la$clinit$ra$$2232()
  modifies int$Random$index0;
 {
	 //  @line: 3
Block35:
	 //  @line: 3
	int$Random$index0 := 0;
	 return;
}


// procedure is generated by joogie.
function {:inline true} $ltint(x : int, y : int) returns (__ret : int) {
if (x < y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $newvariable($param00 : int) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $divint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $geint(x : int, y : int) returns (__ret : int) {
if (x >= y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $mulint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $leint(x : int, y : int) returns (__ret : int) {
if (x <= y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $shlref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqrefarray($param00 : [int]ref, $param11 : [int]ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $reftointarr($param00 : ref) returns (__ret : [int]int);



// procedure is generated by joogie.
function {:inline true} $ltref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $mulreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $shrref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $ushrreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $shrreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $divreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $orint($param00 : int, $param11 : int) returns (__ret : int);



	 //  @line: 2
// <Test11: void main(java.lang.String[])>
procedure void$Test11$main$2229($param_0 : [int]ref)
  modifies $stringSize, java.lang.String$lp$$rp$$Random$args254;
 {
var r02 : [int]ref;
var $i510 : int;
var $i49 : int;
var $i15 : int;
var $i38 : int;
var i813 : int;
var $i26 : int;
var $i611 : int;
var $i03 : int;
var i712 : int;
Block17:
	r02 := $param_0;
	 //  @line: 3
	java.lang.String$lp$$rp$$Random$args254 := r02;
	 //  @line: 5
	$i03 := $refArrSize[r02[$arrSizeIdx]];
	 //  @line: 5
	i712 := $mulint(($i03), (100));
	 //  @line: 5
	$i15 := $refArrSize[r02[$arrSizeIdx]];
	 //  @line: 5
	$i26 := $mulint(($i15), (200));
	 assert ($neint((13), (0))==1);
	 //  @line: 5
	i813 := $divint(($i26), (13));
	 goto Block18;
	 //  @line: 7
Block18:
	 //  @line: 7
	$i38 := $addint((i712), (i813));
	 goto Block19;
	 //  @line: 7
Block19:
	 goto Block22, Block20;
	 //  @line: 7
Block22:
	 //  @line: 7
	 assume ($negInt(($leint(($i38), (0))))==1);
	 //  @line: 8
	 call $i49 := int$Random$random$2231();
	 //  @line: 8
	 call $i510 := int$Random$random$2231();
	 //  @line: 8
	$i611 := $mulint(($i49), ($i510));
	 goto Block23;
	 //  @line: 7
Block20:
	 assume ($leint(($i38), (0))==1);
	 goto Block21;
	 //  @line: 8
Block23:
	 goto Block26, Block24;
	 //  @line: 13
Block21:
	 return;
	 //  @line: 8
Block26:
	 //  @line: 8
	 assume ($negInt(($leint(($i611), (9))))==1);
	 //  @line: 9
	i712 := $addint((i712), (-1));
	 goto Block18;
	 //  @line: 8
Block24:
	 assume ($leint(($i611), (9))==1);
	 goto Block25;
	 //  @line: 11
Block25:
	 //  @line: 11
	i813 := $addint((i813), (-1));
	 goto Block27;
	 //  @line: 11
Block27:
	 goto Block18;
}


// procedure is generated by joogie.
function {:inline true} $reftorealarr($param00 : ref) returns (__ret : [int]realVar);



// procedure is generated by joogie.
function {:inline true} $cmpref(x : ref, y : ref) returns (__ret : int) {
if ($ltref((x), (y)) == 1) then 1 else if ($eqref((x), (y)) == 1) then 0 else -1
}


// procedure is generated by joogie.
function {:inline true} $realtoint($param00 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $geref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $orreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqint(x : int, y : int) returns (__ret : int) {
if (x == y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $ushrref($param00 : ref, $param11 : ref) returns (__ret : int);



// <Random: void <init>()>
procedure void$Random$$la$init$ra$$2230(__this : ref)  requires ($neref((__this), ($null))==1);
 {
var r014 : ref;
Block28:
	r014 := __this;
	 assert ($neref((r014), ($null))==1);
	 //  @line: 1
	 call void$java.lang.Object$$la$init$ra$$28((r014));
	 return;
}


// procedure is generated by joogie.
function {:inline true} $modref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $eqintarray($param00 : [int]int, $param11 : [int]int) returns (__ret : int);



// <java.lang.String: int length()>
procedure int$java.lang.String$length$59(__this : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $negRef($param00 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $lereal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $nereal(x : realVar, y : realVar) returns (__ret : int) {
if (x != y) then 1 else 0
}


// <Test11: void <init>()>
procedure void$Test11$$la$init$ra$$2228(__this : ref)  requires ($neref((__this), ($null))==1);
 {
var r01 : ref;
Block16:
	r01 := __this;
	 assert ($neref((r01), ($null))==1);
	 //  @line: 1
	 call void$java.lang.Object$$la$init$ra$$28((r01));
	 return;
}


// <java.lang.Object: void <init>()>
procedure void$java.lang.Object$$la$init$ra$$28(__this : ref);



// procedure is generated by joogie.
function {:inline true} $instanceof($param00 : ref, $param11 : classConst) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $xorref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $orref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $intarrtoref($param00 : [int]int) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $subreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $shlreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $negInt(x : int) returns (__ret : int) {
if (x == 0) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $gereal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqref(x : ref, y : ref) returns (__ret : int) {
if (x == y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $cmpint(x : int, y : int) returns (__ret : int) {
if (x < y) then 1 else if (x == y) then 0 else -1
}


// procedure is generated by joogie.
function {:inline true} $andint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $andreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $shlint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $xorint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $subint(x : int, y : int) returns (__ret : int) {
(x - y)
}


