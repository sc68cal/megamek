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
package megamek.common.weapons;

import megamek.common.TechConstants;

/**
 * @author Sebastian Brocks
 *
 */
public class CLSRM5 extends SRMWeapon {

    /**
     * 
     */
    public CLSRM5() {
        super(); 
        this.techLevel = TechConstants.T_CLAN_LEVEL_2;
        this.name = "SRM 5";
        this.setInternalName("CLSRM5");
        this.addLookupName("Clan SRM-5");
        this.addLookupName("Clan SRM 5");
        this.rackSize = 5;
        this.shortRange = 3;
        this.mediumRange = 6;
        this.longRange = 9;
        this.extremeRange = 12;
        this.bv = 58;
        this.flags |= F_NO_FIRES;
        this.cost = 80000;
    }
}