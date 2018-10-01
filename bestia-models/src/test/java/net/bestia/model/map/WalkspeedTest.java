package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;

public class WalkspeedTest {

    @Test(expected = IllegalArgumentException.class)
    public void fromInt_outOfRange_throws() {
        Walkspeed.Companion.fromInt(2003);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromInt_negative_throws() {
        Walkspeed.Companion.fromInt(-1);
    }

    @Test
    public void fromInt_ok() {
        Walkspeed.Companion.fromInt(100);
        Walkspeed.Companion.fromInt(0);
        Walkspeed.Companion.fromInt(300);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromFloat_outOfRange_throws() {
        new Walkspeed(3.7f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromFloat_negative_throws() {
        new Walkspeed(-1.7f);
    }

    @Test
    public void fromFloat_ok() {
        new Walkspeed(1.7f);
        new Walkspeed(0f);
        new Walkspeed(Walkspeed.MAX_WALKSPEED);
    }

    @Test
    public void getSpeed_ok() {
        Walkspeed ws = new Walkspeed(Walkspeed.MAX_WALKSPEED);
        Assert.assertEquals(0.01f, Walkspeed.MAX_WALKSPEED, ws.getSpeed());
    }

    @Test
    public void toInt_ok() {
        Walkspeed ws = new Walkspeed(Walkspeed.MAX_WALKSPEED);
        Assert.assertEquals(Walkspeed.MAX_WALKSPEED_INT, ws.toInt());

        ws = new Walkspeed(0f);
        Assert.assertEquals(0, ws.toInt());

        ws = Walkspeed.Companion.fromInt(100);
        Assert.assertEquals(100, ws.toInt());
    }
}
