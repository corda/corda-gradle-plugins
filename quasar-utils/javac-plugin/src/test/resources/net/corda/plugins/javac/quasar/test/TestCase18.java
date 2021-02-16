public class TestCase18 {

    //This is a custom exception type that will be used as a suspendable marker
    class CustomSuspendableThrowable extends Exception {}

    void bar() throws CustomSuspendableThrowable {}

    void foo() {
        try {
            //This should fail because method BarTestCase18#bar is suspendable
            //since it throws CustomSuspendableThrowable
            bar();
        } catch(CustomSuspendableThrowable e) {
        }
    }
}