package com.example;

public class JabelExample {
    public static void main(String[] args) {
        System.out.println(new JabelExample().run(args));
    }

    public String run(String[] args) {
        var result = switch (args.length) {
            case 1 -> {
                break "one";
            }
            case 2, 3 -> "two or three";
            default -> new JabelExample().new Inner().innerPublic();
        };
        return result;
    }

    private String outerPrivate() {
        // Test indy strings
        return "idk " + Integer.toString(0);
    }

    class Inner {

        public String innerPublic() {
            // Test nest-mate
            return outerPrivate();
        }
    }
}
