/*
 * DiceRandom.java
 *
 * Created on March 18, 2002, 1:51 PM
 */

package megamek.common.util;

import java.util.*;

/**
 * This subclass of random just implements the nextInt(int) method, as 
 * implemented in JDK 1.2 and above.
 *
 * @author  Ben
 * @version 
 */
public class DiceRandom extends Random {

    /** Creates new DiceRandom */
    public DiceRandom() {
        super();
    }
    
    public int nextInt(int n) {
        if (n<=0)
            throw new IllegalArgumentException("n must be positive");

        if ((n & -n) == n)  // i.e., n is a power of 2
            return (int)((n * (long)next(31)) >> 31);

        int bits, val;
        do {
            bits = next(31);
            val = bits % n;
        } while(bits - val + (n-1) < 0);
        return val;
    }
    
    public int nextD6() {
        return nextInt(6) + 1;
    }
}
