package com.example;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record RecordExample(int i, String s, long l, float f, double d, String[] arr) {

    public static RecordExample DUMMY = new RecordExample(0, null, 0, 0, 0, null);

    public RecordExample {
        if (i > 1_000_000) {
            throw new IllegalArgumentException("'i' is too big");
        }
    }
}
