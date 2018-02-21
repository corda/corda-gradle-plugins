package net.corda.example;

public class HasVisibleMethod {
    @InvisibleAnnotation
    public void hasInvisibleAnnotation() {
        System.out.println("VISIBLE METHOD, INVISIBLE ANNOTATION");
    }
}