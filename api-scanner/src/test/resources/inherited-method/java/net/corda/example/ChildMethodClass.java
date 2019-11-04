package net.corda.example;

public class ChildMethodClass extends ParentMethodClass {
    public int getChildNumber() {
        return 101;
    }

    protected static String geChildName() {
        return ChildMethodClass.class.getName();
    }
}
