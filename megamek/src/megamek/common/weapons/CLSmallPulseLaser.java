/**
 * MegaMek - Copyright (C) 2004,2005 Ben Mazur (bmazur@sev.org)
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
/*
 * Created on Sep 12, 2004
 *
 */
package megamek.common.weapons;

import megamek.common.TechConstants;

/**
 * @author Andrew Hunter
 * 
 */
public class CLSmallPulseLaser extends LaserWeapon {
    /**
     * 
     */
    public CLSmallPulseLaser() {
        super();
        this.techLevel = TechConstants.T_CLAN_LEVEL_2;
        this.name = "Small Pulse Laser";
        this.setInternalName("CLSmallPulseLaser");
        this.addLookupName("Clan Pulse Small Laser");
        this.addLookupName("Clan Small Pulse Laser");
        this.heat = 2;
        this.damage = 3;
        this.toHitModifier = -2;
        this.shortRange = 2;
        this.mediumRange = 4;
        this.longRange = 6;
        this.extremeRange = 8;
        this.waterShortRange = 1;
        this.waterMediumRange = 2;
        this.waterLongRange = 4;
        this.waterExtremeRange = 4;
        this.tonnage = 1.0f;
        this.criticals = 1;
        this.bv = 24;
        this.cost = 16000;
    }
}
