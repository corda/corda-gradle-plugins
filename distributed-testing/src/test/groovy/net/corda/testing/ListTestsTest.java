package net.corda.testing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ListTestsTest {

    @Test
    public void shouldAllocateTests() {

        for (int numberOfTests = 0; numberOfTests < 100; numberOfTests++) {
            for (int numberOfForks = 1; numberOfForks < 100; numberOfForks++) {


                List<String> tests = IntStream.range(0, numberOfTests).boxed()
                        .map(integer -> "Test.method" + integer.toString())
                        .collect(Collectors.toList());
                ListShufflerAndAllocator testLister = new ListShufflerAndAllocator(tests);

                List<String> listOfLists = new ArrayList<>();
                for (int fork = 0; fork < numberOfForks; fork++) {
                    listOfLists.addAll(testLister.getTestsForFork(fork, numberOfForks, 0));
                }

                Assertions.assertEquals(listOfLists.size(), tests.size());
                Assertions.assertEquals(new HashSet<>(listOfLists).size(), tests.size());
                Assertions.assertEquals(listOfLists.stream().sorted().collect(Collectors.toList()), tests.stream().sorted().collect(Collectors.toList()));
            }
        }

    }

}
