import co.paralleluniverse.fibers.Suspendable;

interface BarTestCase16 {
    @Suspendable
    void bar();
}

public class TestCase16 {
    void foo(BarTestCase16 bar) {
        //This should fail since method BarTestCase16#bar is suspendable
        bar.bar();
    }
}