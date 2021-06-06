package com.example;

import org.junit.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class RecordExampleTest {

    @Test(expected = IllegalArgumentException.class)
    public void testCtor() {
        int i = 1_000_000 + 1;
        new RecordExample(i, "yeah", 100500, 0.5f, 5d, new String[]{"Hello", "World!"});
    }

    @Test
    public void testToString() {
        RecordExample r = new RecordExample(42, "yeah", 100500, 0.5f, 5d, new String[]{"Hello", "World!"});

        assertEquals(
                "RecordExample[i=42,s=yeah,l=100500,f=0.5,d=5.0,arr=[Ljava.lang.String;@hash]",
                Objects.toString(r).replaceAll("@[a-f0-9]{8}", "@hash")
        );
    }

    @Test
    public void testToStringWithNulls() {
        RecordExample r = new RecordExample(42, null, 100500, 0.5f, 5d, null);

        assertEquals(
                "RecordExample[i=42,s=null,l=100500,f=0.5,d=5.0,arr=null]",
                Objects.toString(r)
        );
    }

    @Test
    public void testEqualsSame() {
        RecordExample r = new RecordExample(42, null, 100500, 0.5f, 5d, null);
        assertEquals(r, r);
    }

    @Test
    public void testEqualsSameValues() {
        String s = "So cool!";
        String[] arr = new String[] { "Hello", "World!"};
        assertEquals(
                new RecordExample(42, s, 100500, 0.5f, 5d, arr),
                new RecordExample(42, s, 100500, 0.5f, 5d, arr)
        );
    }

    @Test
    public void testNotEquals() {
        assertNotEquals(
                new RecordExample(42, "l", 100500, 0.5f, 5d, new String[] { "Hello", "World!"}),
                new RecordExample(42, "l", 100500, 0.5f, 5d, new String[] { "Hello", "World!"})
        );
    }

    @Test
    public void testHashCodeWithDefaults() {
        RecordExample r = new RecordExample(0, null, 0, 0f, 5d, null);
        assertEquals(
                intellijStyleHashCode(r),
                Objects.hashCode(r)
        );
    }

    @Test
    public void testHashCode() {
        RecordExample r = new RecordExample(42, "Hi", 100500, 0.5f, 5d, new String[]{"Hello", "World!"});
        assertEquals(
                intellijStyleHashCode(r),
                Objects.hashCode(r)
        );
    }

    static int intellijStyleHashCode(RecordExample r) {
        int result = r.i();
        result = 31 * result + (r.s() != null ? r.s().hashCode() : 0);
        result = 31 * result + (int) (r.l() ^ (r.l() >>> 32));
        result = 31 * result + (r.f() != +0.0f ? Float.floatToIntBits(r.f()) : 0);

        long temp = Double.doubleToLongBits(r.d());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + Arrays.hashCode(r.arr());
        return result;
    }
}
