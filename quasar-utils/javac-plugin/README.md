# Overview

This is a `javac` (the java compiler) plugin to ease detection of erroneous quasar usage, particularly calling a suspendable method 
from the body of a non suspendable method (which is a common cause of hard-to-troubleshoot runtime errors).

A Java method is deemed suspendable if 
- it is annotated with the `@Suspendable` annotation
- it is declared to throw `SuspendExecution`
- it is mentioned in any `META-INF/suspendables` file available in the compilation classpath
- it overrides another method mentioned in any `META-INF/suspendables` file available in the compilation classpath

## Usage

Simply apply the `quasar-utils` gradle plugin to your project and activate the java compiler plugin with

```groovy
quasar {
    enableJavacPlugin = true
}
```

From this moment, trying to compile a java source file like 
```java
import co.paralleluniverse.fibers.Suspendable;

interface Bar {
    @Suspendable
    void bar();
}

public class Foo {
    void foo(Bar bar) {
        bar.bar();
    }
}
```

will fail with

```
/home/user/code/sample-project/src/main/java/Foo.java:10: error: Invocation of suspendable method from non suspendable method
        bar.bar();
           ^
```

### Build

This plugin uses internal compiler API (it would be great to just rely on the public API, but the offered 
functionality is simply not enough) which is not guaranteed to be stable across Java version bumps.
there have been indeed small changes between version 8 and version 11 that are dealt with in 
`net.corda.plugins.javac.quasar.ASTUtils`. 
Despite this, this project builds (or at least it is supposed to) on both JDK 8 and JDK 11, 
the generated artifact only contains Java 8 bytecode and works on both JDK 8 and JDK 11 compiler 
(and hopefully even future Java compiler versions, but who knows).

### Supported compilers

This plugin has been successfully tested with

- Zulu 8
- Corretto 8
- OpenJdk 8 (OpenJ9)
- OpenJdk 8 (Hotspot)
- Corretto 11
- OpenJdk 11 (OpenJ9)
- OpenJdk 11 (Hotspot)
