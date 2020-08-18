package com.example;

import org.junit.Test;

import static org.junit.Assert.*;

public class JabelExampleTest {

    @Test(expected = ClassNotFoundException.class)
    public void isProbablyJava8() throws Exception {
        Class.forName("java.lang.StackWalker");
    }

    @Test
    public void testRuntimeIsJava8() {
        String jver = System.getProperty("java.version");
        String needle = "1.8";
        assertTrue("Java version expected: " + needle + " actual: " + jver, jver.startsWith(needle));
    }

    @Test
    public void testPostJava8Features() {
        JabelExample jabelExample = new JabelExample();

        String result = jabelExample.run(new String[0]);

        assertTrue("'" + result + "' should start with 'idk '", result.startsWith("idk "));
    }

    @Test
    public void testJava13PreviewFeatureTextBlocksJep355(){
        String TEXT_BLOCK_JSON = """
{
    "name" : "Baeldung",
    "website" : "https://www.%s.com/"
}
""";
    }

    /**
     * JEP 354: Switch Expressions (Second Preview)
     * https://openjdk.java.net/jeps/354
     */
    @Test
    public void testJava13PreviewJep354() {
        var me = 4;
        var operation = "squareMe";
        var result = switch (operation) {
            case "doubleMe" -> {
                yield me * 2;
            }
            case "squareMe" -> {
                yield me * me;
            }
            default -> me;
        };

        assertEquals(16, result);
    }
//
//    @Test
//    public void tgesd(){
//        JabelExampleTest jabelExampleTest = new JabelExampleTest();
//        jabelExampleTest.whenSwitchingOnOperationSquareMe_thenWillReturnSquare();
//    }

}