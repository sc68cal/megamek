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

package megamek.common;

import java.awt.Polygon;
import java.util.*;

import megamek.common.actions.*;

/**
 * The compute class is designed to provide static methods for mechs
 * and other entities moving, firing, etc.
 */
public class Compute
{
    public static final int        ARC_360          = 0;
    public static final int        ARC_FORWARD      = 1;
    public static final int        ARC_LEFTARM      = 2;
    public static final int        ARC_RIGHTARM     = 3;
    public static final int        ARC_REAR         = 4;
    public static final int        ARC_LEFTSIDE     = 5;
    public static final int        ARC_RIGHTSIDE    = 6;
    
    public static final int        GEAR_LAND        = 0;
    public static final int        GEAR_BACKUP      = 1;
    public static final int        GEAR_JUMP        = 2;
    public static final int        GEAR_CHARGE      = 3;
    public static final int        GEAR_DFA         = 4;
    
    public static final Random random = new Random();
    
    /**
     * Simulates six-sided die rolls.
     */
    public static int d6(int dice) {
        int total = 0;
        for (int i = 0; i < dice; i++) {
            total += (int)(random.nextDouble() * 6 + 1);
            /*
            double roll = random.nextDouble();
            int side = 1;
            while ((roll -= (1.0 / 6.0)) >= 0) {
                side++;
            }
            total += side;
            */
        }
        return total;
    }
    
    /**
     * A single die
     */
    public static int d6() {
        return d6(1);
    }
    
    /**
     * Returns the odds that a certain number or above 
     * will be rolled on 2d6.
     */
    public static double oddsAbove(int n) {
        if (n < 2) {
            return Double.MAX_VALUE;
        }
        double[] odds = {Double.MAX_VALUE, Double.MAX_VALUE,
                         100.0, 97.2, 91.6, 83.3, 72.2, 58.3, 
                         41.6, 27.7, 16.6, 8.3, 5.56, 2.78, 0};
        return odds[n];
    }
    
    /**
     * Generates a MovementData to rotate the entity to it's new facing
     */
    public static MovementData rotatePathfinder(Game game, int entityId, 
                                                int destFacing) {
        final Entity entity = game.getEntity(entityId);
        return rotatePathfinder(entity.getFacing(), destFacing);
    }
    
    /**
     * Generates a MovementData object to rotate from the start facing to the
     * destination facing.
     */
    public static MovementData rotatePathfinder(int facing, int destFacing) {
        MovementData md = new MovementData();

        // adjust facing
        while (facing != destFacing) {
            int stepType = MovementData.getDirection(facing, destFacing);
            md.addStep(stepType);
            facing = MovementData.getAdjustedFacing(facing, stepType);
        }
        
        return md;
    }
    
    /**
     * Generates MovementData for a mech to move from its current position
     * to the destination.
     */
    public static MovementData lazyPathfinder(Game game, int entityId, 
                                                  Coords dest) {
        final Entity entity = game.getEntity(entityId);
        return lazyPathfinder(entity.getPosition(), entity.getFacing(), dest);
    }
    
    /**
     * Generates MovementData for a mech to move from the start position and
     * facing to the destination
     */
    public static MovementData lazyPathfinder(Coords src, int facing, Coords dest) {
        MovementData md = new MovementData();
        
        int curFacing = facing;
        Coords curPos = new Coords(src);
        
        while(!curPos.equals(dest)) {
            // adjust facing
            md.append(rotatePathfinder(curFacing, curPos.direction1(dest)));
            // step forwards
            md.addStep(MovementData.STEP_FORWARDS);

            curFacing = curPos.direction1(dest);
            curPos = curPos.translated(curFacing);
        }
        
        return md;
    }
    
    
    
    /**
     * Backwards walking pathfinder.  Note that this will let you do impossible
     * things, like run backwards.
     */
    public static MovementData backwardsLazyPathfinder(Coords src, int facing, Coords dest) {
        MovementData md = new MovementData();
        
        int curFacing = facing;
        Coords curPos = new Coords(src);
        
        while(!curPos.equals(dest)) {
            // adjust facing
            int destFacing = (curPos.direction1(dest) + 3) % 6;
            md.append(rotatePathfinder(curFacing, destFacing));
            
            // step backwards
            md.addStep(MovementData.STEP_BACKWARDS);

            curFacing = destFacing;
            curPos = curPos.translated((destFacing + 3) % 6);
        }
        
        return md;
    }
    
