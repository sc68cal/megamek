/**
 * MegaMek - Copyright (C) 2000,2001,2002,2003,2004 Ben Mazur (bmazur@sev.org)
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

import com.sun.java.util.collections.Comparator;
import com.sun.java.util.collections.ArrayList;
import com.sun.java.util.collections.Collections;
import com.sun.java.util.collections.HashMap;
import com.sun.java.util.collections.Iterator;
import com.sun.java.util.collections.List;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Holds movement path for an entity.
 */
public class MovePath implements Cloneable, Serializable {
    public static final int STEP_FORWARDS = 1;
    public static final int STEP_BACKWARDS = 2;
    public static final int STEP_TURN_LEFT = 3;
    public static final int STEP_TURN_RIGHT = 4;
    public static final int STEP_GET_UP = 5;
    public static final int STEP_GO_PRONE = 6;
    public static final int STEP_START_JUMP = 7;
    public static final int STEP_CHARGE = 8;
    public static final int STEP_DFA = 9;
    public static final int STEP_FLEE = 10;
    public static final int STEP_LATERAL_LEFT = 11;
    public static final int STEP_LATERAL_RIGHT = 12;
    public static final int STEP_LATERAL_LEFT_BACKWARDS = 13;
    public static final int STEP_LATERAL_RIGHT_BACKWARDS = 14;
    public static final int STEP_UNJAM_RAC = 15;
    public static final int STEP_LOAD = 16;
    public static final int STEP_UNLOAD = 17;
    public static final int STEP_EJECT = 18;
    public static final int STEP_CLEAR_MINEFIELD = 19;

    public static class Key {
        private Coords coords;
        private int facing;
        private boolean isJump;

        public Key(Coords coords, int facing, boolean isJump) {
            this.coords = coords;
            this.facing = facing;
            this.isJump = isJump;
        }

        public boolean equals(Object obj) {
            Key s1 = (Key) obj;
            if (s1 != null) {
                return isJump == s1.isJump && facing == s1.facing && coords.equals(s1.coords);
            }
            return false;
        }

        public int hashCode() {
            return isJump ? 1 : 0 + 7 * (facing + 31 * coords.hashCode());
        }
    }

    protected Vector steps = new Vector();

    protected transient Game game;
    protected transient Entity entity;

    public static final int DEFAULT_PATHFINDER_TIME_LIMIT = 2000;

    /**
     * Generates a new, empty, movement path object.
     */
    public MovePath(Game game, Entity entity) {
        this.entity = entity;
        this.game = game;
    }

    public Entity getEntity() {
        return entity;
    }

    public boolean isInfantry() {
        return entity instanceof Infantry;
    }

    public Key getKey() {
        return new Key(getFinalCoords(), getFinalFacing(), isJumping());
    }

