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

package megamek.client;

import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import java.util.Vector;

import megamek.client.bot.BotClient;
import megamek.common.*;
import megamek.common.actions.AttackAction;
import megamek.common.actions.ClubAttackAction;
import megamek.common.actions.DodgeAction;
import megamek.common.actions.EntityAction;
import megamek.common.actions.FlipArmsAction;
import megamek.common.actions.TorsoTwistAction;
import megamek.common.event.GameAttackEvent;
import megamek.common.event.GameEntityChangeEvent;
import megamek.common.event.GameMapQueryEvent;
import megamek.common.event.GamePlayerChatEvent;
import megamek.common.event.GamePlayerDisconnectedEvent;
import megamek.common.event.GameReportEvent;
import megamek.common.event.GameSettingsChangeEvent;
import megamek.common.options.GameOptions;
import megamek.common.preference.PreferenceManager;

public class Client implements Runnable {
    // we need these to communicate with the server
    private String name;
    Socket socket;
    private ObjectInputStream in = null;
    private ObjectOutputStream out = null;

    // some info about us and the server
    private boolean connected = false;
    public int local_pn = -1;
    private int connFailures = 0;
    private static final int MAX_CONN_FAILURES = 100;
    private String host;
    private int port;
    
    // the game state object
    public IGame game = new Game();

    // here's some game phase stuff
    private MapSettings mapSettings;
    public String eotr;
    
    private Thread pump;

    //And close client events!
    private Vector closeClientListeners = new Vector();

    // we might want to keep a server log...
    private megamek.server.ServerLog serverlog;

    /**
     * Construct a client which will try to connect.  If the connection
     * fails, it will alert the player, free resources and hide the frame.
     *
     * @param name the player name for this client
     * @param host the hostname
     * @param port the host port
     */
    public Client(String name, String host, int port) {
        // construct new client
        this.name = name;
        this.host = host;
        this.port = port;

        if (PreferenceManager.getClientPreferences().keepServerlog() && !(this instanceof BotClient)) {
            // we need to keep a copy of the log
            serverlog = new megamek.server.ServerLog(PreferenceManager.getClientPreferences().getServerlogFilename(), 
                    true, (new Integer(PreferenceManager.getClientPreferences().getServerlogMaxSize()).longValue() * 1024 * 1024) );
        };
    }


    /**
     * Attempt to connect to the specified host
     */
    public void connect() throws UnknownHostException, IOException {
        socket = new Socket(host, port);
        pump = new Thread(this, "Client Pump"); //$NON-NLS-1$
        pump.start();
    }

    /**
     * Shuts down threads and sockets
     */
    public void die() {
        // If we're still connected, tell the server that we're going down.
        if (connected) {
            send(new Packet(Packet.COMMAND_CLOSE_CONNECTION));
        }
        connected = false;
        pump = null;

        // shut down threads & sockets
        try {
            socket.close();
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // not a big deal, just never connected
        }
        
        for (int i = 0; i < closeClientListeners.size(); i++){
            ((CloseClientListener)closeClientListeners.elementAt(i)).clientClosed();
        }

        if (serverlog != null) {
            try {
                serverlog.close();
            } catch (IOException e) {
                System.err.print("Exception closing logfile: "); //$NON-NLS-1$
                e.printStackTrace();
            };
        }
        System.out.println("client: died"); //$NON-NLS-1$
        
    }

    /**
     * The client has become disconnected from the server
     */
    protected void disconnected() {
        if (connected) {
            connected = false;
            die();
        }
        if (!host.equals("localhost")) { //$NON-NLS-1$
            game.processGameEvent(new GamePlayerDisconnectedEvent(this, getLocalPlayer()));
        }
    }
    
    /**
     * Return an enumeration of the players in the game
     */
    public Enumeration getPlayers() {
        return game.getPlayers();
    }

    public Entity getEntity(int id) {
        return game.getEntity(id);
    }

    /**
     * Returns the individual player assigned the index
     * parameter.
     */
    public Player getPlayer(int idx) {
        return (Player) game.getPlayer(idx);
    }

    /**
     * Return the local player
     */
    public Player getLocalPlayer() {
        return getPlayer(local_pn);
    }

    /**
     * Returns an <code>Enumeration</code> of the entities that match
     * the selection criteria.
     */
    public Enumeration getSelectedEntities(EntitySelector selector) {
        return game.getSelectedEntities(selector);
    }

