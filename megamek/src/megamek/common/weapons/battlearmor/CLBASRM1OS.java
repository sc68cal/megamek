/**
 * MegaMek - Copyright (C) 2005 Ben Mazur (bmazur@sev.org)
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 */
package megamek.common.weapons.battlearmor;

import megamek.common.TechConstants;
import megamek.common.weapons.SRMWeapon;

/**
 * @author Sebastian Brocks
 */
public class CLBASRM1OS extends SRMWeapon {

    /**
     *
     */
    private static final long serialVersionUID = 7660446177480426870L;

    /**
     *
     */
    public CLBASRM1OS() {
        super();
        techLevel.put(3071, TechConstants.T_CLAN_TW);
        name = "SRM 1 (OS)";
        setInternalName("CLBASRM1OS");
        rackSize = 1;
        shortRange = 3;
        mediumRange = 6;
        longRange = 9;
        extremeRange = 12;
        bv = 3;
        flags = flags.or(F_NO_FIRES).or(F_BA_WEAPON).or(F_ONESHOT).andNot(F_MECH_WEAPON).andNot(F_TANK_WEAPON).andNot(F_AERO_WEAPON).andNot(F_PROTO_WEAPON);
        tonnage = .020f;
        criticals = 2;
        cost = 2500;
        introDate = 2868;
        techLevel.put(2868, techLevel.get(3071));
        availRating = new int[] { RATING_X, RATING_D, RATING_C };
        techRating = RATING_F;
    }
}
