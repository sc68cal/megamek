/**
 * MegaMek -
 * Copyright (C) 2000,2001,2002,2003,2004,2005,2006,2007 Ben Mazur (bmazur@sev.org)
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

import megamek.common.weapons.*;

//TODO add XML support back in.

/*import java.io.File;
import java.text.NumberFormat;
import java.util.Enumeration;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;  
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.w3c.dom.*;
*/
/**
 * A type of mech or vehicle weapon.  There is only one instance of this
 * weapon for all weapons of this type.
 */
public class WeaponType extends EquipmentType {
    public static final int     DAMAGE_MISSILE = -2;
    public static final int     DAMAGE_VARIABLE = -3;
    public static final int     DAMAGE_SPECIAL = -4;
    public static final int     DAMAGE_ARTILLERY = -5;
    public static final int     WEAPON_NA = Integer.MIN_VALUE;

    // weapon flags (note: many weapons can be identified by their ammo type)
    public static final long     F_DIRECT_FIRE        = 1 << 0; // marks any weapon affected by a targetting computer
    public static final long     F_FLAMER             = 1 << 1;
    public static final long     F_LASER              = 1 << 2; // for eventual glazed armor purposes
    public static final long     F_PPC                = 1 << 3; //              "
    public static final long     F_AUTO_TARGET        = 1 << 4; // for weapons that target automatically (AMS)
    public static final long     F_NO_FIRES           = 1 << 5; // cannot start fires
    public static final long     F_PROTOMECH          = 1 << 6; // Protomech weapons, which need weird ammo stuff.
    public static final long     F_SOLO_ATTACK        = 1 << 7; // must be only weapon attacking
    public static final long     F_SPLITABLE          = 1 << 8; // Weapons that can be split between locations
    public static final long     F_MG                 = 1 << 9; // MGL; for rapid fire set up
    public static final long     F_INFERNO            = 1 << 10; // Inferno weapon
    public static final long     F_INFANTRY           = 1 << 11; // small calibre weapon, no ammo, damage based on # men shooting
    public static final long     F_BATTLEARMOR        = 1 << 12; // weapon is only for one trooper of the squad/point
    public static final long     F_DOUBLE_HITS        = 1 << 13; // two shots hit per one rolled
    public static final long     F_MISSILE_HITS       = 1 << 14; // use missile rules or # of hits
    public static final long     F_ONESHOT            = 1 << 15; // weapon is oneShot.
    public static final long     F_ARTILLERY          = 1 << 16;
    public static final long     F_BALLISTIC          = 1 << 17; // For Gunnery/Ballistic skill
    public static final long     F_ENERGY             = 1 << 18; // For Gunnery/Energy skill
    public static final long     F_MISSILE            = 1 << 19; // For Gunnery/Missile skill
    public static final long     F_PLASMA             = 1 << 20; // For fires
    public static final long     F_INCENDIARY_NEEDLES = 1 << 21; // For fires
    public static final long     F_PROTOTYPE          = 1 << 22; // for war of 3039 prototype weapons
    public static final long     F_HEATASDICE         = 1 << 23; // heat is listed in dice, not points
    public static final long     F_AMS                = 1 << 24; // Weapon is an anti-missile system.
    public static final long     F_BOOST_SWARM        = 1 << 25; // boost leg & swarm
    public static final long     F_INFANTRY_ONLY      = 1 << 26; // only target infantry
    public static final long     F_TAG                = 1 << 27; // Target acquisition gear
    public static final long     F_C3M                = 1 << 28; // C3 Master with Target acquisition gear
    public static final long     F_PLASMA_MFUK        = 1 << 29; // Plasma Rifle
    public static final long     F_EXTINGUISHER       = 1 << 30; // Fire extinguisher
    public static final long     F_SINGLE_TARGET      = 1 << 31; // Does less damage to PBI in maxtech rules
    public static final long     F_PULSE              = 1 << 32; // pulse weapons
    public static final long     F_BURST_FIRE         = 1 << 33; // full damage vs infantry
    public static final long     F_MGA                = 1 << 34; // machine gun array
    public static final long     F_NO_AIM             = 1 << 35; // machine gun array

    //protected RangeType rangeL;
    protected int   heat;
    protected int   damage;
    public int      rackSize; // or AC size, or whatever
    public int      ammoType;

