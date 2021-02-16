import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class TestCase17 {

    //This is a custom annotation type that will be used as a suspendable marker
    @Target(ElementType.METHOD)
    @interface CustomSuspendableMarker {}

    @CustomSuspendableMarker
    void bar() {}

    void foo() {
        //This should fail because method BarTestCase17#bar is suspendable
        //since it is annotated with CustomSuspendableMarker
        bar();
    }
}