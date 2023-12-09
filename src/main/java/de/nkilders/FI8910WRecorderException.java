package de.nkilders;

public class FI8910WRecorderException extends Exception {

    public FI8910WRecorderException(String message) {
        this(message, null);
    }

    public FI8910WRecorderException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
