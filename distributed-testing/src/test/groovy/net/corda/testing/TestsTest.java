package net.corda.testing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

public class TestsTest {
    @Test
    public void read() {
        final Tests tests = new Tests();
        Assertions.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n";
        tests.addTests(Tests.read(new StringReader(s)));

        Assertions.assertFalse(tests.isEmpty());
        Assertions.assertEquals((long) tests.getDuration("hello"), 100);
    }

    @Test
    public void write() {
        final StringWriter writer = new StringWriter();
        final Tests tests = new Tests();
        Assertions.assertTrue(tests.isEmpty());
        tests.addDuration("hello", 100);
        tests.write(writer);
        Assertions.assertFalse(tests.isEmpty());

        final StringReader reader = new StringReader(writer.toString());
        final Tests otherTests = new Tests();
        otherTests.addTests(Tests.read(reader));

        Assertions.assertFalse(tests.isEmpty());
        Assertions.assertFalse(otherTests.isEmpty());
        Assertions.assertEquals(tests.size(), otherTests.size());
        Assertions.assertEquals(tests.getDuration("hello"), otherTests.getDuration("hello"));
    }

    @Test
    public void addingTestChangesMeanDuration() {
        final Tests tests = new Tests();
        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n";
        tests.addTests(Tests.read(new StringReader(s)));

        Assertions.assertFalse(tests.isEmpty());
        // 400 total for 4 tests
        Assertions.assertEquals((long) tests.getDuration("hello"), 100);

        // 1000 total for 5 tests = 200 mean
        tests.addDuration("hello", 600);
        Assertions.assertEquals((long) tests.getDuration("hello"), 200);
    }

    @Test
    public void addTests() {
        final Tests tests = new Tests();
        Assertions.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n"
                + "goodbye,200,4\n";

        tests.addTests(Tests.read(new StringReader(s)));
        Assertions.assertFalse(tests.isEmpty());
        Assertions.assertEquals(tests.size(), 2);
    }

    @Test
    public void getDuration() {
        final Tests tests = new Tests();
        Assertions.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n"
                + "goodbye,200,4\n";

        tests.addTests(Tests.read(new StringReader(s)));
        Assertions.assertFalse(tests.isEmpty());
        Assertions.assertEquals(tests.size(), 2);

        Assertions.assertEquals(100L, tests.getDuration("hello"));
        Assertions.assertEquals(200L, tests.getDuration("goodbye"));
    }

    @Test
    public void addTestInfo() {
        final Tests tests = new Tests();
        Assertions.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n"
                + "goodbye,200,4\n";

        tests.addTests(Tests.read(new StringReader(s)));
        Assertions.assertFalse(tests.isEmpty());
        Assertions.assertEquals(2, tests.size());

        tests.addDuration("foo", 55);
        tests.addDuration("bar", 33);
        Assertions.assertEquals(4, tests.size());

        tests.addDuration("bar", 56);
        Assertions.assertEquals(4, tests.size());
    }

    @Test
    public void addingNewDurationUpdatesRunCount() {

        final Tests tests = new Tests();
        Assertions.assertTrue(tests.isEmpty());

        final String s = Tests.TEST_NAME + "," + Tests.MEAN_DURATION_NANOS + "," + Tests.NUMBER_OF_RUNS + '\n'
                + "hello,100,4\n"
                + "goodbye,200,4\n";

        tests.addTests(Tests.read(new StringReader(s)));
        Assertions.assertFalse(tests.isEmpty());
        Assertions.assertEquals(2, tests.size());

        tests.addDuration("foo", 55);

        Assertions.assertEquals(0, tests.getRunCount("bar"));

        tests.addDuration("bar", 33);
        Assertions.assertEquals(4, tests.size());

        tests.addDuration("bar", 56);
        Assertions.assertEquals(2, tests.getRunCount("bar"));
        Assertions.assertEquals(4, tests.size());

        tests.addDuration("bar", 56);
        tests.addDuration("bar", 56);
        Assertions.assertEquals(4, tests.getRunCount("bar"));

        Assertions.assertEquals(4, tests.getRunCount("hello"));
        tests.addDuration("hello", 22);
        tests.addDuration("hello", 22);
        tests.addDuration("hello", 22);
        Assertions.assertEquals(7, tests.getRunCount("hello"));
    }
}