    public int      minimumRange;
    public int      shortRange;
    public int      mediumRange;
    public int      longRange;
    public int      extremeRange;
    public int      waterShortRange;
    public int      waterMediumRange;
    public int      waterLongRange;
    public int      waterExtremeRange;

    public void setDamage(int inD) {
        damage = inD;
    }

    public void setName (String inN) {
        name = inN;
        setInternalName(inN);
    }

    public void setMinimumRange(int inMR) {
        minimumRange = inMR;
    }

    public void setRanges(int sho, int med, int lon, int ext) {
        shortRange = sho;
        mediumRange = med;
        longRange = lon;
        extremeRange = ext;
    }

    public void setWaterRanges(int sho, int med, int lon, int ext) {
        waterShortRange = sho;
        waterMediumRange = med;
        waterLongRange = lon;
        waterExtremeRange = ext;
    }

    public void setAmmoType(int inAT) {
        ammoType = inAT;
    }

    public void setRackSize(int inRS) {
        rackSize = inRS;
    }

    public WeaponType() {
    }

    public int getHeat() {
        return heat;
    }

    public int getFireTN() {
        if (hasFlag(F_NO_FIRES)) {
            return TargetRoll.IMPOSSIBLE;
        } else if (hasFlag(F_FLAMER)) {
            return 4;
        } else if (hasFlag(F_PLASMA)) {
            return 5;
        } else if (hasFlag(F_PLASMA_MFUK)) {
            return 5;
        } else if (hasFlag(F_INCENDIARY_NEEDLES)) {
            return 6;
        } else if (hasFlag(F_PPC) || hasFlag(F_LASER)) {
            return 7;
        } else {
            return 9;
        }
    }

    public int getDamage() {
        return damage;
    }

    public int getRackSize() {
        return rackSize;
    }

    public int getAmmoType() {
        return ammoType;
    }

    public int[] getRanges() {
        return new int[] {minimumRange, shortRange, mediumRange,
                          longRange, extremeRange};
    }

    public int getMinimumRange() {
        return minimumRange;
    }

    public int getShortRange() {
        return shortRange;
    }

    public int getMediumRange() {
        return mediumRange;
    }

    public int getLongRange() {
        return longRange;
    }

    public int getExtremeRange() {
        return extremeRange;
    }

    public int[] getWRanges() {
        return new int[] {minimumRange, waterShortRange, waterMediumRange,
                          waterLongRange, waterExtremeRange};
    }

    public int getWShortRange() {
        return waterShortRange;
    }

    public int getWMediumRange() {
        return waterMediumRange;
    }

    public int getWLongRange() {
        return waterLongRange;
    }

    public int getWExtremeRange() {
        return waterExtremeRange;
    }

