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
 */
public class ISMRM2 extends MRMWeapon {

    /**
     * 
     */
    private static final long serialVersionUID = -8616767319138511565L;

    /**
     * 
     */
    public ISMRM2() {
        super();
        this.techLevel.put(3071,TechConstants.T_IS_TW_NON_BOX);
        this.name = "MRM 2";
        this.setInternalName(this.name);
        this.addLookupName("MRM-2");
        this.addLookupName("ISMRM2");
        this.addLookupName("IS MRM 2");
        this.rackSize = 2;
        this.shortRange = 3;
        this.mediumRange = 8;
        this.longRange = 15;
        this.extremeRange = 16;
        this.bv = 13;
        introDate = 3057;
        techLevel.put(3057,techLevel.get(3071));
        availRating = new int[]{RATING_X,RATING_X,RATING_E};
        techRating = RATING_E;
    }
}