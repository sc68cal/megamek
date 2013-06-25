/**
 * MegaMek - Copyright (C) 2004 Ben Mazur (bmazur@sev.org)
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
 * Created on Jun 1, 2005
 *
 */
package megamek.common;

/**
 * @author Andrew Hunter
 * VTOLs are helicopters (more or less.)  They don't really work properly yet.  Don't use them.
 */
public class VTOL extends Tank {
    
    public static final int LOC_ROTOR = 5;  //will this cause problems w/r/t turrets?
    
    protected static String[] LOCATION_ABBRS = { "BD", "FR", "RS", "LS", "RR", "RO" };
    protected static String[] LOCATION_NAMES = { "Body", "Front", "Right", "Left", "Rear", "Rotor" };
    
    //critical hits
    public static final int CRIT_COPILOT           = 15;
    public static final int CRIT_PILOT             = 16;
    public static final int CRIT_ROTOR_DAMAGE      = 17;
    public static final int CRIT_ROTOR_DESTROYED   = 18;
    public static final int CRIT_FLIGHT_STABILIZER = 19;

    public VTOL() {
        super();
    }
    
    public String[] getLocationAbbrs() {
        return LOCATION_ABBRS;
    }

    public String[] getLocationNames() {
        return LOCATION_NAMES;
    }

    /* (non-Javadoc)
     * @see megamek.common.Entity#checkSkid(int, megamek.common.IHex, int, megamek.common.MoveStep, int, int, megamek.common.Coords, megamek.common.Coords, boolean, int)
     */
    public PilotingRollData checkSkid(int moveType, IHex prevHex, int overallMoveType, MoveStep prevStep, int prevFacing, int curFacing, Coords lastPos, Coords curPos, boolean isInfantry, int distance) {
        PilotingRollData roll = getBasePilotingRoll();
        roll.addModifier(TargetRoll.CHECK_FALSE,"Check false: VTOLs can't skid");
        return roll;
    }