    /**
     * Returns the number of first selectable entity
     */
    public int getFirstEntityNum() {
        return game.getFirstEntityNum();
    }

    /**
     * Returns the number of the next selectable entity after the one given
     */
    public int getNextEntityNum(int entityId) {
        return game.getNextEntityNum(entityId);
    }

    /**
     * Returns the number of the first deployable entity
     */
    public int getFirstDeployableEntityNum() {
        return game.getFirstDeployableEntityNum();
    }

    /**
     * Returns the number of the next deployable entity
     */
    public int getNextDeployableEntityNum(int entityId) {
        return game.getNextDeployableEntityNum(entityId);
    }

    /**
     * Shortcut to game.board
     */
    public IBoard getBoard() {
        return game.getBoard();
    }

    /**
     * Returns an emumeration of the entities in game.entities
     */
    public Enumeration getEntities() {
        return game.getEntities();
    }

    public MapSettings getMapSettings() {
        return mapSettings;
    }

    /**
     * Changes the game phase, and the displays that go
     * along with it.
     */
    protected void changePhase(int phase) {
        game.setPhase(phase);
        // Handle phase-specific items.
        switch (phase) {
        case Game.PHASE_STARTING_SCENARIO :
            sendDone(true);
            break;
        case Game.PHASE_EXCHANGE :
            sendDone(true);
            break;
        case Game.PHASE_DEPLOYMENT :
            memDump("entering deployment phase"); //$NON-NLS-1$
            break;
        case Game.PHASE_TARGETING :
            memDump("entering targeting phase"); //$NON-NLS-1$
            break;
        case Game.PHASE_MOVEMENT :
            memDump("entering movement phase"); //$NON-NLS-1$
            break;
        case Game.PHASE_OFFBOARD :
            memDump("entering offboard phase"); //$NON-NLS-1$
            break;
        case Game.PHASE_FIRING :
            memDump("entering firing phase"); //$NON-NLS-1$
            break;
        case Game.PHASE_PHYSICAL :
            memDump("entering physical phase"); //$NON-NLS-1$
            break;
        }
    }

    /**
     * Adds the specified close client listener to receive
     * close client events.
     * This is used by external programs running megamek
     *
     * @param l            the game listener.
     */
    public void addCloseClientListener(CloseClientListener l) {
        closeClientListeners.addElement(l);
    }

