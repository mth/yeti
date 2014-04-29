package yeti.lang.grammar;

import java.util.Scanner;
import mouse.runtime.*;

public class PEGParser {
    private static void parse(Source src, boolean[] bad) {
        YetiPEG peg = new YetiPEG();
        if (peg.parse(src)) {
            System.out.println("OK");
        } else {
            System.out.println("NOK");
            bad[0] = true;
        }
    }

    public static void main(String[] argv) throws Exception {
        boolean[] bad = { false };
        Source src;
        if (argv.length == 0) {
            parse(new SourceString(
                    new Scanner(System.in).useDelimiter("\\A").next()), bad);
        } else {
            for (int i = 0; i < argv.length; ++i) {
                SourceFile f = new SourceFile(argv[i]);
                if (f.created()) {
                    System.out.print(argv[i] + ": ");
                    parse(f, bad);
                }
            }
        }
        if (bad[0])
            System.exit(1);
    }
}
