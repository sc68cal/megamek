/*
 * MegaMek - Copyright (C) 2000-2003 Ben Mazur (bmazur@sev.org)
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
 * Mounted.java
 *
 * Created on April 1, 2002, 1:29 PM
 */

package megamek.common;

import java.io.*;

/**
 * This describes equipment mounted on a mech.
 *
 * @author  Ben
 * @version 
 */
public class Mounted implements Serializable, RoundUpdated {
    
    private boolean usedThisRound = false;
    private boolean destroyed = false;
    private boolean hit = false;
    private boolean missing = false;
    private boolean jammed = false;
    
    private int mode; //Equipment's current state.  On or Off.  Sixshot or Fourshot, etc
    private int pendingMode = -1; // if mode changes happen at end of turn
    
    private int location;
    private boolean rearMounted;
    
    private Mounted linked = null; // for ammo, or artemis
    private Mounted linkedBy = null; // reverse link for convenience
    
    private Entity entity; // what I'm mounted on
    
    private transient EquipmentType type;
    private String typeName;

    // ammo-specific stuff.  Probably should be a subclass
    private int shotsLeft;
    private boolean m_bPendingDump;
    private boolean m_bDumping;
    
    // handle split weapons
    private boolean bSplit = false;
    private int nFoundCrits = 0;
    

    /** Creates new Mounted */
    public Mounted(Entity entity, EquipmentType type) {
        this.entity = entity;
        this.type = type;
        this.typeName = type.getInternalName();
        
        if (type instanceof AmmoType) {
            shotsLeft = ((AmmoType)type).getShots();
        }
    }
    
    /**
     * Changing ammo loadouts allows updating AmmoTypes of existing bins.
     * This is the only circumstance under which this should happen.
     */
    
    public void changeAmmoType(AmmoType at) {
        if ( !(type instanceof AmmoType)) {
            System.out.println("Attempted to change ammo type of non-ammo");
            return;
        }
        this.type = at;
        this.typeName = at.getInternalName();
        shotsLeft = at.getShots();
    }

    /**
     * Restores the equipment from the name
     */
    public void restore() {
        this.type = EquipmentType.getByInternalName(typeName);
        
        if (this.type == null) {
            System.err.println("Mounted.restore: could not restore equipment type \"" + typeName + "\"");
        }
    }
    
    public EquipmentType getType() {
        return type;
    }
    
    public String curMode() {
        return type.getModes()[mode];
    }
    
    public String pendingMode() {
        if (pendingMode == -1) {
            return "None";
        }
        return type.getModes()[pendingMode];
    }
    
    public int switchMode() {
        if (type.hasModes()) {
            int nMode = 0;
            if (pendingMode > -1) {
                nMode = (pendingMode + 1) % type.getModes().length;
            }
            else {
                nMode = (mode + 1) % type.getModes().length;
            }
            setMode(nMode);
            return nMode;
        }
        return -1;
    }
    
    public int setMode(String s) {
        for (int x = 0; x < type.getModes().length; x++) {
            if (type.getModes()[x].equals(s)) {
                setMode(x);
                return x;
            }
        }
        return -1;
    }
    
    public void setMode(int n) {
        if (type.hasModes()) {
            if (type.hasInstantModeSwitch()) {
                mode = n;
            }
            else if (pendingMode != n) {
                pendingMode = n;
            }
        }
    }
    
    public void newRound() {
        setUsedThisRound(false);
        if (type.hasModes() && pendingMode != -1) {
            mode = pendingMode;
            pendingMode = -1;
        }
    }
    
    
    /**
     * Shortcut to type.getName()
     */
    public String getName() {
        return type.getName();
    }
    
    public String getDesc() {
        StringBuffer desc = new StringBuffer(type.getDesc());
        if (destroyed) {
            desc.insert(0, "*");
        } else if (usedThisRound) {
            desc.insert(0, "+");
        } else if (jammed) {
            desc.insert(0, "j ");
        }
        if (rearMounted) {
            desc.append(" (R)");
        }
        if (type instanceof AmmoType) {
            desc.append(" (");
            desc.append(shotsLeft);
            desc.append(")");
        }
        return desc.toString();
    }
    
    public boolean isReady() {
        return !usedThisRound && !destroyed && !jammed;
    }
    
