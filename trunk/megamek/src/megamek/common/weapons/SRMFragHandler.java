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

import megamek.common.Building;
import megamek.common.Entity;
import megamek.common.HitData;
import megamek.common.IGame;
import megamek.common.Report;
import megamek.common.ToHitData;
import megamek.common.actions.WeaponAttackAction;
import megamek.server.Server;

/**
 * @author Sebastian Brocks
 *
 */
public class SRMFragHandler extends SRMHandler {

    /**
     * @param t
     * @param w
     * @param g
     * @param s
     */
    public SRMFragHandler(ToHitData t, WeaponAttackAction w, IGame g, Server s) {
        super(t, w, g, s);
        sSalvoType = " fragmentation missile(s) ";
    }
    
    /*
     *  (non-Javadoc)
     * @see megamek.common.weapons.WeaponHandler#handleBuildingDamage(java.util.Vector, megamek.common.Building, int, boolean)
     */
    protected void handleBuildingDamage(Vector<Report> vPhaseReport, Building bldg,
            int nDamage, boolean bSalvo) {
        return;
    }

    /*
     *  (non-Javadoc)
     * @see megamek.common.weapons.WeaponHandler#handleEntityDamage(megamek.common.Entity, java.util.Vector, megamek.common.Building, int, int, int, int)
     */
    protected void handleEntityDamage(Entity entityTarget,
            Vector<Report> vPhaseReport, Building bldg, int hits, int nCluster,
            int nDamPerHit, int bldgAbsorbs) {
        int nDamage;
        HitData hit = entityTarget.rollHitLocation(toHit.getHitTable(), toHit
                .getSideTable(), waa.getAimedLocation(), waa.getAimingMode());
        // Resolve damage normally.
        nDamage = nDamPerHit * Math.min(nCluster, hits);

        // A building may be damaged, even if the squad is not.
        if ( bldgAbsorbs > 0 ) {
            int toBldg = Math.min( bldgAbsorbs, nDamage );
            nDamage -= toBldg;
            Report.addNewline(vPhaseReport);
            Report buildingReport = server.damageBuilding( bldg, toBldg );
            buildingReport.indent(2);
            buildingReport.subject = subjectId;
            vPhaseReport.addElement(buildingReport);
        }
        if (bGlancing) {
            hit.makeGlancingBlow();
        }
        // A building may absorb the entire shot.
        if ( nDamage == 0 ) {
            r = new Report(3415);
            r.subject = subjectId;
            r.indent(2);
            r.addDesc(entityTarget);
            r.newlines = 0;
            vPhaseReport.addElement(r);
        } else {
            if (bGlancing) {
                hit.makeGlancingBlow();
            }
            vPhaseReport.addAll(
                    server.damageEntity(entityTarget, hit, nDamage, false, 1, false, false, throughFront));
        }

    }
}
