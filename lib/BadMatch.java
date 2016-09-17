package yeti.lang;

/**
 * Exception thrown when case of cannot match the given value.
 */
public class BadMatch extends IllegalArgumentException {
    public final Object value;
    private final String source;
    private final int line;
    private final int column;

    /**
     * Constructs the bad match exception.
     * @param match Mismatched value
     * @param source Source file name (null - unknown)
     * @param line Source line number (&lt; 0 - unknown) 
     * @param column Source column number (&lt; 0 - unknown)
     */
    public BadMatch(Object match, String source, int line, int column) {
        super(msg(match, source, line, column));
        this.source = source;
        this.line = line;
        this.column = column;
        this.value = match;
    }

    /**
     * Returns the source file name.
     * @param location Out parameter for error location in the source file.
     *                 First array element is assigned error line number
     *                 and second element is assigned the column number.
     */
    public String getSource(int[] location) {
        if (location != null) {
            location[0] = line;
            location[1] = column;
        }
        return source;
    }

    private static String msg(Object match, String source, int line, int col) {
        StringBuffer buf = new StringBuffer();
        if (source != null) {
            buf.append(source).append(':');
        }
        if (line > 0) {
            buf.append(line).append(':');
            if (col > 0) {
                buf.append(col).append(':');
            }
        }
        if (buf.length() > 0) {
            buf.append(':');
        }
        return buf.append("bad match (").append(match).append(')').toString();
    }
}