    /**
     * TODO: should be a method of entity.
     */
    boolean isUsingManAce() {
        return entity.getCrew().getOptions().booleanOption("maneuvering_ace");
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (Enumeration i = steps.elements(); i.hasMoreElements();) {
            sb.append(i.nextElement().toString());
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Returns the number of steps in this movement
     */
    public int length() {
        return steps.size();
    }

    /**
     * Add a new step to the movement path.
     *
     * @param type the type of movement.
     */
    public MovePath addStep(int type) {
        return addStep(new MoveStep(this, type));
    }

    /**
     * Add a new step to the movement path with the given target.
     *
     * @param type   the type of movement.
     * @param target the <code>Targetable</code> object that is the target of
     *               this step. For example, the enemy being charged.
     */
    public MovePath addStep(int type, Targetable target) {
        return addStep(new MoveStep(this, type, target));
    }

    public boolean canShift() {
        return ((entity instanceof QuadMech) || isUsingManAce()) && !isJumping();
    }

    /**
     * Initializes a step as part of this movement path. Then adds it to the
     * list.
     *
     * @param step
     */
    protected MovePath addStep(MoveStep step) {
        steps.addElement(step);

        // transform lateral shifts for quads or maneuverability aces
        if (canShift()) {
            transformLateralShift();
        }
        MoveStep prev = getStep(steps.size() - 2);

        // TODO: more elegant method possible here?
        if (prev != null && prev.isStackingViolation()) {
            // if previous step is stacking violation, fully recompile
            compile(game, entity);
            return this;
        }

        try {
            step.compile(game, entity, prev);
        } catch (RuntimeException re) {
            step.setMovementType(Entity.MOVE_ILLEGAL);
        }

        if (!step.isLegal()) {
            return this;
        }

        // set moveType, illegal, trouble flags
        step.compileIllegal(game, entity, prev);

        // check for illegal jumps
        if (isJumping()) {
            Coords start = entity.getPosition();
            Coords land = step.getPosition();
            int distance = start.distance(land);

            if (step.getMpUsed() > distance) {
                step.setMovementType(Entity.MOVE_ILLEGAL);
            }
        }
        return this;
    }

    public void compile(Game g, Entity en) {
        this.game = g;
        this.entity = en;
        Vector temp = (Vector) steps.clone();
        steps.removeAllElements();
        for (int i = 0; i < temp.size(); i++) {
            MoveStep step = (MoveStep) temp.elementAt(i);
            if (step.getTarget(game) != null) {
                step = new MoveStep(this, step.getType(), step.getTarget(game));
            } else {
                step = new MoveStep(this, step.getType());
            }
            this.addStep(step);
        }
        compileLastStep();
    }

    public void removeLastStep() {
        if (steps.size() > 0) {
            steps.removeElementAt(steps.size() - 1);
        }
    }

    public void clear() {
        steps.removeAllElements();
    }

    public Enumeration getSteps() {
        return steps.elements();
    }

    public MoveStep getStep(int index) {
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        return (MoveStep) steps.elementAt(index);
    }

    /**
     * Check for any of the specified type of step in the path
     */
    public boolean contains(int type) {
        for (final Enumeration i = getSteps(); i.hasMoreElements();) {
            MoveStep step = (MoveStep) i.nextElement();
            if (step.getType() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check for MASC use
     */
    public boolean hasActiveMASC() {
        for (final Enumeration i = getSteps(); i.hasMoreElements();) {
            MoveStep step = (MoveStep) i.nextElement();
            if (step.isUsingMASC()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the final coordinates if a mech were to perform all the steps in
     * this path.
     */
    public Coords getFinalCoords() {
        if (getLastStep() != null) {
            return getLastStep().getPosition();
        }
        return entity.getPosition();
    }

    /**
     * Returns the final facing if a mech were to perform all the steps in this
     * path.
     */
    public int getFinalFacing() {
        if (getLastStep() != null) {
            return getLastStep().getFacing();
        }
        return entity.getFacing();
    }

    /**
     * Returns whether or not a unit would end up prone after all of the steps
     */
    public boolean getFinalProne() {
        if (getLastStep() != null) {
            return getLastStep().isProne();
        }
        if (entity == null)
            return false;
        return entity.isProne();
    }

    public int getLastStepMovementType() {
        if (getLastStep() == null) {
            return Entity.MOVE_NONE;
        }
        return getLastStep().getMovementType();
    }

    public MoveStep getLastStep() {
        return getStep(steps.size() - 1);
    }

    /* Debug method */
    public void printAllSteps() {
        System.out.println("*Steps*");
        for (int i = 0; i < steps.size(); i++) {
            System.out.println("  " + i + ": " + getStep(i) + ", " + getStep(i).getMovementType());
        }
    }

    /**
     * Removes impossible steps.
     */
    public void clipToPossible() {
        // hopefully there's no impossible steps in the middle of possible ones
        Vector goodSteps = new Vector();
        for (final Enumeration i = steps.elements(); i.hasMoreElements();) {
            final MoveStep step = (MoveStep) i.nextElement();
            if (step.getMovementType() != Entity.MOVE_ILLEGAL) {
                goodSteps.addElement(step);
            }
        }
        steps = goodSteps;
    }

    /**
     * Changes turn-forwards-opposite-turn sequences into quad lateral shifts.
     * <p/>
     * Finds the sequence of three steps that can be transformed, then removes
     * all three and replaces them with the lateral shift step.
     */
    private void transformLateralShift() {
        if (steps.size() < 3) {
            return;
        }
        int index = steps.size() - 3;
        MoveStep step1 = getStep(index);
        MoveStep step2 = getStep(index + 1);
        MoveStep step3 = getStep(index + 2);

        if (step1.oppositeTurn(step3)
            && (step2.getType() == MovePath.STEP_BACKWARDS || step2.getType() == MovePath.STEP_FORWARDS)) {
            int stepType = step1.getType();
            int direction = step2.getType();
            // remove all old steps
            steps.removeElementAt(index);
            steps.removeElementAt(index);
            steps.removeElementAt(index);
            // add new step
            MoveStep shift = new MoveStep(this, lateralShiftForTurn(stepType, direction));
            addStep(shift);
        }
    }

    /**
     * Returns the lateral shift that corresponds to the turn direction
     */
    public static int lateralShiftForTurn(int turn, int direction) {
        if (direction == MovePath.STEP_FORWARDS) {
            switch (turn) {
                case MovePath.STEP_TURN_LEFT :
                    return MovePath.STEP_LATERAL_LEFT;
                case MovePath.STEP_TURN_RIGHT :
                    return MovePath.STEP_LATERAL_RIGHT;
                default :
                    return turn;
            }
        } else {
            switch (turn) {
                case MovePath.STEP_TURN_LEFT :
                    return MovePath.STEP_LATERAL_LEFT_BACKWARDS;
                case MovePath.STEP_TURN_RIGHT :
                    return MovePath.STEP_LATERAL_RIGHT_BACKWARDS;
                default :
                    return turn;
            }
        }
    }

    /**
     * Returns the turn direction that corresponds to the lateral shift
     */
    static int turnForLateralShift(int shift) {
        switch (shift) {
            case MovePath.STEP_LATERAL_LEFT :
                return MovePath.STEP_TURN_LEFT;
            case MovePath.STEP_LATERAL_RIGHT :
                return MovePath.STEP_TURN_RIGHT;
            case MovePath.STEP_LATERAL_LEFT_BACKWARDS :
                return MovePath.STEP_TURN_LEFT;
            case MovePath.STEP_LATERAL_RIGHT_BACKWARDS :
                return MovePath.STEP_TURN_RIGHT;
            default :
                return shift;
        }
    }

    /**
     * Returns the direction (either MovePath.STEP_TURN_LEFT or
     * STEP_TURN_RIGHT) that the destination facing lies in.
     */
    public static int getDirection(int facing, int destFacing) {
        final int rotate = (destFacing + (6 - facing)) % 6;
        return rotate >= 3 ? STEP_TURN_LEFT : STEP_TURN_RIGHT;
    }

    /**
     * Returns the adjusted facing, given the start facing.
     */
    public static int getAdjustedFacing(int facing, int movement) {
        if (movement == STEP_TURN_RIGHT) {
            return (facing + 1) % 6;
        } else if (movement == STEP_TURN_LEFT) {
            return (facing + 5) % 6;
        }
        return facing;
    }

    /**
     * Returns the number of MPs used in the path
     */
    public int getMpUsed() {
        if (getLastStep() != null) {
            return getLastStep().getMpUsed();
        }
        return 0;
    }

    /**
     * Returns the logical number of hexes moved
     * the path (does not count turns, etc).
     */
    public int getHexesMoved() {
        if (getLastStep() == null) {
            return 0;
        }
        return getLastStep().getDistance();
    }

    public boolean isJumping() {
        if (steps.size() > 0) {
            return getStep(0).getType() == MovePath.STEP_START_JUMP;
        }
        return false;
    }

    public void compileLastStep() {
        compileLastStep(true);
    }

    public void compileLastStep(boolean clip) {
        if (clip)
            clipToPossible(); //get rid of "trailing garbage"

        for (int i = length() - 1; i >= 0; i--) {
            final MoveStep step = getStep(i);
            if (step.checkAndSetIllegal(game, entity)) {
                continue;
            }
            break;
        }
    }

    /**
     * Extend the current path to the destination <code>Coords</code>.
     *
     * @param   dest the destination <code>Coords</code> of the move.
     * @param   type the type of movment step required.
     */
    public void findPathTo(Coords dest, int type) {
        int timeLimit = Settings.maxPathfinderTime;

        if (10000 <= timeLimit) {
            System.out.print("WARNING!!!  Settings allow up to ");
            System.out.print(timeLimit);
            System.out.println(" milliseconds to find the optimum path!");
        }
        this.notSoLazyPathfinder(dest, type, timeLimit);
    }

    public boolean isMoveLegal() {
        // Moves which end up off of the board are not legal.
        if (!game.getBoard().contains(getFinalCoords())) {
            return false;
        }

        if (getLastStep() == null) {
            return true;
        }

        return getLastStep().isLegal();
    }

    /**
     * An A* pathfinder to get from the end of the current path
     * (or entity's position if empty) to the destination.
     *
     * @param dest The goal hex
     * @param type The type of move we want to do
     * @param timeLimit the maximum <code>int</code> number of
     *          milliseconds to take hunting for an ideal path.
     */
    private void notSoLazyPathfinder(final Coords dest, final int type, final int timeLimit) {
        long endTime = System.currentTimeMillis() + timeLimit;

        int step = type;
        if (step != MovePath.STEP_BACKWARDS) {
            step = MovePath.STEP_FORWARDS;
        }

        if (this.getFinalCoords().equals(dest)) {
            return;
        }

        //should rules like this be in here?
        if (step == MovePath.STEP_BACKWARDS
            && game.board.getHex(dest).getElevation() > game.board.getHex(getFinalCoords()).getElevation()) {
            return;
        }

        MovePathComparator mpc = new MovePathComparator(dest);

        MovePath bestPath = (MovePath) this.clone();

        HashMap discovered = new HashMap();
        discovered.put(bestPath.getKey(), bestPath);

        ArrayList candidates = new ArrayList();
        candidates.add(bestPath);

        boolean keepLooping = true;
        int loopcount = 0;

        while (candidates.size() > 0 && keepLooping) {
            MovePath candidatePath = (MovePath) candidates.remove(0);
            Coords startingPos = candidatePath.getFinalCoords();

            Iterator adjacent = candidatePath.getNextMoves(step == STEP_BACKWARDS, step == STEP_FORWARDS).iterator();
            while (adjacent.hasNext()) {
                MovePath expandedPath = (MovePath) adjacent.next();

                if (expandedPath.getLastStep().isMovementPossible(this.game, startingPos)) {
                    MovePath found = (MovePath) discovered.get(expandedPath.getKey());
                    if (found != null && mpc.compare(found, expandedPath) <= 0) {
                        continue;
                    }
                    //does our moving gets us to the destination?
                    if (expandedPath.getFinalCoords().equals(dest)) {
                        if (type != MovePath.STEP_FORWARDS && type != MovePath.STEP_BACKWARDS) {
                            MovePath pathOriginalType = (MovePath) candidatePath.clone();
                            pathOriginalType.addStep(type);
                            bestPath = pathOriginalType;
                        } else {
                            bestPath = expandedPath;
                        }
                        keepLooping = false;
                        break;
                    }

                    int index = Collections.binarySearch(candidates, expandedPath, mpc);
                    if (index < 0) {
                        index = -index - 1;
                    }
                    candidates.add(index, expandedPath);
                    discovered.put(expandedPath.getKey(), expandedPath);
                    if (candidates.size() > 500) {
                        candidates.remove(candidates.size() - 1);
                    }
                }
            }
            loopcount++;
            if (loopcount % 256 == 0 && keepLooping && candidates.size() > 0) {
                if (mpc.compare(bestPath, candidates.get(0)) < 0) {
                    bestPath = (MovePath) candidates.get(0);
                    keepLooping = System.currentTimeMillis() < endTime;
                } else {
                    keepLooping = false;
                }
            }
        } //end while

        if (getFinalCoords().distance(dest) > bestPath.getFinalCoords().distance(dest)) {
            //Make the path we found, this path.
            this.steps = bestPath.steps;
        }
    }

    /**
     * Returns a list of possible moves that result in a
     * facing/position/(jumping|prone) change, special steps (mine clearing and
     * such) must be handled elsewhere.
     */
    public List getNextMoves(boolean backward, boolean forward) {
        ArrayList result = new ArrayList();
        MoveStep last = getLastStep();
        if (isJumping()) {
            MovePath next = (MovePath) this.clone();
            for (int i = 0; i < 5; i++) {
                result.add(next);
                result.add(((MovePath) next.clone()).addStep(MovePath.STEP_FORWARDS));
                next = (MovePath) next.clone();
                next.addStep(MovePath.STEP_TURN_RIGHT);
            }
            return result;
        }
        if (getFinalProne()) {
            if (last != null && last.getType() != STEP_TURN_RIGHT) {
                result.add(((MovePath) this.clone()).addStep(MovePath.STEP_TURN_LEFT));
            }
            if (last != null && last.getType() != STEP_TURN_LEFT) {
                result.add(((MovePath) this.clone()).addStep(MovePath.STEP_TURN_RIGHT));
            }
            result.add(((MovePath) this.clone()).addStep(MovePath.STEP_GET_UP));
            return result;
        }
        if (canShift()) {
            if (forward && (last == null || last.getType() != MovePath.STEP_LATERAL_LEFT)) {
                result.add(((MovePath) this.clone()).addStep(STEP_LATERAL_RIGHT));
            }
            if (forward && (last == null || last.getType() != MovePath.STEP_LATERAL_RIGHT)) {
                result.add(((MovePath) this.clone()).addStep(MovePath.STEP_LATERAL_LEFT));
            }
            if (backward && (last == null || last.getType() != MovePath.STEP_LATERAL_LEFT_BACKWARDS)) {
                result.add(((MovePath) this.clone()).addStep(MovePath.STEP_LATERAL_RIGHT_BACKWARDS));
            }
            if (backward && (last == null || last.getType() != MovePath.STEP_LATERAL_RIGHT_BACKWARDS)) {
                result.add(((MovePath) this.clone()).addStep(MovePath.STEP_LATERAL_LEFT_BACKWARDS));
            }
        }
        if (forward && (last == null || last.getType() != MovePath.STEP_BACKWARDS)) {
            result.add(((MovePath) this.clone()).addStep(MovePath.STEP_FORWARDS));
        }
        if (last == null || last.getType() != MovePath.STEP_TURN_LEFT) {
            result.add(((MovePath) this.clone()).addStep(MovePath.STEP_TURN_RIGHT));
        }
        if (last == null || last.getType() != MovePath.STEP_TURN_RIGHT) {
            result.add(((MovePath) this.clone()).addStep(MovePath.STEP_TURN_LEFT));
        }
        if (backward && (last == null || last.getType() != MovePath.STEP_FORWARDS)) {
            result.add(((MovePath) this.clone()).addStep(MovePath.STEP_BACKWARDS));
        }
        return result;
    }

    /**
     * Clones this path, will contain a new clone of the steps
     * so that the clone is independent from the original.
     *
     * @return the cloned MovePath
     */
    public Object clone() {
        MovePath copy = new MovePath(this.game, this.entity);
        copy.steps = (Vector) steps.clone();
        return copy;
    }

    /**
     * Rotate from the current facing to the destination facing.
     */
    public void rotatePathfinder(int destFacing) {
        while (getFinalFacing() != destFacing) {
            int stepType = getDirection(getFinalFacing(), destFacing);
            addStep(stepType);
        }
    }

    protected class MovePathComparator implements Comparator {
        private Coords destination;

        public MovePathComparator(Coords destination) {
            this.destination = destination;
        }

        public int compare(Object o1, Object o2) {
            MovePath first = (MovePath) o1;
            MovePath second = (MovePath) o2;

            int firstMP = 0, secondMP = 0;

            firstMP = first.getMpUsed() + first.getFinalCoords().distance(destination);
            secondMP = second.getMpUsed() + second.getFinalCoords().distance(destination);
            return firstMP - secondMP;
        }
    }
}