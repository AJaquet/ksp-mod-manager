package llorx.kspModManager;

import java.io.FileOutputStream;
import java.io.PrintStream;

public class ErrorLog {
	static void log(Throwable e) {
		try {
			PrintStream ps = new PrintStream(new FileOutputStream("errors.txt", true));
			e.printStackTrace(ps);
		} catch (Throwable ee) {
		}
	}
}