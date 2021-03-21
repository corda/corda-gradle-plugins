package net.corda.gradle;

public abstract sealed class WantedSealedClass permits WantedSubclass, UnwantedSubclass {
}
