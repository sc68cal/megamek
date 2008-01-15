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
import megamek.common.Building;
import megamek.common.Compute;
import megamek.common.Entity;
import megamek.common.HitData;
import megamek.common.IGame;
import megamek.common.Infantry;
import megamek.common.Mech;
import megamek.common.Report;
import megamek.common.ToHitData;
import megamek.common.actions.WeaponAttackAction;
import megamek.server.Server;

public class PlasmaCannonHandler extends AmmoWeaponHandler {
    /**
     * @param toHit
     * @param waa
     * @param g
     */
    public PlasmaCannonHandler(ToHitData toHit, WeaponAttackAction waa, IGame g,
            Server s) {
        super(toHit, waa, g, s);
    }
    
    /*
     *  (non-Javadoc)
     * @see megamek.common.weapons.WeaponHandler#handleEntityDamage(megamek.common.Entity, java.util.Vector, megamek.common.Building, int, int, int, int)
     */
    protected void handleEntityDamage(Entity entityTarget,
            Vector<Report> vPhaseReport, Building bldg, int hits, int nCluster,
            int nDamPerHit, int bldgAbsorbs) {
        
        if (entityTarget instanceof Mech) {
            
            HitData hit = entityTarget.rollHitLocation(toHit.getHitTable(), toHit
                    .getSideTable(), waa.getAimedLocation(), waa.getAimingMode());

            if ( entityTarget.removePartialCoverHits(hit.getLocation(), toHit.getCover(),
                    Compute.targetSideTable(ae, entityTarget)) ) {
                // Weapon strikes Partial Cover.
                r = new Report(3460);
                r.subject = subjectId;
                r.add(entityTarget.getShortName());
                r.add(entityTarget.getLocationAbbr(hit));
                r.newlines = 0;
                r.indent(2);
                vPhaseReport.addElement(r);
                missed = true;
                return;
            }
            
            if (!bSalvo) {
                // Each hit in the salvo get's its own hit location.
                r = new Report(3405);
                r.subject = subjectId;
                r.add(toHit.getTableDesc());
                r.add(entityTarget.getLocationAbbr(hit));
                r.newlines = 0;
                vPhaseReport.addElement(r);
            }

            if (hit.hitAimedLocation()) {
                r = new Report(3410);
                r.subject = subjectId;
                r.newlines = 0;
                vPhaseReport.addElement(r);
            }
            // Resolve damage normally.
            int nDamage = nDamPerHit * Math.min(nCluster, hits);

            // A building may be damaged, even if the squad is not.
            if (bldgAbsorbs > 0) {
                nDamage *= 2;
                int toBldg = Math.min(bldgAbsorbs, nDamage);
                nDamage -= toBldg;
                Report.addNewline(vPhaseReport);
                Report buildingReport = server.damageBuilding(bldg, toBldg);
                buildingReport.indent(2);
                buildingReport.subject = subjectId;
                vPhaseReport.addElement(buildingReport);
                if ( nDamage == 0)
                    missed = true;
            }

            if ( missed )
                return;
            
            r = new Report(3400);
            r.subject = subjectId;
            r.indent(2);
            int extraHeat = Compute.d6(2);
            r.add(extraHeat);
            r.choose(true);
            r.newlines = 0;
            vPhaseReport.addElement(r);
            entityTarget.heatFromExternal += extraHeat;
        } else
            super.handleEntityDamage(entityTarget, vPhaseReport, bldg, hits,
                nCluster, nDamPerHit, bldgAbsorbs);
    }
    
    /*
     * (non-Javadoc)
     * @see megamek.common.weapons.WeaponHandler#calcDamagePerHit()
     */
    protected int calcDamagePerHit() {
        if (target instanceof Mech) {
            return 0;
        } else {
            if (target instanceof Infantry && !(target instanceof BattleArmor))
                return Compute.d6(3);
            return 1;
        }
    }
    
    /*
     * (non-Javadoc)
     * @see megamek.common.weapons.WeaponHandler#calcnCluster()
     */
    protected int calcnCluster() {
        if (target instanceof Mech) {
            bSalvo = false;
            return 1;
        } else {
            bSalvo = true;
            return 5;
        }
    }
    
    /*
     * (non-Javadoc)
     * @see megamek.common.weapons.WeaponHandler#calcHits(java.util.Vector)
     */
    protected int calcHits(Vector<Report> vPhaseReport) {
        // conventional infantry gets hit in one lump
        // BAs do one lump of damage per BA suit
        if (target instanceof Infantry && !(target instanceof BattleArmor)) {
            if (ae instanceof BattleArmor) {
                bSalvo = true;
                return ((BattleArmor)ae).getShootingStrength();
            }
            return 1;
        }
        if (target instanceof Mech) {
            return 1;
        } else {
            if (target instanceof BattleArmor && ((BattleArmor)target).hasFireresistantArmor())
                return 0;
            return Compute.d6(3);
        }
    }
}
