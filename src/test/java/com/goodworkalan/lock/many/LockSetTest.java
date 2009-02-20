package com.goodworkalan.lock.many;

import org.testng.annotations.Test;

public class LockSetTest
{
    @Test
    public void constructor()
    {
        new LatchSet<Long>(5000);
    }
}
