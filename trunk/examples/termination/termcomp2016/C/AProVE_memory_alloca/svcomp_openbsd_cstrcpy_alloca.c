//#termcomp16-someonesaidyes
#include <stdlib.h>

extern int __VERIFIER_nondet_int(void);

char *
cstrcpy(char *to, const char *from)
{
	char *save = to;

	for (; (*to = *from) != '\0'; ++from, ++to);
	return(save);
}

int main() {
  int length1 = __VERIFIER_nondet_int();
  int length2 = __VERIFIER_nondet_int();
  if (length1 < 1) {
      length1 = 1;
  }
  if (length2 < 1) {
      length2 = 1;
  }
  if (length1 < length2) return 0;
  char* nondetArea = (char*) alloca(length1 * sizeof(char));
  char* nondetString = (char*) alloca(length2 * sizeof(char));
  nondetString[length2-1] = '\0';
  cstrcpy(nondetArea,nondetString);
  return 0;
}
  
