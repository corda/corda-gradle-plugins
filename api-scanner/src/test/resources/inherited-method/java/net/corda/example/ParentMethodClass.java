package net.corda.example;

public class ParentMethodClass {
    public String getParentMessage() {
        return "Hello World!";
    }

    protected static String getParentName() {
        return ParentMethodClass.class.getName();
    }
}
