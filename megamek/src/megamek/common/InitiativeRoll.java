/*
 * InitaitiveRoll.java
 *
 * Created on April 25, 2002, 12:21 PM
 */

package megamek.common;

import java.util.*;
import java.io.*;

/**
 * A roll, or sequence of rolls, made by the player to determine initiative 
 * order.  Also contains some methods for ordering players by initiative.
 *
 * @author  Ben
 * @version 
 */
public class InitiativeRoll implements com.sun.java.util.collections.Comparable, Serializable {
    
    private Vector rolls = new Vector();

    /** Creates new InitaitiveRoll */
    public InitiativeRoll() {
    }

    public void clear() {
       rolls.removeAllElements();
    }
    
    public void addRoll() {
       rolls.addElement(new Integer(Compute.d6(2)));
    }
    
    public int size() {
        return rolls.size();
    }
    
    public int getRoll(int index) {
        return ((Integer)rolls.elementAt(index)).intValue();
    }
    
    
    /**
     * Two initiative rolls are equal if they match, roll by roll
     */
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object == null || getClass() != object.getClass()) {
            return false;
        }
        InitiativeRoll other = (InitiativeRoll)object;
        if (size() != other.size()) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (getRoll(i) != other.getRoll(i)) {
                return false;
            }
        }
        return true;
    }
    
    public int compareTo(Object object) {
        if (getClass() != object.getClass()) {
            throw new IllegalArgumentException("InitiativeRoll must be compared to InitiativeRoll");
        }
        InitiativeRoll other = (InitiativeRoll)object;
        int minSize = Math.min(size(), other.size());
        int compare = 0;
        for (int i = 0; i < minSize; i++) {
            compare = getRoll(i) - other.getRoll(i);
            if (compare != 0) {
                return compare;
            }
        }
        return compare;
    }
    
}