    /**
     * Charge pathfinder.  Finds a path up to the square before the target,
     * then charges
     */
    public static MovementData chargeLazyPathfinder(Coords src, int facing,
                                                        Coords dest) {
        MovementData md = new MovementData();
        
        int curFacing = facing;
        Coords curPos = new Coords(src);
        
        Coords subDest = dest.translated(dest.direction1(src));
        
        while(!curPos.equals(subDest)) {
            // adjust facing
            md.append(rotatePathfinder(curFacing, curPos.direction1(subDest)));
            // step forwards
            md.addStep(MovementData.STEP_FORWARDS);

            curFacing = curPos.direction1(subDest);
            curPos = curPos.translated(curFacing);
        }
        
        // adjust facing
        md.append(rotatePathfinder(curFacing, curPos.direction1(dest)));
        // charge!
        md.addStep(MovementData.STEP_CHARGE);
        
        return md;
    }
    
    
    /**
     * "Compiles" some movement data by setting all the flags.  Determines which
     * steps are possible, how many movement points each uses, and where they
     * occur.
     */
    public static void compile(Game game, int entityId, MovementData md) {
        final Entity entity = game.getEntity(entityId);
        
        // some flags
        int curFacing = entity.getFacing();
        Coords lastPos;
        Coords curPos = new Coords(entity.getPosition());
        int mpUsed = 0;
        int distance = 0;
        boolean isProne = entity.isProne();
        boolean hasJustStood = false;
        
        int overallMoveType = Entity.MOVE_WALK;
        boolean isJumping = false;
        boolean isRunProhibited = false;
        boolean isMovementLegal = true;
        
        boolean isDanger = false;
        boolean isPastDanger = false;
        
        // check for jumping
        if (md.contains(MovementData.STEP_START_JUMP)) {
            overallMoveType = Entity.MOVE_JUMP;
            isJumping = true;
        }
        
        // check for backwards movement
        if (md.contains(MovementData.STEP_BACKWARDS)) {
            isRunProhibited = true;
        }
        
        // first pass: set position, facing and mpUsed; figure out overallMoveType
        for (final Enumeration i = md.getSteps(); i.hasMoreElements();) {
            final MovementData.Step step = (MovementData.Step)i.nextElement();
            
            int stepMp = 0;
            
            lastPos = new Coords(curPos);
            
            // 
            switch(step.getType()) {
            case MovementData.STEP_TURN_LEFT :
            case MovementData.STEP_TURN_RIGHT :
                stepMp = (isJumping || hasJustStood) ? 0 : 1;
                curFacing = MovementData.getAdjustedFacing(curFacing, step.getType());
                break;
            case MovementData.STEP_FORWARDS :
            case MovementData.STEP_BACKWARDS :
            case MovementData.STEP_CHARGE :
            case MovementData.STEP_DFA :
                // step forwards or backwards
                if (step.getType() == MovementData.STEP_BACKWARDS) {
                    curPos = curPos.translated((curFacing + 3) % 6);
                } else {
                    curPos = curPos.translated(curFacing);
                }

                stepMp = getMovementCostFor(game, entityId, lastPos, curPos,
                                            overallMoveType);
                // check for water
                if (game.board.getHex(curPos).getTerrainType() == Terrain.WATER) {
                    isRunProhibited = true;
                }
                hasJustStood = false;
                distance += 1;
                break;
            case MovementData.STEP_GET_UP :
                stepMp = 2;
                hasJustStood = true;
                break;
            default :
                stepMp = 0;
            }
            
            mpUsed += stepMp;
            
            // check for running
            if (overallMoveType == Entity.MOVE_WALK 
                && mpUsed > entity.getWalkMP()) {
                overallMoveType = Entity.MOVE_RUN;
            }
            
            // set flags
            step.setPosition(curPos);
            step.setFacing(curFacing);
            step.setMpUsed(mpUsed);
            step.setDistance(distance);
        }
        
        // running with gyro or hip hit is dangerous!
        if (!isRunProhibited && overallMoveType == Entity.MOVE_RUN
            && (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM,
                                             Mech.SYSTEM_GYRO,Mech.LOC_CT) > 0
                || entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM,
                                                Mech.ACTUATOR_HIP, Mech.LOC_RLEG) > 0
                || entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM,
                                                Mech.ACTUATOR_HIP, Mech.LOC_LLEG) > 0)) {
            md.getStep(0).setDanger(true);
        }
        
        // second pass: set moveType, illegal, trouble flags
        curPos = new Coords(entity.getPosition());
        for (final Enumeration i = md.getSteps(); i.hasMoreElements();) {
            final MovementData.Step step = (MovementData.Step)i.nextElement();
            
            lastPos = new Coords(curPos);
            curPos = step.getPosition();
            
            // guilty until proven innocent
            int moveType = Entity.MOVE_ILLEGAL;
            
            // check for valid jump mp
            if (overallMoveType == Entity.MOVE_JUMP 
                && step.getMpUsed() <= entity.getJumpMP()
                && !entity.isProne()) {
                moveType = Entity.MOVE_JUMP;
            }
            
            // check for valid walk/run mp
            if ((overallMoveType == Entity.MOVE_WALK
                 || overallMoveType == Entity.MOVE_RUN)
                && (!entity.isProne() ||
                    md.contains(MovementData.STEP_GET_UP))) {
                if (step.getMpUsed() <= entity.getWalkMP()) {
                    moveType = Entity.MOVE_WALK;
                } else if (step.getMpUsed() <= entity.getRunMP()
                           && !isRunProhibited) {
                    moveType = Entity.MOVE_RUN;
                }
            }
            
            // check if this movement is illegal for reasons other than points
            if (!isMovementPossible(game, entityId, lastPos, curPos, moveType, step.getType())) {
                moveType = Entity.MOVE_ILLEGAL;
            }
            
            // no legal moves past an illegal one
            if (moveType == Entity.MOVE_ILLEGAL) {
                isMovementLegal = false;
            }
            
            // check for danger
            isDanger = step.isDanger();
            isDanger |= isPilotingSkillNeeded(game, entityId, lastPos, 
                                              curPos, moveType);
            
            // getting up is also danger
            if (step.getType() == MovementData.STEP_GET_UP) {
                isDanger = true;
            }
            
            // set flags
            step.setDanger(isDanger);
            step.setPastDanger(isPastDanger);
            step.setMovementType(isMovementLegal 
                                 ? moveType : Entity.MOVE_ILLEGAL);
            
            // set past danger
            isPastDanger |= isDanger;
        }
        
        // third pass (sigh) : avoid stacking violations
        isMovementLegal = false;
        for (int i = md.length() - 1; i >= 0; i--) {
            final MovementData.Step step = md.getStep(i);
            
            // find the last legal step
            if (!isMovementLegal && step.getMovementType() != Entity.MOVE_ILLEGAL) {
                final Entity entityInHex = game.getEntity(step.getPosition());
                if (entityInHex != null && !entityInHex.equals(entity)
                        && step.getType() != MovementData.STEP_CHARGE
                        && step.getType() != MovementData.STEP_DFA) {
                    // can't move here
                    step.setMovementType(Entity.MOVE_ILLEGAL);
                } else {
                    isMovementLegal = true;
                }
            }
        }
        
        md.setCompiled(true);
    }
    
    /**
     * Amount of movement points required to move from start to dest
     */
    public static int getMovementCostFor(Game game, int entityId, 
                                             Coords src, Coords dest,
                                             int movementType) {
        final Entity entity = game.getEntity(entityId);
        final Hex srcHex = game.board.getHex(src);
        final Hex destHex = game.board.getHex(dest);
        
        // arguments valid?
        if (entity == null) {
            throw new IllegalArgumentException("Entity invalid.");
        }
        if (src.distance(dest) > 1) {
            throw new IllegalArgumentException("Coordinates must be adjacent.");
        }
        if (srcHex == null || destHex == null) {
            throw new IllegalArgumentException("Coordinates must be on the board.");
        }
        
        // jumping always costs 1
        if (movementType == Entity.MOVE_JUMP) {
            return 1;
        }
        
        int mp = 0;
        
        // account for terrain
        switch(destHex.getTerrainType()) {
        default:
            mp = 1;
            break;
        case Terrain.ROUGH :
        case Terrain.RUBBLE :
        case Terrain.FOREST_LITE :
            mp = 2;
            break;
        case Terrain.FOREST_HVY :
            mp = 3;
            break;
        case Terrain.WATER :
            switch(destHex.getElevation()) {
            case 0 :
                mp = 1;
                break;
            case -1 :
                mp = 2;
                break;
            default:
                mp = 4;
            }
            break;
        }
        // account for elevation?
        if (srcHex.getElevation() != destHex.getElevation()) {
            int delta_e = Math.abs(srcHex.getElevation() - destHex.getElevation());
            mp += delta_e;
        }
        
        return mp;
    }
    
    /**
     * Is movement possible from start to dest?
     * 
     * This makes the assumtion that entity.getPosition() returns the 
     * position the movement started in.
     */
    public static boolean isMovementPossible(Game game, int entityId, 
                                             Coords src, Coords dest,
                                             int entityMoveType,
                                             int stepType) {
        final Entity entity = game.getEntity(entityId);
        final Hex srcHex = game.board.getHex(src);
        final Hex destHex = game.board.getHex(dest);
        
        // arguments valid?
        if (entity == null) {
            throw new IllegalArgumentException("Entity invalid.");
        }
        if (src.distance(dest) > 1) {
            throw new IllegalArgumentException("Coordinates must be adjacent.");
        }
        
        if (entityMoveType == Entity.MOVE_ILLEGAL) {
            // that was easy
            return false;
        }
        // another easy check
        if (!game.board.contains(dest)) {
            return false;
        }
        // check elevation difference > 2
        if (entityMoveType != Entity.MOVE_JUMP 
            && Math.abs(srcHex.getElevation() - destHex.getElevation()) > 2) {
            return false;
        }
        // units moving backwards may not change elevation levels (I think this rule's dumb)
        if (stepType == MovementData.STEP_BACKWARDS
            && srcHex.getElevation() != destHex.getElevation()) {
            return false;
        }
        // can't run into water
        if (entityMoveType == Entity.MOVE_RUN 
            && destHex.getTerrainType() == Terrain.WATER
            && destHex.getElevation() < 0) {
            return false;
        }
        // can't jump out of water
        if (entityMoveType == Entity.MOVE_JUMP 
            && entity.getPosition().equals(src)
            && srcHex.getTerrainType() == Terrain.WATER) {
            return false;
        }
        // can't move into a hex with an enemy unit, unless charging or jumping
        if (entityMoveType != Entity.MOVE_JUMP
            && stepType != MovementData.STEP_CHARGE
            && stepType != MovementData.STEP_DFA
            && game.getEntity(dest) != null
            && !game.getEntity(dest).getOwner().equals(entity.getOwner())) {
            return false;
        }
        // can't jump over too-high terrain
        if (entityMoveType == Entity.MOVE_JUMP
            && destHex.getElevation() 
               > (game.board.getHex(entity.getPosition()).getElevation() +
                  entity.getJumpMP())) {
            return false;
        }
        
        return true;
    }
    
    /** 
     * @return true if a piloting skill roll is needed to traverse the terrain
     */
    public static boolean isPilotingSkillNeeded(Game game, int entityId, 
                                                Coords src, Coords dest,
                                                int movementType) {
        final Entity entity = game.getEntity(entityId);
        final Hex srcHex = game.board.getHex(src);
        final Hex destHex = game.board.getHex(dest);
        
        // arguments valid?
        if (entity == null) {
            throw new IllegalArgumentException("Entity invalid.");
        }
        if (src.distance(dest) > 1) {
            throw new IllegalArgumentException("Coordinates must be adjacent.");
        }
        
        // let's only worry about actual movement, please
        if (src.equals(dest)) {
            return false;
        }
        
        // check for rubble
        if (movementType != Entity.MOVE_JUMP
            && (destHex.getTerrainType() == Terrain.RUBBLE)) {
            return true;
        }
        
        // check for water
        if (movementType != Entity.MOVE_JUMP
            && destHex.getTerrainType() == Terrain.WATER
            && destHex.getElevation() < 0) {
            return true;
        }
        
        
        return false;
    }
    
    /**
     * Can the defending unit be displaced from the source to the destination?
     */
    public static boolean isValidDisplacement(Game game, int entityId, 
                                              Coords src, int direction) {
        return isValidDisplacement(game, entityId, src, 
                                   src.translated(direction));
    }
    /**
     * Can the defending unit be displaced from the source to the destination?
     */
    public static boolean isValidDisplacement(Game game, int entityId, 
                                              Coords src, Coords dest) {
        final Entity entity = game.getEntity(entityId);
        final Hex srcHex = game.board.getHex(src);
        final Hex destHex = game.board.getHex(dest);
        final Coords[] intervening = intervening(src, dest);
        
        // arguments valid?
        if (entity == null) {
            throw new IllegalArgumentException("Entity invalid.");
        }
        
        // an easy check
        if (!game.board.contains(dest)) {
            return false;
        }
        for (int i = 0; i < intervening.length; i++) {
            final Hex hex = game.board.getHex(intervening[i]);
            // can't go up 2+ levels
            if (hex.getElevation() - srcHex.getElevation() > 1) {
                return false;
            }
        }
        
        // okay, that's about all the checks
        return true;
    }
    
    /**
     * Gets a valid displacement, preferably in the direction indicated.
     * 
     * @return valid displacement coords, or null if none
     */
    public static Coords getValidDisplacement(Game game, int entityId, 
                                              Coords src, int direction) {
        final Entity entity = game.getEntity(entityId);
        // check the surrounding hexes
        if (isValidDisplacement(game, entityId, src, direction)) {
            // direction already valid?  (probably)
            return src.translated(direction);
        } else if (isValidDisplacement(game, entityId, src, (direction + 1) % 6)) {
            // 1 right?
            return src.translated((direction + 1) % 6);
        } else if (isValidDisplacement(game, entityId, src, (direction + 5) % 6)) {
            // 1 left?
            return src.translated((direction + 5) % 6);
        } else if (isValidDisplacement(game, entityId, src, (direction + 2) % 6)) {
            // 2 right?
            return src.translated((direction + 2) % 6);
        } else if (isValidDisplacement(game, entityId, src, (direction + 4) % 6)) {
            // 2 left?
            return src.translated((direction + 4) % 6);
        } else if (isValidDisplacement(game, entityId, src, (direction + 3) % 6)) {
            // opposite?
            return src.translated((direction + 3) % 6);
        } else {
            // well, tried to accomodate you... too bad.
            return null;
        }
    }
    
    /**
     * Can we do a valid DFA with this movement?
     */
    public static boolean isValidDFA(Game game, int entityId, MovementData md) {
        final Entity entity = game.getEntity(entityId);
        Coords targetCoords;
        Entity target;
        MovementData.Step lastValid = null;
        Hex srcHex;
        Hex destHex;
        
        // need to have jumped (duh)
        if (!md.contains(MovementData.STEP_START_JUMP)) { 
            return false;
        }
        
        // determine last valid step
        compile(game, entityId, md);
        for (final Enumeration i = md.getSteps(); i.hasMoreElements();) {
            final MovementData.Step step = (MovementData.Step)i.nextElement();
            if (step.getMovementType() == Entity.MOVE_ILLEGAL) {
                break;
            } else {
                lastValid = step;
            }
        }
        
        // check target
        targetCoords = lastValid.getPosition().translated(lastValid.getFacing());
        target = game.getEntity(targetCoords);

        // do you have enough mp?   
    
        return true;
    }
    
    /**
     * Can we do a valid charge with this movement?
     */
    public static boolean isValidCharge(Game game, int entityId, MovementData md) {
        final Entity entity = game.getEntity(entityId);
        Coords targetCoords;
        Entity target;
        MovementData.Step lastValid = null;
        Hex srcHex;
        Hex destHex;
        
        // no jumping or backwards
        if (md.contains(MovementData.STEP_START_JUMP)
            || md.contains(MovementData.STEP_BACKWARDS)) {
            return false;
        }
        
        // determine last valid step
        compile(game, entityId, md);
        for (final Enumeration i = md.getSteps(); i.hasMoreElements();) {
            final MovementData.Step step = (MovementData.Step)i.nextElement();
            if (step.getMovementType() == Entity.MOVE_ILLEGAL) {
                break;
            } else {
                lastValid = step;
            }
        }
        
        if (lastValid == null) {
            
            System.out.println("compute.isvalidcharge: could not find last valid step");
            
            return false;
        }
        
        // determine target
        targetCoords = lastValid.getPosition().translated(lastValid.getFacing());
        target = game.getEntity(targetCoords);
        
        if (target == null) {
            
            System.out.println("compute.isvalidcharge: could not find target");
            
            return false;
        }
        
        // target must have moved already
        if (target.ready) {
            
            System.out.println("compute.isvalidcharge: target has not moved yet");
            
            return false;
        }

        // do you have enough mp?   
    
        return true;
    }
    
    /**
     * Returns an entity's base piloting skill roll needed
     */
    public static PilotingRollData getBasePilotingRoll(Game game, int entityId) {
        final Entity entity = game.getEntity(entityId);
        
        PilotingRollData roll;
        
        // gyro operational?
        if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_GYRO, Mech.LOC_CT) > 1) {
            return new PilotingRollData(entityId, PilotingRollData.AUTOMATIC_FALL, "Gyro destroyed");
        }
        // both legs present?
        if (entity.isLocationDestroyed(Mech.LOC_RLEG) && entity.isLocationDestroyed(Mech.LOC_LLEG)) {
            return new PilotingRollData(entityId, PilotingRollData.AUTOMATIC_FALL, "Both legs destroyed");
        }
        // entity shut down?
        if (entity.isShutDown()) {
            return new PilotingRollData(entityId, PilotingRollData.AUTOMATIC_FALL, "Reactor shut down");
        }
        // pilot awake?
        if (!entity.getCrew().isActive()) {
            return new PilotingRollData(entityId, PilotingRollData.AUTOMATIC_FALL, "Pilot unconcious");
        }
        
        // okay, let's figure out the stuff then
        roll = new PilotingRollData(entityId, entity.getCrew().getPiloting(),  
                                    entity.getCrew().getPiloting() + " (Base piloting skill)");
        
        // right leg destroyed?
        if (entity.isLocationDestroyed(Mech.LOC_RLEG)) {
            roll.addModifier(5, "Right Leg destroyed");
        } else {
            // check for damaged hip actuators
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_HIP, Mech.LOC_RLEG) > 0) {
                roll.addModifier(2, "Right Hip Actuator destroyed");
            }
            // upper leg actuators?
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_UPPER_LEG, Mech.LOC_RLEG) > 0) {
                roll.addModifier(1, "Right Upper Leg Actuator destroyed");
            }
            // lower leg actuators?
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_LOWER_LEG, Mech.LOC_RLEG) > 0) {
                roll.addModifier(1, "Right Lower Leg Actuator destroyed");
            }
            // foot actuators?
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_FOOT, Mech.LOC_RLEG) > 0) {
                roll.addModifier(1, "Right Foot Actuator destroyed");
            }
        }
        // left leg destroyed?
        if (entity.isLocationDestroyed(Mech.LOC_LLEG)) {
            roll.addModifier(5, "Left Leg destroyed");
        } else {
            // check for damaged hip actuators
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_HIP, Mech.LOC_LLEG) > 0) {
                roll.addModifier(2, "Left Hip Actuator destroyed");
            }
            // upper leg actuators?
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_UPPER_LEG, Mech.LOC_LLEG) > 0) {
                roll.addModifier(1, "Left Upper Leg Actuator destroyed");
            }
            // lower leg actuators?
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_LOWER_LEG, Mech.LOC_LLEG) > 0) {
                roll.addModifier(1, "Left Lower Leg Actuator destroyed");
            }
            // foot actuators?
            if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_FOOT, Mech.LOC_LLEG) > 0) {
                roll.addModifier(1, "Left Foot Actuator destroyed");
            }
        }
        // gyro hit?
        if (entity.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_GYRO, Mech.LOC_CT) > 0) {
            roll.addModifier(3, "Gyro damaged");
        }
        
        return roll;
    }
    
    public static ToHitData toHitWeapon(Game game, WeaponAttackAction waa) {
        return toHitWeapon(game, waa.getEntityId(), waa.getTargetId(), 
                           waa.getWeaponId());
    }
    
    /**
     * To-hit number for attacker firing a weapon at the target.
     * 
     * @param game          the game
     * @param attackerId    the attacker id number
     * @param targetId      the target id number
     * @param weaponId      the weapon id number
     */
    public static ToHitData toHitWeapon(Game game, int attackerId, 
                                        int targetId, int weaponId) {
        final Entity ae = game.getEntity(attackerId);
        final Entity te = game.getEntity(targetId);
        final MountedWeapon w = ae.getWeapon(weaponId);
        final Coords[] in = intervening(ae.getPosition(), te.getPosition());
        
        ToHitData toHit;
        boolean pc = false; // partial cover
        
        // sensors operational?
        final int sensorHits = ae.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.SYSTEM_SENSORS, Mech.LOC_HEAD);
        if (sensorHits > 1) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Attacker sensors destroyed.");
        }
        
        // weapon in arc?
        if (!isInArc(ae.getPosition(), ae.getSecondaryFacing(), 
            te.getPosition(), ae.getWeaponArc(weaponId))) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not in arc");
        }
        
        // LOS?
        final int ael = ae.getElevation(game.board) + 1;
        final int tel = te.getElevation(game.board) + 1;
        int ilw = 0;  // intervening light woods
        int ihw = 0;  // intervening heavy woods
        
        for (int i = 0; i < in.length; i++) {
            // don't count attacker or target hexes
            if (in[i].equals(ae.getPosition()) || in[i].equals(te.getPosition())) {
                continue;
            }
            
            final Hex h = game.board.getHex(in[i]);
            final int hel = h.getElevation();
            
            // check for block by terrain
            if ((hel > ael && hel > tel) 
                    || (hel > ael && ae.getPosition().distance(in[i]) <= 1)
                    || (hel > tel && te.getPosition().distance(in[i]) <= 1)) {
                return new ToHitData(ToHitData.IMPOSSIBLE, "LOS blocked by terrain");
            }
            
            // determine number of woods hexes in the way
            if (h.getTerrainType() == Terrain.FOREST_LITE 
                    || h.getTerrainType() == Terrain.FOREST_HVY) {
                if ((hel + 2 > ael && hel + 2 > tel) 
                        || (hel + 2 > ael && ae.getPosition().distance(in[i]) <= 1) 
                        || (hel + 2 > tel && te.getPosition().distance(in[i]) <= 1)) {
                    ilw += (h.getTerrainType() == Terrain.FOREST_LITE ? 1 : 0);
                    ihw += (h.getTerrainType() == Terrain.FOREST_HVY ? 1 : 0);
                }
            }
            
            // check for partial cover
            if (te.getPosition().distance(in[i]) <= 1 && hel == tel && ael <= tel) {
                pc = true;
            }
        }
        
        // more than 1 heavy woods or more than two light woods block LOS
        if (ilw + ihw * 2 >= 3) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "LOS blocked by woods");
        }
        
        
        // first: gunnery skill
        toHit = new ToHitData(ae.crew.getGunnery(), 
                              ae.crew.getGunnery() + " (gunnery skill)");
        
        // determine range
        final int range = ae.getPosition().distance(te.getPosition());
        // if out of range, short circuit logic
        if (range > w.getType().getLongRange()) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target out of range");
        }
        if (range > w.getType().getMediumRange()) {
            // long range, add +4
            toHit.addModifier(4, "long range");
        } else if (range > w.getType().getShortRange()) {
            // medium range, add +2
            toHit.addModifier(2, "medium range");
        } else {
            // also check for minimum range
            if (range <= w.getType().getMinimumRange()) {
                int minPenalty = w.getType().getMinimumRange() - range + 1;
                toHit.addModifier(minPenalty, "minumum range");
            }
        }
        
        // attacker movement
        toHit.append(getAttackerMovementModifier(game, attackerId));
        
        // target movement
        toHit.append(getTargetMovementModifier(game, targetId));
        
        // attacker terrain
        toHit.append(getAttackerTerrainModifier(game, attackerId));
        
        // target terrain
        toHit.append(getTargetTerrainModifier(game, targetId));
        
        Hex targetHex = game.board.getHex(te.getPosition());
        if (targetHex.getTerrainType() == Terrain.WATER) {
            if (targetHex.getElevation() == -1) {
                if (te.isProne()) {
                    return new ToHitData(ToHitData.IMPOSSIBLE, "Target prone in depth 1 water");
                } else {
                    pc = true;
                }
            } else if (targetHex.getElevation() < -1) {
                return new ToHitData(ToHitData.IMPOSSIBLE, "Target in depth 2+ water");
            }
        }


        // intervening terrain
        if (ilw > 0) {
            toHit.addModifier(ilw, ilw + " light woods intervening");
        }
        if (ihw > 0) {
            toHit.addModifier(ihw * 2, ihw + " heavy woods intervening");
        }
        
        // partial cover
        if (pc) {
            toHit.addModifier(3, "target has partial cover");
            toHit.setHitTable(ToHitData.HIT_PUNCH);
        }
        
        //TODO: multiple targets...

        // heat
        if (ae.getHeatFiringModifier() != 0) {
            toHit.addModifier(ae.getHeatFiringModifier(), "heat");
        }

        // arm critical hits to attacker
        if (ae.getNumberOfCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_SHOULDER, w.getLocation()) > 0
            && ae.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_SHOULDER, w.getLocation()) > 0) {
            toHit.addModifier(4, "shoulder actuator destroyed");
        } else {
            int actuatorHits = 0;
            if (ae.getNumberOfCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_UPPER_ARM, w.getLocation()) > 0
                && ae.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_UPPER_ARM, w.getLocation()) > 0) {
                actuatorHits++;
            }
            if (ae.getNumberOfCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_LOWER_ARM, w.getLocation()) > 0
                && ae.getDestroyedCriticals(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_LOWER_ARM, w.getLocation()) > 0) {
                actuatorHits++;
            }
            if (actuatorHits > 0) {
                toHit.addModifier(actuatorHits, actuatorHits + " destroyed arm actuators");
            }
        }
        
        // sensors critical hit to attacker
        if (sensorHits > 0) {
            toHit.addModifier(2, "attacker sensors damaged");
        }
        
        // target immobile
        if (te.isImmobile()) {
            toHit.addModifier(-4, "target immobile");
        }
        
        // attacker prone
        if (ae.isProne()) {
            // must have an arm intact
            //TODO: can only fire from one arm, other used for prop
            if (ae.isLocationDestroyed(Mech.LOC_RARM) && ae.isLocationDestroyed(Mech.LOC_LARM)) {
                return new ToHitData(ToHitData.IMPOSSIBLE, "Prone and both arms destroyed");
            }
            if ((ae.isLocationDestroyed(Mech.LOC_LARM) && w.getLocation() == Mech.LOC_RARM)
                || (ae.isLocationDestroyed(Mech.LOC_RARM) && w.getLocation() == Mech.LOC_LARM)) {
                return new ToHitData(ToHitData.IMPOSSIBLE, "Prone and opposite arm destroyed");
            }
            // can't fire leg weapons
            if (w.getLocation() == Mech.LOC_LLEG && w.getLocation() == Mech.LOC_RLEG) {
                return new ToHitData(ToHitData.IMPOSSIBLE, "Can't fire leg-mounted weapons while prone");
            }
            toHit.addModifier(2, "attacker prone");
        }
        
        // target prone
        if (te.isProne()) {
            // easier when point-blank
            if (range == 1) {
                toHit.addModifier(-2, "target prone and adjacent");
            }
            // harder at range
            if (range > 1) {
                toHit.addModifier(1, "target prone and at range");
            }
        }
        
        // factor in target side
        toHit.setSideTable(targetSideTable(ae.getPosition(), te.getPosition(),
                                            te.getFacing()));
        
        // okay!
        return toHit;
    }

    public static ToHitData toHitPunch(Game game, PunchAttackAction paa) {
        return toHitPunch(game, paa.getEntityId(), paa.getTargetId(), 
                          paa.getArm());
    }
    
    
    
    /**
     * To-hit number for the specified arm to punch
     */
    public static ToHitData toHitPunch(Game game, int attackerId, 
                                       int targetId, int arm) {
        final Entity ae = game.getEntity(attackerId);
        final Entity te = game.getEntity(targetId);
        final int attackerElevation = game.board.getHex(ae.getPosition()).getElevation();
        final int targetElevation = game.board.getHex(te.getPosition()).getElevation();
        final int armLoc = (arm == PunchAttackAction.RIGHT)
                           ? Mech.LOC_RARM : Mech.LOC_LARM;
        final int armArc = (arm == PunchAttackAction.RIGHT)
                           ? Compute.ARC_RIGHTARM : Compute.ARC_LEFTARM;
        ToHitData toHit;

        // arguments legal?
        if (arm != PunchAttackAction.RIGHT && arm != PunchAttackAction.LEFT) {
            throw new IllegalArgumentException("Arm must be LEFT or RIGHT");
        }
        if (ae == null || te == null) {
            throw new IllegalArgumentException("Attacker or target id not valid");
        }
        
        // check if arm is present
        if (ae.isLocationDestroyed(armLoc)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Arm missing");
        }
        
        // check if shoulder is functional
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                        Mech.ACTUATOR_SHOULDER, armLoc) == 0) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Shoulder destroyed");
        }  
        
        // check if attacker has fired arm-mounted weapons
        for (Enumeration i = ae.weapons.elements(); i.hasMoreElements();) {
            MountedWeapon weapon = (MountedWeapon)i.nextElement();
            if (weapon.isFiredThisRound()) {
                if (weapon.getLocation() == armLoc) {
                    return new ToHitData(ToHitData.IMPOSSIBLE, 
                                         "Weapons fired from arm this turn");
                }
            }
        }
        
        // check range
        if (ae.getPosition().distance(te.getPosition()) > 1 ) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not in range");
        }
        
        // check elevation (target must be same or one level higher only)
        if (attackerElevation != targetElevation 
            && attackerElevation != (targetElevation - 1)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target elevation not in range");
        }
        
        // check facing
        if (!isInArc(ae.getPosition(), ae.getSecondaryFacing(), 
                     te.getPosition(), armArc)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not in arc");
        }
        
        // can't punch while prone (except vehicles, but they're not in the game yet)
        if (ae.isProne()) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Attacker is prone");
        }
        
        // can't punch prone mechs (unless they're one level higher)
        if (te.isProne() && attackerElevation != (targetElevation - 1)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target is prone");
        }
        
        // okay, modifiers...
        toHit = new ToHitData(4, "4 (base)");
        
        // attacker movement
        toHit.append(getAttackerMovementModifier(game, attackerId));
        
        // target movement
        toHit.append(getTargetMovementModifier(game, targetId));
        
        // attacker terrain
        toHit.append(getAttackerTerrainModifier(game, attackerId));
        
        // target terrain
        toHit.append(getTargetTerrainModifier(game, targetId));
        
        // damaged or missing actuators
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_UPPER_ARM, armLoc) == 0) {
            toHit.addModifier(2, "Upper arm actuator destroyed");
        }
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_LOWER_ARM, armLoc) == 0) {
            toHit.addModifier(2, "Lower arm actuator destroyed");
        }
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_HAND, armLoc) == 0) {
            toHit.addModifier(1, "Hand actuator destroyed");
        }

        // target immobile
        if (te.isImmobile()) {
            toHit.addModifier(-4, "target immobile");
        }
        
        // elevation
        if (attackerElevation == (targetElevation - 1)) {
            if (te.isProne()) {
                toHit.setHitTable(ToHitData.HIT_NORMAL);
            } else {
                toHit.setHitTable(ToHitData.HIT_KICK);
            }
        } else {
            toHit.setHitTable(ToHitData.HIT_PUNCH);
        }
        
        // factor in target side
        toHit.setSideTable(targetSideTable(ae.getPosition(), te.getPosition(),
                                            te.getFacing()));

        // done!
        return toHit;
    }
    
    /**
     * Damage that the specified mech does with a punch.
     */
    public static int getPunchDamageFor(Entity entity, int arm) {
        final int armLoc = (arm == PunchAttackAction.RIGHT)
                           ? Mech.LOC_RARM : Mech.LOC_LARM;
        int damage = (int)Math.floor(entity.getWeight() / 10.0);
        float multiplier = 1.0f;
        
        if (entity.getGoodCriticals(CriticalSlot.TYPE_SYSTEM, 
                                    Mech.ACTUATOR_UPPER_ARM, armLoc) == 0) {
            multiplier /= 2.0f;
        }
        if (entity.getGoodCriticals(CriticalSlot.TYPE_SYSTEM, 
                                    Mech.ACTUATOR_LOWER_ARM, armLoc) == 0) {
            multiplier /= 2.0f;
        }
        if (entity.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                    Mech.ACTUATOR_SHOULDER, armLoc) == 0) {
            damage = 0;
        }
        return (int)Math.floor(damage * multiplier);
    }
    
    public static ToHitData toHitKick(Game game, KickAttackAction kaa) {
        return toHitKick(game, kaa.getEntityId(), kaa.getTargetId(), 
                         kaa.getLeg());
    }
    
    /**
     * To-hit number for the specified leg to kick
     */
    public static ToHitData toHitKick(Game game, int attackerId, 
                                       int targetId, int leg) {
        final Entity ae = game.getEntity(attackerId);
        final Entity te = game.getEntity(targetId);
        final int attackerElevation = game.board.getHex(ae.getPosition()).getElevation();
        final int targetElevation = game.board.getHex(te.getPosition()).getElevation();
        final int legLoc = (leg == KickAttackAction.RIGHT)
                           ? Mech.LOC_RLEG : Mech.LOC_LLEG;
        ToHitData toHit;

        // arguments legal?
        if (leg != KickAttackAction.RIGHT && leg != KickAttackAction.LEFT) {
            throw new IllegalArgumentException("Leg must be LEFT or RIGHT");
        }
        if (ae == null || te == null) {
            throw new IllegalArgumentException("Attacker or target id not valid");
        }
        
        // check if both legs are present
        if (ae.isLocationDestroyed(Mech.LOC_RLEG)
            || ae.isLocationDestroyed(Mech.LOC_LLEG)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Leg missing");
        }
        
        // check if both hips are operational
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_HIP, Mech.LOC_RLEG) == 0
            || ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                   Mech.ACTUATOR_HIP, Mech.LOC_LLEG) == 0) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Hip destroyed");
        }  
        
        // check if attacker has fired leg-mounted weapons
        for (Enumeration i = ae.weapons.elements(); i.hasMoreElements();) {
            MountedWeapon weapon = (MountedWeapon)i.nextElement();
            if (weapon.isFiredThisRound()) {
                if (weapon.getLocation() == legLoc) {
                    return new ToHitData(ToHitData.IMPOSSIBLE, 
                                         "Weapons fired from leg this turn");
                }
            }
        }
        
        // check range
        if (ae.getPosition().distance(te.getPosition()) > 1 ) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not in range");
        }
        
        // check elevation (target must be same or one level lower only)
        if (attackerElevation != targetElevation 
            && attackerElevation != (targetElevation + 1)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target elevation not in range");
        }
        
        // check facing
        if (!isInArc(ae.getPosition(), ae.getFacing(), 
                     te.getPosition(), Compute.ARC_FORWARD)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not in arc");
        }
        
        // can't kick while prone
        if (ae.isProne()) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Attacker is prone");
        }
        
        // can't kick a prone mech one level lower
        if (te.isProne() && attackerElevation == (targetElevation + 1)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target is prone and lower");
        }
        
        // okay, modifiers...
        toHit = new ToHitData(3, "3 (base)");
        
        // attacker movement
        toHit.append(getAttackerMovementModifier(game, attackerId));
        
        // target movement
        toHit.append(getTargetMovementModifier(game, targetId));
        
        // attacker terrain
        toHit.append(getAttackerTerrainModifier(game, attackerId));
        
        // target terrain
        toHit.append(getTargetTerrainModifier(game, targetId));
        
        // damaged or missing actuators
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_UPPER_LEG, legLoc) == 0) {
            toHit.addModifier(2, "Upper leg actuator destroyed");
        }
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_LOWER_LEG, legLoc) == 0) {
            toHit.addModifier(2, "Lower leg actuator destroyed");
        }
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_FOOT, legLoc) == 0) {
            toHit.addModifier(1, "Foot actuator destroyed");
        }

        // target immobile
        if (te.isImmobile()) {
            toHit.addModifier(-4, "target immobile");
        }
        
        // elevation
        if (attackerElevation == (targetElevation + 1)) {
                toHit.setHitTable(ToHitData.HIT_PUNCH);
        } else {
            if (te.isProne()) {
                toHit.setHitTable(ToHitData.HIT_NORMAL);
            } else {
                toHit.setHitTable(ToHitData.HIT_KICK);
            }
        }
        
        // factor in target side
        toHit.setSideTable(targetSideTable(ae.getPosition(), te.getPosition(),
                                            te.getFacing()));

        // done!
        return toHit;
    }
    
    public static ToHitData toHitPush(Game game, PushAttackAction paa) {
        return toHitPush(game, paa.getEntityId(), paa.getTargetId());
    }
    
    /**
     * To-hit number for the specified arm to punch
     */
    public static ToHitData toHitPush(Game game, int attackerId, int targetId) {
        final Entity ae = game.getEntity(attackerId);
        final Entity te = game.getEntity(targetId);
        final int attackerElevation = game.board.getHex(ae.getPosition()).getElevation();
        final int targetElevation = game.board.getHex(te.getPosition()).getElevation();
        ToHitData toHit = null;

        // arguments legal?
        if (ae == null || te == null || ae == te) {
            throw new IllegalArgumentException("Attacker or target id not valid");
        }
        
        // check if both arms are present
        if (ae.isLocationDestroyed(Mech.LOC_RARM)
            || ae.isLocationDestroyed(Mech.LOC_LARM)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Arm missing");
        }
        
        // check if attacker has fired arm-mounted weapons
        for (Enumeration i = ae.weapons.elements(); i.hasMoreElements();) {
            MountedWeapon weapon = (MountedWeapon)i.nextElement();
            if (weapon.isFiredThisRound()) {
                if (weapon.getLocation() == Mech.LOC_RARM
                    || weapon.getLocation() == Mech.LOC_LARM) {
                    return new ToHitData(ToHitData.IMPOSSIBLE, 
                                         "Weapons fired from arm this turn");
                }
            }
        }
        
        // check range
        if (ae.getPosition().distance(te.getPosition()) > 1 ) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not in range");
        }
        
        // target must be at same elevation
        if (attackerElevation != targetElevation) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not at same elevation");
        }
        
        // check facing
        if (!te.getPosition().equals(ae.getPosition().translated(ae.getFacing()))) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not directly ahead of feet");
        }
        
        // can't push while prone
        if (ae.isProne()) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Attacker is prone");
        }
        
        // can't push prone mechs
        if (te.isProne()) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target is prone");
        }
        
        // okay, modifiers...
        toHit = new ToHitData(4, "4 (base)");
        
        // attacker movement
        toHit.append(getAttackerMovementModifier(game, attackerId));
        
        // target movement
        toHit.append(getTargetMovementModifier(game, targetId));
        
        // attacker terrain
        toHit.append(getAttackerTerrainModifier(game, attackerId));
        
        // target terrain
        toHit.append(getTargetTerrainModifier(game, targetId));
        
        // damaged or missing actuators
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_SHOULDER, Mech.LOC_RARM) == 0) {
            toHit.addModifier(2, "Right Shoulder destroyed");
        }
        if (ae.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                Mech.ACTUATOR_SHOULDER, Mech.LOC_LARM) == 0) {
            toHit.addModifier(2, "Left Shoulder destroyed");
        }

        // target immobile
        if (te.isImmobile()) {
            toHit.addModifier(-4, "target immobile");
        }
        
        // side and elevation shouldn't matter

        // done!
        return toHit;
    }
    
    /**
     * Checks if a charge can hit the target, including movement
     */
    public static ToHitData toHitCharge(Game game, int attackerId, int targetId, MovementData md) {
        final Entity ae = game.getEntity(attackerId);
        final Entity te = game.getEntity(targetId);
        Coords chargeSrc = ae.getPosition();
        MovementData.Step chargeStep = null;
        
        // let's just check this
        if (!md.contains(MovementData.STEP_CHARGE)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Charge action not found");
        }
        
        // no jumping
        if (md.contains(MovementData.STEP_START_JUMP)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "No jumping allowed while charging");
        }
        
        // no backwards
        if (md.contains(MovementData.STEP_BACKWARDS)) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "No backwards movement allowed while charging");
        }
        
        // determine last valid step
        compile(game, attackerId, md);
        for (final Enumeration i = md.getSteps(); i.hasMoreElements();) {
            final MovementData.Step step = (MovementData.Step)i.nextElement();
            if (step.getMovementType() == Entity.MOVE_ILLEGAL) {
                break;
            } else {
                if (step.getType() == MovementData.STEP_CHARGE) {
                    chargeStep = step;
                } else {
                    chargeSrc = step.getPosition();
                }
            }
        }
        
        if (chargeStep == null) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Could not determine last valid step");
        }
        
        // need to reach target
        if (!te.getPosition().equals(chargeStep.getPosition())) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Could not reach target with movement");
        }
        
        // target must have moved already
        if (te.ready) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target must be done with movement");
        }
        
        return toHitCharge(game, attackerId, targetId, chargeSrc);
    }
    
    public static ToHitData toHitCharge(Game game, ChargeAttackAction caa) {
        final Entity entity = game.getEntity(caa.getEntityId());
        return toHitCharge(game, caa.getEntityId(), caa.getTargetId(), entity.getPosition());
    }
    
    /**
     * To-hit number for a charge
     */
    public static ToHitData toHitCharge(Game game, int attackerId, int targetId, Coords src) {
        final Entity ae = game.getEntity(attackerId);
        final Entity te = game.getEntity(targetId);
        final int attackerElevation = game.board.getHex(src).getElevation();;
        final int targetElevation = game.board.getHex(te.getPosition()).getElevation();
        ToHitData toHit = null;
        
        // arguments legal?
        if (ae == null || te == null || ae == te) {
            throw new IllegalArgumentException("Attacker or target id not valid");
        }
        
        // check range
        if (src.distance(te.getPosition()) > 1 ) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target not in range");
        }
        
        // target must be within 1 elevation level
        if (Math.abs(attackerElevation - targetElevation) > 1) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target must be within 1 elevation level");
        }
        
        // can't charge while prone
        if (ae.isProne()) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Attacker is prone");
        }
        
        // can't charge prone mechs
        if (te.isProne()) {
            return new ToHitData(ToHitData.IMPOSSIBLE, "Target is prone");
        }
        
        // okay, modifiers...
        toHit = new ToHitData(5, "5 (base)");
        
        // attacker movement
        toHit.append(getAttackerMovementModifier(game, attackerId));
        
        // target movement
        toHit.append(getTargetMovementModifier(game, targetId));
        
        // attacker terrain
        toHit.append(getAttackerTerrainModifier(game, attackerId));
        
        // target terrain
        toHit.append(getTargetTerrainModifier(game, targetId));
        
        // target immobile
        if (te.isImmobile()) {
            toHit.addModifier(-4, "target immobile");
        }
        
        // side and elevation shouldn't matter

        // done!
        return toHit;
    }
        
    
    /**
     * Damage that the specified mech does with a kick
     */
    public static int getKickDamageFor(Entity entity, int leg) {
        final int legLoc = (leg == KickAttackAction.RIGHT)
                           ? Mech.LOC_RLEG : Mech.LOC_LLEG;
        int damage = (int)Math.floor(entity.getWeight() / 5.0);
        float multiplier = 1.0f;
        
        if (entity.getGoodCriticals(CriticalSlot.TYPE_SYSTEM, 
                                    Mech.ACTUATOR_UPPER_LEG, legLoc) == 0) {
            multiplier /= 2.0f;
        }
        if (entity.getGoodCriticals(CriticalSlot.TYPE_SYSTEM, 
                                    Mech.ACTUATOR_LOWER_LEG, legLoc) == 0) {
            multiplier /= 2.0f;
        }
        if (entity.getGoodCriticals(CriticalSlot.TYPE_SYSTEM,
                                    Mech.ACTUATOR_HIP, legLoc) == 0) {
            damage = 0;
        }
        return (int)Math.floor(damage * multiplier);
    }
    
    /**
     * Modifier to attacks due to attacker movement
     */
    public static ToHitData getAttackerMovementModifier(Game game, int entityId) {
        final Entity entity = game.getEntity(entityId);
        ToHitData toHit = new ToHitData(0, "");
        
        if (entity.moved == Entity.MOVE_WALK) {
            toHit.addModifier(1, "attacker walked");
        } else if (entity.moved == Entity.MOVE_RUN) {
            toHit.addModifier(2, "attacker ran");
        } else if (entity.moved == Entity.MOVE_JUMP) {
            toHit.addModifier(3, "attacker jumped");
        }
        
        return toHit;
    }
    
    /**
     * Modifier to attacks due to target movement
     */
    public static ToHitData getTargetMovementModifier(Game game, int entityId) {
        Entity entity = game.getEntity(entityId);
        return getTargetMovementModifier(entity.delta_distance, 
                                         entity.moved == Entity.MOVE_JUMP);
    }
  
    /**
     * Target movement modifer for the specified delta_distance
     */
    public static ToHitData getTargetMovementModifier(int distance, boolean jumped) {
        ToHitData toHit = new ToHitData(0, "");
      
        if (distance >= 3 && distance <= 4) {
            toHit.addModifier(1, "target moved 3-4 hexes");
        } else if (distance >= 5 && distance <= 6) {
            toHit.addModifier(2, "target moved 5-6 hexes");
        } else if (distance >= 7 && distance <= 9) {
            toHit.addModifier(3, "target moved 7-9 hexes");
        } else if (distance >= 10) {
            toHit.addModifier(4, "target moved 10+ hexes");
        }
        if (jumped) {
            toHit.addModifier(1, "target jumped");
        }
      
        return toHit;
    }
    
    /**
     * Modifier to attacks due to attacker terrain
     */
    public static ToHitData getAttackerTerrainModifier(Game game, int entityId) {
        final Hex hex = game.board.getHex(game.getEntity(entityId).getPosition());
        ToHitData toHit = new ToHitData(0, "");

        if (hex.getTerrainType() == Terrain.WATER) {
            toHit.addModifier(1, "attacker in water");
        }
        
        return toHit;
    }
    
    /**
     * Modifier to attacks due to target terrain
     */
    public static ToHitData getTargetTerrainModifier(Game game, int entityId) {
        final Hex hex = game.board.getHex(game.getEntity(entityId).getPosition());
        ToHitData toHit = new ToHitData(0, "");

        switch (hex.getTerrainType()) {
        case Terrain.WATER :
            toHit.addModifier(-1, "target in water");
            break;
        case Terrain.FOREST_LITE :
            toHit.addModifier(1, "target in light woods");
            break;
        case Terrain.FOREST_HVY :
            toHit.addModifier(2, "target in heavy woods");
            break;
        default:
            // no modifier
            break;
        }
        
        return toHit;
    }
    
    /**
     * Returns true if the target is in the specified arc.
     * @param ac the attacker coordinate
     * @param af the attacker facing
     * @param tc the target coordinate
     * @param arc the arc
     */
    public static boolean isInArc(Coords ac, int af, Coords tc, int arc) {
        // calculate firing angle
        int fa = ac.degree(tc) - af * 60;
        if (fa < 0) {
            fa += 360;
        }
        // is it in the specifed arc?
        switch(arc) {
        case ARC_FORWARD :
            return fa >= 300 || fa <= 60;
        case Compute.ARC_RIGHTARM :
            return fa >= 300 || fa <= 120;
        case Compute.ARC_LEFTARM :
            return fa >= 240 || fa <= 60;
        case ARC_REAR :
            return fa > 120 && fa < 240;
        case ARC_360 :
      return true;
        default:
            return false;
        }
    }
    
    /**
     * Returns the next coords or two after cur along the line
     * 
     * @return an array of either 1 or 2 coords
     */
    private static Coords[] getNextCoords(Coords cur, Coords cur1,
                                          Coords last0, Coords last1,
                                          int x0, int y0, int x1, int y1) {
        Coords next0 = null;
        Coords next1 = null;
        
        
        
        if (next1 == null) {
            Coords[] next = new Coords[1];
            next[0] = next0;
            return next;
        } else {
            Coords[] next = new Coords[2];
            next[0] = next0;
            next[1] = next1;
            return next;
        }
    }
    
    /**
     * This returns the Coords of hexes that are crossed by a straight line 
     * from the middle of the hex at Coords a to the middle of the hex at 
     * Coords b.
     * 
     * This is the brute force, integer version based off of some of the 
     * formulas at Amit's game programming site 
     * (http://www-cs-students.stanford.edu/~amitp/gameprog.html)
     */
    public static Coords[] intervening(Coords a, Coords b) {
        IdealHex aHex = new IdealHex(a);
        IdealHex bHex = new IdealHex(b);
        
        // test any hexes that we think might be in the way
        int rangeWidth = Math.abs(a.x - b.x) + 1;
        int rangeHeight = Math.abs(a.y - b.y) + 1;
        int rangeArea = rangeWidth * rangeHeight; // hexes to test
        Vector trueCoords = new Vector();
        
        for (int i = 0; i < rangeArea; i++) {
            Coords c = new Coords(i % rangeWidth + Math.min(a.x, b.x), i / rangeWidth + Math.min(a.y, b.y));
            IdealHex cHex = new IdealHex(c);
            // test the polygon
            if (cHex.isIntersectedBy(aHex.cx, aHex.cy, bHex.cx, bHex.cy)) {
                trueCoords.addElement(c);
            }
        }
        
        // make a nice array to return
        Coords[] trueArray = new Coords[trueCoords.size()];
        trueCoords.copyInto(trueArray);
        
        System.out.print("compute: intervening from " + a.getBoardNum() + " to " + b.getBoardNum() + " [ ");
        for (Enumeration i = trueCoords.elements(); i.hasMoreElements();) {
            final Coords coords = (Coords)i.nextElement();
            System.out.print(coords.getBoardNum() + " ");
        }
        System.out.print("]\n");
        
        return trueArray;
    }

    /**
     * This returns the Coords that are crossed by a straight
     * line from Coords a to Coords b.
     * 
     * Old version.  Brute force and tests every point on the line.  Ick, ick.
     */
    public static Coords[] intervening1(Coords a, Coords b) {
        //System.err.print("r: intervening from " + a.getBoardNum() + " to " + b.getBoardNum() + " [ ");
        
        // set up hexagon poly
        Polygon p = new Polygon();
        p.addPoint(21, 0);
        p.addPoint(62, 0);
        p.addPoint(83, 35);
        p.addPoint(83, 36);
        p.addPoint(62, 71);
        p.addPoint(21, 71);
        p.addPoint(0, 36);
        p.addPoint(0, 35);
        
        // set up line from one to the next
        int lx = Math.abs(a.x - b.x) * 63 + 1;  // line width
        int ly = Math.abs((a.y * 72 + (a.isXOdd() ? 36 : 0)) - (b.y * 72 + ((b.x & 1) == 1 ? 36 : 0))) + 1; // line height
        boolean lxl = lx > ly; // line width longer?
        int llong = lxl ? lx : ly;  // line longer dimension
        int lshort = lxl ? ly : lx;  // line shorter dimension
        
        // we will always want to increase the longer dimension
        int lox, loy;
        boolean ld;
        if ((lxl && a.x < b.x) || (!lxl && a.y < b.y)) {
            lox = a.x * 63 + 42;
            loy = a.y * 72 + (a.isXOdd() ? 72 : 36);
            ld = (lxl && a.y < b.y) || (!lxl && a.x < b.x);
        } else {
            lox = b.x * 63 + 42;
            loy = b.y * 72 + (b.isXOdd() ? 72 : 36);
            ld = (lxl && b.y < a.y) || (!lxl && b.x < a.x);
        }
        
        // make an array of the shorter point dimensions for each of the longer ones
        int lsa[] = new int[llong];  // line shorter array
        for (int i = 0; i < llong; i++) {
            lsa[i] = (int)Math.round(((float)i / (float)llong) * (float)lshort);
            if (!ld) {
                lsa[i] = 0 - lsa[i];
            }
        }
        
        // test any hexes that we think might be in the way
        int hrw = Math.abs(a.x - b.x) + 1;
        int hrh = Math.abs(a.y - b.y) + 1;
        int htt = hrw * hrh; // hexes to test
        Coords[] pc = new Coords[htt]; // possible coordinates
        boolean[] in = new boolean[htt]; // intervening flag
        int not = 0;  // number of trues
        
        for (int i = 0; i < htt; i++) {
            Coords c = new Coords(i % hrw + Math.min(a.x, b.x), i / hrw + Math.min(a.y, b.y));
            pc[i] = c;
            // set up a polygon for this coordinate
            Polygon hp = new Polygon(p.xpoints, p.ypoints, p.npoints);
            hp.translate(c.x * 63, c.y * 72 + (c.isXOdd() ? 36 : 0));
            // test the points of the line possibly going thru that hex
            if (lxl) {
                for (int j = 0; j < 84; j++) {
                    int tx = c.x * 63 + j;
                    if (tx >= lox && tx < lox + llong && !c.equals(a) && !c.equals(b)) {
                        if (hp.contains(tx, lsa[tx - lox] + loy)) {
                            in[i] = true;
                            not++;
//System.err.println("r: testing #" + i + ", " + c + " : " + in[i]);
                            break;
                        }
                    }
                    in[i] = false;
                }
            } else {
                for (int j = 0; j < 72; j++) {
                    int ty = c.y * 72 + (c.isXOdd() ? 36 : 0) + j;
                    if (ty >= loy && ty < loy + llong && !c.equals(a) && !c.equals(b)) {
                        if (hp.contains(lsa[ty - loy] + lox, ty)) {
                            in[i] = true;
                            not++;
//System.err.println("r: testing #" + i + ", " + c + " : " + in[i]);
                            break;
                        }
                    }
                    in[i] = false;
                }
            }
        }
        
        // create array of "true" coordinates.
        Coords[] ih = new Coords[not];
        int ihi = 0;
        for (int i = 0; i < htt; i++) {
            if (in[i]) {
//System.err.print(pc[i].getBoardNum() + " ");
                ih[ihi++] = pc[i];
            }
        }
//System.err.print("]\n");
        
        return ih;
    }
    
    /**
     * Returns the side location table that you should be using
     */
    public static int targetSideTable(Coords ac, Coords tc, int tf) {
        // calculate firing angle
        int fa = tc.degree(ac) - tf * 60;
        if (fa < 0) {
            fa += 360;
        }
        if (fa > 90 && fa <= 150) {
            return ToHitData.SIDE_RIGHT;
        }
        if (fa > 150 && fa < 210) {
            return ToHitData.SIDE_REAR;
        }
        if (fa >= 210 && fa < 270) {
            return ToHitData.SIDE_LEFT;
        }
        return ToHitData.SIDE_FRONT;
    }
    
    
    /**
     * Roll the number of missiles (or whatever) on the missile
     * hit table.
     */
    public static int missilesHit(int missiles) {
        if (missiles == 2) {
            switch(d6(2)) {
            case 2 :
            case 3 :
            case 4 :
            case 5 :
            case 6 :
            case 7 :
                return 1;
            case 8 :
            case 9 :
            case 10 :
            case 11 :
            case 12 :
                return 2;
            }
        }
        
        if (missiles == 4) {
            switch(d6(2)) {
            case 2 :
                return 1;
            case 3 :
            case 4 :
            case 5 :
            case 6 :
                return 2;
            case 7 :
            case 8 :
            case 9 :
            case 10 :
                return 3;
            case 11 :
            case 12 :
                return 4;
            }
        }
        
        if (missiles == 5) {
            switch(d6(2)) {
            case 2 :
                return 1;
            case 3 :
            case 4 :
                return 2;
            case 5 :
            case 6 :
            case 7 :
            case 8 :
                return 3;
            case 9 :
            case 10 :
                return 4;
            case 11 :
            case 12 :
                return 5;
            }
        }
        
        if (missiles == 6) {
            switch(d6(2)) {
            case 2 :
            case 3 :
                return 2;
            case 4 :
            case 5 :
                return 3;
            case 6 :
            case 7 :
            case 8 :
                return 4;
            case 9 :
            case 10 :
                return 5;
            case 11 :
            case 12 :
                return 6;
            }
        }
        
        if (missiles == 10) {
            switch(d6(2)) {
            case 2 :
            case 3 :
                return 3;
            case 4 :
                return 4;
            case 5 :
            case 6 :
            case 7 :
            case 8 :
                return 6;
            case 9 :
            case 10 :
                return 8;
            case 11 :
            case 12 :
                return 10;
            }
        }
        
        if (missiles == 10) {
            switch(d6(2)) {
            case 2 :
            case 3 :
                return 3;
            case 4 :
                return 4;
            case 5 :
            case 6 :
            case 7 :
            case 8 :
                return 6;
            case 9 :
            case 10 :
                return 8;
            case 11 :
            case 12 :
                return 10;
            }
        }
        
        if (missiles == 15) {
            switch(d6(2)) {
            case 2 :
            case 3 :
                return 5;
            case 4 :
                return 6;
            case 5 :
            case 6 :
            case 7 :
            case 8 :
                return 9;
            case 9 :
            case 10 :
                return 12;
            case 11 :
            case 12 :
                return 15;
            }
        }
        
        if (missiles == 20) {
            switch(d6(2)) {
            case 2 :
            case 3 :
                return 6;
            case 4 :
                return 9;
            case 5 :
            case 6 :
            case 7 :
            case 8 :
                return 12;
            case 9 :
            case 10 :
                return 16;
            case 11 :
            case 12 :
                return 20;
            }
        }
        
        return 0;
    }
    
}
