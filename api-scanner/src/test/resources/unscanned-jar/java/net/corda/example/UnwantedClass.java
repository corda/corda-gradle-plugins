package net.corda.example;

// Dummy class so that there is something that the API Scanner shouldn't be scanning.
public class UnwantedClass {
    private final String message;

    public UnwantedClass(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
