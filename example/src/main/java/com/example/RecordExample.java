package com.example;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

public class RecordExample {

    @Desugar
    record Test(int a, String b) {
        public Test {
            if (a > 1_000_000) {
                throw new IllegalArgumentException("Blah");
            }
        }
    }

    public static void main(String[] args) {
        var rec = new Test(100500, "yeah!");
        System.out.println("Record: " + rec);
        System.out.println("hashcode: " + rec.hashCode());
        System.out.println("Manual hashcode: " + Objects.hash(rec.a, rec.b));
        System.out.println("Equals to self: " + rec.equals(rec));
        System.out.println("Equals to similar: " + rec.equals(new Test(100500, "yeah!")));
        System.out.println("Not Equals to different: " + rec.equals(new Test(42, "Nope!")));
    }
}
