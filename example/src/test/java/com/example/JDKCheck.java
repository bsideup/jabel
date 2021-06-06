package com.example;

import org.junit.Test;

public class JDKCheck {

    @Test(expected = ClassNotFoundException.class)
    public void isJava8() throws Exception {
        Class.forName("java.lang.StackWalker");
    }
}
