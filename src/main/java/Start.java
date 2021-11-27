import de.nkilders.FI8910WRecorder;

public class Start {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Arguments: <host> <user> <password>");
            return;
        }

        new FI8910WRecorder(args[0], args[1], args[2]);
    }

}