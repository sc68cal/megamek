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

import java.util.Vector;

import megamek.common.BattleArmor;
import megamek.common.Compute;
import megamek.common.Entity;
import megamek.common.IGame;
import megamek.common.Infantry;
import megamek.common.RangeType;
import megamek.common.Report;
import megamek.common.Targetable;
import megamek.common.ToHitData;
import megamek.common.actions.WeaponAttackAction;
import megamek.server.Server;

public class BALBXHandler extends WeaponHandler implements AttackHandler {

    /**
     *
     */
    private static final long serialVersionUID = -6378056043285522609L;

    String sSalvoType = " pellet(s) ";

    public BALBXHandler(ToHitData t, WeaponAttackAction w, IGame g, Server s) {
        super(t, w, g, s);
    }

    /*
     * (non-Javadoc)
     *
     * @see megamek.common.weapons.WeaponHandler#calcDamagePerHit()
     */
    @Override
    protected int calcDamagePerHit() {
        if ((target instanceof Infantry) && !(target instanceof BattleArmor)) {
            double toReturn = Compute.directBlowInfantryDamage(
                    wtype.getRackSize() * 2, bDirect ? toHit.getMoS() / 3 : 0,
                    wtype.getInfantryDamageClass(),
                    ((Infantry) target).isMechanized());
            if (bGlancing) {
                toReturn /= 2;
            }
            return (int) Math.floor(toReturn);
        }
        return 1;
    }

    @Override
    protected int calcHits(Vector<Report> vPhaseReport) {
        // conventional infantry gets hit in one lump
        // BAs do one lump of damage per BA suit
        if ((target instanceof Infantry) && !(target instanceof BattleArmor)) {
            bSalvo = true;
            Report r = new Report(3325);
            r.subject = subjectId;
            r.add(wtype.getRackSize()
                    * ((BattleArmor) ae).getShootingStrength());
            r.add(sSalvoType);
            r.add(" ");
            vPhaseReport.add(r);
            return ((BattleArmor) ae).getShootingStrength();

        }
        Entity entityTarget = (target.getTargetType() == Targetable.TYPE_ENTITY) ? (Entity) target
                : null;
        int missilesHit;
        int nMissilesModifier = 0;
        boolean tacopscluster = game.getOptions().booleanOption(
                "tacops_clusterhitpen");

        int[] ranges = wtype.getRanges(weapon);
        if (tacopscluster) {
            if (nRange <= 1) {
                nMissilesModifier += 1;
            } else if (nRange <= ranges[RangeType.RANGE_MEDIUM]) {
                nMissilesModifier += 0;
            } else {
                nMissilesModifier -= 1;
            }
        }

        if (game.getOptions().booleanOption("tacops_range")
                && (nRange > ranges[RangeType.RANGE_LONG])) {
            nMissilesModifier -= 2;
        }

        if (bGlancing) {
            nMissilesModifier -= 4;
        }

        if (bDirect) {
            nMissilesModifier += (toHit.getMoS() / 3) * 2;
        }

        if (game.getPlanetaryConditions().hasEMI()) {
            nMissilesModifier -= 2;
        }

        if (allShotsHit()) {
            missilesHit = wtype.getRackSize();
        } else {

            missilesHit = Compute.missilesHit(wtype.getRackSize()
                    * ((BattleArmor) ae).getShootingStrength(),
                    nMissilesModifier, weapon.isHotLoaded(), false, false);

        }

        if (missilesHit > 0) {
            Report r = new Report(3325);
            r.subject = subjectId;
            r.add(missilesHit);
            r.add(sSalvoType);
            r.add(toHit.getTableDesc());
            r.newlines = 0;
            vPhaseReport.addElement(r);
            if (nMissilesModifier != 0) {
                if (nMissilesModifier > 0) {
                    r = new Report(3340);
                } else {
                    r = new Report(3341);
                }
                r.subject = subjectId;
                r.add(nMissilesModifier);
                r.newlines = 0;
                vPhaseReport.addElement(r);
            }
        }
        Report r = new Report(3345);
        r.subject = subjectId;
        vPhaseReport.addElement(r);
        bSalvo = true;
        return missilesHit;
    }

}
