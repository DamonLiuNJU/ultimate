int nondet() { int a; return a; }
_Bool nondet_bool() { _Bool a; return a; }
int main() {
int v1 = nondet();
int v2 = nondet();
int v3 = nondet();
int v4 = nondet();
int v5 = nondet();
goto loc6;
loc6:
 if (nondet_bool()) {
  goto loc5;
 }
 goto end;
loc0:
 if (nondet_bool()) {
  goto loc1;
 }
 goto end;
loc4:
 if (nondet_bool()) {
  goto loc2;
 }
 goto end;
loc2:
 if (nondet_bool()) {
  if (!( 10 <= v2 )) goto end;
  goto loc3;
 }
 if (nondet_bool()) {
  if (!( 1+v2 <= 10 )) goto end;
  v2 = 1+v2;
  goto loc4;
 }
 goto end;
loc1:
 if (nondet_bool()) {
  if (!( 10 <= v1 )) goto end;
  v5 = nondet();
  v2 = 0;
  goto loc4;
 }
 if (nondet_bool()) {
  if (!( 1+v1 <= 10 )) goto end;
  v1 = 1+v1;
  goto loc0;
 }
 goto end;
loc5:
 if (nondet_bool()) {
  v3 = nondet();
  v4 = nondet();
  v1 = 0;
  goto loc0;
 }
 goto end;
loc3:
end:
;
}