    /* (non-Javadoc)
     * @see megamek.common.Tank#calculateBattleValue(boolean)
     */
    public int calculateBattleValue(boolean assumeLinkedC3) {
        double dbv = 0; // defensive battle value
        double obv = 0; // offensive bv
        
        // total armor points
        dbv += getTotalArmor();
        
        // total internal structure        
        dbv += (double)getTotalInternal() / 2;
        
        // add defensive equipment
        double dEquipmentBV = 0;
        for (Mounted mounted : getEquipment()) {
            EquipmentType etype = mounted.getType();

            // don't count destroyed equipment
            if (mounted.isDestroyed())
                continue;

            if ((etype instanceof WeaponType && ((WeaponType)etype).getAmmoType() == AmmoType.T_AMS)
            || (etype instanceof AmmoType && ((AmmoType)etype).getAmmoType() == AmmoType.T_AMS)
            || etype.hasFlag(MiscType.F_ECM)) {
                dEquipmentBV += etype.getBV(this);
            }
        }
        dbv += dEquipmentBV;
        
        double typeModifier;
        typeModifier=.4;
        
        dbv *= typeModifier;
        
        // adjust for target movement modifier
        int tmmRan = Compute.getTargetMovementModifier(getOriginalRunMP(), true, true).getValue();
        if (tmmRan > 5) {
            tmmRan = 5;
        }
        double[] tmmFactors = { 1.0, 1.1, 1.2, 1.3, 1.4, 1.5 };
        dbv *= tmmFactors[tmmRan];
        
        double weaponBV = 0;
        
        // figure out base weapon bv
        double weaponsBVFront = 0;
        double weaponsBVRear = 0;
        boolean hasTargComp = hasTargComp();
        for (Mounted mounted : getWeaponList()) {
            WeaponType wtype = (WeaponType)mounted.getType();
            double dBV = wtype.getBV(this);

            // don't count destroyed equipment
            if (mounted.isDestroyed())
                continue;

            // don't count AMS, it's defensive
            if (wtype.getAmmoType() == AmmoType.T_AMS) {
                continue;
            }
            
            // artemis bumps up the value
            if (mounted.getLinkedBy() != null) {
                Mounted mLinker = mounted.getLinkedBy();
                if (mLinker.getType() instanceof MiscType && 
                        mLinker.getType().hasFlag(MiscType.F_ARTEMIS)) {
                    dBV *= 1.2;
                }
            } 
            
            // and we'll add the tcomp here too
            if (wtype.hasFlag(WeaponType.F_DIRECT_FIRE) && hasTargComp) {
                dBV *= 1.2;
            }
            
            if (mounted.getLocation() == LOC_REAR) {
                weaponsBVRear += dBV;
            } else {
                weaponsBVFront += dBV;
            }
        }
        for (Mounted mounted : getEquipment()) {
            if (mounted.isDestroyed()) {
                continue;
            }
            if (mounted.getName().equals("Beagle Active Probe")) {
                weaponsBVFront += 10; 
            }
            if (mounted.getName().equals("Active Probe")) {
                weaponsBVFront += 12;
            }
            if (mounted.getName().equals("Clan Active Probe")) {
                weaponsBVFront += 12;
            }
            if (mounted.getName().equals("Light Active Probe")) {
                weaponsBVFront += 7;
            }
        }
        if (weaponsBVFront > weaponsBVRear) {
            weaponBV += weaponsBVFront;
            weaponBV += (weaponsBVRear * 0.5);
        } else {
            weaponBV += weaponsBVRear;
            weaponBV += (weaponsBVFront * 0.5);
        }
        
        // add ammo bv
        double ammoBV = 0;
        for (Mounted mounted : getAmmo()) {
            AmmoType atype = (AmmoType)mounted.getType();
            
            // don't count depleted ammo
            if (mounted.getShotsLeft() == 0)
                continue;

            // don't count AMS, it's defensive
            if (atype.getAmmoType() == AmmoType.T_AMS) {
                continue;
            }

            ammoBV += atype.getBV(this);
        }
        weaponBV += ammoBV;
        
        // adjust further for speed factor
        double speedFactor = 2*getOriginalRunMP() - 5;
        speedFactor /= 10;
        speedFactor++;
        speedFactor = Math.pow(speedFactor, 1.2);
        speedFactor = Math.round(speedFactor * 100) / 100.0;

        obv = weaponBV * speedFactor;

        // we get extra bv from c3 networks. a valid network requires at least 2 members
        // some hackery and magic numbers here.  could be better
        // also, each 'has' loops through all equipment.  inefficient to do it 3 times
        double xbv = 0.0;
        if ((hasC3MM() && calculateFreeC3MNodes() < 2) ||
            (hasC3M() && calculateFreeC3Nodes() < 3) ||
            (hasC3S() && C3Master > NONE) ||
            (hasC3i() && calculateFreeC3Nodes() < 5) ||
            assumeLinkedC3) {
                xbv = Math.round(0.35 * weaponsBVFront + (0.5 * weaponsBVRear));
        }

        // and then factor in pilot
        double pilotFactor = crew.getBVSkillMultiplier();

        //return (int)Math.round((dbv + obv + xbv) * pilotFactor);
        int finalBV = (int)Math.round(dbv + obv + xbv);

        int retVal = (int)Math.round(finalBV * pilotFactor);
        return retVal;
    }

    /* (non-Javadoc)
     * @see megamek.common.Tank#canCharge()
     */
    public boolean canCharge() {
        return false;
    }
    
    /**
     * Returns the name of the type of movement used.
     * This is VTOL-specific.
     */
    public String getMovementString(int mtype) {
        switch(mtype) {
        case IEntityMovementType.MOVE_VTOL_WALK :
            return "Cruised";
        case IEntityMovementType.MOVE_VTOL_RUN :
            return "Flanked";
        case IEntityMovementType.MOVE_NONE :
            return "None";
        default :
            return "Unknown!";
        }
    }
    
