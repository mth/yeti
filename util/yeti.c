#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char** argv) {
	char *jar, **args;
	jar = getenv("YETI_JAR");
	if (!jar) {
		jar = "/usr/share/yeti/yeti.jar";
	}
	args = malloc((argc + 3) * sizeof (char*));
	args[0] = "java";
	args[1] = "-jar";
	args[2] = jar;
	memcpy(args + 3, argv + 1, argc * sizeof (char*));
	execvp(args[0], args);
	perror(args[0]);
	return 1;
}
