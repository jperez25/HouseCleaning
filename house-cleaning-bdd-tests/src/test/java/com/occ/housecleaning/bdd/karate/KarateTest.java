package com.occ.housecleaning.bdd.karate;

import io.karatelabs.junit6.Karate;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/** Entry point: each Karate scenario appears as its own node in the JUnit tree. */
class KarateTest {

    @TestFactory
    Stream<DynamicNode> karateFeatures() {
        return Karate.run("classpath:features/karate").threads(4).stream();
    }
}