    /**
     * Returns the name of the type of movement used.
     * This is tank-specific.
     */
    public String getMovementAbbr(int mtype) {
        switch(mtype) {
        case IEntityMovementType.MOVE_VTOL_WALK :
            return "C";
        case IEntityMovementType.MOVE_VTOL_RUN :
            return "F";
        case IEntityMovementType.MOVE_NONE :
            return "N";
        default :
            return "?";
        }
    }

    public int getMaxElevationChange() {
        return 999;
    }

    public boolean isHexProhibited(IHex hex) {
        if(hex.containsTerrain(Terrains.IMPASSABLE)) return true;
        return false;
    }

    /* (non-Javadoc)
     * @see megamek.common.Tank#isRepairable()
     */
    public boolean isRepairable() {
        boolean retval = this.isSalvage();
        int loc = Tank.LOC_FRONT;
        while ( retval && loc < VTOL.LOC_ROTOR ) {
            int loc_is = this.getInternal( loc );
            loc++;
            retval = (loc_is != IArmorState.ARMOR_DOOMED) && (loc_is != IArmorState.ARMOR_DESTROYED);
        }
        return retval;
    }

    /* (non-Javadoc)
     * This really, really isn't right.
     */
    public HitData rollHitLocation(int table, int side) {
        int nArmorLoc = LOC_FRONT;
        boolean bSide = false;
        if (side == ToHitData.SIDE_LEFT) {
            nArmorLoc = LOC_LEFT;
            bSide = true;
        }
        else if (side == ToHitData.SIDE_RIGHT) {
            nArmorLoc = LOC_RIGHT;
            bSide = true;
        }
        else if (side == ToHitData.SIDE_REAR) {
            nArmorLoc = LOC_REAR;
        }
        HitData rv = new HitData(nArmorLoc);
        switch (Compute.d6(2)) {
            case 2:
                rv.setEffect(HitData.EFFECT_CRITICAL);
                break;
            case 3:
            case 4:
                rv = new HitData(LOC_ROTOR, false, HitData.EFFECT_VEHICLE_MOVE_DAMAGED);
                break;
            case 5:
                if(bSide)
                    rv = new HitData(LOC_FRONT);
                else
                    rv = new HitData(LOC_RIGHT);
                break;
            case 6:
            case 7:
                break;
            case 8:
                if(bSide) {
                    rv.setEffect(HitData.EFFECT_CRITICAL);
                }
                break;
            case 9:
                if(bSide)
                    rv = new HitData(LOC_REAR);
                else
                    rv = new HitData(LOC_LEFT);
                break;
            case 10:
            case 11:
                rv = new HitData(LOC_ROTOR, false, HitData.EFFECT_VEHICLE_MOVE_DAMAGED);
            case 12:
                rv = new HitData(LOC_ROTOR, false, HitData.EFFECT_CRITICAL | HitData.EFFECT_VEHICLE_MOVE_DAMAGED);                
        }
        if(table == ToHitData.HIT_SWARM)
            rv.setEffect(rv.getEffect() | HitData.EFFECT_CRITICAL);
        return rv;
    }

    public boolean doomedInVacuum() {
        return true;
    }
    
    public void setOnFire(boolean inferno) {
        super.setOnFire(inferno);
        extinguishLocation(LOC_ROTOR);
    }

