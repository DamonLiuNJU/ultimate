int nondet() { int a; return a; }
_Bool nondet_bool() { _Bool a; return a; }
int main() {
int v1 = nondet();
int v2 = nondet();
int v3 = nondet();
int v4 = nondet();
int v5 = nondet();
goto loc11;
loc11:
 if (nondet_bool()) {
  goto loc10;
 }
 goto end;
loc2:
 if (nondet_bool()) {
  goto loc4;
 }
 goto end;
loc3:
 if (nondet_bool()) {
  goto loc0;
 }
 goto end;
loc6:
 if (nondet_bool()) {
  goto loc7;
 }
 goto end;
loc0:
 if (nondet_bool()) {
  if (!( v1 <= 0 )) goto end;
  goto loc1;
 }
 if (nondet_bool()) {
  if (!( 1 <= v1 )) goto end;
  v1 = -1+v1;
  v2 = 1+v2;
  goto loc2;
 }
 goto end;
loc5:
 if (nondet_bool()) {
  v3 = -1+v3;
  goto loc6;
 }
 goto end;
loc8:
 if (nondet_bool()) {
  v1 = -1+v1;
  v2 = 1+v2;
  goto loc5;
 }
 goto end;
loc9:
 if (nondet_bool()) {
  if (!( v5 <= 0 )) goto end;
  if (!( 0 <= v5 )) goto end;
  goto loc5;
 }
 if (nondet_bool()) {
  if (!( 1 <= v5 )) goto end;
  goto loc8;
 }
 if (nondet_bool()) {
  if (!( 1+v5 <= 0 )) goto end;
  goto loc8;
 }
 goto end;
loc7:
 if (nondet_bool()) {
  if (!( v3 <= 0 )) goto end;
  goto loc2;
 }
 if (nondet_bool()) {
  if (!( 1 <= v3 )) goto end;
  v5 = nondet();
  goto loc9;
 }
 goto end;
loc4:
 if (nondet_bool()) {
  if (!( v2 <= 0 )) goto end;
  goto loc3;
 }
 if (nondet_bool()) {
  if (!( 1 <= v2 )) goto end;
  v2 = -1+v2;
  v3 = -1+v1;
  goto loc6;
 }
 goto end;
loc10:
 if (nondet_bool()) {
  v1 = v4;
  v2 = 0;
  goto loc3;
 }
 goto end;
loc1:
end:
;
}
