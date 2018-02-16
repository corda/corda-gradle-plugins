package net.corda.example;

@Visible
public class WithVisibleAnnotation {
    @Visible
    public void hasVisibleAnnotation() {
        System.out.println("VISIBLE ANNOTATION");
    }
}