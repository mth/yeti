#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <termios.h>
#include <unistd.h>

int main(int argc, char** argv) {
	struct termios term;
	struct stat statbuf;
	char *jar, **args;
	int n = 0;
	jar = getenv("YETI_JAR");
	if (!jar)
		jar = "/usr/share/yeti/yeti.jar";
	args = malloc((argc + 3) * sizeof (char*));
	if (!tcgetattr(1, &term) && !tcgetattr(0, &term) &&
	    !stat("/usr/bin/rlwrap", &statbuf) && (statbuf.st_mode & 0700))
		args[n++] = "rlwrap";
	args[n++] = "java";
	args[n++] = "-jar";
	args[n++] = jar;
	memcpy(args + n, argv + 1, argc * sizeof (char*));
	execvp(args[0], args);
	perror(args[0]);
	return 1;
}
