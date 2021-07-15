package com.example.library;

import java.util.function.Function;

public interface ExternalLibrary extends Function<String, String> {
    @Override
    String apply(String data);
}