    /**
     * Add all the types of weapons we can create to the list
     */
    public static void initializeTypes() {
            //Laser types
            addType(new ISMediumLaser());
            addType(new ISLargeLaser());
            addType(new ISSmallLaser());
            addType(new ISLargePulseLaser());
            addType(new ISLargeXPulseLaser());
            addType(new ISERLargeLaser());
            addType(new ISERLargeLaserPrototype());
            addType(new ISERMediumLaser());
            addType(new ISMediumPulseLaser());
            addType(new ISMediumPulseLaserPrototype());
            addType(new ISMediumXPulseLaser());
            addType(new ISSmallPulseLaser());
            addType(new ISSmallXPulseLaser());
            addType(new ISERSmallLaser());
            addType(new CLERLargeLaser());
            addType(new CLHeavyLargeLaser());
            addType(new CLLargePulseLaser());
            addType(new CLERLargePulseLaser());
            addType(new CLERMediumLaser());
            addType(new CLHeavyMediumLaser());
            addType(new CLMediumPulseLaser());
            addType(new CLERMediumPulseLaser());
            addType(new CLERSmallLaser());
            addType(new CLSmallPulseLaser());
            addType(new CLERSmallPulseLaser());
            addType(new CLHeavySmallLaser());
            addType(new CLERMicroLaser());
            addType(new CLMicroPulseLaser());
            //PPC types
            addType(new ISPPC());
            addType(new ISERPPC());
            addType(new CLERPPC());
            addType(new ISSnubNosePPC());
            addType(new ISLightPPC());
            addType(new ISHeavyPPC());
            addType(new ISHERPPC());
            addType(new ISSupportPPC());
            addType(new CLSupportPPC());
            //Flamers
            addType(new CLFlamer());
            addType(new ISFlamer());
            addType(new CLVehicleFlamer());
            addType(new ISVehicleFlamer());
            addType(new ISHeavyFlamer());
            //Autocannons
            addType(new ISAC2());
            addType(new ISAC5());
            addType(new ISAC10());
            addType(new ISAC20());
            //Ultras
            addType(new ISUAC2());
            addType(new ISUAC5());
            addType(new ISUAC5Prototype());
            addType(new ISUAC10());
            addType(new ISUAC20());
            addType(new ISTHBUAC2());
            addType(new ISTHBUAC10());
            addType(new ISTHBUAC20());
            addType(new CLUAC2());
            addType(new CLUAC5());
            addType(new CLUAC10());
            addType(new CLUAC20());
            //LBXs
            addType(new ISLB2XAC());
            addType(new ISLB5XAC());
            addType(new ISLB10XAC());
            addType(new ISLB10XACPrototype());
            addType(new ISLB20XAC());
            addType(new CLLB2XAC());
            addType(new CLLB5XAC());
            addType(new CLLB10XAC());
            addType(new CLLB20XAC());
            addType(new ISTHBLB2XAC());
            addType(new ISTHBLB5XAC());
            addType(new ISTHBLB20XAC());
            //RACs
            addType(new ISRAC2());
            addType(new ISRAC5());
            //LACs
            addType(new ISLAC2());
            addType(new ISLAC5());
            addType(new ISLAC10());
            addType(new ISLAC20());
            //Gausses
            addType(new ISGaussRifle());
            addType(new ISGaussRiflePrototype());
            addType(new CLGaussRifle());
            addType(new ISLGaussRifle());
            addType(new ISHGaussRifle());
            addType(new CLHAG20());
            addType(new CLHAG30());
            addType(new CLHAG40());
            addType(new CLAPGaussRifle());
            //MGs
            addType(new ISMG());
            addType(new ISLightMG());
            addType(new ISHeavyMG());
            addType(new ISMGA());
            addType(new ISLightMGA());
            addType(new ISHeavyMGA());
            addType(new CLMG());
            addType(new CLLightMG());
            addType(new CLHeavyMG());
            addType(new CLMGA());
            addType(new CLLightMGA());
            addType(new CLHeavyMGA());
            //LRMs
            addType(new ISLRM1());
            addType(new ISLRM2());
            addType(new ISLRM3());
            addType(new ISLRM4());
            addType(new ISLRM5());
            addType(new ISLRM10());
            addType(new ISLRM15());
            addType(new ISLRM20());
            addType(new ISLRM5OS());
            addType(new ISLRM10OS());
            addType(new ISLRM15OS());
            addType(new ISLRM20OS());
            addType(new CLLRM1());
            addType(new CLLRM2());
            addType(new CLLRM3());
            addType(new CLLRM4());
            addType(new CLLRM5());
            addType(new CLLRM10());
            addType(new CLLRM15());
            addType(new CLLRM20());
            addType(new CLLRM5OS());
            addType(new CLLRM10OS());
            addType(new CLLRM15OS());
            addType(new CLLRM20OS());
            addType(new CLStreakLRM5());
            addType(new CLStreakLRM10());
            addType(new CLStreakLRM15());
            addType(new CLStreakLRM20());
            addType(new CLStreakLRM5OS());
            addType(new CLStreakLRM10OS());
            addType(new CLStreakLRM15OS());
            addType(new CLStreakLRM20OS());
            //LRTs
            addType(new ISLRT5());
            addType(new ISLRT10());
            addType(new ISLRT15());
            addType(new ISLRT20());
            addType(new ISLRT5OS());
            addType(new ISLRT10OS());
            addType(new ISLRT15OS());
            addType(new ISLRT20OS());
            addType(new CLLRT5());
            addType(new CLLRT10());
            addType(new CLLRT15());
            addType(new CLLRT20());
            addType(new CLLRT5OS());
            addType(new CLLRT10OS());
            addType(new CLLRT15OS());
            addType(new CLLRT20OS());
            //SRMs
            addType(new ISSRM1());
            addType(new ISSRM2());
            addType(new ISSRM2());
            addType(new ISSRM4());
            addType(new ISSRM5());
            addType(new ISSRM6());
            addType(new ISSRM2OS());
            addType(new ISSRM4OS());
            addType(new ISSRM6OS());
            addType(new CLSRM1());
            addType(new CLSRM2());
            addType(new CLSRM3());
            addType(new CLSRM4());
            addType(new CLSRM5());
            addType(new CLSRM6());
            addType(new CLSRM2OS());
            addType(new CLSRM4OS());
            addType(new CLSRM6OS());
            addType(new CLAdvancedSRM1());
            addType(new CLAdvancedSRM2());
            addType(new CLAdvancedSRM3());
            addType(new CLAdvancedSRM4());
            addType(new CLAdvancedSRM5());
            addType(new CLAdvancedSRM6());
            addType(new ISStreakSRM2());
            addType(new ISStreakSRM4());
            addType(new ISStreakSRM6());
            addType(new ISStreakSRM2OS());
            addType(new ISStreakSRM4OS());
            addType(new ISStreakSRM6OS());
            addType(new CLStreakSRM2());
            addType(new CLStreakSRM4());
            addType(new CLStreakSRM6());
            addType(new CLStreakSRM2OS());
            addType(new CLStreakSRM4OS());
            addType(new CLStreakSRM6OS());
            //SRTs
            addType(new ISSRT2());
            addType(new ISSRT4());
            addType(new ISSRT6());
            addType(new ISSRT2OS());
            addType(new ISSRT4OS());
            addType(new ISSRT6OS());
            addType(new CLSRT2());
            addType(new CLSRT4());
            addType(new CLSRT6());
            addType(new CLSRT2OS());
            addType(new CLSRT4OS());
            addType(new CLSRT6OS());     
            //RLs
            addType(new ISRL1());
            addType(new ISRL2());
            addType(new ISRL3());
            addType(new ISRL4());
            addType(new ISRL5());
            addType(new ISRL10());
            addType(new ISRL15());
            addType(new ISRL20());
            //ATMs
            addType(new CLATM3());
            addType(new CLATM6());
            addType(new CLATM9());
            addType(new CLATM12());
            //MRMs
            addType(new ISMRM1());
            addType(new ISMRM2());
            addType(new ISMRM3());
            addType(new ISMRM4());
            addType(new ISMRM5());
            addType(new ISMRM10());
            addType(new ISMRM20());
            addType(new ISMRM30());
            addType(new ISMRM40());
            addType(new ISMRM10OS());
            addType(new ISMRM20OS());
            addType(new ISMRM30OS());
            addType(new ISMRM40OS());
            //NARCs
            addType(new ISNarc());
            addType(new ISNarcOS());
            addType(new CLNarc());
            addType(new CLNarcOS());
            addType(new ISImprovedNarc());
            addType(new ISImprovedNarcOS());
            //AMSs
            addType(new ISAMS());
            addType(new ISLaserAMS());
            addType(new ISLaserAMSTHB());
            addType(new CLAMS());
            addType(new CLLaserAMS());
            //TAGs
            addType(new ISLightTAG());
            addType(new ISTAG());
            addType(new ISC3M());
            addType(new CLLightTAG());
            addType(new CLTAG());
            //MMLs
            addType(new ISMML3());
            addType(new ISMML5());
            addType(new ISMML7());
            addType(new ISMML9());
            //Arty
            addType(new ISLongTom());
            addType(new ISThumper());
            addType(new ISSniper());
            addType(new ISArrowIV());
            addType(new CLLongTom());
            addType(new CLSniper());
            addType(new CLThumper());
            addType(new CLArrowIV());
            //MFUK weapons
            addType(new CLPlasmaRifle());
            addType(new CLRAC2());
            addType(new CLRAC5());
            addType(new CLRAC10());
            addType(new CLRAC20());
            //misc lvl3 stuff
            addType(new ISRailGun());
            //MapPack Solaris VII
            addType(new ISMagshotGaussRifle());
            addType(new CLMagshotGaussRifle());
            addType(new ISMPod());
            addType(new CLMPod());
            //Thunderbolts
            addType(new ISThunderBolt5());
            addType(new ISThunderBolt10());
            addType(new ISThunderBolt15());
            addType(new ISThunderBolt20());
            //Infantry Attacks
            addType(new LegAttack());
            addType(new SwarmAttack());
            addType(new StopSwarmAttack());
            //Protomech Weapons
            addType(new CLPROSRM1());
            addType(new CLPROSRM2());
            addType(new CLPROSRM3());
            addType(new CLPROSRM4());
            addType(new CLPROSRM5());
            addType(new CLPROSRM6());
            addType(new CLPROSRT1());
            addType(new CLPROSRT2());
            addType(new CLPROSRT3());
            addType(new CLPROSRT4());
            addType(new CLPROSRT5());
            addType(new CLPROSRT6());
            addType(new CLPROStreakSRM1());
            addType(new CLPROStreakSRM2());
            addType(new CLPROStreakSRM3());
            addType(new CLPROStreakSRM4());
            addType(new CLPROStreakSRM5());
            addType(new CLPROStreakSRM6());
            addType(new CLPROLRM1());
            addType(new CLPROLRM2());
            addType(new CLPROLRM3());
            addType(new CLPROLRM4());
            addType(new CLPROLRM5());
            addType(new CLPROLRM6());
            addType(new CLPROLRM7());
            addType(new CLPROLRM8());
            addType(new CLPROLRM9());
            addType(new CLPROLRM10());
            addType(new CLPROLRM11());
            addType(new CLPROLRM12());
            addType(new CLPROLRM13());
            addType(new CLPROLRM14());
            addType(new CLPROLRM15());
            addType(new CLPROLRM16());
            addType(new CLPROLRM17());
            addType(new CLPROLRM18());
            addType(new CLPROLRM19());
            addType(new CLPROLRM20());
            addType(new CLPROLRT1());
            addType(new CLPROLRT2());
            addType(new CLPROLRT3());
            addType(new CLPROLRT4());
            addType(new CLPROLRT5());
            addType(new CLPROLRT6());
            addType(new CLPROLRT7());
            addType(new CLPROLRT8());
            addType(new CLPROLRT9());
            addType(new CLPROLRT10());
            addType(new CLPROLRT11());
            addType(new CLPROLRT12());
            addType(new CLPROLRT13());
            addType(new CLPROLRT14());
            addType(new CLPROLRT15());
            addType(new CLPROLRT16());
            addType(new CLPROLRT17());
            addType(new CLPROLRT18());
            addType(new CLPROLRT19());
            addType(new CLPROLRT20());

            //Infantry Weapons
            addType(new InfantryRifleWeapon());
            addType(new InfantrySRMWeapon());
            addType(new InfantryInfernoSRMWeapon());
            addType(new InfantryLRMWeapon());
            addType(new InfantryLaserWeapon());
            addType(new InfantryFlamerWeapon());
            addType(new InfantryMGWeapon());
            addType(new ISFireExtinguisher());
            addType(new CLFireExtinguisher());

            //plasma weapons
            addType(new ISPlasmaRifle());
            addType(new CLPlasmaCannon());

            //BA weapons
            addType(new CLSmallLaser());
            addType(new ISLightRecoillessRifle());
            addType(new ISMediumRecoillessRifle());
            addType(new ISHeavyRecoillessRifle());
            addType(new CLLightRecoillessRifle());
            addType(new CLMediumRecoillessRifle());
            addType(new CLHeavyRecoillessRifle());
            addType(new CLBearhunterSuperheavyAC());
            addType(new ISKingDavidLightGaussRifle());
            addType(new ISDavidLightGaussRifle());
            addType(new ISFiredrakeNeedler());
            addType(new ISBAPlasmaRifle());
            addType(new ISHeavyMortar());
            addType(new ISLightMortar());
            addType(new ISAutoGrenadeLauncher());
            addType(new ISMicroGrenadeLauncher());
            addType(new CLHeavyGrenadeLauncher());
            addType(new ISCompactNarc());
            addType(new ISMineLauncher());
            addType(new CLMicroBomb());
            addType(new ISGrandMaulerGaussCannon());
            addType(new ISTsunamiGaussRifle());
            addType(new ISSingleBAMG());
            addType(new ISSingleBAFlamer());
            addType(new ISSingleBASmallPulseLaser());
            addType(new ISSingleBASmallLaser());
        }

    public String toString() {
        return "WeaponType: " + name;
    }
}
