package com.example;

import org.junit.Test;

import static org.junit.Assert.*;

public class JabelExampleTest {

    @Test
    public void shouldWork() {
        JabelExample jabelExample = new JabelExample();

        String result = jabelExample.run(new String[0]);

        assertTrue("'" + result + "' should start with 'idk '", result.startsWith("idk "));

    }
}