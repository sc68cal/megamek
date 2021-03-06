/**
 * MegaMek - Copyright (C) 2004,2005 Ben Mazur (bmazur@sev.org)
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
/*
 * Created on Sep 24, 2004
 */
package megamek.common.weapons;

import megamek.common.AmmoType;
import megamek.common.TechConstants;
import megamek.common.WeaponType;

/**
 * @author Andrew Hunter
 */
public class ISHeavyFlamer extends VehicleFlamerWeapon {
    /**
     *
     */
    private static final long serialVersionUID = -3957472644909347725L;

    /**
     *
     */
    public ISHeavyFlamer() {
        super();
        techLevel.put(3071, TechConstants.T_IS_ADVANCED);
        name = "Heavy Flamer";
        setInternalName(name);
        addLookupName("IS Heavy Flamer");
        addLookupName("ISHeavyFlamer");
        heat = 5;
        damage = 4;
        infDamageClass = WeaponType.WEAPON_BURST_6D6;
        rackSize = 2;
        ammoType = AmmoType.T_HEAVY_FLAMER;
        shortRange = 2;
        mediumRange = 3;
        longRange = 4;
        extremeRange = 6;
        tonnage = 1.5f;
        criticals = 1;
        bv = 15;
        cost = 11250;
        flags = flags.or(WeaponType.F_AERO_WEAPON).or(WeaponType.F_MECH_WEAPON)
                .or(WeaponType.F_TANK_WEAPON);
        techRating = RATING_C;
        availRating = new int[] { RATING_X, RATING_X, RATING_E };
        introDate = 3068;
        techLevel.put(3068, techLevel.get(3071));
        techLevel.put(3079, TechConstants.T_IS_TW_NON_BOX);
    }
}
