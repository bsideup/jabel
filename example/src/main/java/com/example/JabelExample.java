package com.example;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class JabelExample {

    public static void main(String[] args) {
        System.out.println(new JabelExample().run(args));
    }

    public String run(String[] args) {
        // Switch Expressions
        // https://openjdk.java.net/jeps/325
        var result = switch (args.length) {
            case 1 -> {
                yield """
                        one...
                        yet pretty long!
                """;
            }
            case 2, 3 -> "two or three";
            default -> new JabelExample().new Inner().innerPublic();
        };
        return result;
    }

    // Project Coin: Allow @SafeVarargs on private methods
    // https://bugs.openjdk.java.net/browse/JDK-7196160
    @SafeVarargs
    private String outerPrivate(List<String>... args) {
        // Look, Ma! No explicit diamond parameter
        var callable = new Callable<>() {

            @Override
            public String call() {
                // Var in lambda parameter
                Function<Object, String> function = (var prefix) -> {
                    // Pattern Matching in instanceof
                    // https://openjdk.java.net/jeps/305
                    if (prefix instanceof String s) {
                        return s + Integer.toString(0);
                    } else {
                        throw new IllegalArgumentException("Expected string!");
                    }
                };
                // Test indy strings
                return function.apply("idk ");
            }
        };

        var closeable = new AutoCloseable() {

            @Override
            public void close() {

            }
        };

        // Project Coin: Allow final or effectively final variables to be used as resources in try-with-resources
        // https://bugs.openjdk.java.net/browse/JDK-7196163
        try (closeable) {
            return callable.call();
        }
    }

    class Inner {

        public String innerPublic() {
            // Test nest-mate
            return outerPrivate();
        }
    }
}
