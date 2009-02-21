package com.goodworkalan.lock.many;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used during commit to lock freed addresses to prevent their possible
 * reallocation from being written before the free operation commit completes.
 * <p>
 * This class is specific to the relatively simple task of preventing the
 * overwrite of a reallocation by replaying a journaled free. It is not intended
 * to be the basis of a general purpose synchronization algorithm. Specifically,
 * it is not intended to lock addresses for users, to coordinate user writes or
 * frees.
 * <p>
 * During free, the address in the address page is set to zero, indicating that
 * the address is available for reallocation. Without some form of
 * synchronization it is possible for another mutator to reallocate the freed
 * address, then commit the reallocation, before the commit that freed the
 * address completes. If the commit that freed the address were not to complete
 * due to a system failure, then when the journals were replayed during recovery
 * the reallocation would be overwritten by the replay of the free.
 * <p>
 * This implementation multiplexes the addresses in 37 different sets of long
 * values and a set is chosen by hashing. Synchronization is performed on the
 * set, so contention is reduced by the reduced chance of two addresses hashing
 * to the same set at the same time.
 * <p>
 * TODO This doesn't work! If you lock the address, mark it as free, then sleep,
 * then the other thread marks it as pending, it sleeps, then you end up
 * flushing the pending to disk. The other thread rolls back. Then soft
 * shutdown. Now you've leaked an address.
 * <p>
 * Which doesn't bother me. I like the idea of a recovery thread. The pack opens
 * quickly, after running journals, but keeps the address pages and user pages
 * out of service until it checks them. It checks address pages until it finds
 * one that has some free slots, then the pack can begin use, as it inspects
 * other address pages for max valued positions, clears then and puts the
 * address page back into service.
 * <p>
 * You can simply make reap threaded. You can run it and it will run to
 * completion, or you can give it its own thread.
 * 
 * @author Alan Gutierrez
 */
public class LatchSet<T>
{
    /** A list of sets of locked addresses. */
    private final List<Set<T>> latches;
    
    /**
     * Create the address locker. There is one address locker per pager.
     */
    public LatchSet(int slots)
    {
        int size = nearestPrime(slots, getClass().getResourceAsStream("primes.txt"));
        this.latches = newLockList(size);
    }
    
    // TODO Document.
    final List<Set<T>> newLockList(int size)
    {
        List<Set<T>> locks = new ArrayList<Set<T>>(size);
        for (int i = 0; i < size; i++)
        {
            locks.add(new HashSet<T>());
        }
        return locks;
    }

    // TODO Document.
    final static int nearestPrime(int approx, InputStream primes)
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(primes));
        int largestPrime = 2;
        String line = null;
        try
        {
            while ((line = reader.readLine()) != null)
            {
                String[] values = line.split("\\s+");
                for (String value : values)
                {
                    largestPrime = Integer.parseInt(value);
                    if (approx <= largestPrime)
                    {
                        return approx;
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        return largestPrime;
    }

    /**
     * Lock the given address, adding it to a set of locked addresses, so that a
     * call to the <code>enter</code> method will block until the the address is
     * unlocked using the <code>unlatch</code> method.
     * 
     * @param address
     *            The address to lock.
     */
    public void latch(T object)
    {
        Set<T> latched = latches.get(object.hashCode() % latches.size());
        synchronized (latched)
        {
            if (latched.contains(object))
            {
                throw new IllegalMonitorStateException();
            }
            latched.add(object);
        }
    }

    /**
     * Unlock all the addresses in the given set of addresses, removing them
     * from this set of locked addresses, and notifying all threads waiting for
     * the address in the <code>bide</code> method.
     * 
     * @param addresses
     *            The set of addresses to unlock.
     */
    public void unlatch(T object)
    {
        Set<T> latched = latches.get(object.hashCode() % latches.size());
        synchronized (latched)
        {
            if (!latched.remove(object))
            {
                throw new IllegalMonitorStateException();
            }
            latched.notifyAll();
        }
    }

    /**
     * If the given address is in the set of locked addresses, block the current
     * thread waiting for the address to become unlocked.
     * 
     * @param address
     *            The address to check.
     */
    public void enter(T object)
    {
        Set<T> latched = latches.get(object.hashCode() % latches.size());
        synchronized (latched)
        {
            while (latched.contains(latched))
            {
                try
                {
                    latched.wait();
                }
                catch (InterruptedException e)
                {
                }
            }
        }
    }
}
