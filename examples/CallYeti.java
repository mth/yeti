import yeti.lang.io;

public class CallYeti {
	public static void main(String[] args) {
		// used the static field
		io.println.apply("Yeti!");
	}

	static {
		// ensure that the module is initialised
		io.eval();
	}
}