    public boolean isUsedThisRound() {
        return usedThisRound;
    }
    
    public void setUsedThisRound(boolean usedThisRound) {
        this.usedThisRound = usedThisRound;
    }
    
    public boolean isDestroyed() {
        return destroyed;
    }
    
    public void setDestroyed(boolean destroyed) {
        this.destroyed = destroyed;
    }
    
    public boolean isHit() {
        return hit;
    }
    
    public void setHit(boolean hit) {
        this.hit = hit;
    }
    
    public boolean isMissing() {
        return missing;
    }
    
    public void setMissing(boolean missing) {
        this.missing = missing;
    }
    
    public boolean isJammed() {
        return jammed;
    }
    
    public void setJammed(boolean j) {
        jammed = j;
    }
    
    public int getShotsLeft() {
        return shotsLeft;
    }
    
    public void setShotsLeft(int shotsLeft) {
        this.shotsLeft = shotsLeft;
    }
    
    /**
     * Returns how many shots the weapon is using
     */
    public int howManyShots() {
        final WeaponType wtype = (WeaponType)this.getType();
        int nShots = 1;
        // figure out # of shots for variable-shot weapons
        if (wtype.getAmmoType() == AmmoType.T_AC_ULTRA && this.curMode().equals("Ultra")) {
            nShots = 2;
        } else if (wtype.getAmmoType() == AmmoType.T_AC_ROTARY ||
                   wtype.getInternalName()
                   .equals(BattleArmor.MINE_LAUNCHER)) {
            if (this.curMode().equals("2-shot")) {
                nShots = 2;
            } else if (this.curMode().equals("4-shot")) {
                nShots = 4;
            } else if (this.curMode().equals("6-shot")) {
                nShots = 6;
            }
        }
        return nShots;
    }
    
    public boolean isPendingDump() {
        return m_bPendingDump;
    }
    
    public void setPendingDump(boolean b) {
        m_bPendingDump = b;
    }
    
    public boolean isDumping() {
        return m_bDumping;
    }
    
    public void setDumping(boolean b) {
        m_bDumping = b;
    }
    
    
    public int getLocation() {
        return location;
    }
    
    public boolean isRearMounted() {
        return rearMounted;
    }
    
    public void setLocation(int location) {
        setLocation(location, false);
    }
    
    public void setLocation(int location, boolean rearMounted) {
        this.location = location;
        this.rearMounted = rearMounted;
    }
    
    public Mounted getLinked() {
        return linked;
    }
    
    public Mounted getLinkedBy() {
        return linkedBy;
    }

    public void setLinked(Mounted linked) {
        this.linked = linked;
        linked.setLinkedBy(this);
    }
    
    // should only be called by setLinked()
    // in the case of a many-to-one relationship (like ammo) this is meaningless
    protected void setLinkedBy(Mounted linker) {
        if (linker.getLinked() != this) {
            // liar
            return;
        }
        linkedBy = linker;
    }
    
    public int getFoundCrits() {
        return nFoundCrits;
    }
    
    public void setFoundCrits(int n) {
        nFoundCrits = n;
    }
    
    public boolean isSplit() {
        return bSplit;
    }
    
    public void setSplit(boolean b) {
        bSplit = b;
    }
    
    public int getExplosionDamage() {
        if (type instanceof AmmoType) {
            AmmoType atype = (AmmoType)type;
            return atype.getDamagePerShot() * atype.getRackSize() * shotsLeft;
        } else if (type instanceof WeaponType) {
            WeaponType wtype = (WeaponType)type;
            //HACK: gauss rifle damage hardcoding
            if (wtype.getAmmoType() == AmmoType.T_GAUSS) {
                return 20;
            } else if (wtype.getAmmoType() == AmmoType.T_GAUSS_LIGHT) {
                return 16;
            } else if (wtype.getAmmoType() == AmmoType.T_GAUSS_HEAVY) {
                return 25;
            } else if (wtype.getAmmoType() == AmmoType.T_AC_ROTARY) {
                return wtype.getDamage();
            }
        }
        // um, otherwise, I'm not sure
        System.err.println("mounted: unable to determine explosion damage for "
                            + getName());
        return 0;
    }
}
