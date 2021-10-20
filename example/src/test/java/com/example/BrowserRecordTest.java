package com.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BrowserRecordTest {

    @Test
    public void anotherRecordTest() {
        Browser browser = new Browser("edge", false);

        assertEquals(
                false,
                browser.headless()
        );
    }
}
