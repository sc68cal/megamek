/**
 * MegaMek - Copyright (C) 2000-2002 Ben Mazur (bmazur@sev.org)
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

package megamek.common.actions;

/**
 * The prone attacker thrashes at the target.
 */
public class ThrashAttackAction
    extends AbstractAttackAction
{   
    public ThrashAttackAction(int entityId, int targetId) {
        super(entityId, targetId);
     }

    public ThrashAttackAction(int entityId, int targetType, int targetId) {
        super(entityId, targetType, targetId);
     }
}