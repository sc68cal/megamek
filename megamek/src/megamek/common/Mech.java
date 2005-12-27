/*
 * MegaMek -
 * Copyright (C) 2000,2001,2002,2003,2004,2005 Ben Mazur (bmazur@sev.org)
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

package megamek.common;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

import megamek.common.CriticalSlot;
import megamek.common.LosEffects;
import megamek.common.ToHitData;
import megamek.common.preference.PreferenceManager;

/**
 * You know what mechs are, silly.
 */
public abstract class Mech
    extends Entity
    implements Serializable
{
    public static final int      NUM_MECH_LOCATIONS = 8;

    // system designators for critical hits
    public static final int        SYSTEM_LIFE_SUPPORT      = 0;
    public static final int        SYSTEM_SENSORS           = 1;
    public static final int        SYSTEM_COCKPIT           = 2;
    public static final int        SYSTEM_ENGINE            = 3;
    public static final int        SYSTEM_GYRO              = 4;

    // actutors are systems too, for now
    public static final int        ACTUATOR_SHOULDER        = 7;
    public static final int        ACTUATOR_UPPER_ARM       = 8;
    public static final int        ACTUATOR_LOWER_ARM       = 9;
    public static final int        ACTUATOR_HAND            = 10;
    public static final int        ACTUATOR_HIP             = 11;
    public static final int        ACTUATOR_UPPER_LEG       = 12;
    public static final int        ACTUATOR_LOWER_LEG       = 13;
    public static final int        ACTUATOR_FOOT            = 14;
    
    public static final String systemNames[] = {"Life Support",
                                                "Sensors",
                                                "Cockpit",
                                                "Engine",
                                                "Gyro",
                                                null,
                                                null,
                                                "Shoulder",
                                                "Upper Arm",
                                                "Lower Arm",
                                                "Hand",
                                                "Hip",
                                                "Upper Leg",
                                                "Lower Leg",
                                                "Foot"};

    // locations
    public static final int        LOC_HEAD                 = 0;
    public static final int        LOC_CT                   = 1;
    public static final int        LOC_RT                   = 2;
    public static final int        LOC_LT                   = 3;
    public static final int        LOC_RARM                 = 4;
    public static final int        LOC_LARM                 = 5;
    public static final int        LOC_RLEG                 = 6;
    public static final int        LOC_LLEG                 = 7;

    // cockpit status
    public static final int        COCKPIT_OFF              = 0;
    public static final int        COCKPIT_ON               = 1;
    public static final int        COCKPIT_AIMED_SHOT       = 2;

    // gyro types
    public static final int         GYRO_UNKNOWN            = -1;
    public static final int         GYRO_STANDARD           = 0;
    public static final int         GYRO_XL                 = 1;
    public static final int         GYRO_COMPACT            = 2;
    public static final int         GYRO_HEAVY_DUTY         = 3;

    public static final String[]    GYRO_STRING = {"Standard Gyro",
                                                   "XL Gyro",
                                                   "Compact Gyro",
                                                   "Heavy Duty Gyro"};
    public static final String[]    GYRO_SHORT_STRING = {"Standard",
                                                         "XL",
                                                         "Compact",
                                                        "Heavy Duty"};

    // cockpit types
    public static final int         COCKPIT_UNKNOWN             = -1;
    public static final int         COCKPIT_STANDARD            = 0;
    public static final int         COCKPIT_TORSO_MOUNTED       = 1;
    public static final int         COCKPIT_SMALL               = 2;
    public static final int         COCKPIT_COMMAND_CONSOLE     = 3;
    public static final int         COCKPIT_DUAL                = 4;

    public static final String[]    COCKPIT_STRING = {"Standard Cockpit",
                                                      "Torso-Mounted Cockpit",
                                                      "Small Cockpit",
                                                      "Command Console",
                                                      "Dual Cockpit"};
    public static final String[]    COCKPIT_SHORT_STRING = {"Standard",
                                                            "Torso Mounted",
                                                            "Small",
                                                            "Command Console",
                                                            "Dual"};

    /**
     * The internal name for Mek Stealth systems.
     */
    public static final String STEALTH = "Mek Stealth";
    public static final String NULLSIG = "Mek Null Signature System";

    //jump types
    public static final int         JUMP_STANDARD = 0;
    public static final int         JUMP_IMPROVED = 1;
    public static final int         JUMP_BOOSTER = 2;
    public static final int         JUMP_DISPOSABLE = 3;

    // rear armor
    private int[] rearArmor;
    private int[] orig_rearArmor;

    private static int[] MASC_FAILURE = {2, 4, 6, 10, 12, 12, 12};
    private int nMASCLevel = 0; // MASCLevel is the # of turns MASC has been used previously
    private boolean usedMASC = false; // Has masc been used?
    private int sinksOn = -1;
    private int sinksOnNextRound = -1;
    private boolean autoEject = true;
    private int cockpitStatus = COCKPIT_ON;
    private int cockpitStatusNextRound = COCKPIT_ON;
    private int improvedJJ = -1;
    private int gyroType = GYRO_STANDARD;
    private int cockpitType = COCKPIT_STANDARD;
    private boolean hasCowl = false;
    private int cowlArmor = 0;

    public void addCockpit() {
        setCritical(LOC_HEAD, 0, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_LIFE_SUPPORT));
        setCritical(LOC_HEAD, 1, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_SENSORS));
        setCritical(LOC_HEAD, 2, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_COCKPIT));
        //slot index 3 is empty
        setCritical(LOC_HEAD, 4, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_SENSORS));
        setCritical(LOC_HEAD, 5, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_LIFE_SUPPORT));
    }

    public void addSmallCockpit() {
        setCritical(LOC_HEAD, 0, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_LIFE_SUPPORT));
        setCritical(LOC_HEAD, 1, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_SENSORS));
        setCritical(LOC_HEAD, 2, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_COCKPIT));
        //slot index 3 is empty
        setCritical(LOC_HEAD, 4, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_SENSORS));
        //slot index 5 is empty
    }

    public void addCommandConsole() {
        // This needs to be implemented.
        // FIXME
    }

    public void addDualCockpit() {
        // This needs to be implemented.
        // FIXME
    }

    public void addTorsoMountedCockpit() {
        // These indexes should NOT be hard-coded!!!
        // This causes problems with strange equipment mixes, like compact gyro + compact engine + torso-mounted cockpit.

        //slot index 0 is empty
        setCritical(LOC_HEAD, 1, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_SENSORS));
        //slot index 2 is empty
        //slot index 3 is empty
        setCritical(LOC_HEAD, 4, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_SENSORS));
        //slot index 5 is empty

        // The following lines were commented out to allow file readers to parse these out of the unit files.
        // Otherwise, hard-coded indexes screw up placement of these, since these crits can move based on the unit configuration.
        //setCritical(LOC_CT, 10, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_COCKPIT));
        //setCritical(LOC_CT, 11, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_SENSORS));

        //setCritical(LOC_LT, 0, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_LIFE_SUPPORT));
        //setCritical(LOC_RT, 0, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_LIFE_SUPPORT));
    }

    public void addGyro() {
        addCompactGyro();
        setCritical(LOC_CT, 5, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_GYRO));
        setCritical(LOC_CT, 6, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_GYRO));
        setGyroType(GYRO_STANDARD);
    }

    public void addCompactGyro() {
        setCritical(LOC_CT, 3, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_GYRO));
        setCritical(LOC_CT, 4, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_GYRO));
        setGyroType(GYRO_COMPACT);
    }

    public void addXLGyro() {
        addGyro();
        setCritical(LOC_CT, 10, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_GYRO));
        setCritical(LOC_CT, 11, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_GYRO));
        setGyroType(GYRO_XL);
    }

    public void addHeavyDutyGyro() {
        addGyro();
        setGyroType(GYRO_HEAVY_DUTY);
    }

    public void addEngineCrits() {
        int centerSlots[] = getEngine().getCenterTorsoCriticalSlots();
        for (int i = 0; i < centerSlots.length; i++) {
            setCritical(LOC_CT, centerSlots[i], new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_ENGINE));
        }

        int sideSlots[] = getEngine().getSideTorsoCriticalSlots();
        for (int i = 0; i < sideSlots.length; i++) {
            setCritical(LOC_LT, sideSlots[i], new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_ENGINE));
            setCritical(LOC_RT, sideSlots[i], new CriticalSlot(CriticalSlot.TYPE_SYSTEM, SYSTEM_ENGINE));
        }
    }

    /**
     * Construct a new, blank, mech.
     */
    public Mech() {
        this(Mech.GYRO_STANDARD, Mech.COCKPIT_STANDARD);
    }

    public Mech(int inGyroType, int inCockpitType) {
        super();

        gyroType = inGyroType;
        cockpitType = inCockpitType;

        rearArmor = new int[locations()];
        orig_rearArmor = new int[locations()];

        for (int i = 0; i < locations(); i++) {
            if (!hasRearArmor(i)) {
              initializeRearArmor(IArmorState.ARMOR_NA, i);
            }
        }

        if (cockpitType == COCKPIT_TORSO_MOUNTED) {
            addTorsoMountedCockpit();
        } else if (cockpitType == COCKPIT_SMALL) {
            addSmallCockpit();
        } else {
            addCockpit();
        }

        if (gyroType == GYRO_COMPACT) {
            addCompactGyro();
        } else if (gyroType == GYRO_XL) {
            addXLGyro();
        } else if (gyroType == GYRO_HEAVY_DUTY) {
            addHeavyDutyGyro();
        } else {
            addGyro();
        }

        // Standard leg crits
        setCritical(LOC_RLEG, 0, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_HIP));
        setCritical(LOC_RLEG, 1, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_UPPER_LEG));
        setCritical(LOC_RLEG, 2, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_LOWER_LEG));
        setCritical(LOC_RLEG, 3, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_FOOT));

        setCritical(LOC_LLEG, 0, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_HIP));
        setCritical(LOC_LLEG, 1, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_UPPER_LEG));
        setCritical(LOC_LLEG, 2, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_LOWER_LEG));
        setCritical(LOC_LLEG, 3, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, ACTUATOR_FOOT));

        // Player setting specify whether their Meks' automatic
        // ejection systems are disabled by default or not.
        this.autoEject = !PreferenceManager.getClientPreferences().defaultAutoejectDisabled();
    }

    public void setCowl (int armor) {
        hasCowl = true;
        cowlArmor = armor;
    }
    
    public int getCowlArmor () {
        if (hasCowl) return cowlArmor;
        return 0;
    }
    
    public boolean hasCowl () {
        return hasCowl;
    }
    
    // Damage the cowl. Returns amount of excess damage
    public int damageCowl (int amount) {
        if (hasCowl) {
            if (amount<cowlArmor) {
                cowlArmor -= amount;
                return 0;
            } else {
                amount -= cowlArmor;
                cowlArmor = 0;
                return amount;
            }
        } else {
            return amount; // No cowl - return full damage
        }
    }
    /**
     * Returns the location that transferred damage or crits will go to from a given location.
     */
    public static int getInnerLocation(int location) {
        switch(location) {
            case Mech.LOC_RT :
            case Mech.LOC_LT :
                return Mech.LOC_CT;
            case Mech.LOC_LLEG :
            case Mech.LOC_LARM :
                return Mech.LOC_LT;
            case Mech.LOC_RLEG :
            case Mech.LOC_RARM :
                return Mech.LOC_RT;
            default:
                return location;
        }
    }

    /**
     * Returns the location with the most restrictive firing arc for a weapon.
     */
    public static int mostRestrictiveLoc(int location1, int location2) {
        if (location1 == location2) {
            return location1;
        } else if (Mech.restrictScore(location1) >= Mech.restrictScore(location2)) {
            return location1;
        } else {
            return location2;
        }
    }

    /**
     * Helper function designed to give relative restrictiveness of locations.
     * Used for finding the most restrictive firing arc for a weapon.
     */
    public static int restrictScore(int location) {
        switch(location) {
            case Mech.LOC_RARM :
            case Mech.LOC_LARM :
                return 0;
            case Mech.LOC_RT :
            case Mech.LOC_LT :
                return 1;
            case Mech.LOC_CT :
                return 2;
            default :
                return 3;
        }
    }

    /**
     * Get the number of turns MASC has been used continuously.
     * <p/>
     * This method should <strong>only</strong> be used during serialization.
     *
     * @return  the <code>int</code> number of turns MASC has been used.
     */
    public int getMASCTurns() {
        return nMASCLevel;
    }

    /**
     * Set the number of turns MASC has been used continuously.
     * <p/>
     * This method should <strong>only</strong> be used during deserialization.
     *
     * @param   turns The <code>int</code> number of turns MASC has been used.
     */
    public void setMASCTurns( int turns ) {
        nMASCLevel = turns;
    }

    /**
     * Determine if MASC has been used this turn.
     * <p/>
     * This method should <strong>only</strong> be used during serialization.
     *
     * @return  <code>true</code> if MASC has been used.
     */
    public boolean isMASCUsed() {
        return usedMASC;
    }

    /**
     * Set whether MASC has been used.
     * <p/>
     * This method should <strong>only</strong> be used during deserialization.
     *
     * @param   used The <code>boolean</code> whether MASC has been used.
     */
    public void setMASCUsed( boolean used ) {
        usedMASC = used;
    }

    public int getMASCTarget() {
        return MASC_FAILURE[nMASCLevel] + 1;
    }

    public boolean checkForMASCFailure(MovePath md, Vector vDesc, Vector vCriticals) {
        if (md.hasActiveMASC()) {
            Report r;
            boolean bFailure = false;

            // If usedMASC is already set, then we've already checked MASC
            // this turn.  If we succeded before, return false.
            // If we failed before, the MASC was destroyed, and we wouldn't
            // have gotten here (hasActiveMASC would return false)
            if (!usedMASC) {
                Mounted equip = getMASC();
                int nRoll = Compute.d6(2);

                usedMASC = true;
                r = new Report(2365);
                r.subject = this.getId();
                r.addDesc(this);
                r.add(equip.getName());
                vDesc.addElement(r);
                r = new Report(2370);
                r.subject = this.getId();
                r.indent();
                r.add(getMASCTarget());
                r.add(nRoll);

                if (nRoll < getMASCTarget()) {
                    // uh oh
                    bFailure = true;
                    r.choose(false);
                    vDesc.addElement(r);

                    if(((MiscType)(equip.getType())).hasSubType(MiscType.S_SUPERCHARGER)) {
                        // do the damage - engine crits
                        int hits = 0;
                        int roll = Compute.d6(2);
                        r = new Report(6310);
                        r.subject = getId();
                        r.add(roll);
                        r.newlines = 0;
                        vDesc.addElement(r);
                        if (roll <= 7) {
                            //no effect
                            r = new Report(6005);
                            r.subject = getId();
                            r.newlines = 0;
                            vDesc.addElement(r);
                        } else if (roll >= 8 && roll <= 9) {
                            hits = 1;
                            r = new Report(6315);
                            r.subject = getId();
                            r.newlines = 0;
                            vDesc.addElement(r);
                        } else if (roll >= 10 && roll <= 11) {
                            hits = 2;
                            r = new Report(6320);
                            r.subject = getId();
                            r.newlines = 0;
                            vDesc.addElement(r);
                        } else if (roll == 12) {
                            hits = 3;
                            r = new Report(6325);
                            r.subject = getId();
                            r.newlines = 0;
                            vDesc.addElement(r);
                        }
                        for(int i=0;i<12 && hits > 0;i++) {
                            CriticalSlot cs = getCritical(LOC_CT, i);
                            if(cs.getType() == CriticalSlot.TYPE_SYSTEM && cs.getIndex() == SYSTEM_ENGINE) {
                                vCriticals.add(new Integer(LOC_CT));
                                vCriticals.add(cs);
                                //vDesc.addAll(server.applyCriticalHit(this, LOC_CT, cs, true));
                                hits--;
                            }
                        }
                    } else {
                        // do the damage.  Rules say 'effects identical as if you took 2 hip crits'. 
                        // We'll just do the hip crits
                        vCriticals.add(new Integer(LOC_RLEG));
                        vCriticals.add(getCritical(LOC_RLEG, 0));
                        vCriticals.add(new Integer(LOC_LLEG));
                        vCriticals.add(getCritical(LOC_LLEG, 0));
                        //getCritical(LOC_RLEG, 0).setDestroyed(true);
                        //getCritical(LOC_LLEG, 0).setDestroyed(true);
                        if (this instanceof QuadMech) {
                            //getCritical(LOC_RARM, 0).setDestroyed(true);
                            //getCritical(LOC_LARM, 0).setDestroyed(true);
                        vCriticals.add(new Integer(LOC_RARM));
                        vCriticals.add(getCritical(LOC_RARM, 0));
                        vCriticals.add(new Integer(LOC_LARM));
                        vCriticals.add(getCritical(LOC_LARM, 0));
                        }
                    }
                    if (equip.getType().hasFlag(MiscType.F_MASC)) {
                        equip.setDestroyed(true);
                        equip.setMode("Off");
                    }
                } else {
                    r.choose(true);
                    vDesc.addElement(r);
                }
            }
            return bFailure;
        }
        return false;
    }

    /**
     * OmniMechs have handles for Battle Armor squads to latch onto. Please
     * note, this method should only be called during this Mech's construction.
     * <p/>
     * Overrides <code>Entity#setOmni(boolean)</code>
     */
    public void setOmni( boolean omni ) {

        // Perform the superclass' action.
        super.setOmni( omni );

        // Add BattleArmorHandles to OmniMechs.
        if ( omni && !hasBattleArmorHandles()) {
            this.addTransporter( new BattleArmorHandles() );
        }
    }

    /**
     * Returns the number of locations in the entity
     */
    public int locations() {
        return NUM_MECH_LOCATIONS;
    }
    
    /**
     * Override Entity#newRound() method.
     */
    public void newRound(int roundNumber) {
        // Walk through the Mech's miscellaneous equipment before
        // we apply our parent class' newRound() functionality
        // because Mek Stealth is set by the Entity#newRound() method.
        for (Enumeration e = getMisc(); e.hasMoreElements(); ) {
            Mounted m = (Mounted) e.nextElement();
            MiscType mtype = (MiscType) m.getType();

            // Stealth can not be turned on if it's ECM is destroyed.
            if (Mech.STEALTH.equals(mtype.getInternalName())
                    && m.getLinked().isDestroyed()
                    && m.getLinked().isBreached()) {
                m.setMode("Off");
            }
        } // Check the next piece of equipment.

        super.newRound(roundNumber);
        // If MASC was used last turn, increment the counter,
        // otherwise decrement.  Then, clear the counter 
        if (usedMASC) {
            nMASCLevel++;
        } else {
            nMASCLevel = Math.max(0, nMASCLevel - 1);
        }

        // Clear the MASC flag 
        usedMASC = false;

        setSecondaryFacing(getFacing());

        // set heat sinks
        sinksOn = sinksOnNextRound;

        // update cockpit status
        cockpitStatus = cockpitStatusNextRound;
    } // End public void newRound()

    /**
     * Returns true if the location in question is a torso location
     */
    public boolean locationIsTorso(int loc) {
        return loc == LOC_CT || loc == LOC_RT || loc == LOC_LT;
    }
    
    /**
     * Returns true if the location in question is a leg
     */
    public boolean locationIsLeg(int loc) {
        return loc == LOC_LLEG || loc == LOC_RLEG;
    }
                                    
    /**
     * Count the number of destroyed or breached legs on the mech
     */
    public int countBadLegs() {
        int badLegs = 0;

        for (int i = 0; i < locations(); i++) {
            badLegs += (locationIsLeg(i) && isLocationBad(i)) ? 1 : 0;
        }

        return badLegs;
    }

    /**
     * Returns true if the entity has a hip crit.
     */
    public boolean hasHipCrit() {
        for ( int loc = 0; loc < NUM_MECH_LOCATIONS; loc++ ) {
            if ( legHasHipCrit( loc ) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true is the location is a leg and has a hip crit
     */   
    public boolean legHasHipCrit(int loc) {
        if (isLocationBad(loc)) {
            return false;
        }
          
        if (locationIsLeg(loc)) {
            return (getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                     Mech.ACTUATOR_HIP, loc) == 0);
        }
        
        return false;
    }
    
    /**
     * Count non-hip leg actuator crits
     */
      public int countLegActuatorCrits(int loc) {
        if (isLocationBad(loc)) {
            return 0;
        }
          
        int legCrits = 0;
        
        if (locationIsLeg(loc)) {
            if (getGoodCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_UPPER_LEG, loc) == 0) {
                legCrits++;
            }
            if (getGoodCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_LOWER_LEG, loc) == 0) {
                legCrits++;
            }
            if (getGoodCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_FOOT, loc) == 0) {
                legCrits++;
            }
        }

        return legCrits;
    }

    public boolean hasEndo() {
        if (getStructureType() == EquipmentType.T_STRUCTURE_UNKNOWN) {
            Enumeration eMisc = getMisc();
            while (eMisc.hasMoreElements()) {
                Mounted mounted = (Mounted)eMisc.nextElement();
                if (mounted.getDesc().indexOf(EquipmentType.getStructureTypeName(EquipmentType.T_STRUCTURE_ENDO_STEEL)) != -1) {
                    setStructureType(EquipmentType.T_STRUCTURE_ENDO_STEEL);
                    break;
                }
                if (mounted.getDesc().indexOf(EquipmentType.getStructureTypeName(EquipmentType.T_STRUCTURE_ENDO_PROTOTYPE)) != -1) {
                    setStructureType(EquipmentType.T_STRUCTURE_ENDO_PROTOTYPE);
                    break;
                }
            }
        }
        return ((getStructureType() == EquipmentType.T_STRUCTURE_ENDO_STEEL) || (getStructureType() == EquipmentType.T_STRUCTURE_ENDO_PROTOTYPE));
    }

    public boolean hasCompositeStructure() {
        if (getStructureType() == EquipmentType.T_STRUCTURE_UNKNOWN) {
            Enumeration eMisc = getMisc();
            while (eMisc.hasMoreElements()) {
                Mounted mounted = (Mounted)eMisc.nextElement();
                if (mounted.getDesc().indexOf(EquipmentType.getStructureTypeName(EquipmentType.T_STRUCTURE_COMPOSITE)) != -1) {
                    setStructureType(EquipmentType.T_STRUCTURE_COMPOSITE);
                    break;
                }
            }
        }
        return (getStructureType() == EquipmentType.T_STRUCTURE_COMPOSITE);
    }

    public boolean hasReinforcedStructure() {
        if (getStructureType() == EquipmentType.T_STRUCTURE_UNKNOWN) {
            Enumeration eMisc = getMisc();
            while (eMisc.hasMoreElements()) {
                Mounted mounted = (Mounted)eMisc.nextElement();
                if (mounted.getDesc().indexOf(EquipmentType.getStructureTypeName(EquipmentType.T_STRUCTURE_REINFORCED)) != -1) {
                    setStructureType(EquipmentType.T_STRUCTURE_REINFORCED);
                    break;
                }
            }
        }
        return (getStructureType() == EquipmentType.T_STRUCTURE_REINFORCED);
    }

    public boolean hasFerro() {
        Enumeration eMisc = getMisc();
        while (eMisc.hasMoreElements()) {
            Mounted mounted = (Mounted)eMisc.nextElement();
            if (mounted.getDesc().indexOf(EquipmentType.getArmorTypeName(EquipmentType.T_ARMOR_FERRO_FIBROUS)) != -1 ||
                mounted.getDesc().indexOf(EquipmentType.getArmorTypeName(EquipmentType.T_ARMOR_FERRO_FIBROUS_PROTO)) != -1) {
                return true;
            }
        }
        return false;
    }

    public boolean hasMASC() {
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            MiscType mtype = (MiscType)((Mounted)i.nextElement()).getType();
            if (mtype.hasFlag(MiscType.F_MASC)) {
                return true;
            }
        }
        return false;
    }

    public Mounted getMASC() {
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted m = (Mounted)i.nextElement();
            MiscType mtype = (MiscType)m.getType();
            if (mtype.hasFlag(MiscType.F_MASC) && m.isReady()) {
                return m;
            }
        }
        return null;
    }

    /**
     * Checks if a mech has an armed MASC system. Note
     *  that the mech will have to exceed its normal run to actually
     *  engage the MASC system
     */
    public boolean hasArmedMASC() {
        for (Enumeration e = getEquipment(); e.hasMoreElements(); ) {
            Mounted m = (Mounted)e.nextElement();
            if (!m.isDestroyed() && !m.isBreached() && m.getType() instanceof MiscType &&
                m.getType().hasFlag(MiscType.F_MASC)
                && m.curMode().equals("Armed")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Same
     */
    public boolean hasTSM() {
        for (Enumeration e = getEquipment(); e.hasMoreElements(); ) {
            Mounted m = (Mounted)e.nextElement();
            if (m.getType() instanceof MiscType && m.getType().hasFlag(MiscType.F_TSM)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasStealth() {
        for ( Enumeration equips = getMisc(); equips.hasMoreElements(); ) {
            Mounted mEquip = (Mounted) equips.nextElement();
            MiscType mtype = (MiscType) mEquip.getType();
            if ( mtype.hasFlag(MiscType.F_STEALTH) ) {
                // The Mek has Stealth Armor or null signature system.
                return true;
            }
        }
        return false;
    }

    /**
     * Depends on engine type
     */
    public int getStandingHeat() {
        return engine.getStandingHeat();
    }

    public void setEngine(Engine e) {
        engine = e;
        if (e.engineValid) {
            setOriginalWalkMP(calculateWalk());
        }
    }

    protected int calculateWalk() {
        return getEngine().getRating() / (int)this.weight;
    }

    /**
     * Depends on engine type
     */
    public int getWalkHeat() {
        return engine.getWalkHeat();
    }

    /**
     * Potentially adjust runMP for MASC
     */
    
    public int getRunMP(boolean gravity) {
        if (hasArmedMASC()) {
            return (getWalkMP(gravity) * 2)-(getArmorType()==EquipmentType.T_ARMOR_HARDENED?1:0);
        }
        return super.getRunMP(gravity)-(getArmorType()==EquipmentType.T_ARMOR_HARDENED?1:0);
    }

    /**
     * Returns run MP without considering MASC
     */
    
    public int getRunMPwithoutMASC(boolean gravity) {
        return super.getRunMP(gravity)-(getArmorType()==EquipmentType.T_ARMOR_HARDENED?1:0);
    }
    public int getOriginalRunMPwithoutMASC() {
        return super.getRunMP(false)-(getArmorType()==EquipmentType.T_ARMOR_HARDENED?1:0);
    }

    /**
     * Returns this entity's running/flank mp as a string.
     */
    public String getRunMPasString() {
        if (hasArmedMASC()) {
            return getRunMPwithoutMASC() + "(" + getRunMP() + ")";
        } else {
            return Integer.toString(getRunMP());
        }

    }

    /**
     * Depends on engine type
     */
    public int getRunHeat() {
        return engine.getRunHeat();
    }

    /**
     * This mech's jumping MP modified for missing jump jets
     */
    public int getJumpMP() {
        int jump = 0;

        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            if (mounted.getType().hasFlag(MiscType.F_JUMP_JET) && !mounted.isDestroyed() && !mounted.isBreached()) {
                jump++;
            } else if (mounted.getType().hasFlag(MiscType.F_JUMP_BOOSTER) && !mounted.isDestroyed() && !mounted.isBreached()) {
                jump = getOriginalJumpMP();
                break;
            }
        }
        
        return applyGravityEffectsOnMP(jump);
    }

    public int getJumpType() {
        // -1 means uninitialized
        // 0 means standard
        // 1 means improved
        if (improvedJJ < 0) {
            for (Object oMount : miscList) {
                Mounted m = (Mounted)oMount;
                if ((m.getType().hasFlag(MiscType.F_JUMP_JET))
                        && ((m.getType().getTechLevel() == TechConstants.T_IS_LEVEL_3)
                            || (m.getType().getTechLevel() == TechConstants.T_CLAN_LEVEL_3))) {
                    improvedJJ = JUMP_IMPROVED;
                    break;
                } else if (m.getType().hasFlag(MiscType.F_JUMP_BOOSTER)) {
                    improvedJJ = JUMP_BOOSTER;
                    break;
                }
            }
        }
        return improvedJJ;
    }

    /**
     * We need to override this here, because mechs generate heat when jumping.
     */
    public int getJumpHeat(int movedMP) {
        switch (getJumpType()) {
            case JUMP_IMPROVED:
                return engine.getJumpHeat(movedMP/2 + movedMP%2);
            case JUMP_BOOSTER:
            case JUMP_DISPOSABLE:
                return 0;
            default:
                return engine.getJumpHeat(movedMP);
        }
    }

    /**
     * Returns this mech's jumping MP, modified for missing & underwater jets and gravity.
     */
    public int getJumpMPWithTerrain() {
        if (getPosition() == null || getJumpType() == JUMP_BOOSTER) {
            return getJumpMP();
        }
        int waterLevel = 0;
        if (!isOffBoard()) {
            waterLevel = game.getBoard().getHex(getPosition()).terrainLevel(Terrains.WATER);
        }        
        if (waterLevel <= 0 || getElevation() >= 0) {
            return getJumpMP();
        } else if (waterLevel > 1) {
            return 0;
        } else { // waterLevel == 1
            return applyGravityEffectsOnMP(torsoJumpJets());
        }
    }
    
    /**
     * Returns the number of (working) jump jets mounted in the torsos.
     */
    public int torsoJumpJets() {
        int jump = 0;
        
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            if (mounted.getType().hasFlag(MiscType.F_JUMP_JET) && !mounted.isDestroyed() && !mounted.isBreached()
            && locationIsTorso(mounted.getLocation())) {
                jump++;
            }
        }
        
        return jump;
    }
    
    /**
     * Returns the elevation of this entity.  Mechs do funny stuff in the 
     * middle of a DFA.
     */
    public int getElevation() {
        int cElev = super.getElevation();
        if (!isMakingDfa()) {
            return cElev;
        }
        // otherwise, we are one elevation above our hex or the target's hex,
        // whichever is higher
        int tElev = game.getBoard().getHex(displacementAttack.getTargetPos()).floor();
        return Math.max(cElev, tElev) + 1;
    }
    
    /**
     * Return the height of this mech above the terrain.
     */
    public int height() {
        return isProne() ? 0 : 1;
    }
    
    /**
     * Adds heat sinks to the engine.  Uses clan/normal depending on the
     * currently set techLevel
     */
    public void addEngineSinks(int totalSinks, boolean dblSinks) {
        addEngineSinks(totalSinks, dblSinks, isClan());
    }
    
    /**
     * Adds heat sinks to the engine.  Adds either the engine capacity, or
     * the entire number of heat sinks, whichever is less
     */
    public void addEngineSinks(int totalSinks, boolean dblSinks, boolean clan) {
        // this relies on these being the correct internalNames for these items
        EquipmentType sinkType;
        if (dblSinks) {
            sinkType = EquipmentType.get(clan ? "CLDoubleHeatSink" : "ISDoubleHeatSink");
        } else {
            sinkType = EquipmentType.get("Heat Sink");
        }
        
        if (sinkType == null) {
            System.out.println("Mech: can't find heat sink to add to engine");
        }
        
        int toAllocate = Math.min(totalSinks, getEngine().integralHeatSinkCapacity());
        
        if (toAllocate == 0 && getEngine().isFusion()) {
            System.out.println("Mech: not putting any heat sinks in the engine?!?!");
        }
        
        for (int i = 0; i < toAllocate; i++) {
            try {
                addEquipment(new Mounted(this, sinkType), Mech.LOC_NONE, false);
            } catch (LocationFullException ex) {
                // um, that's impossible.
            }
        }
    }
    
    /**
     * Returns extra heat generated by engine crits
     */
    public int getEngineCritHeat() {
        int engineCritHeat = 0;
        if (!isShutDown() && getEngine().isFusion()) {
            engineCritHeat += 5 * getHitCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_ENGINE, Mech.LOC_CT);
            engineCritHeat += 5 * getHitCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_ENGINE, Mech.LOC_LT);
            engineCritHeat += 5 * getHitCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_ENGINE, Mech.LOC_RT);
        }
        return engineCritHeat;
    }
    
    /**
     * Returns the number of heat sinks, functional or not.
     */
    public int heatSinks() {
        int sinks = 0;
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            EquipmentType etype= mounted.getType();
            if (etype.hasFlag(MiscType.F_HEAT_SINK)
            || etype.hasFlag(MiscType.F_DOUBLE_HEAT_SINK)) {
                sinks++;
            }
        }
        return sinks;
    }
    
    /**
     * Returns the about of heat that the entity can sink each 
     * turn.
     */
    public int getHeatCapacity() {
        int capacity = 0;
        boolean doubleSinks=false;
        
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            if (mounted.isDestroyed() || mounted.isBreached()) {
                continue;
            }
            if (mounted.getType().hasFlag(MiscType.F_HEAT_SINK)) {
                capacity++;
            } else if(mounted.getType().hasFlag(MiscType.F_DOUBLE_HEAT_SINK)) {
                doubleSinks=true;
                capacity += 2;
            }
        }
        //test for disabled sinks
        capacity-=(getNumberOfSinks() - getActiveSinks())*(doubleSinks? 2: 1);
        
        return capacity;
    }
    
    /**
     * Returns the about of heat that the entity can sink each
     * turn, factoring for water.
     */
    public int getHeatCapacityWithWater() {
        if(hasLaserHeatSinks()) {
            return getHeatCapacity();
        }
        return getHeatCapacity() + Math.min(sinksUnderwater(), 6);
    }
    
    /**
     * Gets the number of heat sinks that are underwater.
     */
    private int sinksUnderwater() {
        if (getPosition() == null ||
            isOffBoard()) {
            return 0;
        }
        
        IHex curHex = game.getBoard().getHex(getPosition());
        // are we even in water?  is it depth 1+
        if (curHex.terrainLevel(Terrains.WATER) <= 0 || getElevation() >= 0) {
            return 0;
        }
        
        // are we entirely underwater?
        if (isProne() || curHex.terrainLevel(Terrains.WATER) >= 2) {
            return getHeatCapacity();
        }
        
        // okay, count leg sinks
        int sinksUnderwater = 0;
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            if (mounted.isDestroyed() || mounted.isBreached() || !locationIsLeg(mounted.getLocation())) {
                continue;
            }
            if (mounted.getType().hasFlag(MiscType.F_HEAT_SINK)) {
                sinksUnderwater++;
            } else if (mounted.getType().hasFlag(MiscType.F_DOUBLE_HEAT_SINK)) {
                sinksUnderwater += 2;
            }
        }
        return sinksUnderwater;
    }

    /**
     * Returns the name of the type of movement used.
     * This is mech-specific.
     */
    public String getMovementString(int mtype) {
        switch(mtype) {
        case IEntityMovementType.MOVE_SKID :
            return "Skidded";
        case IEntityMovementType.MOVE_NONE :
            return "None";
        case IEntityMovementType.MOVE_WALK :
            return "Walked";
        case IEntityMovementType.MOVE_RUN :
            return "Ran";
        case IEntityMovementType.MOVE_JUMP :
            return "Jumped";
        default :
            return "Unknown!";
        }
    }
    
    /**
     * Returns the name of the type of movement used.
     * This is mech-specific.
     */
    public String getMovementAbbr(int mtype) {
        switch(mtype) {
        case IEntityMovementType.MOVE_SKID :
            return "S";
        case IEntityMovementType.MOVE_NONE :
            return "N";
        case IEntityMovementType.MOVE_WALK :
            return "W";
        case IEntityMovementType.MOVE_RUN :
            return "R";
        case IEntityMovementType.MOVE_JUMP :
            return "J";
        default :
            return "?";
        }
    }
    
    public boolean canChangeSecondaryFacing() {
        return !isProne();
    }
  
    /**
     * Can this mech torso twist in the given direction?
     */
    public boolean isValidSecondaryFacing(int dir) {
        int rotate = dir - getFacing();
        if (canChangeSecondaryFacing()) {
            return rotate == 0 || rotate == 1 || rotate == -1 || rotate == -5;
        } else {
            return rotate == 0;
        }
    }

    /**
     * Return the nearest valid direction to torso twist in
     */
    public int clipSecondaryFacing(int dir) {
        if (isValidSecondaryFacing(dir)) {
            return dir;
        }
        // can't twist while prone
        if (!canChangeSecondaryFacing()) {
            return getFacing();
        }
        // otherwise, twist once in the appropriate direction
        final int rotate = (dir + (6 - getFacing())) % 6;
        return rotate >= 3 ? (getFacing() + 5) % 6 : (getFacing() + 1) % 6;
    }
    
    public boolean hasRearArmor(int loc) {
        return loc == LOC_CT || loc == LOC_RT || loc == LOC_LT;
    }
    
    /**
     * Returns the amount of armor in the location specified.  Mech version,
     * handles rear armor.
     */
    public int getArmor(int loc, boolean rear) {
        if (rear && hasRearArmor(loc)) {
            return rearArmor[loc];
        } else {
            return super.getArmor(loc, rear);
        }
    }

    /**
     * Returns the original amount of armor in the location specified.  Mech version,
     * handles rear armor.
     */
    public int getOArmor(int loc, boolean rear) {
        if (rear && hasRearArmor(loc)) {
            return orig_rearArmor[loc];
        } else {
            return super.getOArmor(loc, rear);
        }
    }

    /**
     * Sets the amount of armor in the location specified.  Mech version, handles
     * rear armor.
     */
    public void setArmor(int val, int loc, boolean rear) {
        if (rear && hasRearArmor(loc)) {
            rearArmor[loc] = val;
        } else {
            super.setArmor(val, loc, rear);
        }
    }

    /**
     * Initializes the rear armor on the mech. Sets the original and starting point
     * of the armor to the same number.
     */
      public void initializeRearArmor(int val, int loc) {
        orig_rearArmor[loc] = val;
        setArmor(val, loc, true);
      }
      
    /**
     * Returns the Compute.ARC that the weapon fires into.
     */
    public int getWeaponArc(int wn) {
        final Mounted mounted = getEquipment(wn);
        // rear mounted?
        if (mounted.isRearMounted()) {
            return Compute.ARC_REAR;
        }
        // front mounted
        switch(mounted.getLocation()) {
        case LOC_HEAD :
        case LOC_CT :
        case LOC_RT :
        case LOC_LT :
        case LOC_RLEG :
        case LOC_LLEG :
            return Compute.ARC_FORWARD;
        case LOC_RARM :
            return getArmsFlipped() ? Compute.ARC_REAR : Compute.ARC_RIGHTARM;
        case LOC_LARM :
            return getArmsFlipped() ? Compute.ARC_REAR : Compute.ARC_LEFTARM;
        default :
            return Compute.ARC_360;
        }
    }

    /**
     * Returns true if this weapon fires into the secondary facing arc.  If
     * false, assume it fires into the primary.
     */
    public boolean isSecondaryArcWeapon(int weaponId) {
        // leg-mounted weapons fire into the primary arc, always
        if (getEquipment(weaponId).getLocation() == LOC_RLEG || getEquipment(weaponId).getLocation() == LOC_LLEG) {
            return false;
        }
        // other weapons into the secondary
        return true;
    }
    
    /**
     * Rolls up a hit location
     */
    public HitData rollHitLocation(int table, int side) {
        return rollHitLocation(table, side, LOC_NONE, IAimingModes.AIM_MODE_NONE);
    }     
     
    public HitData rollHitLocation(int table, int side, int aimedLocation, int aimingMode) {
        int roll = -1;
        
        if ((aimedLocation != LOC_NONE) &&
            (aimingMode == IAimingModes.AIM_MODE_TARG_COMP)) {
                return new HitData(aimedLocation, side == ToHitData.SIDE_REAR, true);               
        }
        
        if ((aimedLocation != LOC_NONE) &&
            (aimingMode == IAimingModes.AIM_MODE_IMMOBILE)) {
            roll = Compute.d6(2);
            
            if ((5 < roll) && (roll < 9)) {
                return new HitData(aimedLocation, side == ToHitData.SIDE_REAR, true);
            }
        }

        if(table == ToHitData.HIT_NORMAL || table == ToHitData.HIT_PARTIAL_COVER) {
            roll = Compute.d6(2);
            try {
                PrintWriter pw = PreferenceManager.getClientPreferences().getMekHitLocLog();
                if ( pw != null ) {
                    pw.print( table );
                    pw.print( "\t" );
                    pw.print( side );
                    pw.print( "\t" );
                    pw.println( roll );
                }
            } catch ( Throwable thrown ) {
                thrown.printStackTrace();
            }
            if(side == ToHitData.SIDE_FRONT) {
                // normal front hits
                if(game.getOptions().booleanOption("quad_hit_location")
                        && isProne()) {
                    switch( roll ) {
                    case 2:
                        return tac(table, side, Mech.LOC_CT, false);
                    case 3:
                        return new HitData(Mech.LOC_RLEG);
                    case 4:
                    case 5:
                        return new HitData(Mech.LOC_RARM);
                    case 6:
                        return new HitData(Mech.LOC_RT);
                    case 7:
                        return new HitData(Mech.LOC_CT);
                    case 8:
                        return new HitData(Mech.LOC_LT);
                    case 9:
                    case 10:
                        return new HitData(Mech.LOC_LARM);
                    case 11:
                        return new HitData(Mech.LOC_LLEG);
                    case 12:
                        return new HitData(Mech.LOC_HEAD);
                    }
                } else {
                    switch( roll ) {
                    case 2:
                        return tac(table, side, Mech.LOC_CT, false);
                    case 3:
                    case 4:
                        return new HitData(Mech.LOC_RARM);
                    case 5:
                        return new HitData(Mech.LOC_RLEG);
                    case 6:
                        return new HitData(Mech.LOC_RT);
                    case 7:
                        return new HitData(Mech.LOC_CT);
                    case 8:
                        return new HitData(Mech.LOC_LT);
                    case 9:
                        return new HitData(Mech.LOC_LLEG);
                    case 10:
                    case 11:
                        return new HitData(Mech.LOC_LARM);
                    case 12:
                        return new HitData(Mech.LOC_HEAD);
                    }
                }
            }
            else if(side == ToHitData.SIDE_LEFT) {
                // normal left side hits
                switch( roll ) {
                case 2:
                    return tac(table, side, Mech.LOC_LT, false);
                case 3:
                    return new HitData(Mech.LOC_LLEG);
                case 4:
                case 5:
                    return new HitData(Mech.LOC_LARM);
                case 6:
                    return new HitData(Mech.LOC_LLEG);
                case 7:
                    return new HitData(Mech.LOC_LT);
                case 8:
                    return new HitData(Mech.LOC_CT);
                case 9:
                    return new HitData(Mech.LOC_RT);
                case 10:
                    return new HitData(Mech.LOC_RARM);
                case 11:
                    return new HitData(Mech.LOC_RLEG);
                case 12:
                    return new HitData(Mech.LOC_HEAD);
                }
            }
            else if(side == ToHitData.SIDE_RIGHT) {
                // normal right side hits
                switch( roll ) {
                case 2:
                    return tac(table, side, Mech.LOC_RT, false);
                case 3:
                    return new HitData(Mech.LOC_RLEG);
                case 4:
                case 5:
                    return new HitData(Mech.LOC_RARM);
                case 6:
                    return new HitData(Mech.LOC_RLEG);
                case 7:
                    return new HitData(Mech.LOC_RT);
                case 8:
                    return new HitData(Mech.LOC_CT);
                case 9:
                    return new HitData(Mech.LOC_LT);
                case 10:
                    return new HitData(Mech.LOC_LARM);
                case 11:
                    return new HitData(Mech.LOC_LLEG);
                case 12:
                    return new HitData(Mech.LOC_HEAD);
                }
            }
            else if(side == ToHitData.SIDE_REAR) {
                // normal rear hits
                if(game.getOptions().booleanOption("quad_hit_location")
                        && isProne()) {
                    switch( roll ) {
                    case 2:
                        return tac(table, side, Mech.LOC_CT, true);
                    case 3:
                        return new HitData(Mech.LOC_RARM, true);
                    case 4:
                    case 5:
                        return new HitData(Mech.LOC_RLEG, true);
                    case 6:
                        return new HitData(Mech.LOC_RT, true);
                    case 7:
                        return new HitData(Mech.LOC_CT, true);
                    case 8:
                        return new HitData(Mech.LOC_LT, true);
                    case 9:
                    case 10:
                        return new HitData(Mech.LOC_LLEG, true);
                    case 11:
                        return new HitData(Mech.LOC_LARM, true);
                    case 12:
                        return new HitData(Mech.LOC_HEAD, true);
                    }
                } else {
                    switch( roll ) {
                    case 2:
                        return tac(table, side, Mech.LOC_CT, true);
                    case 3:
                    case 4:
                        return new HitData(Mech.LOC_RARM, true);
                    case 5:
                        return new HitData(Mech.LOC_RLEG, true);
                    case 6:
                        return new HitData(Mech.LOC_RT, true);
                    case 7:
                        return new HitData(Mech.LOC_CT, true);
                    case 8:
                        return new HitData(Mech.LOC_LT, true);
                    case 9:
                        return new HitData(Mech.LOC_LLEG, true);
                    case 10:
                    case 11:
                        return new HitData(Mech.LOC_LARM, true);
                    case 12:
                        return new HitData(Mech.LOC_HEAD, true);
                    }
                }
            }
        }
        if(table == ToHitData.HIT_PUNCH) {
            roll = Compute.d6(1);
            try {
                PrintWriter pw = PreferenceManager.getClientPreferences().getMekHitLocLog();
                if ( pw!= null ) {
                    pw.print( table );
                    pw.print( "\t" );
                    pw.print( side );
                    pw.print( "\t" );
                    pw.println( roll );
                }
            } catch ( Throwable thrown ) {
                thrown.printStackTrace();
            }
            if(side == ToHitData.SIDE_FRONT) {
                // front punch hits
                switch( roll ) {
                case 1:
                    return new HitData(Mech.LOC_LARM);
                case 2:
                    return new HitData(Mech.LOC_LT);
                case 3:
                    return new HitData(Mech.LOC_CT);
                case 4:
                    return new HitData(Mech.LOC_RT);
                case 5:
                    return new HitData(Mech.LOC_RARM);
                case 6:
                    return new HitData(Mech.LOC_HEAD);
                }
            }
            if(side == ToHitData.SIDE_LEFT) {
                // left side punch hits
                switch( roll ) {
                case 1:
                case 2:
                    return new HitData(Mech.LOC_LT);
                case 3:
                    return new HitData(Mech.LOC_CT);
                case 4:
                case 5:
                    return new HitData(Mech.LOC_LARM);
                case 6:
                    return new HitData(Mech.LOC_HEAD);
                }
            }
            if(side == ToHitData.SIDE_RIGHT) {
                // right side punch hits
                switch( roll ) {
                case 1:
                case 2:
                    return new HitData(Mech.LOC_RT);
                case 3:
                    return new HitData(Mech.LOC_CT);
                case 4:
                case 5:
                    return new HitData(Mech.LOC_RARM);
                case 6:
                    return new HitData(Mech.LOC_HEAD);
                }
            }
            if(side == ToHitData.SIDE_REAR) {
                // rear punch hits
                switch( roll ) {
                case 1:
                    return new HitData(Mech.LOC_LARM, true);
                case 2:
                    return new HitData(Mech.LOC_LT, true);
                case 3:
                    return new HitData(Mech.LOC_CT, true);
                case 4:
                    return new HitData(Mech.LOC_RT, true);
                case 5:
                    return new HitData(Mech.LOC_RARM, true);
                case 6:
                    return new HitData(Mech.LOC_HEAD, true);
                }
            }
        }
        if(table == ToHitData.HIT_KICK) {
            roll = Compute.d6(1);
            try {
                PrintWriter pw = PreferenceManager.getClientPreferences().getMekHitLocLog();                
                if ( pw != null ) {
                    pw.print( table );
                    pw.print( "\t" );
                    pw.print( side );
                    pw.print( "\t" );
                    pw.println( roll );
                }
            } catch ( Throwable thrown ) {
                thrown.printStackTrace();
            }
            if(side == ToHitData.SIDE_FRONT || side == ToHitData.SIDE_REAR) {
                // front/rear kick hits
                switch( roll ) {
                case 1:
                case 2:
                case 3:
                    return new HitData(Mech.LOC_RLEG,
                                       (side == ToHitData.SIDE_REAR));
                case 4:
                case 5:
                case 6:
                    return new HitData(Mech.LOC_LLEG,
                                       (side == ToHitData.SIDE_REAR));
                }
            }
            if(side == ToHitData.SIDE_LEFT) {
                // left side kick hits
                return new HitData(Mech.LOC_LLEG);
            }
            if(side == ToHitData.SIDE_RIGHT) {
                // right side kick hits
                return new HitData(Mech.LOC_RLEG);
            }
        }
        if(table == ToHitData.HIT_SWARM) {
            roll = Compute.d6(2);
            try {
                PrintWriter pw = PreferenceManager.getClientPreferences().getMekHitLocLog();
                if ( pw != null ) {
                    pw.print( table );
                    pw.print( "\t" );
                    pw.print( side );
                    pw.print( "\t" );
                    pw.println( roll );
                }
            } catch ( Throwable thrown ) {
                thrown.printStackTrace();
            }
            // Swarm attack locations.
            switch( roll ) {
            case 2:
                return new HitData(Mech.LOC_HEAD, false,
                                   HitData.EFFECT_CRITICAL);
            case 3:
                return new HitData(Mech.LOC_CT, true,
                                   HitData.EFFECT_CRITICAL);
            case 4:
                return new HitData(Mech.LOC_RT, true,
                                   HitData.EFFECT_CRITICAL);
            case 5:
                return new HitData(Mech.LOC_RT, false,
                                   HitData.EFFECT_CRITICAL);
            case 6:
                return new HitData(Mech.LOC_RARM, false,
                                   HitData.EFFECT_CRITICAL);
            case 7:
                return new HitData(Mech.LOC_CT, false,
                                   HitData.EFFECT_CRITICAL);
            case 8:
                return new HitData(Mech.LOC_LARM, false,
                                   HitData.EFFECT_CRITICAL);
            case 9:
                return new HitData(Mech.LOC_LT, false,
                                   HitData.EFFECT_CRITICAL);
            case 10:
                return new HitData(Mech.LOC_LT, true,
                                   HitData.EFFECT_CRITICAL);
            case 11:
                return new HitData(Mech.LOC_CT, true,
                                   HitData.EFFECT_CRITICAL);
            case 12:
                return new HitData(Mech.LOC_HEAD, false,
                                   HitData.EFFECT_CRITICAL);
            }
        }
        if(table == ToHitData.HIT_ABOVE) {
            roll = Compute.d6(1);
            try {
                PrintWriter pw = PreferenceManager.getClientPreferences().getMekHitLocLog();
                if ( pw != null ) {
                    pw.print( table );
                    pw.print( "\t" );
                    pw.print( side );
                    pw.print( "\t" );
                    pw.println( roll );
                }
            } catch ( Throwable thrown ) {
                thrown.printStackTrace();
            }
            // Hits from above.
            switch( roll ) {
            case 1:
                return new HitData( Mech.LOC_LARM,
                                    (side == ToHitData.SIDE_REAR)  );
            case 2:
                return new HitData( Mech.LOC_LT,
                                    (side == ToHitData.SIDE_REAR) );
            case 3:
                return new HitData( Mech.LOC_CT,
                                    (side == ToHitData.SIDE_REAR) );
            case 4:
                return new HitData( Mech.LOC_RT,
                                    (side == ToHitData.SIDE_REAR) );
            case 5:
                return new HitData( Mech.LOC_RARM,
                                    (side == ToHitData.SIDE_REAR)  );
            case 6:
                return new HitData( Mech.LOC_HEAD,
                                    (side == ToHitData.SIDE_REAR)  );
            }
        }
        if(table == ToHitData.HIT_BELOW) {
            roll = Compute.d6(1);
            try {
                PrintWriter pw = PreferenceManager.getClientPreferences().getMekHitLocLog();
                if ( pw != null ) {
                    pw.print( table );
                    pw.print( "\t" );
                    pw.print( side );
                    pw.print( "\t" );
                    pw.println( roll );
                }
            } catch ( Throwable thrown ) {
                thrown.printStackTrace();
            }
            // Hits from below.
            switch( roll ) {
            case 1:
                return new HitData( Mech.LOC_LLEG,
                                    (side == ToHitData.SIDE_REAR)  );
            case 2:
                return new HitData( Mech.LOC_LLEG,
                                    (side == ToHitData.SIDE_REAR)  );
            case 3:
                return new HitData( Mech.LOC_LT,
                                    (side == ToHitData.SIDE_REAR) );
            case 4:
                return new HitData( Mech.LOC_RT,
                                    (side == ToHitData.SIDE_REAR) );
            case 5:
                return new HitData( Mech.LOC_RLEG,
                                    (side == ToHitData.SIDE_REAR)  );
            case 6:
                return new HitData( Mech.LOC_RLEG,
                                    (side == ToHitData.SIDE_REAR)  );
            }
        }
        return null;
    }
    
    /**
     * Called when a thru-armor-crit is rolled.  Checks the game options and
     * either returns no critical hit, rolls a floating crit, or returns a TAC 
     * in the specified location.
     */
    protected HitData tac(int table, int side, int location, boolean rear) {
        if (game.getOptions().booleanOption("no_tac")) {
            return new HitData(location, rear);
        } else if (game.getOptions().booleanOption("floating_crits")) {
            HitData hd = rollHitLocation(table, side);
            return new HitData(hd.getLocation(), hd.isRear(), HitData.EFFECT_CRITICAL);
        } else {
            return new HitData(location, rear, HitData.EFFECT_CRITICAL);
        }
    }

    
    /**
     * Gets the location that excess damage transfers to
     */
    public HitData getTransferLocation(HitData hit) {
        switch(hit.getLocation()) {
        case LOC_RT :
        case LOC_LT :
            return new HitData(LOC_CT, hit.isRear());
        case LOC_LLEG :
        case LOC_LARM :
            return new HitData(LOC_LT, hit.isRear());
        case LOC_RLEG :
        case LOC_RARM :
            return new HitData(LOC_RT, hit.isRear());
        case LOC_HEAD :
        case LOC_CT :
        default:
            return new HitData(LOC_DESTROYED);
        }
    }
    
    /**
     * Gets the location that is destroyed recursively
     */
    public int getDependentLocation(int loc) {
        switch(loc) {
        case LOC_RT :
            return LOC_RARM;
        case LOC_LT :
            return LOC_LARM;
        case LOC_LLEG :
        case LOC_LARM :
        case LOC_RLEG :
        case LOC_RARM :
        case LOC_HEAD :
        case LOC_CT :
        default:
            return LOC_NONE;
        }
    }
    
    /**
     * Sets the internal structure for the mech.
     * 
     * @param head head
     * @param ct center torso
     * @param t right/left torso
     * @param arm right/left arm
     * @param leg right/left leg
     */
    public abstract void setInternal(int head, int ct, int t, int arm, int leg);
    
    /**
     * Set the internal structure to the appropriate value for the mech's
     * weight class
     */
    public void autoSetInternal() {
        // stupid irregular table... grr.
        switch ((int)weight) {
            //                     H, CT,TSO,ARM,LEG
            case 10  : setInternal(3,  4,  3,  1,  2); break;
            case 15  : setInternal(3,  5,  4,  2,  3); break;
            case 20  : setInternal(3,  6,  5,  3,  4); break;
            case 25  : setInternal(3,  8,  6,  4,  6); break;
            case 30  : setInternal(3, 10,  7,  5,  7); break;
            case 35  : setInternal(3, 11,  8,  6,  8); break;
            case 40  : setInternal(3, 12, 10,  6, 10); break;
            case 45  : setInternal(3, 14, 11,  7, 11); break;
            case 50  : setInternal(3, 16, 12,  8, 12); break;
            case 55  : setInternal(3, 18, 13,  9, 13); break;
            case 60  : setInternal(3, 20, 14, 10, 14); break;
            case 65  : setInternal(3, 21, 15, 10, 15); break;
            case 70  : setInternal(3, 22, 15, 11, 15); break;
            case 75  : setInternal(3, 23, 16, 12, 16); break;
            case 80  : setInternal(3, 25, 17, 13, 17); break;
            case 85  : setInternal(3, 27, 18, 14, 18); break;
            case 90  : setInternal(3, 29, 19, 15, 19); break;
            case 95  : setInternal(3, 30, 20, 16, 20); break;
            case 100 : setInternal(3, 31, 21, 17, 21); break;
        }
    }
    
    /**
     * Adds clan CASE in every location
     */
    public void addClanCase() {
        boolean explosiveFound=false;
        EquipmentType clCase = EquipmentType.get("CLCASE");
        for (int i = 0; i < locations(); i++) {
            explosiveFound=false;
            for(Enumeration equip = getEquipment();equip.hasMoreElements();) {
                Mounted m = (Mounted)equip.nextElement();
                if(m.getType().isExplosive() && m.getLocation()==i) {
                    explosiveFound=true;
                }
            }
            if(explosiveFound) {
                try {
                    addEquipment(new Mounted(this, clCase), i, false);
                    } catch (LocationFullException ex) {
                        // um, that's impossible.
                        }
                    }
         }            
        
    }
    
    /**
     * Mounts the specified weapon in the specified location.
     */
    public void addEquipment(Mounted mounted, int loc, boolean rearMounted)
        throws LocationFullException 
    {
        // if there's no actual location, then don't add criticals
        if (loc == LOC_NONE) {
            super.addEquipment(mounted, loc, rearMounted);
            return;
        }
        
        // spreadable or split equipment only gets added to 1 crit at a time, 
        // since we don't know how many are in this location
        int slots = mounted.getType().getCriticals(this);
        if (mounted.getType().isSpreadable() || mounted.isSplit()) {
            slots = 1;
        }
        
        // check criticals for space
        if(getEmptyCriticals(loc) < slots) {
            throw new LocationFullException(mounted.getName() + " does not fit in " + getLocationAbbr(loc) + " on " + getDisplayName() + "\n        free criticals in location: " + getEmptyCriticals(loc) + ", criticals needed: " + slots);
        }
        
        // add it
        if (getEquipmentNum(mounted)==-1)
            super.addEquipment(mounted, loc, rearMounted);

        // add criticals
        int num = getEquipmentNum(mounted);        
        
        for(int i = 0; i < slots; i++) {
            addCritical(loc, new CriticalSlot(CriticalSlot.TYPE_EQUIPMENT, num, mounted.getType().isHittable()));
        }        
    }    
  
    /**
     * Calculates the battle value of this mech
     */
    public int calculateBattleValue() {
        return calculateBattleValue(false);
    }
  
    /**
     * Calculates the battle value of this mech.
     *  If the parameter is true, then the battle value for
     *  c3 will be added whether the mech is currently part of
     *  a network or not.
     */
    public int calculateBattleValue(boolean assumeLinkedC3) {
        double dbv = 0; // defensive battle value
        double obv = 0; // offensive bv

        // Try to find a Mek Stealth system.
        boolean bHasStealthArmor = hasStealth();
        boolean bHasEiSystem = (hasEiCockpit() && getCrew().getOptions().booleanOption("ei_implant"));

        // total armor points
        dbv += getTotalArmor() * 2.0;

        // total internal structure
        double internalMultiplier;
        int CTengine = getNumberOfCriticals(CriticalSlot.TYPE_SYSTEM, SYSTEM_ENGINE, LOC_CT);
        int RTengine = getNumberOfCriticals(CriticalSlot.TYPE_SYSTEM, SYSTEM_ENGINE, LOC_RT);
        if(CTengine > 6) {
            //large engine of some kind
            if(RTengine >=6)
                internalMultiplier = 0.375; // IS large XXL
            else if (RTengine >=4)
                internalMultiplier = 0.5; // clan large XXL
            else if (RTengine >=2)
                internalMultiplier = 0.75; // large XL
            else
                internalMultiplier = 1.125; // large
        } else {
            //normal sized or compact engine
            if(RTengine >=6)
                internalMultiplier = 0.5; // IS XXL
            else if (RTengine >=3)
                internalMultiplier = 0.75; // IS XL, clan XXL
            else if (RTengine >0)
                internalMultiplier = 1.125; // IS L, clan XL
            else
                internalMultiplier = 1.5; //standard or compact
        }

        dbv += getTotalInternal() * internalMultiplier;

        // add weight
        dbv += getWeight();

        // Heavy-duty gyros add 30 DBV.
        if (getGyroType() == GYRO_HEAVY_DUTY)
            dbv += 30;

        // add defensive equipment
        double dEquipmentBV = 0;
        for (Enumeration i = equipmentList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            EquipmentType etype = mounted.getType();

            // don't count destroyed equipment
            if (mounted.isDestroyed())
                continue;

            if ((etype instanceof WeaponType && etype.hasFlag(WeaponType.F_AMS))
                    || (etype instanceof AmmoType && ((AmmoType)etype).getAmmoType() == AmmoType.T_AMS)
                    || (etype instanceof MiscType && (etype.hasFlag(MiscType.F_ECM)
                                            || etype.hasFlag(MiscType.F_AP_POD)))) {
                dEquipmentBV += etype.getBV(this);
            }
        }
        dbv += dEquipmentBV;

        if(bHasEiSystem) {
            dbv -= 25;
        }

        // subtract for explosive ammo
        double ammoPenalty = 0;
        for (Enumeration i = equipmentList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            int loc = mounted.getLocation();
            EquipmentType etype = mounted.getType();

            // only count explosive ammo
            if (!etype.isExplosive()) {
                continue;
            }

            // don't count oneshot ammo
            if (loc == LOC_NONE) {
                continue;
            }

            if (isClan()) {
                // Clan mechs only count ammo in ct, legs or head (per BMRr).
                // Also count ammo in side torsos if mech has xxl engine
                // (extrapolated from rule intent - not covered in rules)
                if ((loc != LOC_CT && loc != LOC_RLEG && loc != LOC_LLEG
                     && loc != LOC_HEAD) &&
                    ((loc == LOC_RT || loc == LOC_LT) &&
                     getEngine().getSideTorsoCriticalSlots().length < 3)) {
                    continue;
                }
             } else {
                // inner sphere with XL or XXL counts everywhere
                if (getEngine().getSideTorsoCriticalSlots().length == 0) {
                    // without XL or XXL, only count torsos if not CASEed,
                    // and arms if arm & torso not CASEed
                    if ((loc == LOC_RT || loc == LOC_LT) && locationHasCase(loc)) {
                        continue;
                    } else if (loc == LOC_LARM && (locationHasCase(loc) || locationHasCase(LOC_LT))) {
                        continue;
                    } else if (loc == LOC_RARM && (locationHasCase(loc) || locationHasCase(LOC_RT))) {
                        continue;
                    }
                }
            }
            
            float tonnage = etype.getTonnage(this);
            
            // gauss rifles only count as one ton for this
            if (etype instanceof WeaponType && etype.getName().indexOf("Gauss") != -1) {
                tonnage = 1.0f;
            }
             
            // RACs don't really count
            if (etype instanceof WeaponType && ((WeaponType)etype).getAmmoType() == AmmoType.T_AC_ROTARY) {
                tonnage = 0.0f;
            }
            // normal ACs only marked as explosive because they are when they just
            // fired incendiary ammo, therefore they don't count for explosive BV
            if (etype instanceof WeaponType && ((WeaponType)etype).getAmmoType() == AmmoType.T_AC) {
                tonnage = 0.0f;
            }
           
            ammoPenalty += 20.0 * tonnage;
        }
        dbv = Math.max(1, dbv - ammoPenalty);
        
        
        // total up maximum heat generated
        double maxumumHeatFront = 0;
        double maxumumHeatRear = 0;
        for (Enumeration i = weaponList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            WeaponType wtype = (WeaponType)mounted.getType();
            double weaponHeat = wtype.getHeat();
            
            // Bug #1112073 says not to count RocketLaunchers.
            if (wtype.getAmmoType() == AmmoType.T_ROCKET_LAUNCHER) {
                continue;
            }

            // double heat for ultras
            if ((wtype.getAmmoType() == AmmoType.T_AC_ULTRA)
                    || (wtype.getAmmoType() == AmmoType.T_AC_ULTRA_THB)) {
                weaponHeat *= 2;
            }

            // Six times heat for RAC
            if (wtype.getAmmoType() == AmmoType.T_AC_ROTARY) {
                weaponHeat *= 6;
            }

            // half heat for streaks
            if ((wtype.getAmmoType() == AmmoType.T_SRM_STREAK) || (wtype.getAmmoType() == AmmoType.T_LRM_STREAK)){
                weaponHeat *= 0.5;
            }
            
            if (mounted.isRearMounted()) {
                maxumumHeatRear += weaponHeat;
            } else {
                maxumumHeatFront += weaponHeat;
            }
        }
        double maximumHeat = Math.max(maxumumHeatFront, maxumumHeatRear);
        if (getJumpMP() > 0) {
            maximumHeat += Math.max(3, getJumpMP());
        } else {
            maximumHeat += 2;
        }

        // Add in Mek Stealth Armor effects.
        if ( bHasStealthArmor ) {
            maximumHeat += 10;
        }

        // adjust for heat efficiency
        if (maximumHeat > getHeatCapacity()) {
            double heatPenalty = ((maximumHeat - getHeatCapacity()) * 5);
            dbv = Math.max(1, dbv - heatPenalty);
        }

        /*
        ** The way the BMRr handles Mek Stealth Armor has been superceded.
        // Add in Mek Stealth Armor effects to the OFFENSIVE Battle Rating.
        if ( bHasStealthArmor ) {
            maximumHeat += 10;
        }
        */

        // adjust for target movement modifier
        int runMP = getOriginalRunMPwithoutMASC();
        // factor in masc or tsm
        if (hasMASC()) {
            runMP = getWalkMP() * 2;
        }
        if (hasTSM()) {
            runMP = (int)Math.ceil((getWalkMP() + 1) * 1.5);
        }
        int tmmRan = Compute.getTargetMovementModifier(runMP, false, false, false).getValue();
        int tmmJumped = Compute.getTargetMovementModifier(getOriginalJumpMP(), true, false, false).getValue();
        int targetMovementModidifer = Math.max(tmmRan, tmmJumped);
        if (targetMovementModidifer > 5) {
            targetMovementModidifer = 5;
        }
        double[] tmmFactors = { 1.0, 1.1, 1.2, 1.3, 1.4, 1.5 };
        double tmmFactor = tmmFactors[targetMovementModidifer];
        // Adjust for Steath Armor on Mek.
        if ( bHasStealthArmor ) {
            tmmFactor += 0.2;
        }
        dbv *= tmmFactor;
        
        double weaponBV = 0;

        // figure out base weapon bv
        double weaponsBVFront = 0;
        double weaponsBVRear = 0;
        boolean hasTargComp = hasTargComp();
        for (Enumeration i = weaponList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            WeaponType wtype = (WeaponType)mounted.getType();
            double dBV = wtype.getBV(this);
            
            // don't count destroyed equipment
            if (mounted.isDestroyed())
                continue;

            // don't count AMS, it's defensive
            if (wtype.hasFlag(WeaponType.F_AMS)) {
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
            if (wtype.hasFlag(WeaponType.F_DIRECT_FIRE)) {
                if(bHasEiSystem && hasTargComp) {
                    dBV *= 1.25;
                } else if(hasTargComp) {
                    dBV *= 1.2;
                } else if(bHasEiSystem) {
                    dBV *= 1.05;
                }
            }
            
            if (mounted.isRearMounted()) {
                weaponsBVRear += dBV;
            } else {
                weaponsBVFront += dBV;
            }
        }
        if (weaponsBVFront > weaponsBVRear) {
            weaponBV += weaponsBVFront;
            weaponBV += (weaponsBVRear * 0.5);
        } else {
            weaponBV += weaponsBVRear;
            weaponBV += (weaponsBVFront * 0.5);
        }
        
        // add offensive misc. equipment BV (everything except AMS, A-Pod, ECM - BMR p152)
        double oEquipmentBV = 0;
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            MiscType mtype = (MiscType)mounted.getType();
 
            // don't count destroyed equipment
            if (mounted.isDestroyed())
                continue;

            if (mtype.hasFlag(MiscType.F_ECM)
                    || mtype.hasFlag(MiscType.F_AP_POD) 
                    || mtype.hasFlag(MiscType.F_TARGCOMP)) //targ counted with weapons 
                continue;
            oEquipmentBV += mtype.getBV(this);
        }
        weaponBV += oEquipmentBV;

        // add ammo bv
        double ammoBV = 0;
        for (Enumeration i = ammoList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            AmmoType atype = (AmmoType)mounted.getType();

            // don't count depleted ammo
            if (mounted.getShotsLeft() == 0)
                continue;

            // don't count AMS, it's defensive
            if (atype.getAmmoType() == AmmoType.T_AMS) {
                continue;
            }

            // don't count oneshot ammo, it's considered part of the launcher.
            if (mounted.getLocation() == Entity.LOC_NONE) {
                // assumption: ammo without a location is for a oneshot weapon
                continue;
            }

            ammoBV += atype.getBV(this);
        }
        weaponBV += ammoBV;
        
        // adjust for heat efficiency
        if (maximumHeat > getHeatCapacity()) {
            double x = (getHeatCapacity() * weaponBV) / maximumHeat;
            double y = (weaponBV - x) / 2;
            weaponBV = x + y;
        }
        
        // adjust further for speed factor
        double speedFactor = getOriginalRunMPwithoutMASC() + getOriginalJumpMP() - 5;
        // +1 for MASC or TSM, you may not have both
        if (hasMASC() || hasTSM()) {
            speedFactor++;
        }
        speedFactor /= 10;
        speedFactor++;
        speedFactor = Math.pow(speedFactor, 1.2);
        speedFactor = Math.round(speedFactor * 100) / 100.0;

        /*
        ** The way the BMRr handles Mek Stealth Armor has been superceded.
        // Adjust for Steath Armor on Mek.
        if ( bHasStealthArmor ) {
            speedFactor += 0.2;
        }
        */

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

        // Possibly adjust for TAG and Arrow IV.
        if (getsTagBVPenalty()) {
            dbv += 200;
        }
        if (getsHomingBVPenalty()) {
            dbv += 200;
        }

        // and then factor in pilot
        double pilotFactor = crew.getBVSkillMultiplier();
        if (bHasEiSystem) {
            pilotFactor += 0.05; //treat piloting as 1 level better
        }
        if (getCockpitType() == Mech.COCKPIT_SMALL) {
            pilotFactor -= 0.05; // Small cockpits piloting treated as 1 level worse.
        }

        return (int)Math.round((dbv + obv + xbv) * pilotFactor);
    }

    public Vector victoryReport() {
        Vector vDesc = new Vector();

        Report r = new Report(7025);
        r.type = Report.PUBLIC;
        r.addDesc(this);
        vDesc.addElement(r);

        r = new Report(7030);
        r.type = Report.PUBLIC;
        r.newlines = 0;
        vDesc.addElement(r);
        vDesc.addAll(crew.getDescVector(false));
        r = new Report(7070, Report.PUBLIC);
        r.newlines = 2;
        r.add(getKillNumber());
        vDesc.addElement(r);

        return vDesc;
    }
  
    /**
     * Add in any piloting skill mods
     */
    public PilotingRollData addEntityBonuses(PilotingRollData roll) {
        // gyro hit?
        if (getBadCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_GYRO, Mech.LOC_CT) > 0) {
            roll.addModifier(3, "Gyro damaged");
        }

        // EI bonus?
        if (hasActiveEiCockpit()) {
            roll.addModifier(-1, "Enhanced Imaging");
        }

        // Small/torso-mounted cockpit penalty?
        if (getCockpitType() == Mech.COCKPIT_SMALL) {
            roll.addModifier(1, "Small Cockpit");
        } else if (getCockpitType() == Mech.COCKPIT_TORSO_MOUNTED) {
            roll.addModifier(1, "Torso-Mounted Cockpit");
            int sensorHits = getBadCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_SENSORS, Mech.LOC_HEAD);
            int sensorHits2 = getBadCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_SENSORS, Mech.LOC_CT);
            if ((sensorHits + sensorHits2) == 3) {
                roll.addModifier(4, "Sensors Completely Destroyed for Torso-Mounted Cockpit");
            } else if (sensorHits == 2) {
                roll.addModifier(4, "Head Sensors Destroyed for Torso-Mounted Cockpit");
            }
        }

        if (getArmorType() == EquipmentType.T_ARMOR_HARDENED) {
            roll.addModifier(1, "Hardened Armor");
        }

        return roll;
    }
      
    public int getMaxElevationChange() {
        return 2;
    }

    /**
     * Determine if this unit has an active stealth system.
     * <p/>
     * Sub-classes are encouraged to override this method.
     *
     * @return  <code>true</code> if this unit has a stealth system that
     *          is currently active, <code>false</code> if there is no
     *          stealth system or if it is inactive.
     */
    public boolean isStealthActive() {
        // Try to find a Mek Stealth system.
        for ( Enumeration equips = getMisc(); equips.hasMoreElements(); ) {
            Mounted mEquip = (Mounted) equips.nextElement();
            MiscType mtype = (MiscType) mEquip.getType();
            if ( Mech.STEALTH.equals(mtype.getInternalName()) ) {
                if (mEquip.curMode().equals( "On" ) && hasActiveECM()) {
                    // Return true if the mode is "On" and ECM is working
                    return true;
                }
            }
            else if (Mech.NULLSIG.equals(mtype.getInternalName())
                    && mEquip.curMode().equals( "On" )
                    && mEquip.isReady()) {
                return true;
            }
        }

        // No Mek Stealth or system inactive.  Return false.
        return false;
    }

    /**
     * Determine the stealth modifier for firing at this unit from the
     * given range.  If the value supplied for <code>range</code> is not
     * one of the <code>Entity</code> class range constants, an
     * <code>IllegalArgumentException</code> will be thrown.
     * <p/>
     * Sub-classes are encouraged to override this method.
     *
     * @param   range - an <code>int</code> value that must match one
     *          of the <code>Compute</code> class range constants.
     * @return  a <code>TargetRoll</code> value that contains the stealth
     *          modifier for the given range.
     */
    public TargetRoll getStealthModifier( int range ) {
        TargetRoll result = null;

        // Stealth must be active.
        if ( !isStealthActive() ) {
            result = new TargetRoll( 0, "stealth not active"  );
        }

        // Determine the modifier based upon the range.
        else {
            switch ( range ) {
            case RangeType.RANGE_MINIMUM:
            case RangeType.RANGE_SHORT:
                result = new TargetRoll( 0, "stealth" );
                break;
            case RangeType.RANGE_MEDIUM:
                result = new TargetRoll( 1, "stealth" );
                break;
            case RangeType.RANGE_LONG:
            case RangeType.RANGE_EXTREME: // TODO : what's the *real* modifier?
                result = new TargetRoll( 2, "stealth" );
                break;
            default:
                throw new IllegalArgumentException
                    ( "Unknown range constant: " + range );
            }
        }

        // Return the result.
        return result;

    } // End public TargetRoll getStealthModifier( char )

    /**
     * Determine if the unit can be repaired, or only harvested for spares.
     *
     * @return  A <code>boolean</code> that is <code>true</code> if the unit
     *          can be repaired (given enough time and parts); if this value
     *          is <code>false</code>, the unit is only a source of spares.
     * @see     Entity#isSalvage()
     */
    public boolean isRepairable() {
        // A Mech is repairable if it is salvageable,
        // and its CT internals are not gone.
        int loc_is = this.getInternal( Mech.LOC_CT );
        return this.isSalvage() &&
            (loc_is != IArmorState.ARMOR_DOOMED) && (loc_is != IArmorState.ARMOR_DESTROYED);
    }

    public boolean canCharge() {
        // Mechs can charge, unless they are Clan and the "no clan physicals" option is set
        return super.canCharge() && !(game.getOptions().booleanOption("no_clan_physical") && isClan());
    }

    public boolean canDFA() {
        // Mechs can DFA, unless they are Clan and the "no clan physicals" option is set
        return super.canDFA() && !(game.getOptions().booleanOption("no_clan_physical") && isClan());
    }
    
    //gives total number of sinks
    public int getNumberOfSinks() {
        int sinks=0;
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            if (mounted.isDestroyed() || mounted.isBreached()) {
                continue;
            }
            if (mounted.getType().hasFlag(MiscType.F_HEAT_SINK)) {
                sinks++;
            } else if(mounted.getType().hasFlag(MiscType.F_DOUBLE_HEAT_SINK)) {
                sinks++;
            }
        }
        return sinks;
    }
    
    public boolean hasDoubleHeatSinks() {
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            if (mounted.getType().hasFlag(MiscType.F_HEAT_SINK)) {
                return false;
            } else if (mounted.getType().hasFlag(MiscType.F_DOUBLE_HEAT_SINK)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean hasLaserHeatSinks() {
        for (Enumeration i = miscList.elements(); i.hasMoreElements();) {
            Mounted mounted = (Mounted)i.nextElement();
            if (mounted.getType().hasFlag(MiscType.F_HEAT_SINK)) {
                return false;
            } else if (mounted.getType().hasFlag(MiscType.F_LASER_HEAT_SINK)) {
                return true;
            }
        }
        return false;
    }
    
    public void setActiveSinksNextRound(int sinks) {
        sinksOnNextRound=sinks;
    }
    
    public int getActiveSinks() {
        if (sinksOn < 0) {
            sinksOn = getNumberOfSinks();
            sinksOnNextRound = sinksOn;
        }
        return sinksOn;
    }

    public int getActiveSinksNextRound() {
        if(sinksOnNextRound < 0)
            return getActiveSinks();
        return sinksOnNextRound;
    }
    
    /**
     * @return Returns the autoEject.
     */
    public boolean isAutoEject() {
        if (getCockpitType() == COCKPIT_TORSO_MOUNTED)
            return false;
        return autoEject;
    }
    /**
     * @param autoEject The autoEject to set.
     */
    public void setAutoEject(boolean autoEject) {
        this.autoEject = autoEject;
    }
    
    public boolean removePartialCoverHits(int location, int cover, int side) {
        //left and right cover are from attacker's POV.
        //if hitting front arc, need to swap them
        if (side == ToHitData.SIDE_FRONT) {
            if ((cover & LosEffects.COVER_LOWRIGHT) != 0 && location == Mech.LOC_LLEG)
                return true;
            if ((cover & LosEffects.COVER_LOWLEFT) != 0 && location == Mech.LOC_RLEG)
                return true;
            if ((cover & LosEffects.COVER_RIGHT) != 0 && (location == Mech.LOC_LARM || location == Mech.LOC_LT))
                return true;
            if ((cover & LosEffects.COVER_LEFT) != 0 && (location == Mech.LOC_RARM || location == Mech.LOC_RT))
                return true;
        } else {
            if ((cover & LosEffects.COVER_LOWLEFT) != 0 && location == Mech.LOC_LLEG)
                return true;
            if ((cover & LosEffects.COVER_LOWRIGHT) != 0 && location == Mech.LOC_RLEG)
                return true;
            if ((cover & LosEffects.COVER_LEFT) != 0 && (location == Mech.LOC_LARM || location == Mech.LOC_LT))
                return true;
            if ((cover & LosEffects.COVER_RIGHT) != 0 && (location == Mech.LOC_RARM || location == Mech.LOC_RT))
                return true;
        }
        return false;
    }

    public boolean doomedInVacuum() {
        return false;
    }

    public boolean hasEiCockpit() {
        return isClan() || super.hasEiCockpit();
    }

    public boolean hasActiveEiCockpit() {
        if(cockpitStatus == COCKPIT_OFF)
            return false;
        if(getBadCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_SENSORS, Mech.LOC_HEAD) > 0)
            return false;
        return super.hasActiveEiCockpit();
    }

    public int getCockpitStatus() {
        return cockpitStatus;
    }

    public int getCockpitStatusNextRound() {
        return cockpitStatusNextRound;
    }

    public void setCockpitStatus(int state) {
        cockpitStatusNextRound = state;
        //on/off allowed only in end phase
        if(state != COCKPIT_OFF && cockpitStatus != COCKPIT_OFF) {
            cockpitStatus = state;
        }
    }

    public int getGyroType() {
        return gyroType;
    }

    public int getCockpitType() {
        return cockpitType;
    }

    public void setGyroType(int type) {
        gyroType = type;
    }

    public void setCockpitType(int type) {
        cockpitType = type;
    }

    public String getGyroTypeString() {
        return getGyroTypeString(getGyroType());
    }

    public String getCockpitTypeString() {
        return getCockpitTypeString(getCockpitType());
    }

    public static String getGyroTypeString(int inGyroType) {
        if ((inGyroType < 0)
                || (inGyroType >= GYRO_STRING.length))
            return "Unknown";
        return GYRO_STRING[inGyroType];
    }

    public static String getGyroTypeShortString(int inGyroType) {
        if ((inGyroType < 0)
                || (inGyroType >= GYRO_SHORT_STRING.length))
            return "Unknown";
        return GYRO_SHORT_STRING[inGyroType];
    }

    public static String getCockpitTypeString(int inCockpitType) {
        if ((inCockpitType < 0)
                || (inCockpitType >= COCKPIT_STRING.length))
            return "Unknown";
        return COCKPIT_STRING[inCockpitType];
    }

    public static int getGyroTypeForString(String inType) {
        if ((inType == null)
                || (inType.length() < 1))
            return GYRO_UNKNOWN;
        for (int x=0; x<GYRO_STRING.length; x++) {
            if ((inType.equals(GYRO_STRING[x]))
                    || (inType.equals(GYRO_SHORT_STRING[x])))
                return x;
        }
        return GYRO_UNKNOWN;
    }   

    public static int getCockpitTypeForString(String inType) {
        if ((inType == null)
                || (inType.length() < 1))
            return COCKPIT_UNKNOWN;
        for (int x=0; x<COCKPIT_STRING.length; x++) {
            if ((inType.equals(COCKPIT_STRING[x]))
                    || (inType.equals(COCKPIT_SHORT_STRING[x])))
                return x;
        }
        return COCKPIT_UNKNOWN;
    }   

    public String getSystemName(int index) {
        if (index == SYSTEM_GYRO)
            return getGyroDisplayString(gyroType);
        if (index == SYSTEM_COCKPIT)
            return getCockpitDisplayString(cockpitType);
        return systemNames[index];
    }

    public static String getGyroDisplayString(int inType) {
        String inName = "";
        switch (inType) {
            case GYRO_XL:
                inName = "GYRO_XL";
                break;
            case GYRO_COMPACT:
                inName = "GYRO_COMPACT";
                break;
            case GYRO_HEAVY_DUTY:
                inName = "GYRO_HEAVY_DUTY";
                break;
            case GYRO_STANDARD:
                inName = "GYRO_STANDARD";
                break;
            default:
                inName = "GYRO_UNKNOWN";
        }
        String result = EquipmentMessages.getString("SystemType.Gyro."+inName);
        if (result != null)
            return result;
        else
            return inName;
    }

    public static String getCockpitDisplayString(int inType) {
        String inName = "";
        switch (inType) {
            case COCKPIT_COMMAND_CONSOLE:
                inName = "COCKPIT_COMMAND_CONSOLE";
                break;
            case COCKPIT_SMALL:
                inName = "COCKPIT_SMALL";
                break;
            case COCKPIT_TORSO_MOUNTED:
                inName = "COCKPIT_TORSO_MOUNTED";
                break;
            case COCKPIT_DUAL:
                inName = "COCKPIT_DUAL";
                break;
            case COCKPIT_STANDARD:
                inName = "COCKPIT_STANDARD";
                break;
            default:
                inName = "GYRO_UNKNOWN";
        }
        String result = EquipmentMessages.getString("SystemType.Cockpit."+inName);
        if (result != null)
            return result;
        else
            return inName;
    }

    public boolean canAssaultDrop() {
        return true;
    }
    
    public boolean isHexProhibited(IHex hex) {
        if(hex.containsTerrain(Terrains.IMPASSABLE)) return true;
        return hex.terrainLevel(Terrains.WOODS) > 2 || hex.terrainLevel(Terrains.JUNGLE) > 2;
    }

}
