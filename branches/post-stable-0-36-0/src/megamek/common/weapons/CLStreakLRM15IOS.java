/**
 * MegaMek - Copyright (C) 2005 Ben Mazur (bmazur@sev.org)
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 */
package megamek.common.weapons;

import megamek.common.TechConstants;

/**
 * @author Sebastian Brocks
 */
public class CLStreakLRM15IOS extends StreakLRMWeapon {

    /**
     *
     */
    private static final long serialVersionUID = 7336450815633311159L;

    /**
     *
     */
    public CLStreakLRM15IOS() {
        super();
        techLevel.put(3071, TechConstants.T_CLAN_EXPERIMENTAL);
        name = "Streak LRM 15 (I-OS)";
        setInternalName("CLIOSStreakLRM15");
        addLookupName("Clan Streak LRM-15 (IOS)");
        addLookupName("Clan Streak LRM 15 (IOS)");
        addLookupName("CLStreakLRM15 (IOS)");
        heat = 5;
        rackSize = 15;
        shortRange = 7;
        mediumRange = 14;
        longRange = 21;
        extremeRange = 28;
        tonnage = 6.5f;
        criticals = 3;
        bv = 52;
        flags = flags.or(F_ONESHOT);
        cost = 320000;
        shortAV = 15;
        medAV = 15;
        longAV = 15;
        maxRange = RANGE_LONG;
        techRating = RATING_B;
        availRating = new int[] { RATING_X, RATING_X, RATING_F };
        introDate = 3058;
        techLevel.put(3058, techLevel.get(3071));
        techLevel.put(3079, TechConstants.T_CLAN_ADVANCED);
    }
}