    /** get the type of critical caused by a critical roll,
     * taking account of existing damage
     * @param roll the final dice roll
     * @param loc  the hit location
     * @return     a critical type
     */
    public int getCriticalEffect(int roll, int loc) {
        if(roll >12) roll = 12;
        if(roll < 6) return CRIT_NONE;
        for(int i=0;i<2;i++) {
            if(i > 0) roll = 6;
            if(loc == LOC_FRONT) {
                switch(roll) {
                case 6:
                    if(!isDriverHit())
                        return CRIT_COPILOT;
                    return CRIT_CREW_KILLED;
                case 7:
                    for(Mounted m:getWeaponList()) {
                        if(m.getLocation() == loc 
                                && !m.isDestroyed() 
                                && !m.isJammed()
                                && !m.isHit()) {
                            return CRIT_WEAPON_JAM;                            
                        }
                    }
                case 8:
                    if(!isStabiliserHit(loc))
                        return CRIT_STABILIZER;
                case 9:
                    if(getSensorHits() < 4)
                        return CRIT_SENSOR;
                case 10:
                    if(!isCommanderHit())
                        return CRIT_PILOT;
                    return CRIT_CREW_KILLED;
                case 11:
                    for(Mounted m:getWeaponList()) {
                        if(m.getLocation() == loc
                                && !m.isDestroyed()
                                && !m.isHit()) {
                            return CRIT_WEAPON_DESTROYED;
                        }
                    }
                case 12:
                    if(!crew.isDead())
                        return CRIT_CREW_KILLED;
                }
            }
            else if(loc == LOC_REAR) {
                switch(roll) {
                case 6:
                    if(getLoadedUnits().size() > 0)
                        return CRIT_CARGO;
                case 7:
                    for(Mounted m:getWeaponList()) {
                        if(m.getLocation() == loc
                                && !m.isDestroyed()
                                && !m.isJammed()
                                && !m.isHit()) {
                            return CRIT_WEAPON_JAM;                            
                        }
                    }
                case 8:
                    if(!isStabiliserHit(loc))
                        return CRIT_STABILIZER;
                case 9:
                    for(Mounted m:getWeaponList()) {
                        if(m.getLocation() == loc
                                && !m.isDestroyed()
                                && !m.isHit()) {
                            return CRIT_WEAPON_DESTROYED;
                        }
                    }
                case 10:
                    if(getSensorHits() < 4)
                        return CRIT_SENSOR;
                case 11:
                    if(!isImmobile())
                        return CRIT_ENGINE;
                case 12:
                    if(getEngine().isFusion() && !isImmobile())
                        return CRIT_ENGINE;
                    else if(!getEngine().isFusion())
                        return CRIT_FUEL_TANK;
                }
            }
            else if(loc == LOC_ROTOR) {
                switch(roll) {
                case 6:
                case 7:
                case 8:
                    if(!isImmobile())
                        return CRIT_ROTOR_DAMAGE;
                case 9:
                case 10:
                    if(!isStabiliserHit(loc))
                        return CRIT_FLIGHT_STABILIZER;
                case 11:
                case 12:
                    return CRIT_ROTOR_DESTROYED;
                }
            }
            else {
                switch(roll) {
                case 6:
                    for(Mounted m:getWeaponList()) {
                        if(m.getLocation() == loc
                                && !m.isDestroyed()
                                && !m.isJammed()
                                && !m.isHit()) {
                            return CRIT_WEAPON_JAM;                            
                        }
                    }
                case 7:
                    if(getLoadedUnits().size() > 0)
                        return CRIT_CARGO;
                case 8:
                    if(!isStabiliserHit(loc))
                        return CRIT_STABILIZER;
                case 9:
                    for(Mounted m:getWeaponList()) {
                        if(m.getLocation() == loc
                                && !m.isDestroyed()
                                && !m.isHit()) {
                            return CRIT_WEAPON_DESTROYED;
                        }
                    }
                case 10:
                    if(!isImmobile())
                        return CRIT_ENGINE;
                case 11:
                    for(Mounted m:getAmmo()) {
                        if(!m.isDestroyed()
                                && !m.isHit()) {
                            return CRIT_AMMO;
                        }
                    }
                case 12:
                    if(getEngine().isFusion() && !isImmobile())
                        return CRIT_ENGINE;
                    else if(!getEngine().isFusion())
                        return CRIT_FUEL_TANK;
                }
            }
        }
        return CRIT_NONE;
    }
    
    public PilotingRollData addEntityBonuses(PilotingRollData prd)
    {
        if(movementDamage > 0) {
            prd.addModifier(movementDamage, "Steering Damage");
        }
        if(isDriverHit())
            prd.addModifier(2, "pilot injured");
        if(isStabiliserHit(LOC_ROTOR))
            prd.addModifier(3, "flight stabiliser damaged");
        return prd;
    }

}