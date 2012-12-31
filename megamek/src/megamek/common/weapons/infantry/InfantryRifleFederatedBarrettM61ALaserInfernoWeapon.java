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
 * Created on Sep 7, 2005
 *
 */
package megamek.common.weapons.infantry;

import megamek.common.AmmoType;
import megamek.common.TechConstants;

/**
 * @author Ben Grills
 */
public class InfantryRifleFederatedBarrettM61ALaserInfernoWeapon extends InfantryWeapon {

    /**
     *
     */
    private static final long serialVersionUID = -3164871600230559641L;

    public InfantryRifleFederatedBarrettM61ALaserInfernoWeapon() {
        super();
        techLevel = TechConstants.T_IS_TW_NON_BOX;
        name = "Federated Barrett M61A Inferno";
        setInternalName(name);
        addLookupName("InfantryFederatedBarrettM61AInferno");
        ammoType = AmmoType.T_NA;
        cost = 2150;
        bv = 1.17;
        flags = flags.or(F_INFERNO).or(F_DIRECT_FIRE).or(F_ENERGY);
        String[] modeStrings = { "Damage", "Heat" };
        setModes(modeStrings);
        infantryDamage = 0.55;
        infantryRange = 2;
        introDate = 3062;
        availRating = new int[]{RATING_X,RATING_X,RATING_D};
        techRating = RATING_C;
    }
}
