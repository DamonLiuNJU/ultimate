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
  if (!( v2 <= 0 )) goto end;
  v1 = 1;
  goto loc1;
 }
 if (nondet_bool()) {
  if (!( 1 <= v2 )) goto end;
  v2 = -1+v2;
  goto loc2;
 }
 goto end;
loc3:
 if (nondet_bool()) {
  v1 = 0;
  goto loc2;
 }
 goto end;
loc1:
end:
;
}
