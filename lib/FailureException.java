package yeti.lang;

public class FailureException extends RuntimeException {
    public FailureException(String what) {
        super(what);
    }
}
