/*
 * MegaMek - Copyright (C) 2000-2011 Ben Mazur (bmazur@sev.org)
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
package megamek.client.bot.princess;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import megamek.client.bot.BotClient;
import megamek.client.bot.ChatProcessor;
import megamek.client.bot.PhysicalOption;
import megamek.client.bot.princess.PathRanker.RankedPath;
import megamek.common.Building;
import megamek.common.BuildingTarget;
import megamek.common.Coords;
import megamek.common.Entity;
import megamek.common.GunEmplacement;
import megamek.common.Mech;
import megamek.common.MechWarrior;
import megamek.common.Minefield;
import megamek.common.MovePath;
import megamek.common.Tank;
import megamek.common.Targetable;
import megamek.common.containers.PlayerIDandList;
import megamek.common.event.GamePlayerChatEvent;
import megamek.common.util.LogLevel;
import megamek.common.util.Logger;

public class Princess extends BotClient {

    private boolean initialized = false;
    public boolean verboseErrorLog;
    public LogLevel verbosity; // controls how many messages are sent to chat

    public String properties_file_name;

    PathSearcher path_searcher;
    BasicPathRanker path_ranker;
    FireControl fire_control;

    double move_evaluation_time_estimate;
    Precognition precognition;
    Thread precognition_thread;
    // PathEnumerator path_enumerator;

    // ----Global Parameters-----
    public double aggression;

    // ----These have to do with the goals or victiory conditions for the
    // bot----
    /*
     * A list of hexes in which the bot wants to destroy buildings
     */
    public ArrayList<Coords> strategic_targets = new ArrayList<Coords>();
    /*
     * Should the rules for forced withdrawal be implemented
     */
    public boolean forced_withdrawal = true;
    /*
     * Which direction should the bot flee. Defaults to North.
     */
    private HomeEdge homeEdge = null;
    /*
     * Should the bot be running away
     */
    public boolean should_flee = false;
    /*
     * Must the bot flee, even if not crippled?
     */
    public boolean must_flee = false;

    // ---------------------------------------------------------------------------

    protected ChatProcessor chatp = new ChatProcessor();

    public Princess(String name, String host, int port, LogLevel verbosity) {
        super(name, host, port);
        // default properties file
        properties_file_name = "mmconf/princess_bot.properties";
        verboseErrorLog = true;
        this.verbosity = verbosity;
    }

    @Override
    protected Vector<Coords> calculateArtyAutoHitHexes() {
        final String METHOD_NAME = "calculateArtyAutoHitHexes()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            // currently returns no artillery hit spots
            // make an empty list
            PlayerIDandList<Coords> artyAutoHitHexes = new PlayerIDandList<Coords>();
            // attach my player id to it
            artyAutoHitHexes.setPlayerID(getLocalPlayer().getId());
            return artyAutoHitHexes;
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected void calculateDeployment() {
        final String METHOD_NAME = "calculateDeployment()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {

            // get the first unit
            int entNum = game.getFirstDeployableEntityNum();
            if (verbosity.getLevel() > LogLevel.WARNING.getLevel()) {
                sendChat("deploying unit " + getEntity(entNum).getChassis());
            }
            // on the list to be deployed
            // get a set of all the
            Coords[] cStart = getStartingCoordsArray();
            if (cStart.length == 0) {
                Logger.log(getClass(), METHOD_NAME, LogLevel.ERROR,
                           "No valid locations to deploy "
                           + getEntity(entNum).getDisplayName());
            }
            // get the coordinates I can deploy on
            Coords cDeploy = getCoordsAround(getEntity(entNum), cStart);
            if (cDeploy == null) {
                Logger.log(getClass(),
                           METHOD_NAME,
                           LogLevel.ERROR,
                           "getCoordsAround gave no location for "
                           + getEntity(entNum).getChassis());
            }
            // first coordinate that is legal to put this unit on now find some
            // sort
            // of reasonable facing. If there are deployed enemies, face them
            int decent_facing = -1;
            for (Entity e : getEnemyEntities()) {
                if (e.isDeployed() && (!e.isOffBoard())) {
                    decent_facing = cDeploy.direction(e.getPosition());
                    break;
                }
            }
            // if I haven't found a decent facing, then at least face towards
            // the
            // center of the board
            if (decent_facing == -1) {
                Coords center = new Coords(game.getBoard().getWidth() / 2, game
                                                                                   .getBoard().getHeight() / 2);
                decent_facing = cDeploy.direction(center);
            }
            deploy(entNum, cDeploy, decent_facing, 0);
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected void calculateFiringTurn() {
        final String METHOD_NAME = "calculateFiringTurn()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            Entity shooter = game.getFirstEntity(getMyTurn()); // get the first
            // entity that can
            // act this turn
            // make sure weapons are loaded
//            fire_control.loadAmmo(shooter);
            FiringPlan plan = fire_control.getBestFiringPlan(
                    shooter, game);
            if (plan != null) {
                Logger.log(getClass(), METHOD_NAME, plan.getDebugDescription(false));
                // tell the game I want to fire
                sendAttackData(shooter.getId(),
                               plan.getEntityActionVector());

            } else {
                sendAttackData(shooter.getId(), null);
            }
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected Vector<Minefield> calculateMinefieldDeployment() {
        final String METHOD_NAME = "calculateMinefieldDeployment()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            // currently returns no minefields
            // make an empty vector
            return new Vector<Minefield>();
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected MovePath calculateMoveTurn() {
        final String METHOD_NAME = "calculateMoveTurn()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            // first move useless units: immobile units, ejected mechwarrior,
            // etc
            Entity moving_entity = null;
            Entity e = game.getFirstEntity();
            do {
                // ignore loaded and off-board units
                if ((e.getPosition() == null) || e.isOffBoard()) {
                    continue;
                }
                if (e.isImmobile()) {
                    moving_entity = e;
                    break;
                }
                if (e instanceof MechWarrior) {
                    moving_entity = e;
                    break;
                }
                e = game.getEntity(game.getNextEntityNum(e.getId()));
            } while (!e.equals(game.getFirstEntity()));
            // after that, moving farthest units first
            if (moving_entity == null) {
                double furthest_dist = 0;
                e = game.getFirstEntity();
                do {
                    // ignore loaded and off-board units
                    if ((e.getPosition() == null) || e.isOffBoard()) {
                        continue;
                    }
                    double dist = BasicPathRanker.distanceToClosestEnemy(e,
                                                                         e.getPosition(), game);
                    if ((moving_entity == null) || (dist > furthest_dist)) {
                        moving_entity = e;
                        furthest_dist = dist;
                    }
                    e = game.getEntity(game.getNextEntityNum(e.getId()));
                } while (!e.equals(game.getFirstEntity()));
            }

            return continueMovementFor(moving_entity);
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected PhysicalOption calculatePhysicalTurn() {
        final String METHOD_NAME = "calculatePhysicalTurn()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            // get the first entity that can act this turn
            Entity first_entity = game.getFirstEntity(getMyTurn());
            Entity hitter = first_entity;
            PhysicalInfo bestAttack = null;
            do {
                Logger.log(getClass(),
                           METHOD_NAME,
                           "Calculating physical attacks for "
                           + hitter.getDisplayName());
                // this is an array of all my enemies
                ArrayList<Entity> enemies = getEnemyEntities();
                // cycle through potential enemies
                for (Entity e : enemies) {
                    if (e.getPosition() == null) {
                        continue; // Skip enemies not on the board.
                    }
                    PhysicalInfo right_punch = new PhysicalInfo(
                            hitter, e, PhysicalAttackType.RIGHT_PUNCH, game);
                    if (right_punch.getUtility() > 0) {
                        if ((bestAttack == null)
                            || (right_punch.getUtility()> bestAttack.getUtility())) {
                            bestAttack = right_punch;
                        }
                    }
                    PhysicalInfo left_punch = new PhysicalInfo(
                            hitter, e, PhysicalAttackType.LEFT_PUNCH, game);
                    if (left_punch.getUtility() > 0) {
                        if ((bestAttack == null)
                            || (left_punch.getUtility() >bestAttack.getUtility())) {
                            bestAttack = left_punch;
                        }
                    }
                    PhysicalInfo right_kick = new PhysicalInfo(
                            hitter, e, PhysicalAttackType.RIGHT_KICK, game);
                    if (right_kick.getUtility() > 0) {
                        if ((bestAttack == null)
                            || (right_kick.getUtility() > bestAttack.getUtility())) {
                            bestAttack = right_kick;
                        }
                    }
                    PhysicalInfo left_kick = new PhysicalInfo(
                            hitter, e, PhysicalAttackType.LEFT_KICK, game);
                    if (left_kick.getUtility() > 0) {
                        if ((bestAttack == null)
                            || (left_kick.getUtility() > bestAttack.getUtility())) {
                            bestAttack = left_kick;
                        }
                    }

                }
                if (bestAttack != null) {
                    Logger.log(getClass(), METHOD_NAME, "Attack is a " + bestAttack.getAttackType().name());
                } else {
                    Logger.log(getClass(), METHOD_NAME, "No useful attack to be made");
                }
                if (bestAttack != null) {
                    return bestAttack.getAsPhysicalOption(this);
                }
                hitter = game.getEntity(game.getNextEntityNum(hitter.getId()));
                // otherwise, check if the next entity can hit something
                if (hitter.equals(first_entity)) {
                    hitter = null; // getNextEntity is incorrect, it does not
                    // return
                    // null at the end, it returns the first entity
                }
            } while (hitter != null);
            // no one can hit anything anymore, so give up
            return null;
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected MovePath continueMovementFor(Entity entity) {
        final String METHOD_NAME = "continueMovementFor(Entity)";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            // figure out who moved last, and who's move lists need to be
            // updated

            // moves this entity during movement phase
            Logger.log(getClass(), METHOD_NAME, "Moving " + entity.getDisplayName()
                                                + " (ID " + entity.getId() + ")");
            precognition.insureUpToDate();

            if (((entity.isCrippled() && forced_withdrawal) || must_flee || should_flee)
                && !entity.isImmobile()) {
                String msg = entity.getDisplayName()
                             + (should_flee ? " is retreating"
                                            : must_flee ? "is forced to withdraw" : " is crippled and withdrawing");
                Logger.log(getClass(), METHOD_NAME, msg);
                sendChat(msg);
            }

            // If this entity must withdraw, is on its home edge and is able to
            // flee the board, do so.
            if (((entity.isCrippled() && forced_withdrawal) || must_flee || should_flee)
                && entity.canFlee()
                && (BasicPathRanker.distanceToHomeEdge(
                    entity.getPosition(), getHomeEdge(), game) <= 0)) {
                MovePath mp = new MovePath(game, entity);
                mp.addStep(MovePath.MoveStepType.FLEE);
                return mp;
            }

            boolean ejectionPossible = ((entity instanceof Mech) || (entity instanceof Tank))
                                       && entity.getCrew().isActive()
                                       && !entity.hasQuirk("no_eject");
            if (entity instanceof Mech) {
                ejectionPossible &= (((Mech) entity).getCockpitType() != Mech.COCKPIT_TORSO_MOUNTED);
            }
            if (entity instanceof Tank) {
                ejectionPossible &= game.getOptions().booleanOption(
                        "vehicles_can_eject");
            }

            // If this entity is immobile as well as crippled, eject the
            // pilot/crew if possible.
            // Do the same if entity's chance to get up is 0
            // Do the same if forced withdrawal is activated and this entity's
            // chance to get up is < 15%
            MovePath getUpMovePath = new MovePath(game, entity);
            MovePath.MoveStepType getUpStepType = game.getOptions()
                                                      .booleanOption("tacops_careful_stand") ? MovePath.MoveStepType
                                                          .CAREFUL_STAND
                                                                                             : MovePath.MoveStepType
                                                          .GET_UP;
            getUpMovePath.addStep(getUpStepType);
            if ((entity.isImmobile()
                 || entity.isPermanentlyImmobilized()
                 || (entity.isProne() && (PathRanker
                                                  .getMovePathSuccessProbability(getUpMovePath) <= 0.025)) || (entity
                                                                                                                       .isProne() && forced_withdrawal && (PathRanker
                                                                                                                                                                   .getMovePathSuccessProbability(getUpMovePath) <= 0.15)))
                && entity.isCrippled() && ejectionPossible) {
                String msg = entity.getDisplayName()
                             + " is immobile.  Abandoning unit.";
                Logger.log(getClass(), METHOD_NAME, msg);
                sendChat(msg);
                MovePath mp = new MovePath(game, entity);
                mp.addStep(MovePath.MoveStepType.EJECT);
                return mp;
            }

            // precognition.path_enumerator.debugPrintContents();

            ArrayList<MovePath> paths = precognition.getPathEnumerator().unit_paths.get(entity.getId());

            if (paths == null) {
                Logger.log(getClass(), METHOD_NAME, LogLevel.WARNING,
                           "No valid paths found.");
                return new MovePath(game, entity);
            }
            double this_time_estimate = (paths.size() * move_evaluation_time_estimate) / 1e3;
            if (verbosity.getLevel() > LogLevel.WARNING.getLevel()) {
                String timeestimate = "unknown.";
                if (this_time_estimate != 0) {
                    timeestimate = Integer.toString((int) this_time_estimate)
                                   + " seconds";
                }
                String message = "Moving " + entity.getChassis() + ". "
                                 + Long.toString(paths.size())
                                 + " paths to consider.  Estimated time to completion: "
                                 + timeestimate;
                sendChat(message);
            }
            long start_time = System.currentTimeMillis();
            path_ranker.initUnitTurn(entity, game);
            ArrayList<RankedPath> rankedpaths = path_ranker.rankPaths(paths,
                                                                      game);
            long stop_time = System.currentTimeMillis();
            // update path evaluation time estimate
            double updated_estimate = ((double) (stop_time - start_time))
                                      / ((double) paths.size());
            if (move_evaluation_time_estimate == 0) {
                move_evaluation_time_estimate = updated_estimate;
            }
            move_evaluation_time_estimate = 0.5 * (updated_estimate + move_evaluation_time_estimate);
            if (rankedpaths.size() == 0) {
                return new MovePath(game, entity);
            }
            Logger.log(getClass(),
                       METHOD_NAME,
                       "Path ranking took "
                       + Long.toString(stop_time - start_time)
                       + " milliseconds");
            precognition.unpause();
            RankedPath bestpath = PathRanker.getBestPath(rankedpaths);
            Logger.log(getClass(), METHOD_NAME,
                       "Best Path: " + bestpath.path.toString() + "  Rank: "
                       + bestpath.rank);
            bestpath.path.printAllSteps();
            return bestpath.path;
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected void initFiring() {
        final String METHOD_NAME = "initFiring()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {

            // ----Debugging: print out any errors made in guessing to hit
            // values-----
            Vector<Entity> ents = game.getEntitiesVector();
            for (Entity ent : ents) {
                String errors = fire_control.checkAllGuesses(ent, game);
                if (errors != null) {
                    Logger.log(getClass(), METHOD_NAME, LogLevel.ERROR, errors);
                }
            }
            // -----------------------------------------------------------------------
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected void initMovement() {
        final String METHOD_NAME = "initMovement()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            // reset strategic targets
//            fire_control.additional_targets = new ArrayList<Targetable>();
            for (Coords strategic_target : strategic_targets) {
                if (game.getBoard().getBuildingAt(strategic_target) == null) {
                    sendChat("No building to target in Hex "
                             + strategic_target.toFriendlyString()
                             + ", ignoring.");
                } else {
                    fire_control.addAdditionalTarget(new BuildingTarget(
                            strategic_target, game.getBoard(), false));
                    sendChat("Building in Hex "
                             + strategic_target.toFriendlyString()
                             + " designated strategic target.");
                }
            }

            // Pick up on any turrets and shoot their buildings as well.
            Enumeration<Building> buildings = game.getBoard().getBuildings();
            while (buildings.hasMoreElements()) {
                Building bldg = buildings.nextElement();
                Enumeration<Coords> bldgCoords = bldg.getCoords();
                while (bldgCoords.hasMoreElements()) {
                    Coords coords = bldgCoords.nextElement();
                    for (Enumeration<Entity> i = game.getEntities(coords, true); i
                            .hasMoreElements(); ) {
                        Entity entity = i.nextElement();
                        BuildingTarget bt = new BuildingTarget(coords,
                                                               game.getBoard(), false);
                        if ((entity instanceof GunEmplacement)
                            && entity.getOwner()
                                     .isEnemyOf(getLocalPlayer())
                            && (fire_control.getAdditionalTargets().indexOf(bt) == -1)) {
                            fire_control.addAdditionalTarget(bt);
                            sendChat("Building in Hex "
                                     + coords.toFriendlyString()
                                     + " designated target due to Gun Emplacement.");
                        }
                    }
                }
            }
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    public void initialize() {
        final String METHOD_NAME = "initialize()";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            if (initialized) {
                return; // no need to initialize twice
            }
            Properties configfile = new Properties();
            try {
                configfile.load(new FileInputStream(properties_file_name));
                Logger.log(getClass(), METHOD_NAME, "Loading behavior from "
                                                    + properties_file_name);
            } catch (FileNotFoundException e) {
                Logger.log(getClass(), METHOD_NAME, LogLevel.ERROR,
                           "Princess config file not found!");
                Logger.log(getClass(), METHOD_NAME, e);
            } catch (IOException e) {
                Logger.log(getClass(), METHOD_NAME, LogLevel.ERROR,
                           "IO Error in Princess config file!");
                Logger.log(getClass(), METHOD_NAME, e);
            }
            path_searcher = new PathSearcher(this);
            path_ranker = new BasicPathRanker(configfile, this);
            path_ranker.hyper_aggression = 0.1 * Math
                    .pow(10.0, aggression / 50);
            path_ranker.botbase = this;
            path_searcher.ranker = path_ranker;
            fire_control = new FireControl();
            path_ranker.firecontrol = fire_control;
            precognition = new Precognition(this);
            precognition.setGame(game);
            path_ranker.path_enumerator = precognition.getPathEnumerator();

            precognition_thread = new Thread(precognition,
                                             "Princess-precognition");
            precognition_thread.start();
            // precognition.pause();
            initialized = true;
            BotGeometry.debugSelfTest(this);
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    @Override
    protected void processChat(GamePlayerChatEvent ge) {
        final String METHOD_NAME = "processChat(GamePlayerChatEvent)";
        Logger.methodBegin(getClass(), METHOD_NAME);

        try {
            String msg = "Received message: \"" + ge.getMessage()
                         + "\".\tMessage type: " + ge.getEventName();
            Logger.log(getClass(), METHOD_NAME, msg);

            //            StringTokenizer st = new StringTokenizer(ge.getMessage(), ":"); //$NON-NLS-1$
            // String message = st.nextToken();
            // if (message == null) {
            // return;
            // }
            // if (message.contains("flee")) {
            // log(getClass(), METHOD_NAME, "Received flee order!");
            // sendChat("Run Away!");
            // should_flee = true;
            // }

            StringTokenizer st = new StringTokenizer(ge.getMessage(), ":"); //$NON-NLS-1$
            String nameFrom = st.nextToken();
            String secondToken = null;
            String nameTo = null;
            String message = null;

            if (st.hasMoreTokens()) {
                secondToken = st.nextToken().trim();

                if ("help".equalsIgnoreCase(secondToken)) {

                    sendChat("Available commands :");
                    String[] commands = {"[help]", "[botname]:flee",
                                         "[botname]:reset (Resets bot parameters from file)"};
                    for (String command : commands) {
                        sendChat(command);
                    }

                    chatp.processChat(ge, this);
                    return;
                } else {
                    nameTo = secondToken;
                }
            }

            if (st.hasMoreTokens()) {
                message = st.nextToken().trim();
            }

            if ((nameTo == null) || (message == null) || (getLocalPlayer() == null)) {
                chatp.processChat(ge, this);
                return;
            }

            if (nameTo.equalsIgnoreCase(getLocalPlayer().getName())) {
                if (message.equalsIgnoreCase("flee")) {
                    Logger.log(getClass(), METHOD_NAME,
                               " received flee order. Running away to " + getHomeEdge().toString() + " edge !");
                    sendChat(getLocalPlayer().getName()
                             + " received flee order. Running away to "
                             + getHomeEdge().toString() + " edge !");
                    should_flee = true;
                } else if (message.equalsIgnoreCase("reset")) {
                    Logger.log(getClass(), METHOD_NAME,
                               " reseting parameters from properties file");
                    sendChat(getLocalPlayer().getName()
                             + " reseting parameters from properties file");
                    path_ranker.resetParametersFromProperties();
                    should_flee = false;
                }
            }

            chatp.processChat(ge, this);
        } finally {
            Logger.methodEnd(getClass(), METHOD_NAME);
        }
    }

    public HomeEdge getHomeEdge() {
        if (homeEdge == null) {
            homeEdge = BasicPathRanker.getDefaultHomeEdge();
        }
        return homeEdge;
    }

    public void setHomeEdge(HomeEdge homeEdge) {
        if (homeEdge == null) {
            Logger.log(getClass(), "setHomeEdge(BasicPathRanker.HomeEdge)",
                       new IllegalArgumentException("Home Edge is required!"));
            homeEdge = BasicPathRanker.getDefaultHomeEdge();
        }
        this.homeEdge = homeEdge;
    }
}