    /**
     *
     */
    public void retrieveServerInfo() {
        int retry = 50;
        while (retry-- > 0 && !connected) {
            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException ex) {
                    ;
                }
            }
        }
    }

    /**
     * is it my turn?
     */
    public boolean isMyTurn() {
        return game.getTurn() != null && game.getTurn().isValid(local_pn, game);
    }

    /**
     * Can I unload entities stranded on immobile transports?
     */
    public boolean canUnloadStranded() {
        return game.getTurn() instanceof GameTurn.UnloadStrandedTurn && game.getTurn().isValid(local_pn, game);
    }

    /**
     * Send command to unload stranded entities to the server
     */
    public void sendUnloadStranded(int[] entityIds) {
        Object[] data = new Object[1];
        data[0] = entityIds;
        send(new Packet(Packet.COMMAND_UNLOAD_STRANDED, data));
    }

    /**
     * Change whose turn it is.
     */
    protected void changeTurnIndex(int index) {
        game.setTurnIndex(index);
    }

    /**
     * Send mode-change data to the server
     */
    public void sendModeChange(int nEntity, int nEquip, int nMode) {
        Object[] data = { new Integer(nEntity), new Integer(nEquip), new Integer(nMode)};
        send(new Packet(Packet.COMMAND_ENTITY_MODECHANGE, data));
    }

    /**
     * Send mode-change data to the server
     */
    public void sendAmmoChange(int nEntity, int nWeapon, int nAmmo) {
        Object[] data = { new Integer(nEntity), new Integer(nWeapon), new Integer(nAmmo)};
        send(new Packet(Packet.COMMAND_ENTITY_AMMOCHANGE, data));
    }

    /**
     * Send movement data for the given entity to the server.
     */
    public void moveEntity(int id, MovePath md) {
        Object[] data = new Object[2];

        data[0] = new Integer(id);
        data[1] = md;

        send(new Packet(Packet.COMMAND_ENTITY_MOVE, data));
    }

    /**
     * Maintain backwards compatability.
     *
     * @param   id - the <code>int</code> ID of the deployed entity
     * @param   c - the <code>Coords</code> where the entity should be deployed
     * @param   nFacing - the <code>int</code> direction the entity should face
     */
    public void deploy(int id, Coords c, int nFacing) {
        this.deploy(id, c, nFacing, new Vector());
    }

    /**
     * Deploy an entity at the given coordinates, with the given facing,
     * and starting with the given units already loaded.
     *
     * @param   id - the <code>int</code> ID of the deployed entity
     * @param   c - the <code>Coords</code> where the entity should be deployed
     * @param   nFacing - the <code>int</code> direction the entity should face
     * @param   loadedUnits - a <code>List</code> of units that start the game
     *          being transported byt the deployed entity.
     */
    public void deploy(int id, Coords c, int nFacing, Vector loadedUnits) {
        int packetCount = 4 + loadedUnits.size();
        int index = 0;
        Object[] data = new Object[packetCount];
        data[index++] = new Integer(id);
        data[index++] = c;
        data[index++] = new Integer(nFacing);
        data[index++] = new Integer(loadedUnits.size());

        Enumeration iter = loadedUnits.elements();
        while (iter.hasMoreElements()) {
            data[index++] = new Integer(((Entity) iter.nextElement()).getId());
        }

        send(new Packet(Packet.COMMAND_ENTITY_DEPLOY, data));
    }

    /**
     * Send a weapon fire command to the server.
     */
    public void sendAttackData(int aen, Vector attacks) {
        Object[] data = new Object[2];

        data[0] = new Integer(aen);
        data[1] = attacks;

        send(new Packet(Packet.COMMAND_ENTITY_ATTACK, data));
    }

    /**
     * Send the game options to the server
     */
    public void sendGameOptions(String password, Vector options) {
        final Object[] data = new Object[2];
        data[0] = password;
        data[1] = options;
        send(new Packet(Packet.COMMAND_SENDING_GAME_SETTINGS, data));
    }

    /**
     * Send the game settings to the server
     */
    public void sendMapSettings(MapSettings settings) {
        send(new Packet(Packet.COMMAND_SENDING_MAP_SETTINGS, settings));
    }

    /**
     * Send the game settings to the server
     */
    public void sendMapQuery(MapSettings query) {
        send(new Packet(Packet.COMMAND_QUERY_MAP_SETTINGS, query));
    }

    /**
     * Broadcast a general chat message from the local player
     */
    public void sendChat(String message) {
        send(new Packet(Packet.COMMAND_CHAT, message));
    }

    /**
     * Sends a "player done" message to the server.
     */
    public synchronized void sendDone(boolean done) {
        send(new Packet(Packet.COMMAND_PLAYER_READY, new Boolean(done)));
    }

    /**
     * Sends a "reroll initiative" message to the server.
     */
    public void sendRerollInitiativeRequest() {
        send(new Packet(Packet.COMMAND_REROLL_INITIATIVE));
    }

    /**
     * Sends the info associated with the local player.
     */
    public void sendPlayerInfo() {
        Player player = game.getPlayer(local_pn);
        PreferenceManager.getClientPreferences().setLastPlayerColor(player.getColorIndex());
        PreferenceManager.getClientPreferences().setLastPlayerCategory(player.getCamoCategory());
        PreferenceManager.getClientPreferences().setLastPlayerCamoName(player.getCamoFileName());
        send(new Packet(Packet.COMMAND_PLAYER_UPDATE, player));
    }

    /**
     * Sends an "add entity" packet
     */
    public void sendAddEntity(Entity entity) {
        send(new Packet(Packet.COMMAND_ENTITY_ADD, entity));
    }

    /**
     * Sends an "deploy minefields" packet
     */
    public void sendDeployMinefields(Vector minefields) {
        send(new Packet(Packet.COMMAND_DEPLOY_MINEFIELDS, minefields));
    }

    /**
     * Sends a "set Artillery Autohit Hexes" packet
     */
    public void sendArtyAutoHitHexes(Vector hexes) {
        send(new Packet(Packet.COMMAND_SET_ARTYAUTOHITHEXES, hexes));
    }
    
    /**
     * Sends an "update entity" packet
     */
    public void sendUpdateEntity(Entity entity) {
        send(new Packet(Packet.COMMAND_ENTITY_UPDATE, entity));
    }

    /**
     * Sends a "delete entity" packet
     */
    public void sendDeleteEntity(int id) {
        send(new Packet(Packet.COMMAND_ENTITY_REMOVE, new Integer(id)));
    }

    /**
     * Receives player information from the message packet.
     */
    protected void receivePlayerInfo(Packet c) {
        int pindex = c.getIntValue(0);
        Player newPlayer = (Player) c.getObject(1);
        if (getPlayer(newPlayer.getId()) == null) {
            game.addPlayer(pindex, newPlayer);
        } else {
            game.setPlayer(pindex, newPlayer);
        }

        PreferenceManager.getClientPreferences().setLastPlayerColor(newPlayer.getColorIndex());
        PreferenceManager.getClientPreferences().setLastPlayerCategory(newPlayer.getCamoCategory());
        PreferenceManager.getClientPreferences().setLastPlayerCamoName(newPlayer.getCamoFileName());
    }

    /**
     * Loads the turn list from the data in the packet
     */
    protected void receiveTurns(Packet packet) {
        game.setTurnVector((Vector) packet.getObject(0));
    }

    /**
     * Loads the board from the data in the net command.
     */
    protected void receiveBoard(Packet c) {
        Board newBoard = (Board) c.getObject(0);
        game.setBoard(newBoard);
    }

    /**
     * Loads the entities from the data in the net command.
     */
    protected void receiveEntities(Packet c) {
        Vector newEntities = (Vector) c.getObject(0);
        Vector newOutOfGame = (Vector) c.getObject(1);

        // Replace the entities in the game.
        game.setEntitiesVector(newEntities);
        if (newOutOfGame != null) {
            game.setOutOfGameEntitiesVector(newOutOfGame);
        }
    }

    /**
     * Loads entity update data from the data in the net command.
     */
    protected void receiveEntityUpdate(Packet c) {
        int eindex = c.getIntValue(0);
        Entity entity = (Entity) c.getObject(1);
        // Replace this entity in the game.
        game.setEntity(eindex, entity);        
    }

    protected void receiveEntityAdd(Packet packet) {
        int entityId = packet.getIntValue(0);
        Entity entity = (Entity) packet.getObject(1);

        // Add the entity to the game.
        game.addEntity(entityId, entity);
    }

    protected void receiveEntityRemove(Packet packet) {
        int entityId = packet.getIntValue(0);
        int condition = packet.getIntValue(1);
        // Move the unit to its final resting place.
        game.removeEntity(entityId, condition);
    }

    protected void receiveEntityVisibilityIndicator(Packet packet) {
        Entity e = game.getEntity(packet.getIntValue(0));
        if (e != null) { // we may not have this entity due to double blind
            e.setSeenByEnemy(packet.getBooleanValue(1));
            e.setVisibleToEnemy(packet.getBooleanValue(2));
            //this next call is only needed sometimes, but we'll just
            // call it everytime
            game.processGameEvent(new GameEntityChangeEvent(this,e));
        }
    }

    protected void receiveDeployMinefields(Packet packet) {
        game.addMinefields((Vector) packet.getObject(0));
    }

    protected void receiveSendingMinefields(Packet packet) {
        game.setMinefields((Vector) packet.getObject(0));
    }

    protected void receiveRevealMinefield(Packet packet) {
        game.addMinefield((Minefield) packet.getObject(0));
    }

    protected void receiveRemoveMinefield(Packet packet) {
        game.removeMinefield((Minefield) packet.getObject(0));
    }

    protected void receiveBuildingUpdateCF(Packet packet) {
        game.getBoard().updateBuildingCF((Vector) packet.getObject(0));
    }

    protected void receiveBuildingCollapse(Packet packet) {
        game.getBoard().collapseBuilding((Vector) packet.getObject(0));
    }

    /**
     * Loads entity firing data from the data in the net command
     */
    protected void receiveAttack(Packet c) {
        Vector vector = (Vector) c.getObject(0);
        boolean charge = c.getBooleanValue(1);
        boolean addAction = true;
        for (Enumeration i = vector.elements(); i.hasMoreElements();) {
            EntityAction ea = (EntityAction) i.nextElement();
            int entityId = ea.getEntityId();
            if (ea instanceof TorsoTwistAction && game.hasEntity(entityId)) {
                TorsoTwistAction tta = (TorsoTwistAction) ea;
                Entity entity = game.getEntity(entityId);
                entity.setSecondaryFacing(tta.getFacing());
            } else if (ea instanceof FlipArmsAction && game.hasEntity(entityId)) {
                FlipArmsAction faa = (FlipArmsAction) ea;
                Entity entity = game.getEntity(entityId);
                entity.setArmsFlipped(faa.getIsFlipped());
            } else if (ea instanceof DodgeAction && game.hasEntity(entityId)) {
                Entity entity = game.getEntity(entityId);
                entity.dodging = true;
                addAction = false;
            } else if (ea instanceof AttackAction) {
                if (ea instanceof ClubAttackAction) {
                    ClubAttackAction clubAct = (ClubAttackAction) ea;
                    Entity entity = game.getEntity(clubAct.getEntityId());
                    clubAct.setClub(Compute.clubMechHas(entity));
                }
                game.processGameEvent(new GameAttackEvent(this,(AttackAction)ea));
            }

            if (addAction) {
                // track in the appropriate list
                if (charge) {
                    game.addCharge((AttackAction) ea);
                } else {
                    game.addAction(ea);
                }
            }
        }
    }

    /**
     * Saves server entity status data to a local file
     */
    private void saveEntityStatus(String sStatus) {
        try {
            FileWriter fw = new FileWriter("entitystatus.txt"); //$NON-NLS-1$
            fw.write(sStatus);
            fw.flush();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a complete net command from the given input stream
     */
    private Packet readPacket() {
        try {
            if (in == null) {
                in = new ObjectInputStream(socket.getInputStream());
            }

            Packet packet = (Packet)in.readObject();

            /* Packet debug code
            if (packet == null) {
                System.out.println("c: received null packet");
            } else if (packet.getData() == null) {
                System.out.println("c: received empty packet");
            } else {
                System.out.println("c: received command #" + packet.getCommand() + " with " + packet.getData().length + " zipped entries totaling " + packet.byteLength + " bytes in size");
            } */

            // All went well.  Reset the failure count.
            this.connFailures = 0;
            return packet;
        } catch (SocketException ex) {
            // assume client is shutting down
            System.err.println("client: Socket error (server closed?)"); //$NON-NLS-1$
            if (this.connFailures > MAX_CONN_FAILURES) {
                disconnected();
            } else {
                this.connFailures++;
            }
            return null;
        } catch (IOException ex) {
            System.err.println("client: IO error reading command:"); //$NON-NLS-1$
            disconnected();
            return null;
        } catch (ClassNotFoundException ex) {
            System.err.println("client: class not found error reading command:"); //$NON-NLS-1$
            ex.printStackTrace();
            disconnected();
            return null;
        }
    }

    /**
     * send the message to the server
     */
    protected void send(Packet packet) {
        packet.zipData();
        try {
            if (out == null) {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
            }
            out.reset(); // write each packet fresh; a lot changes
            out.writeObject(packet);
            out.flush();
            //            System.out.println("c: packet #" + packet.getCommand() + " sent");
        } catch (IOException ex) {
            System.err.println("client: error sending command."); //$NON-NLS-1$
        }
    }

    //
    // Runnable
    //
    public void run() {
        Thread currentThread = Thread.currentThread();
        while(pump == currentThread) {
            Packet c = readPacket();
            if (c == null) {
                System.out.println("client: got null packet"); //$NON-NLS-1$
                continue;
            }
            switch (c.getCommand()) {
                case Packet.COMMAND_CLOSE_CONNECTION :
                    disconnected();
                    break;
                case Packet.COMMAND_SERVER_GREETING :
                    connected = true;
                    send(new Packet(Packet.COMMAND_CLIENT_NAME, name));
                    break;
                case Packet.COMMAND_LOCAL_PN :
                    this.local_pn = c.getIntValue(0);
                    break;
                case Packet.COMMAND_PLAYER_UPDATE :
                    receivePlayerInfo(c);
                    break;
                case Packet.COMMAND_PLAYER_READY :
                    getPlayer(c.getIntValue(0)).setDone(c.getBooleanValue(1));
                    break;
                case Packet.COMMAND_PLAYER_ADD :
                    receivePlayerInfo(c);
                    break;
                case Packet.COMMAND_PLAYER_REMOVE :
                    game.removePlayer(c.getIntValue(0));
                    break;
                case Packet.COMMAND_CHAT :
                    if (null!=serverlog && PreferenceManager.getClientPreferences().keepServerlog()) {
                        serverlog.append( (String) c.getObject(0) );
                    };
                    game.processGameEvent(new GamePlayerChatEvent(this,null, (String) c.getObject(0)));
                    break;
                case Packet.COMMAND_ENTITY_ADD :
                    receiveEntityAdd(c);
                    break;
                case Packet.COMMAND_ENTITY_UPDATE :
                    receiveEntityUpdate(c);
                    break;
                case Packet.COMMAND_ENTITY_REMOVE :
                    receiveEntityRemove(c);
                    break;
                case Packet.COMMAND_ENTITY_VISIBILITY_INDICATOR :
                    receiveEntityVisibilityIndicator(c);
                    break;
                case Packet.COMMAND_SENDING_MINEFIELDS :
                    receiveSendingMinefields(c);
                    break;
                case Packet.COMMAND_DEPLOY_MINEFIELDS :
                    receiveDeployMinefields(c);
                    break;
                case Packet.COMMAND_REVEAL_MINEFIELD :
                    receiveRevealMinefield(c);
                    break;
                case Packet.COMMAND_REMOVE_MINEFIELD :
                    receiveRemoveMinefield(c);
                    break;
                case Packet.COMMAND_CHANGE_HEX :
                    game.getBoard().setHex((Coords) c.getObject(0), (Hex) c.getObject(1));
                    break;
                case Packet.COMMAND_BLDG_UPDATE_CF :
                    receiveBuildingUpdateCF(c);
                    break;
                case Packet.COMMAND_BLDG_COLLAPSE :
                    receiveBuildingCollapse(c);
                    break;
                case Packet.COMMAND_PHASE_CHANGE :
                    changePhase(c.getIntValue(0));
                    break;
                case Packet.COMMAND_TURN :
                    changeTurnIndex(c.getIntValue(0));
                    break;
                case Packet.COMMAND_ROUND_UPDATE :
                    game.setRoundCount(c.getIntValue(0));
                    break;
                case Packet.COMMAND_SENDING_TURNS :
                    receiveTurns(c);
                    break;
                case Packet.COMMAND_SENDING_BOARD :
                    receiveBoard(c);
                    break;
                case Packet.COMMAND_SENDING_ENTITIES :
                    receiveEntities(c);
                    break;
                case Packet.COMMAND_SENDING_REPORT :
                    if (null!=serverlog && PreferenceManager.getClientPreferences().keepServerlog()) {
                        if (null==eotr || ((String) c.getObject(0)).length() < eotr.length() ) {
                            // first report packet
                            serverlog.append( (String) c.getObject(0) );
                        } else {
                            // append only the new part, not what's already in eotr
                            serverlog.append( ((String) c.getObject(0)).substring(eotr.length()) );
                        };
                    };
                    eotr = (String) c.getObject(0);
                    game.processGameEvent(new GameReportEvent(this));
                    break;
                case Packet.COMMAND_ENTITY_ATTACK :
                    receiveAttack(c);
                    break;
                case Packet.COMMAND_SENDING_GAME_SETTINGS :
                    game.setOptions((GameOptions) c.getObject(0));
                    break;
                case Packet.COMMAND_SENDING_MAP_SETTINGS :
                    mapSettings = (MapSettings) c.getObject(0);
                    game.processGameEvent(new GameSettingsChangeEvent(this));
                    break;
                case Packet.COMMAND_QUERY_MAP_SETTINGS :
                    game.processGameEvent(new GameMapQueryEvent(this, (MapSettings)c.getObject(0)));
                    break;
                case Packet.COMMAND_END_OF_GAME :
                    String sReport = (String) c.getObject(0);
                    game.end(c.getIntValue(1), c.getIntValue(2));
                    // save victory report
                    saveEntityStatus(sReport);
                    break;
            }
        }
    }

    /**
     * Perform a dump of the current memory usage.
     * <p/>
     * This method is useful in tracking performance issues on various
     * player's systems.  You can activate it by changing the "memorydumpon"
     * setting to "true" in the MegaMek.cfg file.
     *
     * @param   where - a <code>String</code> indicating which part of the
     *          game is making this call.
     *
     * @see     megamek.common.Settings#memoryDumpOn
     * @see     megamek.client.Client#changePhase(int)
     */
    private void memDump(String where) {
        if (PreferenceManager.getClientPreferences().memoryDumpOn()) {
            StringBuffer buf = new StringBuffer();
            final long total = Runtime.getRuntime().totalMemory();
            final long free = Runtime.getRuntime().freeMemory();
            final long used = total - free;
            buf.append("Memory dump ").append(where); //$NON-NLS-1$
            for (int loop = where.length(); loop < 25; loop++) {
                buf.append(' ');
            }
            buf.append(": used (").append(used).append(") + free (").append(free).append(") = ").append(total); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            System.out.println(buf.toString());
        }
    }
    
    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

}
