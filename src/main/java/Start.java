import de.nkilders.FI8910WRecorder;
import de.nkilders.FI8910WRecorderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Start {
	private static final Logger LOGGER = LoggerFactory.getLogger(Start.class);

	public static void main(String[] args) throws FI8910WRecorderException {
		if (args.length < 3) {
			LOGGER.error("Arguments: <host> <user> <password>");
			return;
		}

		new FI8910WRecorder(args[0], args[1], args[2]).start();
	}

}
