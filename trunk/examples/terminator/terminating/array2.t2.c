int nondet() { int a; return a; }
_Bool nondet_bool() { _Bool a; return a; }
int main() {
int v1 = nondet();
int v2 = nondet();
goto loc4;
loc4:
 if (nondet_bool()) {
  goto loc3;
 }
 goto end;
loc2:
 if (nondet_bool()) {
  goto loc0;
 }
 goto end;
loc0:
 if (nondet_bool()) {
  if (!( 50 <= v1 )) goto end;
  goto loc1;
 }
 if (nondet_bool()) {
  if (!( 1+v1 <= 50 )) goto end;
  v1 = 1+v1;
  goto loc2;
 }
 goto end;
loc3:
 if (nondet_bool()) {
  v1 = 0;
  v2 = nondet();
  v1 = 0;
  goto loc2;
 }
 goto end;
loc1:
end:
;
}
