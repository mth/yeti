package yeti.lang.grammar;

import java.util.Scanner;
import mouse.runtime.*;

public class Parser {
    public static void main(String[] argv) throws Exception {
        Source src;
        if (argv.length == 0) {
            src = new SourceString(
                    new Scanner(System.in).useDelimiter("\\A").next());
        } else {
            src = new SourceFile(argv[0]);
        }
        YetiPEG peg = new YetiPEG();
        if (peg.parse(src)) {
            System.out.println("OK");
        } else {
            System.err.println("NOK");
        }
    }
}
