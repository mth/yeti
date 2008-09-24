package yeti.lang;

public class ExitError extends Error {
    private int exitCode;

    public ExitError(int exitCode) {
        super("sysExit " + exitCode);
    	this.exitCode = exitCode;
    }

    public int getExitCode() {
    	return exitCode;
    }
}
