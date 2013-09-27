package megamek.client.ui.swing;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import megamek.client.TimerSingleton;
import megamek.client.event.BoardViewEvent;
import megamek.client.event.BoardViewListener;
import megamek.client.event.MechDisplayEvent;
import megamek.client.event.MechDisplayListener;
import megamek.client.ui.swing.util.KeyAlphaFilter;
import megamek.client.ui.swing.util.PlayerColors;
import megamek.client.ui.swing.util.StraightArrowPolygon;
import megamek.common.Compute;
import megamek.common.Coords;
import megamek.common.Entity;
import megamek.common.GunEmplacement;
import megamek.common.IBoard;
import megamek.common.IEntityMovementMode;
import megamek.common.IEntityMovementType;
import megamek.common.IGame;
import megamek.common.IHex;
import megamek.common.Infantry;
import megamek.common.LosEffects;
import megamek.common.Mech;
import megamek.common.Minefield;
import megamek.common.Mounted;
import megamek.common.MovePath;
import megamek.common.MoveStep;
import megamek.common.Player;
import megamek.common.Protomech;
import megamek.common.Tank;
import megamek.common.TargetRoll;
import megamek.common.Targetable;
import megamek.common.UnitLocation;
import megamek.common.WeaponType;
import megamek.common.actions.ArtilleryAttackAction;
import megamek.common.actions.AttackAction;
import megamek.common.actions.ChargeAttackAction;
import megamek.common.actions.ClubAttackAction;
import megamek.common.actions.DfaAttackAction;
import megamek.common.actions.EntityAction;
import megamek.common.actions.KickAttackAction;
import megamek.common.actions.PhysicalAttackAction;
import megamek.common.actions.ProtomechPhysicalAttackAction;
import megamek.common.actions.PunchAttackAction;
import megamek.common.actions.PushAttackAction;
import megamek.common.actions.SearchlightAttackAction;
import megamek.common.actions.WeaponAttackAction;
import megamek.common.event.BoardEvent;
import megamek.common.event.BoardListener;
import megamek.common.event.GameBoardChangeEvent;
import megamek.common.event.GameBoardNewEvent;
import megamek.common.event.GameEntityChangeEvent;
import megamek.common.event.GameEntityNewEvent;
import megamek.common.event.GameEntityRemoveEvent;
import megamek.common.event.GameListener;
import megamek.common.event.GameListenerAdapter;
import megamek.common.event.GameNewActionEvent;
import megamek.common.event.GamePhaseChangeEvent;
import megamek.common.preference.IClientPreferences;
import megamek.common.preference.IPreferenceChangeListener;
import megamek.common.preference.PreferenceChangeEvent;
import megamek.common.preference.PreferenceManager;

/**
 * Displays the board; lets the user scroll around and select points on it.
 */
public class BoardView1
    extends JPanel
    implements IBoardView, Scrollable, BoardListener, MouseListener, KeyEventDispatcher, MechDisplayListener, IPreferenceChangeListener
{

    private static final long serialVersionUID = -5582195884759007416L;
    private static final int        TRANSPARENT = 0xFFFF00FF;

    // the dimensions of megamek's hex images
    private static final int        HEX_W = 84;
    private static final int        HEX_H = 72;
    private static final int        HEX_WC = HEX_W - HEX_W/4;
    
    // line width of the c3 network lines
    private static final int C3_LINE_WIDTH = 1;

    private static Font FONT_10 = new Font("SansSerif", Font.PLAIN, 10); //$NON-NLS-1$
    private static Font FONT_12 = new Font("SansSerif", Font.PLAIN, 12); //$NON-NLS-1$

    private Dimension       hex_size = null;
    
    private Font       font_hexnum          = FONT_10;
    private Font       font_minefield   = FONT_12;

    private IGame game;

    private Dimension boardSize;

    // scrolly stuff:
    private JScrollPane scrollpane = null;
    
    // entity sprites
    private ArrayList<EntitySprite> entitySprites = new ArrayList<EntitySprite>();
    private HashMap<Integer,EntitySprite> entitySpriteIds = new HashMap<Integer,EntitySprite>();

    // sprites for the three selection cursors
    private CursorSprite cursorSprite;
    private CursorSprite highlightSprite;
    private CursorSprite selectedSprite;
    private CursorSprite firstLOSSprite;
    private CursorSprite secondLOSSprite;

    // sprite for current movement
    private ArrayList<StepSprite> pathSprites = new ArrayList<StepSprite>();

    // vector of sprites for all firing lines
    private ArrayList<AttackSprite> attackSprites = new ArrayList<AttackSprite>();

    // vector of sprites for C3 network lines
    private ArrayList<C3Sprite> C3Sprites = new ArrayList<C3Sprite>();

    private TilesetManager tileManager = null;

    // polygons for a few things
    private Polygon              hexPoly;
    private Polygon[]            facingPolys;
    private Polygon[]            movementPolys;

    // the player who owns this BoardView's client
    private Player               localPlayer = null;

    // should we mark deployment hexes for a player?
    private Player               m_plDeployer = null;

    // should be able to turn it off(board editor)
    private boolean              useLOSTool = true;

    // Initial scale factor for sprites and map
    private float               scale = 1.00f;
        
    // Displayables (Chat box, etc.)
    private ArrayList<Displayable> displayables = new ArrayList<Displayable>();

    // Move units step by step
    private ArrayList<MovingUnit>               movingUnits = new ArrayList<MovingUnit>();
    private long                             moveWait = 0;

    // moving entity sprites
    private ArrayList<MovingEntitySprite> movingEntitySprites = new ArrayList<MovingEntitySprite>();
    private HashMap<Integer,MovingEntitySprite> movingEntitySpriteIds = new HashMap<Integer,MovingEntitySprite>();
    private ArrayList<GhostEntitySprite> ghostEntitySprites = new ArrayList<GhostEntitySprite>();
    protected transient ArrayList<BoardViewListener> boardListeners = new ArrayList<BoardViewListener>();

    // wreck sprites
    private ArrayList<WreckSprite> wreckSprites = new ArrayList<WreckSprite>();

    private Coords rulerStart; // added by kenn
    private Coords rulerEnd; // added by kenn
    private Color rulerStartColor; // added by kenn
    private Color rulerEndColor; // added by kenn
    
    private Coords lastCursor;
    private Coords highlighted;
    private Coords selected;
    private Coords firstLOS;


    //selected entity and weapon for artillery display
    private Entity selectedEntity = null;
    private Mounted selectedWeapon = null;
    
    //hexes with ECM effect
    private HashMap<Coords, Integer> ecmHexes = null;

    /**
     * Construct a new board view for the specified game
     */
    public BoardView1(IGame game) throws java.io.IOException {
        this.game = game;

        tileManager = new TilesetManager(this);

        game.addGameListener(gameListener);
        game.getBoard().addBoardListener(this);
        scheduleRedrawTimer();//call only once
        addMouseListener(this);
        MouseMotionListener doScrollRectToVisible = new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
               Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
               ((JPanel)e.getSource()).scrollRectToVisible(r);
           }
        };
        addMouseMotionListener(doScrollRectToVisible);
        setAutoscrolls(true);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);

        updateBoardSize();
        
        hex_size = new Dimension((int)(HEX_W*scale), (int)(HEX_H*scale));
        
        initPolys();

        cursorSprite = new CursorSprite(Color.cyan);
        highlightSprite = new CursorSprite(Color.white);
        selectedSprite = new CursorSprite(Color.blue);
        firstLOSSprite = new CursorSprite(Color.red);
        secondLOSSprite = new CursorSprite(Color.red);
        
        PreferenceManager.getClientPreferences().addPreferenceChangeListener(this);
    }
    protected final RedrawWorker redrawWorker = new RedrawWorker();
    /**
     *  this should only be called once!! this will cause
     *  a timer to schedule constant screen updates every 20 
     *  milliseconds! 
     *  
     */
    protected void scheduleRedrawTimer() {
        final TimerTask redraw=new TimerTask() {
            public void run() {
                try {
                    SwingUtilities.invokeAndWait(redrawWorker);
                } catch(Exception ie) {
                }    
            }
        };
        TimerSingleton.getInstance().schedule(redraw,20,20);            
    }
    protected void scheduleRedraw() {
        try {
            SwingUtilities.invokeLater(redrawWorker);
        } catch(Exception ie) {
        }    
    }
    public void preferenceChange(PreferenceChangeEvent e) {
        if(e.getName().equals(IClientPreferences.MAP_TILESET)) {
            updateBoard();
        }
    }

    /**
     * Adds the specified board listener to receive
     * board events from this board.
     *
     * @param listener the board listener.
     */
    public void addBoardViewListener(BoardViewListener listener) {
        if (!boardListeners.contains(listener)) {
            boardListeners.add(listener);
        }
    }
    
    /**
     * Removes the specified board listener.
     *
     * @param listener the board listener.
     */
    public void removeBoardViewListener(BoardViewListener listener) {
        boardListeners.remove(listener);
    }

    /**
     * Notifies attached board listeners of the event.
     *
     * @param event the board event.
     */
    public void processBoardViewEvent(BoardViewEvent event) {
        if (boardListeners == null) {
            return;
        }
        for(BoardViewListener l: boardListeners) {
            switch(event.getType()) {
            case BoardViewEvent.BOARD_HEX_CLICKED :
            case BoardViewEvent.BOARD_HEX_DOUBLECLICKED :
            case BoardViewEvent.BOARD_HEX_DRAGGED :
                l.hexMoused(event);
                break;
            case BoardViewEvent.BOARD_HEX_CURSOR :
                l.hexCursor(event);
                break;
            case BoardViewEvent.BOARD_HEX_HIGHLIGHTED :
                l.boardHexHighlighted(event);
                break;
            case BoardViewEvent.BOARD_HEX_SELECTED :
                l.hexSelected(event);
                break;
            case BoardViewEvent.BOARD_FIRST_LOS_HEX :
                l.firstLOSHex(event);
                break;
            case BoardViewEvent.BOARD_SECOND_LOS_HEX :
                l.secondLOSHex(event, getFirstLOS());
                break;
            case BoardViewEvent.FINISHED_MOVING_UNITS :
                l.finishedMovingUnits(event);
                break;
            case BoardViewEvent.SELECT_UNIT :
                l.unitSelected(event);
                break;
            }
        }
    }

    private void addMovingUnit(Entity entity, Vector<UnitLocation> movePath) {
        if ( !movePath.isEmpty() ) {
            MovingUnit m = new MovingUnit(entity, movePath);
            movingUnits.add(m);

            GhostEntitySprite ghostSprite = new GhostEntitySprite(entity);
            ghostEntitySprites.add(ghostSprite);

            // Center on the starting hex of the moving unit.
            UnitLocation loc = movePath.get(0);
            centerOnHex( loc.getCoords() );
        }
    }

    public void addDisplayable(Displayable disp) {
        displayables.add(disp);
    }

    public void removeDisplayable(Displayable disp) {
        displayables.remove(disp);
    }

    /**
     * Specify the scrollbars that control this view's positioning.
     *
     * @param   vertical - the vertical <code>Scrollbar</code>
     * @param   horizontal - the horizontal <code>Scrollbar</code>
     */
    public void setScrollPane (JScrollPane scrollPane) {
    	this.scrollpane = scrollPane;
    }
    
    /**
     * Draw the screen!
     */
    public synchronized void paintComponent(Graphics g) {
        // Limit our size to the viewport of the scroll pane.
        final Dimension size = scrollpane.getViewport().getSize();


        if (!this.isTileImagesLoaded()) {
            g.drawString(Messages.getString("BoardView1.loadingImages"), 20, 50); //$NON-NLS-1$
            if (!tileManager.isStarted()) {
                System.out.println("boardview1: loading images for board"); //$NON-NLS-1$
                tileManager.loadNeededImages(game);
            }
            //wait 1 second, then repaint
            repaint(1000);
            return;
        }

        // draw the board
        drawHexes(g);

        // draw wrecks
        if (GUIPreferences.getInstance().getShowWrecks()) {
            drawSprites(g,wreckSprites);
        }

        // Minefield signs all over the place!
        drawMinefields(g);
        
        // Artillery targets
        drawArtilleryHexes(g);

        // draw highlight border
        drawSprite(g,highlightSprite);

        // draw cursors
        drawSprite(g,cursorSprite);
        drawSprite(g,selectedSprite);
        drawSprite(g,firstLOSSprite);
        drawSprite(g,secondLOSSprite);

        // draw deployment indicators
        if (m_plDeployer != null) {
            drawDeployment(g);
        }

        // draw C3 links
        drawSprites(g,C3Sprites);

        // draw onscreen entities
        drawSprites(g,entitySprites);

        // draw moving onscreen entities
        drawSprites(g,movingEntitySprites);

        // draw ghost onscreen entities
        drawSprites(g,ghostEntitySprites);

        // draw onscreen attacks
        drawSprites(g,attackSprites);

        // draw movement, if valid
        drawSprites(g,pathSprites);

        // added by kenn
        // draw the ruler line
        if (rulerStart != null) {
            Point start =  getCentreHexLocation(rulerStart);
            if (rulerEnd != null) {
                Point end = getCentreHexLocation(rulerEnd);
                g.setColor(Color.yellow);
                g.drawLine(start.x, start.y, end.x, end.y);

                g.setColor(rulerEndColor);
                g.fillRect(end.x - 1, end.y - 1, 2, 2);
            }

            g.setColor(rulerStartColor);
            g.fillRect(start.x - 1, start.y - 1, 2, 2);
        }
        // end kenn

        // draw all the "displayables"
        for (int i = 0; i < displayables.size(); i++) {
            Displayable disp = displayables.get(i);
            disp.draw(g, size);
        }
    }

    /**
     * Updates the boardSize variable with the proper values for this board.
     */
    private void updateBoardSize() {
        int width = game.getBoard().getWidth() * (int)(HEX_WC*scale) + (int)(HEX_W/4*scale);
        int height = game.getBoard().getHeight() * (int)(HEX_H*scale) + (int)(HEX_H/2*scale);
        boardSize = new Dimension(width, height);
    }
    
    /**
     * Looks through a vector of buffered images and draws them if they're
     * onscreen.
     */
    private synchronized void drawSprites(Graphics g, ArrayList<? extends Sprite> spriteArrayList) {
        for (Sprite sprite : spriteArrayList) {
            drawSprite(g, sprite);
        }
    }

    /**
     * Draws a sprite, if it is in the current view
     */
    private final void drawSprite(Graphics g, Sprite sprite) {
        Rectangle view = g.getClipBounds();
        if (view.intersects(sprite.getBounds()) &&
            !sprite.hidden) {
            if (!sprite.isReady()) {
                sprite.prepare();
            }
            sprite.drawOnto(g, sprite.getBounds().x, sprite.getBounds().y, this);
        }
    }
    
    /**
     * Draw an outline around legal deployment hexes
     */
    private void drawDeployment(Graphics g) {
        Rectangle view = g.getClipBounds();
        // only update visible hexes
        int drawX = view.x / (int)(HEX_WC*scale) - 1;
        int drawY = view.y / (int)(HEX_H*scale) - 1;

        int drawWidth = view.width / (int)(HEX_WC*scale) + 3;
        int drawHeight = view.height / (int)(HEX_H*scale) + 3;
        IBoard board = game.getBoard();
        // loop through the hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                Point p = getHexLocation(c);
                if (board.isLegalDeployment(c, m_plDeployer)) {
                    g.setColor(Color.yellow);
                    int[] xcoords = { p.x + (int)(21*scale), p.x + (int)(62*scale), p.x + (int)(83*scale), p.x + (int)(83*scale),
                            p.x + (int)(62*scale), p.x + (int)(21*scale), p.x, p.x };
                    int[] ycoords = { p.y, p.y, p.y + (int)(35*scale), p.y + (int)(36*scale), p.y + (int)(71*scale),
                            p.y + (int)(71*scale), p.y + (int)(36*scale), p.y + (int)(35*scale) };
                    g.drawPolygon(xcoords, ycoords, 8);
                }
            }
        }
    }

    /**
     * returns the weapon selected in the mech display,
     * or null if none selected or it is not artillery
     * or null if the selected entity is not owned
     **/
    private Mounted getSelectedArtilleryWeapon() {
        if (selectedEntity == null || selectedWeapon == null) {
            return null;
        }

        if (!selectedEntity.getOwner().equals(localPlayer)) {
            return null; // Not my business to see this
        }
    
        if(selectedEntity.getEquipmentNum(selectedWeapon) == -1) {
            return null; //inconsistent state - weapon not on entity
        }

        if(!(selectedWeapon.getType() instanceof WeaponType && selectedWeapon.getType().hasFlag(WeaponType.F_ARTILLERY))) {
            return null; //not artillery
        }

        //otherwise, a weapon is selected, and it is artillery
        return selectedWeapon;
    }
    
    /** Display artillery modifier in pretargeted hexes
     */
    private void drawArtilleryHexes(Graphics g) {
        Mounted weapon = getSelectedArtilleryWeapon();
        Rectangle view = g.getClipBounds();
        
        if(game.getArtillerySize()==0 && weapon==null) {
            return; //nothing to do
        }
        
        int drawX = view.x / (int)(HEX_WC*scale) - 1;
        int drawY = view.y / (int)(HEX_H*scale) - 1;

        int drawWidth = view.width / (int)(HEX_WC*scale) + 3;
        int drawHeight = view.height / (int)(HEX_H*scale) + 3;

        IBoard board = game.getBoard();
        Image scaledImage;

        // loop through the hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                Point p = getHexLocation(c);

                if (!board.contains(c)){ continue; }

                if(weapon != null) {
                    //process targetted hexes
                    int amod = 0;
                    //Check the predesignated hexes
                    if(selectedEntity.getOwner().getArtyAutoHitHexes().contains(c)) {
                        amod = TargetRoll.AUTOMATIC_SUCCESS;
                    }
                    else {
                        amod = selectedEntity.aTracker.getModifier(weapon, c);
                    }

                    if(amod!=0) {

                        //draw the crosshairs
                        if(amod==TargetRoll.AUTOMATIC_SUCCESS) {
                            //predesignated or already hit
                            scaledImage = tileManager.getArtilleryTarget(TilesetManager.ARTILLERY_AUTOHIT);
                        } else {
                            scaledImage = tileManager.getArtilleryTarget(TilesetManager.ARTILLERY_ADJUSTED);
                        }

                        g.drawImage(scaledImage, p.x, p.y, this);
                    }
                }
                //process incoming attacks - requires server to update client's view of game
                
                for(Enumeration attacks=game.getArtilleryAttacks();attacks.hasMoreElements();) {
                    ArtilleryAttackAction a = (ArtilleryAttackAction)attacks.nextElement();

                    if(a.getWR().waa.getTarget(game).getPosition().equals(c)) {
                        scaledImage = tileManager.getArtilleryTarget(TilesetManager.ARTILLERY_INCOMING);
                        g.drawImage(scaledImage, p.x, p.y, this);
                        break; //do not draw multiple times, tooltop will show all attacks
                    }
                }
            }
        }
    }
    
    /*
        NOTENOTENOTE: (itmo)
        wouldnt this be simpler with two arrays. One with
        the strings {"BoardView1.thunderblaablaa","BoardView1.Conventi.."}
        one with the offsets {51,51,42} etc
        Preferably indexed by an enum:
        enum{
            Conventional,
            Thunder; 
        }
        
        or something? 
    */
    /**
     * Writes "MINEFIELD" in minefield hexes...
     */
    private void drawMinefields(Graphics g) {
        Rectangle view = g.getClipBounds();
        // only update visible hexes
        int drawX = view.x / (int)(HEX_WC*scale) - 1;
        int drawY = view.y / (int)(HEX_H*scale) - 1;

        int drawWidth = view.width / (int)(HEX_WC*scale) + 3;
        int drawHeight = view.height / (int)(HEX_H*scale) + 3;

        IBoard board = game.getBoard();
        // loop through the hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                Point p = getHexLocation(c);
                
                if (!board.contains(c)){ continue; }
                if (!game.containsMinefield(c)){ continue; }
                
                Minefield mf = game.getMinefields(c).get(0);
                
                Image tmpImage = tileManager.getMinefieldSign();
                g.drawImage(
                        tmpImage,
                        p.x + (int)(13*scale), 
                        p.y + (int)(13*scale), 
                        this);
                
                g.setColor(Color.black);
                int nbrMfs = game.getNbrMinefields(c);
                if (nbrMfs > 1) {
                    drawCenteredString(Messages.getString("BoardView1.Multiple"),  //$NON-NLS-1$
                                p.x,
                                p.y + (int)(51*scale),
                                font_minefield,
                                g);
                } else if (nbrMfs == 1) {
                    switch (mf.getType()) {
                        case (Minefield.TYPE_CONVENTIONAL) :
                            drawCenteredString(
                                    Messages.getString("BoardView1.Conventional"), //$NON-NLS-1$
                                    p.x,
                                    p.y + (int)(51*scale),
                                    font_minefield,
                                    g);
                            break;
                        case (Minefield.TYPE_THUNDER) :
                            drawCenteredString(
                                    Messages.getString("BoardView1.Thunder") + mf.getDamage() + ")",  //$NON-NLS-1$ //$NON-NLS-2$
                                    p.x, 
                                    p.y + (int)(51*scale),
                                    font_minefield,
                                    g);
                        break;
                        case (Minefield.TYPE_THUNDER_INFERNO) :
                            drawCenteredString(
                                    Messages.getString("BoardView1.Thunder-Inf") + mf.getDamage() + ")",  //$NON-NLS-1$ //$NON-NLS-2$
                                    p.x,
                                    p.y + (int)(51*scale),
                                    font_minefield,
                                    g);
                        break;
                        case (Minefield.TYPE_THUNDER_ACTIVE) :
                            drawCenteredString(
                                    Messages.getString("BoardView1.Thunder-Actv") + mf.getDamage() + ")",  //$NON-NLS-1$ //$NON-NLS-2$
                                    p.x,
                                    p.y + (int)(51*scale),
                                    font_minefield,
                                    g);
                        break;
                        case (Minefield.TYPE_COMMAND_DETONATED) :
                            drawCenteredString(
                                    Messages.getString("BoardView1.Command-"),  //$NON-NLS-1$
                                    p.x,
                                    p.y + (int)(51*scale),
                                    font_minefield,
                                    g);
                            drawCenteredString(
                                    Messages.getString("BoardView1.detonated"),  //$NON-NLS-1$
                                    p.x,
                                    p.y + (int)(60*scale),
                                    font_minefield,
                                    g);
                        break;
                        case (Minefield.TYPE_VIBRABOMB) :
                            drawCenteredString(
                                    Messages.getString("BoardView1.Vibrabomb"),  //$NON-NLS-1$
                                    p.x,
                                    p.y + (int)(51*scale),
                                    font_minefield,
                                    g);
                  if (mf.getPlayerId() == localPlayer.getId()) {
                      drawCenteredString(
                                    "(" + mf.getSetting() + ")",  //$NON-NLS-1$ //$NON-NLS-2$
                                        p.x,
                                        p.y + (int)(60*scale),
                                        font_minefield,
                                        g);
                  }
                        break;
                    }
                }
            }
        }
    }

    private void drawCenteredString( String string, int x, int y, Font font, Graphics graph ){
        FontMetrics currentMetrics = getFontMetrics(font);
        int stringWidth = currentMetrics.stringWidth(string);
        
        x += ((hex_size.width - stringWidth)/2);
        
        graph.setFont(font);
        graph.drawString( string, x, y );
    }
    
    /** 
     * This method creates an image the size of the entire board (all
     * mapsheets), draws the hexes onto it, and returns that image.
     */
    public Image getEntireBoardImage() {
        Image entireBoard = createImage(boardSize.width, boardSize.height);
        Graphics boardGraph = entireBoard.getGraphics();
        drawHexes(boardGraph);
        boardGraph.dispose();
        return entireBoard;
    }

    /**
     * Redraws all hexes in the specified rectangle
     */
    private void drawHexes(Graphics g) {
        Rectangle view = g.getClipBounds();
        // only update visible hexes
        int drawX = view.x / (int)(HEX_WC*scale) - 1;
        int drawY = view.y / (int)(HEX_H*scale) - 1;

        int drawWidth = view.width / (int)(HEX_WC*scale) + 3;
        int drawHeight = view.height / (int)(HEX_H*scale) + 3;

/*        // only draw what we came to draw
        boardGraph.setClip(rect.x - boardRect.x, rect.y - boardRect.y,
                           rect.width, rect.height);

        // clear, if we need to
        if (rect.x < (21*scale)) {
            g.clearRect(
                    rect.x - boardRect.x, rect.y - boardRect.y,
                    (int)(21*scale) - rect.x, rect.height);
        }
        if (rect.y < (36*scale)) {
            g.clearRect(
                    rect.x - boardRect.x, rect.y - boardRect.y,
                    rect.width, (int)(36*scale) - rect.y);
        }
        if (rect.x > boardSize.width - view.width - (21*scale)) {
            g.clearRect(
                    boardRect.width - (int)(21*scale), rect.y - boardRect.y,
                    (int)(21*scale), rect.height);
        }
        if (rect.y > boardSize.height - view.height - (int)(36*scale)) {
            g.clearRect(
                    rect.x - boardRect.x, boardRect.height - (int)(36*scale),
                    rect.width, (int)(36*scale));
        }
*/
        // draw some hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                drawHex(new Coords(j + drawX, i + drawY), g);
            }
        }
    }

    /**
     * Draws a hex onto the board buffer.  This assumes that boardRect is
     * current, and does not check if the hex is visible.
     */
    private void drawHex(Coords c, Graphics boardGraph) {
        if (!game.getBoard().contains(c)) {
            return;
        }

        final IHex hex = game.getBoard().getHex(c);
        final Point hexLoc = getHexLocation(c);

        // offset drawing point        
        int drawX = hexLoc.x; //- boardRect.x;
        int drawY = hexLoc.y; //- boardRect.y;

        // draw picture
        Image baseImage = tileManager.baseFor(hex);
        
        boardGraph.drawImage(baseImage, drawX, drawY, this);
        
        if (tileManager.supersFor(hex) != null) {
            for (Iterator i = tileManager.supersFor(hex).iterator(); i.hasNext();){
                boardGraph.drawImage((Image)i.next(), drawX, drawY, this);
            }
        }
        
        if(ecmHexes != null) {
            Integer tint = ecmHexes.get(c);
            if(tint != null) {
                boardGraph.drawImage(tileManager.getEcmShade(tint.intValue()), drawX, drawY, this);
            }
        }
        
        if(GUIPreferences.getInstance().getBoolean(GUIPreferences.ADVANCED_DARKEN_MAP_AT_NIGHT) && 
            game.getOptions().booleanOption("night_battle") &&
            !game.isPositionIlluminated(c)) {
            boardGraph.drawImage(tileManager.getNightFog(), drawX, drawY, this);
        }
        boardGraph.setColor(GUIPreferences.getInstance().getMapTextColor());
        
        // draw hex number
        if (scale >= 0.5){
            drawCenteredString(
                    c.getBoardNum(),
                    drawX,
                    drawY + (int)(12*scale),
                    font_hexnum,
                    boardGraph);
        }
        
        // draw elevation borders
        boardGraph.setColor(Color.black);
        if (drawElevationLine(c, 0)) {
            boardGraph.drawLine(drawX + (int)(21*scale), drawY, drawX + (int)(62*scale), drawY);
        }
        if (drawElevationLine(c, 1)) {
            boardGraph.drawLine(drawX + (int)(62*scale), drawY, drawX + (int)(83*scale), drawY + (int)(35*scale));
        }
        if (drawElevationLine(c, 2)) {
            boardGraph.drawLine(drawX + (int)(83*scale), drawY + (int)(36*scale), drawX + (int)(62*scale), drawY + (int)(71*scale));
        }
        if (drawElevationLine(c, 3)) {
            boardGraph.drawLine(drawX + (int)(62*scale), drawY + (int)(71*scale), drawX + (int)(21*scale), drawY + (int)(71*scale));
        }
        if (drawElevationLine(c, 4)) {
            boardGraph.drawLine(drawX + (int)(21*scale), drawY + (int)(71*scale), drawX, drawY + (int)(36*scale));
        }
        if (drawElevationLine(c, 5)) {
            boardGraph.drawLine(drawX, drawY + (int)(35*scale), drawX + (int)(21*scale), drawY);
        }

        // draw mapsheet borders
        if(GUIPreferences.getInstance().getShowMapsheets()) {
            boardGraph.setColor(GUIPreferences.getInstance().getColor(GUIPreferences.ADVANCED_MAPSHEET_COLOR));
            if(c.x % 16 == 0) {
                //left edge of sheet (edge 4 & 5)
                boardGraph.drawLine(drawX + (int)(21*scale), drawY + (int)(71*scale), drawX, drawY + (int)(36*scale));
                boardGraph.drawLine(drawX, drawY + (int)(35*scale), drawX + (int)(21*scale), drawY);
            }
            else if(c.x % 16 == 15) {
                //right edge of sheet (edge 1 & 2)
                boardGraph.drawLine(drawX + (int)(62*scale), drawY, drawX + (int)(83*scale), drawY + (int)(35*scale));
                boardGraph.drawLine(drawX + (int)(83*scale), drawY + (int)(36*scale), drawX + (int)(62*scale), drawY + (int)(71*scale));
            }
            if(c.y % 17 == 0) {
                //top edge of sheet (edge 0 and possible 1 & 5)
                boardGraph.drawLine(drawX + (int)(21*scale), drawY, drawX + (int)(62*scale), drawY);
                if(c.x % 2 == 0) {
                    boardGraph.drawLine(drawX + (int)(62*scale), drawY, drawX + (int)(83*scale), drawY + (int)(35*scale));
                    boardGraph.drawLine(drawX, drawY + (int)(35*scale), drawX + (int)(21*scale), drawY);
                }
            } else if (c.y % 17 == 16) {
                //bottom edge of sheet (edge 3 and possible 2 & 4)
                boardGraph.drawLine(drawX + (int)(62*scale), drawY + (int)(71*scale), drawX + (int)(21*scale), drawY + (int)(71*scale));
                if(c.x % 2 == 1) {
                    boardGraph.drawLine(drawX + (int)(83*scale), drawY + (int)(36*scale), drawX + (int)(62*scale), drawY + (int)(71*scale));
                    boardGraph.drawLine(drawX + (int)(21*scale), drawY + (int)(71*scale), drawX, drawY + (int)(36*scale));
                }
            }
            boardGraph.setColor(Color.black);
        }
    }

    /**
     * Returns true if an elevation line should be drawn between the starting
     * hex and the hex in the direction specified.  Results should be
     * transitive, that is, if a line is drawn in one direction, it should be
     * drawn in the opposite direction as well.
     */
    private final boolean drawElevationLine(Coords src, int direction) {
        final IHex srcHex = game.getBoard().getHex(src);
        final IHex destHex = game.getBoard().getHexInDir(src, direction);
        return destHex != null && srcHex.floor() != destHex.floor();
    }

    /**
     * Returns the absolute position of the upper-left hand corner
     * of the hex graphic
     */
    private Point getHexLocation(int x, int y) {
        return new Point(
                x * (int)(HEX_WC*scale),
                y * (int)(HEX_H*scale) + ((x & 1) == 1 ? (int)(HEX_H/2*scale) : 0));
    }
    private Point getHexLocation(Coords c) {
        return getHexLocation(c.x, c.y);
    }

    // added by kenn
    /**
     * Returns the absolute position of the centre
     * of the hex graphic
     */
    private Point getCentreHexLocation(int x, int y) {
        Point p = getHexLocation(x, y);
        p.x += (HEX_W/2*scale);
        p.y += (HEX_H/2*scale);
        return p;
    }
    private Point getCentreHexLocation(Coords c) {
        return getCentreHexLocation(c.x, c.y);
    }

    public void drawRuler(Coords s, Coords e, Color sc, Color ec) {
        rulerStart = s;
        rulerEnd = e;
        rulerStartColor = sc;
        rulerEndColor = ec;

        repaint();
    }
    // end kenn

    /**
     * Returns the coords at the specified point
     */
    Coords getCoordsAt(Point p) {
        final int x = (p.x ) / (int)(HEX_WC*scale);
        final int y = ((p.y ) - ((x & 1) == 1 ? (int)(HEX_H/2*scale) : 0)) / (int)(HEX_H*scale);
        return new Coords(x, y);
    }

    public void redrawMovingEntity(Entity entity, Coords position, int facing) {
        Integer entityId = new Integer( entity.getId() );
        EntitySprite sprite = entitySpriteIds.get( entityId );
        ArrayList<EntitySprite> newSprites;
        HashMap<Integer,EntitySprite> newSpriteIds;

        if (sprite != null) {
            newSprites = new ArrayList<EntitySprite>(entitySprites);
            newSpriteIds = new HashMap<Integer,EntitySprite>(entitySpriteIds);

            newSprites.remove(sprite);

            entitySprites = newSprites;
            entitySpriteIds = newSpriteIds;
        }

        MovingEntitySprite mSprite = movingEntitySpriteIds.get(entityId);
        ArrayList<MovingEntitySprite> newMovingSprites = new ArrayList<MovingEntitySprite>(movingEntitySprites);
        HashMap<Integer,MovingEntitySprite> newMovingSpriteIds = new HashMap<Integer,MovingEntitySprite>(movingEntitySpriteIds);


        if (mSprite != null) {
            newMovingSprites.remove(mSprite);
        }

        if (entity.getPosition() != null) {
            mSprite = new MovingEntitySprite(entity, position, facing);
            newMovingSprites.add(mSprite);
            newMovingSpriteIds.put( entityId, mSprite );
        }

        movingEntitySprites = newMovingSprites;
        movingEntitySpriteIds = newMovingSpriteIds;
    }

    public boolean isMovingUnits() {
        return movingUnits.size() > 0;
    }

    /**
     * Clears the sprite for an entity and prepares it to be re-drawn.
     *  Replaces the old sprite with the new!
     *
     *  Try to prevent annoying ConcurrentModificationExceptions
     */
    public void redrawEntity(Entity entity) {
        Integer entityId = new Integer( entity.getId() );
        EntitySprite sprite = entitySpriteIds.get( entityId );
        ArrayList<EntitySprite> newSprites = new ArrayList<EntitySprite>(entitySprites);
        HashMap<Integer,EntitySprite> newSpriteIds = new HashMap<Integer,EntitySprite>(entitySpriteIds);


        if (sprite != null) {
            newSprites.remove(sprite);
        }
        Coords position = entity.getPosition();
        if (position != null) {            
            sprite = new EntitySprite(entity);
            newSprites.add(sprite);
            newSpriteIds.put( entityId, sprite );
        }

        entitySprites = newSprites;
        entitySpriteIds = newSpriteIds;

        for (C3Sprite c3Sprite : C3Sprites) {
            if (c3Sprite.entityId == entity.getId()) {
                C3Sprites.remove(c3Sprite);
            }
        }

        if(entity.hasC3() || entity.hasC3i()) addC3Link(entity);

        scheduleRedraw();
    }

    /**
     * Clears all old entity sprites out of memory and sets up new ones.
     */
    private void redrawAllEntities() {
        ArrayList<EntitySprite> newSprites = new ArrayList<EntitySprite>(game.getNoOfEntities());
        HashMap<Integer,EntitySprite> newSpriteIds = new HashMap<Integer,EntitySprite>(game.getNoOfEntities());
        ArrayList<WreckSprite> newWrecks = new ArrayList<WreckSprite>();

        Enumeration e = game.getWreckedEntities();
        while (e.hasMoreElements()) {
            Entity entity = (Entity) e.nextElement();
            if (!(entity instanceof Infantry) && (entity.getPosition() != null)) {
                WreckSprite ws = new WreckSprite(entity);
                newWrecks.add(ws);
            }
        }

        clearC3Networks();
        for (Enumeration i = game.getEntities(); i.hasMoreElements();) {
            final Entity entity = (Entity)i.nextElement();
            if (entity.getPosition() == null) continue;

            EntitySprite sprite = new EntitySprite(entity);
            newSprites.add(sprite);
            newSpriteIds.put(new Integer(entity.getId()), sprite);

            if(entity.hasC3() || entity.hasC3i()) addC3Link(entity);
        }

        entitySprites = newSprites;
        entitySpriteIds = newSpriteIds;
        wreckSprites = newWrecks;

        scheduleRedraw();
    }

    public void centerOnHex(Coords c) {
        if ( null == c ) return;
        Point hexPoint = getHexLocation(c);
        Rectangle rect = new Rectangle(hexPoint.x, hexPoint.y, HEX_W, HEX_H);
        scrollpane.getViewport().scrollRectToVisible(rect);
        repaint();
    }

    /**
     * Clears the old movement data and draws the new.  Since it's less
     * expensive to check for and reuse old step sprites than to make a whole
     * new one, we do that.
     */
    public void drawMovementData(MovePath md) {
        ArrayList<StepSprite> temp = pathSprites;
        MoveStep previousStep = null;

        clearMovementData();

        for (Enumeration i = md.getSteps(); i.hasMoreElements();) {
            final MoveStep step = (MoveStep)i.nextElement();
            // check old movement path for reusable step sprites
            boolean found = false;
            for (Iterator<StepSprite> j = temp.iterator(); j.hasNext();) {
                final StepSprite sprite = j.next();
                if (sprite.getStep().canReuseSprite(step)) {
                    pathSprites.add(sprite);
                    found = true;
                }
            }
            if (!found) {
                if (previousStep != null &&
                    (step.getType() == MovePath.STEP_UP ||
                     step.getType() == MovePath.STEP_DOWN) &&
                    (previousStep.getType() == MovePath.STEP_UP ||
                     previousStep.getType() == MovePath.STEP_DOWN)) {
                    //Mark the previous elevation change sprite hidden
                    // so that we can draw a new one in it's place without
                    // having overlap.
                    pathSprites.get(pathSprites.size() -1 ).hidden = true;
                }
                pathSprites.add(new StepSprite(step));
            }
            previousStep = step;
        }
    }

    /**
     * Clears current movement data from the screen
     */
    public void clearMovementData() {
        pathSprites = new ArrayList<StepSprite>();
        repaint();
    }

    public void setLocalPlayer(Player p) {
        localPlayer = p;
    }

    public Player getLocalPlayer() {
        return localPlayer;
    }

    /**
     * Specifies that this should mark the deployment hexes for a player.  If
     * the player is set to null, no hexes will be marked.
     */
    public void markDeploymentHexesFor(Player p)
    {
        m_plDeployer = p;
    }

    /**
     * Adds a c3 line to the sprite list.
     */
    public void addC3Link(Entity e) {
        if (e.getPosition() == null) return;

        if(e.hasC3i()) {
            for (java.util.Enumeration i = game.getEntities(); i.hasMoreElements();) {
                final Entity fe = (Entity)i.nextElement();
                if (fe.getPosition() == null) return;
                if ( e.onSameC3NetworkAs(fe)) {
                    C3Sprites.add(new C3Sprite(e, fe));
                }
            }
        }
        else if(e.getC3Master() != null) {
            Entity eMaster = e.getC3Master();
            if (eMaster.getPosition() == null) return;

            // ECM cuts off the network
            if (!Compute.isAffectedByECM(e, e.getPosition(), eMaster.getPosition())
                &&!Compute.isAffectedByECM(eMaster, eMaster.getPosition(), eMaster.getPosition())) {
                C3Sprites.add(new C3Sprite(e, e.getC3Master()));
            }
        }
    }

    /**
     * Adds an attack to the sprite list.
     */
    public synchronized void addAttack(AttackAction aa) {
        // do not make a sprite unless we're aware of both entities
        // this is not a great solution but better than a crash
        Entity ae = game.getEntity(aa.getEntityId());
        Targetable t = game.getTarget(aa.getTargetType(), aa.getTargetId());
        if (ae == null || t == null 
                || t.getTargetType() == Targetable.TYPE_INARC_POD 
                || t.getPosition() == null
                || ae.getPosition() == null) {
            return;
        }

        for (final Iterator<AttackSprite> i = attackSprites.iterator(); i.hasNext();) {
            final AttackSprite sprite = i.next();

            // can we just add this attack to an existing one?
            if (sprite.getEntityId() == aa.getEntityId()
                && sprite.getTargetId() == aa.getTargetId()) {
                // use existing attack, but add this weapon
                if (aa instanceof WeaponAttackAction) {
                    WeaponAttackAction waa = (WeaponAttackAction)aa;
                    if ( aa.getTargetType() != Targetable.TYPE_HEX_ARTILLERY) {
                        sprite.addWeapon(waa);
                    } else if ( waa.getEntity(game).getOwner().getId() == localPlayer.getId()) {
                        sprite.addWeapon(waa);
                    }
                }
                if (aa instanceof KickAttackAction) {
                    sprite.addWeapon((KickAttackAction)aa);
                }
                if (aa instanceof PunchAttackAction) {
                    sprite.addWeapon((PunchAttackAction)aa);
                }
                if (aa instanceof PushAttackAction) {
                    sprite.addWeapon((PushAttackAction)aa);
                }
                if (aa instanceof ClubAttackAction) {
                    sprite.addWeapon((ClubAttackAction)aa);
                }
                if (aa instanceof ChargeAttackAction) {
                    sprite.addWeapon((ChargeAttackAction)aa);
                }
                if (aa instanceof DfaAttackAction) {
                    sprite.addWeapon((DfaAttackAction)aa);
                }
                if (aa instanceof ProtomechPhysicalAttackAction) {
                    sprite.addWeapon((ProtomechPhysicalAttackAction)aa);
                }
                if (aa instanceof SearchlightAttackAction) {
                    sprite.addWeapon((SearchlightAttackAction)aa);
                }
                return;
            }
        }
        // no re-use possible, add a new one
        // don't add a sprite for an artillery attack made by the other player
        if (aa instanceof WeaponAttackAction) {
            WeaponAttackAction waa = (WeaponAttackAction)aa;
            if ( aa.getTargetType() != Targetable.TYPE_HEX_ARTILLERY) {
                attackSprites.add(new AttackSprite(aa));
            } else if ( waa.getEntity(game).getOwner().getId() == localPlayer.getId()) {
                attackSprites.add(new AttackSprite(aa));
            }
        } else {
            attackSprites.add(new AttackSprite(aa));
        }
    }

    /** Removes all attack sprites from a certain entity */
    public synchronized void removeAttacksFor(int entityId) {
        for (Iterator<AttackSprite> i = attackSprites.iterator(); i.hasNext();) {
            AttackSprite sprite = i.next();
            if (sprite.getEntityId() == entityId) {
                i.remove();
            }
        }
    }

    /**
     * Clears out all attacks and re-adds the ones in the current game.
     */
    public void refreshAttacks() {
        clearAllAttacks();
        for (Enumeration i = game.getActions(); i.hasMoreElements();) {
            EntityAction ea = (EntityAction)i.nextElement();
            if (ea instanceof AttackAction) {
                addAttack((AttackAction)ea);
            }
        }
        for (Enumeration i = game.getCharges(); i.hasMoreElements();) {
               EntityAction ea = (EntityAction)i.nextElement();
            if (ea instanceof PhysicalAttackAction) {
                addAttack((AttackAction)ea);
            }
        }
    }

    public void clearC3Networks() {
        C3Sprites.clear();
    }

    /**
     * Clears out all attacks that were being drawn
     */
    public void clearAllAttacks() {
        attackSprites.clear();
    }

    protected void secondLOSHex(Coords c2, Coords c1) {
        if (useLOSTool) {
            
            boolean mechInFirst = GUIPreferences.getInstance().getMechInFirst();
            boolean mechInSecond = GUIPreferences.getInstance().getMechInSecond();
            
            LosEffects.AttackInfo ai = new LosEffects.AttackInfo();
            ai.attackPos = c1;
            ai.targetPos = c2;
            ai.attackHeight = mechInFirst?1:0;
            ai.targetHeight = mechInSecond?1:0;
            ai.attackAbsHeight = game.getBoard().getHex(c1).floor() + ai.attackHeight;
            ai.targetAbsHeight = game.getBoard().getHex(c2).floor() + ai.targetHeight;

            LosEffects le = LosEffects.calculateLos(game, ai);
            StringBuffer message = new StringBuffer();
            message.append(Messages.getString("BoardView1.Attacker", new Object[]{ //$NON-NLS-1$
                    mechInFirst ? Messages.getString("BoardView1.Mech") : Messages.getString("BoardView1.NonMech"), //$NON-NLS-1$ //$NON-NLS-2$
                    c1.getBoardNum()}));
            message.append(Messages.getString("BoardView1.Target", new Object[]{ //$NON-NLS-1$
                    mechInSecond ? Messages.getString("BoardView1.Mech") : Messages.getString("BoardView1.NonMech"), //$NON-NLS-1$ //$NON-NLS-2$
                    c2.getBoardNum()}));
            if (le.isBlocked()) {
                message.append(Messages.getString("BoardView1.LOSBlocked", new Object[]{ //$NON-NLS-1$
                    new Integer(c1.distance(c2))}));
            } else {
                message.append(Messages.getString("BoardView1.LOSNotBlocked", new Object[]{ //$NON-NLS-1$
                        new Integer(c1.distance(c2))}));
                if (le.getHeavyWoods() > 0) {
                    message.append(Messages.getString("BoardView1.HeavyWoods", new Object[]{ //$NON-NLS-1$
                            new Integer(le.getHeavyWoods())}));
                }
                if (le.getLightWoods() > 0) {
                    message.append(Messages.getString("BoardView1.LightWoods", new Object[]{ //$NON-NLS-1$
                            new Integer(le.getLightWoods())}));
                }
                if (le.getLightSmoke() > 0) {
                    message.append(Messages.getString("BoardView1.LightSmoke", new Object[]{ //$NON-NLS-1$
                            new Integer(le.getLightSmoke())}));
                }
                if (le.getHeavySmoke() > 0) {
                    if (game.getOptions().booleanOption("maxtech_fire")) { //$NON-NLS-1$
                        message.append(Messages.getString("BoardView1.HeavySmoke", new Object[]{ //$NON-NLS-1$
                                new Integer(le.getHeavySmoke())}));
                    }
                    else {
                        message.append(Messages.getString("BoardView1.Smoke", new Object[]{ //$NON-NLS-1$
                                new Integer(le.getHeavySmoke())}));
                    }
                }
                if (le.isTargetCover()) {
                    message.append(Messages.getString("BoardView1.TargetPartialCover")); //$NON-NLS-1$
                }
                if (le.isAttackerCover()) {
                    message.append(Messages.getString("BoardView1.AttackerPartialCover")); //$NON-NLS-1$
                }
            }
            JOptionPane.showMessageDialog(this.getRootPane(), message.toString(), Messages.getString("BoardView1.LOSTitle"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Initializes the various overlay polygons with their
     * vertices.
     */
    public void initPolys() {
        // hex polygon
        hexPoly = new Polygon();
        hexPoly.addPoint(21, 0);
        hexPoly.addPoint(62, 0);
        hexPoly.addPoint(83, 35);
        hexPoly.addPoint(83, 36);
        hexPoly.addPoint(62, 71);
        hexPoly.addPoint(21, 71);
        hexPoly.addPoint(0, 36);
        hexPoly.addPoint(0, 35);

        // facing polygons
        facingPolys = new Polygon[6];
        facingPolys[0] = new Polygon();
        facingPolys[0].addPoint(41, 3);
        facingPolys[0].addPoint(38, 6);
        facingPolys[0].addPoint(45, 6);
        facingPolys[0].addPoint(42, 3);
        facingPolys[1] = new Polygon();
        facingPolys[1].addPoint(69, 17);
        facingPolys[1].addPoint(64, 17);
        facingPolys[1].addPoint(68, 23);
        facingPolys[1].addPoint(70, 19);
        facingPolys[2] = new Polygon();
        facingPolys[2].addPoint(69, 53);
        facingPolys[2].addPoint(68, 49);
        facingPolys[2].addPoint(64, 55);
        facingPolys[2].addPoint(68, 54);
        facingPolys[3] = new Polygon();
        facingPolys[3].addPoint(41, 68);
        facingPolys[3].addPoint(38, 65);
        facingPolys[3].addPoint(45, 65);
        facingPolys[3].addPoint(42, 68);
        facingPolys[4] = new Polygon();
        facingPolys[4].addPoint(15, 53);
        facingPolys[4].addPoint(18, 54);
        facingPolys[4].addPoint(15, 48);
        facingPolys[4].addPoint(14, 52);
        facingPolys[5] = new Polygon();
        facingPolys[5].addPoint(13, 19);
        facingPolys[5].addPoint(15, 23);
        facingPolys[5].addPoint(19, 17);
        facingPolys[5].addPoint(17, 17);

        // movement polygons
        movementPolys = new Polygon[8];
        movementPolys[0] = new Polygon();
        movementPolys[0].addPoint(41, 65);
        movementPolys[0].addPoint(38, 68);
        movementPolys[0].addPoint(45, 68);
        movementPolys[0].addPoint(42, 65);
        movementPolys[1] = new Polygon();
        movementPolys[1].addPoint(17, 48);
        movementPolys[1].addPoint(12, 48);
        movementPolys[1].addPoint(16, 54);
        movementPolys[1].addPoint(17, 49);
        movementPolys[2] = new Polygon();
        movementPolys[2].addPoint(18, 19);
        movementPolys[2].addPoint(17, 15);
        movementPolys[2].addPoint(13, 21);
        movementPolys[2].addPoint(17, 20);
        movementPolys[3] = new Polygon();
        movementPolys[3].addPoint(41, 6);
        movementPolys[3].addPoint(38, 3);
        movementPolys[3].addPoint(45, 3);
        movementPolys[3].addPoint(42, 6);
        movementPolys[4] = new Polygon();
        movementPolys[4].addPoint(67, 15);
        movementPolys[4].addPoint(66, 19);
        movementPolys[4].addPoint(67, 20);
        movementPolys[4].addPoint(71, 20);
        movementPolys[5] = new Polygon();
        movementPolys[5].addPoint(69, 55);
        movementPolys[5].addPoint(66, 50);
        movementPolys[5].addPoint(67, 49);
        movementPolys[5].addPoint(72, 48);

        movementPolys[6] = new Polygon(); // up arrow with tail
        movementPolys[6].addPoint(35, 44);
        movementPolys[6].addPoint(30, 49);
        movementPolys[6].addPoint(33, 49);
        movementPolys[6].addPoint(33, 53);
        movementPolys[6].addPoint(38, 53);
        movementPolys[6].addPoint(38, 49);
        movementPolys[6].addPoint(41, 49);
        movementPolys[6].addPoint(36, 44);
        movementPolys[7] = new Polygon(); // down arrow with tail
        movementPolys[7].addPoint(34, 53);
        movementPolys[7].addPoint(29, 48);
        movementPolys[7].addPoint(32, 48);
        movementPolys[7].addPoint(32, 44);
        movementPolys[7].addPoint(37, 44);
        movementPolys[7].addPoint(37, 48);
        movementPolys[7].addPoint(40, 48);
        movementPolys[7].addPoint(35, 53);
    }

    private synchronized boolean doMoveUnits(long idleTime) {
        boolean movingSomething = false;

        if (movingUnits.size() > 0) {

            moveWait += idleTime;

            if (moveWait > GUIPreferences.getInstance().getInt("AdvancedMoveStepDelay")) {

                ArrayList<MovingUnit> spent = new ArrayList<MovingUnit>();

                for (MovingUnit move : movingUnits) {
                    movingSomething = true;
                    Entity ge = game.getEntity(move.entity.getId()); 
                    if (move.path.size() > 0) {
                        UnitLocation loc = move.path.get(0);
                        if (ge != null) {
                            redrawMovingEntity( move.entity,
                                                loc.getCoords(),
                                                loc.getFacing() );
                        }
                        move.path.remove(0);
                    } else {
                        if (ge != null) {
                            redrawEntity(ge);
                        }
                        spent.add(move);
                    }

                }

                for (MovingUnit move : spent) {
                    movingUnits.remove(move);
                }
                moveWait = 0;

                if (movingUnits.size() == 0) {
                    movingEntitySpriteIds.clear();
                    movingEntitySprites.clear();
                    ghostEntitySprites.clear();
                    processBoardViewEvent(new BoardViewEvent(this, BoardViewEvent.FINISHED_MOVING_UNITS));
                }
            }
        }
        return movingSomething;
    }

    //
    // KeyEventDispatcher
    //
    public boolean dispatchKeyEvent(KeyEvent ke) {
        JScrollBar vbar = scrollpane.getVerticalScrollBar();
        JScrollBar hbar = scrollpane.getHorizontalScrollBar();
        switch(ke.getKeyCode()) {
        case KeyEvent.VK_NUMPAD7 :
            hbar.setValue((int)(hbar.getValue() - HEX_W*scale));
            vbar.setValue((int)(vbar.getValue() - HEX_H*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD8 :
        case KeyEvent.VK_UP :
            vbar.setValue((int)(vbar.getValue() - HEX_H*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD9 :
            hbar.setValue((int)(hbar.getValue() + HEX_W*scale));
            vbar.setValue((int)(vbar.getValue() - HEX_H*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD1 :
            hbar.setValue((int)(hbar.getValue() - HEX_W*scale));
            vbar.setValue((int)(vbar.getValue() + HEX_H*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD2 :
        case KeyEvent.VK_DOWN :
            vbar.setValue((int)(vbar.getValue() + HEX_H*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD3 :
            hbar.setValue((int)(hbar.getValue() + HEX_W*scale));
            vbar.setValue((int)(vbar.getValue() + HEX_H*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD4 :
        case KeyEvent.VK_LEFT :
            hbar.setValue((int)(hbar.getValue() - HEX_W*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD6 :
        case KeyEvent.VK_RIGHT :
            hbar.setValue((int)(hbar.getValue() + HEX_W*scale));
            ke.consume();
            break;
        case KeyEvent.VK_NUMPAD5 :
            // center on the selected entity
            centerOnHex(selectedEntity.getPosition());
            ke.consume();
            break;
        }

        repaint();
        return false;
    }

    //
    // MouseListener
    //
    public void mousePressed(MouseEvent me) {

        Point point = me.getPoint();
        if ( null == point ) {
            return;
        }


        // Disable scrolling when ctrl or alt is held down, since this
        //  means the user wants to use the LOS/ruler tools.
        int mask = InputEvent.CTRL_MASK | InputEvent.ALT_MASK;
        if ( !GUIPreferences.getInstance().getRightDragScroll() &&
            !GUIPreferences.getInstance().getAlwaysRightClickScroll() &&    
            game.getPhase() == IGame.PHASE_FIRING ) {
            // In the firing phase, also disable scrolling if
            // the right or middle buttons are clicked, since
            // this means the user wants to activate the
            // popup menu or ruler tool.
            mask |= InputEvent.BUTTON2_MASK | InputEvent.BUTTON3_MASK;
        }

        // disable auto--edge-scrolling if no option set
        if ( !GUIPreferences.getInstance().getAutoEdgeScroll() ) {
            mask |= InputEvent.BUTTON1_MASK;
        }
        // disable edge-scrolling if no option set
        if ( !GUIPreferences.getInstance().getClickEdgeScroll() ) {
            mask |= InputEvent.BUTTON3_MASK;
        }
        
        if ( GUIPreferences.getInstance().getRightDragScroll() ) {
            mask |= InputEvent.BUTTON2_MASK;
        }

        mouseAction(getCoordsAt(point), BOARD_HEX_DRAG, me.getModifiers());
    }

    public void mouseReleased(MouseEvent me) {
        for (int i = 0; i < displayables.size(); i++) {
            Displayable disp = displayables.get(i);
            if (disp.isReleased()) {
                return;
            }
        }

        //Unless the user has auto-scroll on and is using the left
        //mouse button, no click action should be triggered if the map
        //is being scrolled.
        /*if (scrolled && (me.getModifiers() & InputEvent.BUTTON1_MASK) == 0 || !GUIPreferences.getInstance().getAutoEdgeScroll())
            return;*/
        if (me.getClickCount() == 1) {
            mouseAction(getCoordsAt(me.getPoint()), BOARD_HEX_CLICK, me.getModifiers());
        } else {
            mouseAction(getCoordsAt(me.getPoint()), BOARD_HEX_DOUBLECLICK, me.getModifiers());
        }
    }

    public void mouseEntered(MouseEvent me) {
    }
    public void mouseExited(MouseEvent me) {
    }
    public void mouseClicked(MouseEvent me) {
    }
    
    private class MovingUnit {
        public Entity entity;
        public ArrayList<UnitLocation> path;
        MovingUnit(Entity entity, Vector<UnitLocation> path) {
            this.entity = entity;
            this.path = new ArrayList<UnitLocation>(path);
        }
    }

    /**
     * Everything in the main map view is either the board or it's a sprite
     * displayed on top of the board.  Most sprites store a transparent image
     * which they draw onto the screen when told to.  Sprites keep a bounds
     * rectangle, so it's easy to tell when they'return onscreen.
     */
    private abstract class Sprite implements ImageObserver
    {
        protected Rectangle bounds;
        protected Image image;

        //Set this to true if you don't want the sprite to be drawn.
        protected boolean hidden = false;

        /**
         * Do any necessary preparation.  This is called after creation,
         * but before drawing, when a device context is ready to draw with.
         */
        public abstract void prepare();

        /**
         * When we draw our buffered images, it's necessary to implement
         * the ImageObserver interface.  This provides the necesasry
         * functionality.
         */
        public boolean imageUpdate(Image image, int infoflags, int x, int y,
                                   int width, int height) {
            if (infoflags == ImageObserver.ALLBITS) {
                prepare();
                repaint();
                return false;
            }
            return true;
        }

        /**
         * Returns our bounding rectangle.  The coordinates here are stored
         * with the top left corner of the _board_ being 0, 0, so these do
         * not always correspond to screen coordinates.
         */
        public Rectangle getBounds() {
            return bounds;
        }

        /**
         * Are we ready to draw?  By default, checks to see that our buffered
         * image has been created.
         */
        public boolean isReady() {
            return image != null;
        }

        /**
         * Draws this sprite onto the specified graphics context.
         */
        public void drawOnto(Graphics g, int x, int y, ImageObserver observer) {
            drawOnto(g, x, y, observer, false);
        }

        public void drawOnto(Graphics g, int x, int y, ImageObserver observer, boolean makeTranslucent) {
            if (isReady()) {
                if (makeTranslucent) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                    g2.drawImage(image, x, y, observer);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                } else {
                    g.drawImage(image, x, y, observer);
                }
            } else {
                // grrr... we'll be ready next time!
                prepare();
            }
        }

        /**
         * Returns true if the point is inside this sprite.  Uses board
         * coordinates, not screen coordinates.   By default, just checks our
         * bounding rectangle, though some sprites override this for a smaller
         * sensitive area.
         */
        public boolean isInside(Point point) {
            return bounds.contains(point);
        }

        /**
         * Since most sprites being drawn correspond to something in the game,
         * this returns a little info for a tooltip.
         */
        public String[] getTooltip() {
            return null;
        }
    }

    /**
     * Sprite for a cursor.  Just a hexagon outline in a specified color.
     */
    private class CursorSprite extends Sprite
    {
        private Color color;
        private Coords hexLoc;
        
        public CursorSprite(Color color) {
            this.color = color;
            this.bounds = new Rectangle(hexPoly.getBounds().width + 1,
                    hexPoly.getBounds().height + 1);
            this.image = null;

            // start offscreen
            setOffScreen();
        }

        public void prepare() {
            // create image for buffer
            Image tempImage = createImage(bounds.width, bounds.height);
            Graphics graph = tempImage.getGraphics();

            // fill with key color
            graph.setColor(new Color(TRANSPARENT));
            graph.fillRect(0, 0, bounds.width, bounds.height);
            // draw attack poly
            graph.setColor(color);
            graph.drawPolygon(hexPoly);

            // create final image
            image = createImage(new FilteredImageSource(tempImage.getSource(),
                    new KeyAlphaFilter(TRANSPARENT)));
            graph.dispose();
            tempImage.flush();
        }

        public void setOffScreen(){
            bounds.setLocation(-100, -100);
            hexLoc = new Coords(-2, -2);
        }
        
        public void setHexLocation( Coords hexLoc ){
            this.hexLoc = hexLoc;
            bounds.setLocation(getHexLocation(hexLoc));
        }
        
        public Rectangle getBounds(){
            this.bounds = new Rectangle(hexPoly.getBounds().width + 1,
                    hexPoly.getBounds().height + 1);
            bounds.setLocation(getHexLocation( hexLoc ));
            
            return bounds;
        }
    }

    
    private class GhostEntitySprite extends Sprite {
        private Entity entity;
        private Rectangle modelRect;

        public GhostEntitySprite(Entity entity) {
            this.entity = entity;

            String shortName = entity.getShortName();
            Font font = new Font("SansSerif", Font.PLAIN, 10); //$NON-NLS-1$
            modelRect = new Rectangle(47, 55,
                            getFontMetrics(font).stringWidth(shortName) + 1,
                            getFontMetrics(font).getAscent());
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));

            this.bounds = tempBounds;
            this.image = null;
        }

        /**
         * Creates the sprite for this entity.  It is an extra pain to
         * create transparent images in AWT.
         */
        public void prepare() {
            // create image for buffer
            Image tempImage;
            Graphics graph;
            try {
                tempImage = createImage(bounds.width, bounds.height);
                graph = tempImage.getGraphics();
            } catch (NullPointerException ex) {
                // argh!  but I want it!
                return;
            }

            // fill with key color
            graph.setColor(new Color(TRANSPARENT));
            graph.fillRect(0, 0, bounds.width, bounds.height);

            // draw entity image
            graph.drawImage(tileManager.imageFor(entity), 0, 0, this);

            // create final image
                image = createImage(new FilteredImageSource(tempImage.getSource(),
                        new KeyAlphaFilter(TRANSPARENT)));
            graph.dispose();
            tempImage.flush();
        }

        public Rectangle getBounds(){
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));
            this.bounds = tempBounds;
        
            return bounds;
        }
        
        public void drawOnto(Graphics g, int x, int y, ImageObserver observer) {
            drawOnto(g, x, y, observer, true);
        }

    }

    private class MovingEntitySprite extends Sprite {
        private int facing;
        private Entity entity;
        private Rectangle modelRect;

        public MovingEntitySprite(Entity entity, Coords position, int facing) {
            this.entity = entity;
            this.facing = facing;

            String shortName = entity.getShortName();
            Font font = new Font("SansSerif", Font.PLAIN, 10); //$NON-NLS-1$
            modelRect = new Rectangle(47, 55,
                            getFontMetrics(font).stringWidth(shortName) + 1,
                            getFontMetrics(font).getAscent());
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(position));

            this.bounds = tempBounds;
            this.image = null;
        }
        
        /**
         * Creates the sprite for this entity.  It is an extra pain to
         * create transparent images in AWT.
         */
        public void prepare() {
            // create image for buffer
            Image tempImage;
            Graphics graph;
            try {
                tempImage = createImage(bounds.width, bounds.height);
                graph = tempImage.getGraphics();
            } catch (NullPointerException ex) {
                // argh!  but I want it!
                return;
            }

            // fill with key color
            graph.setColor(new Color(TRANSPARENT));
            graph.fillRect(0, 0, bounds.width, bounds.height);

            // draw entity image
            graph.drawImage(tileManager.imageFor(entity, facing), 0, 0, this);

            // create final image
            image = createImage(new FilteredImageSource(tempImage.getSource(),
                    new KeyAlphaFilter(TRANSPARENT)));
            graph.dispose();
            tempImage.flush();
        }
    }


    /**
     * Sprite for an wreck.  Consists
     * of an image, drawn from the Tile Manager and an identification label.
     */
    private class WreckSprite extends Sprite
    {
        private Entity entity;
        private Rectangle modelRect;

        public WreckSprite(Entity entity) {
            this.entity = entity;

            String shortName = entity.getShortName();
            
            Font font = new Font("SansSerif", Font.PLAIN, 10); //$NON-NLS-1$
            modelRect = new Rectangle(47, 55,
                                    getFontMetrics(font).stringWidth(shortName) + 1,
                                    getFontMetrics(font).getAscent());
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));

            this.bounds = tempBounds;
            this.image = null;
        }

        public Rectangle getBounds(){
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));
            this.bounds = tempBounds;
                    
            return bounds;
        }
        
        /**
         * Creates the sprite for this entity.  It is an extra pain to
         * create transparent images in AWT.
         */
        public void prepare() {
            // figure out size
            String shortName = entity.getShortName();
            Font font = new Font("SansSerif", Font.PLAIN, 10); //$NON-NLS-1$
            Rectangle tempRect =
                new Rectangle(47, 55,
                              getFontMetrics(font).stringWidth(shortName) + 1,
                              getFontMetrics(font).getAscent());

            // create image for buffer
            Image tempImage;
            Graphics graph;
            try {
                tempImage = createImage(bounds.width, bounds.height);
                graph = tempImage.getGraphics();
            } catch (NullPointerException ex) {
                // argh!  but I want it!
                return;
            }

            // fill with key color
            graph.setColor(new Color(TRANSPARENT));
            graph.fillRect(0, 0, bounds.width, bounds.height);

            // Draw wreck image,if we've got one.
            Image wreck = tileManager.wreckMarkerFor(entity);
            if ( null != wreck ) {
                graph.drawImage( wreck, 0, 0, this );
            }

            // draw box with shortName
            Color text = Color.lightGray;
            Color bkgd = Color.darkGray;
            Color bord = Color.black;

            graph.setFont(font);
            graph.setColor(bord);
            graph.fillRect(tempRect.x, tempRect.y,
                           tempRect.width, tempRect.height);
            tempRect.translate(-1, -1);
            graph.setColor(bkgd);
            graph.fillRect(tempRect.x, tempRect.y,
                           tempRect.width, tempRect.height);
            graph.setColor(text);
            graph.drawString(shortName, tempRect.x + 1,
                             tempRect.y + tempRect.height - 1);

            // create final image
            image = createImage(new FilteredImageSource(tempImage.getSource(),
                    new KeyAlphaFilter(TRANSPARENT)));
            graph.dispose();
            tempImage.flush();
        }

        /**
         * Overrides to provide for a smaller sensitive area.
         */
        public boolean isInside(Point point) {
            return false;
        }

    }
    /**
     * Sprite for an entity.  Changes whenever the entity changes.  Consists
     * of an image, drawn from the Tile Manager; facing and possibly secondary
     * facing arrows; armor and internal bars; and an identification label.
     */
    private class EntitySprite extends Sprite
    {
        private Entity entity;
        private Rectangle entityRect;
        private Rectangle modelRect;

        public EntitySprite(Entity entity) {
            this.entity = entity;

            String shortName = entity.getShortName();
            
            if (entity.getMovementMode() == IEntityMovementMode.VTOL) {
                shortName = shortName.concat(" (FL: ").concat(Integer.toString(entity.getElevation())).concat(")");
            }
            int face = entity.isCommander()?Font.ITALIC:Font.PLAIN;
            Font font = new Font("SansSerif", face, 10); //$NON-NLS-1$
            modelRect = new Rectangle(47, 55,
                                        getFontMetrics(font).stringWidth(shortName) + 1,
                                        getFontMetrics(font).getAscent());
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));

            this.bounds = tempBounds;
            this.entityRect = new Rectangle(bounds.x + (int)(20*scale),
                                            bounds.y + (int)(14*scale),
                                            (int)(44*scale),
                                            (int)(44*scale));
            this.image = null;
        }

        public Rectangle getBounds(){
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));
            this.bounds = tempBounds;
        
            this.entityRect = new Rectangle(
                    bounds.x + (int)(20*scale),
                    bounds.y + (int)(14*scale),
                    (int)(44*scale),
                    (int)(44*scale));
            
            return bounds;
        }
        
        public void drawOnto(Graphics g, int x, int y, ImageObserver observer) {
            if (trackThisEntitiesVisibilityInfo(this.entity)
                && !this.entity.isVisibleToEnemy()
                && GUIPreferences.getInstance().getBoolean(GUIPreferences.ADVANCED_TRANSLUCENT_HIDDEN_UNITS)) {
                // create final image with translucency
                drawOnto(g, x, y, observer, true);
            } else {
                drawOnto(g, x, y, observer, false);
            }
        }

        /**
         * Creates the sprite for this entity.  It is an extra pain to
         * create transparent images in AWT.
         */
        public void prepare() {
            // figure out size
            String shortName = entity.getShortName();
            if (entity.getMovementMode() == IEntityMovementMode.VTOL) {
                shortName = shortName.concat(" (FL: ").concat(Integer.toString(entity.getElevation())).concat(")");
            }
            if (PreferenceManager.getClientPreferences().getShowUnitId()) {
                shortName+=(Messages.getString("BoardView1.ID")+entity.getId()); //$NON-NLS-1$
            }
            int face = entity.isCommander()?Font.ITALIC:Font.PLAIN;
            Font font = new Font("SansSerif", face, 10); //$NON-NLS-1$
            Rectangle tempRect =
                new Rectangle(47, 55,
                              getFontMetrics(font).stringWidth(shortName) + 1,
                              getFontMetrics(font).getAscent());

            // create image for buffer
            Image tempImage;
            Graphics graph;
            try {
                tempImage = createImage(bounds.width, bounds.height);
                graph = tempImage.getGraphics();
            } catch (NullPointerException ex) {
                // argh!  but I want it!
                return;
            }

            // fill with key color
            graph.setColor(new Color(TRANSPARENT));
            graph.fillRect(0, 0, bounds.width, bounds.height);

            // draw entity image
            graph.drawImage(tileManager.imageFor(entity), 0, 0, this);

            // draw box with shortName
            Color text, bkgd, bord;
            if (entity.isDone()) {
                text = Color.lightGray;
                bkgd = Color.darkGray;
                bord = Color.black;
            } else if (entity.isImmobile()) {
                text = Color.darkGray;
                bkgd = Color.black;
                bord = Color.lightGray;
            } else {
                text = Color.black;
                bkgd = Color.lightGray;
                bord = Color.darkGray;
            }
            graph.setFont(font);
            graph.setColor(bord);
            graph.fillRect(tempRect.x, tempRect.y,
                           tempRect.width, tempRect.height);
            tempRect.translate(-1, -1);
            graph.setColor(bkgd);
            graph.fillRect(tempRect.x, tempRect.y,
                           tempRect.width, tempRect.height);
            graph.setColor(text);
            graph.drawString(shortName, tempRect.x + 1,
                             tempRect.y + tempRect.height - 1);

            // draw facing
            graph.setColor(Color.white);
            if (entity.getFacing() != -1 && !(entity instanceof Infantry && ((Infantry)entity).getDugIn() == Infantry.DUG_IN_NONE)) {
                graph.drawPolygon(facingPolys[entity.getFacing()]);
            }

            // determine secondary facing for non-mechs & flipped arms
            int secFacing = entity.getFacing();
            if (!(entity instanceof Mech || entity instanceof Protomech)) {
                secFacing = entity.getSecondaryFacing();
            } else if (entity.getArmsFlipped()) {
                secFacing = (entity.getFacing() + 3) % 6;
            }
            // draw red secondary facing arrow if necessary
            if (secFacing != -1 && secFacing != entity.getFacing()) {
                graph.setColor(Color.red);
                graph.drawPolygon(facingPolys[secFacing]);
            }

            // Determine if the entity has a locked turret,
            // and if it is a gun emplacement
            boolean turretLocked = false;
            int crewStunned = 0;
            boolean ge = false;
            if (entity instanceof Tank) {
                turretLocked =
                    !((Tank) entity).hasNoTurret() &&
                    !entity.canChangeSecondaryFacing();
                crewStunned =
                    ((Tank)entity).getStunnedTurns();
            } else if (entity instanceof GunEmplacement) {
                turretLocked = 
                    ((GunEmplacement) entity).hasTurret() &&
                    !entity.canChangeSecondaryFacing();
                ge = true;
            }

            // draw condition strings
            if (entity.crew.isDead()) {
                // draw "CREW DEAD"
                graph.setColor(Color.darkGray);
                graph.drawString(Messages.getString("BoardView1.CrewDead"), 18, 39); //$NON-NLS-1$
                graph.setColor(Color.red);
                graph.drawString(Messages.getString("BoardView1.CrewDead"), 17, 38); //$NON-NLS-1$
            }
            else if (!ge && entity.isImmobile()) {
                if (entity.isProne()) {
                    // draw "IMMOBILE" and "PRONE"
                    graph.setColor(Color.darkGray);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 18, 35); //$NON-NLS-1$
                    graph.drawString(Messages.getString("BoardView1.PRONE"), 26, 48); //$NON-NLS-1$
                    graph.setColor(Color.red);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 17, 34); //$NON-NLS-1$
                    graph.setColor(Color.yellow);
                    graph.drawString(Messages.getString("BoardView1.PRONE"), 25, 47); //$NON-NLS-1$
                } else if (crewStunned > 0) {
                    //draw IMMOBILE and STUNNED
                    graph.setColor(Color.darkGray);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 18, 35); //$NON-NLS-1$
                    graph.drawString(Messages.getString("BoardView1.STUNNED", new Object[]{crewStunned}), 22, 48); //$NON-NLS-1$
                    graph.setColor(Color.red);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 17, 34); //$NON-NLS-1$
                    graph.setColor(Color.yellow);
                    graph.drawString(Messages.getString("BoardView1.STUNNED", new Object[]{crewStunned}), 21, 47); //$NON-NLS-1$
                } else if (turretLocked) {
                    // draw "IMMOBILE" and "LOCKED"
                    graph.setColor(Color.darkGray);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 18, 35); //$NON-NLS-1$
                    graph.drawString(Messages.getString("BoardView1.LOCKED"), 22, 48); //$NON-NLS-1$
                    graph.setColor(Color.red);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 17, 34); //$NON-NLS-1$
                    graph.setColor(Color.yellow);
                    graph.drawString(Messages.getString("BoardView1.LOCKED"), 21, 47); //$NON-NLS-1$
                } else {
                    // draw "IMMOBILE"
                    graph.setColor(Color.darkGray);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 18, 39); //$NON-NLS-1$
                    graph.setColor(Color.red);
                    graph.drawString(Messages.getString("BoardView1.IMMOBILE"), 17, 38); //$NON-NLS-1$
                }
            } else if (entity.isProne()) {
                // draw "PRONE"
                graph.setColor(Color.darkGray);
                graph.drawString(Messages.getString("BoardView1.PRONE"), 26, 39); //$NON-NLS-1$
                graph.setColor(Color.yellow);
                graph.drawString(Messages.getString("BoardView1.PRONE"), 25, 38); //$NON-NLS-1$
            } else if (crewStunned > 0) {
                //draw STUNNED
                graph.setColor(Color.darkGray);
                graph.drawString(Messages.getString("BoardView1.STUNNED", new Object[]{crewStunned}), 22, 48); //$NON-NLS-1$
                graph.setColor(Color.yellow);
                graph.drawString(Messages.getString("BoardView1.STUNNED", new Object[]{crewStunned}), 21, 47); //$NON-NLS-1$
            } else if (turretLocked) {
                // draw "LOCKED"
                graph.setColor(Color.darkGray);
                graph.drawString(Messages.getString("BoardView1.LOCKED"), 22, 39); //$NON-NLS-1$
                graph.setColor(Color.yellow);
                graph.drawString(Messages.getString("BoardView1.LOCKED"), 21, 38); //$NON-NLS-1$
            }

            // If this unit is being swarmed or is swarming another, say so.
            if ( Entity.NONE != entity.getSwarmAttackerId() ) {
                // draw "SWARMED"
                graph.setColor(Color.darkGray);
                graph.drawString(Messages.getString("BoardView1.SWARMED"), 17, 22); //$NON-NLS-1$
                graph.setColor(Color.red);
                graph.drawString(Messages.getString("BoardView1.SWARMED"), 16, 21); //$NON-NLS-1$
            }

            // If this unit is transporting another, say so.
            if ((entity.getLoadedUnits()).size() > 0) {
                // draw "T"
                graph.setColor(Color.darkGray);
                graph.drawString("T", 20, 71); //$NON-NLS-1$
                graph.setColor(Color.black);
                graph.drawString("T", 19, 70); //$NON-NLS-1$
            }
            
            // If this unit is stuck, say so.
            if ((entity.isStuck())) {
                graph.setColor(Color.darkGray);
                graph.drawString(Messages.getString("BoardView1.STUCK"), 26, 61); //$NON-NLS-1$
                graph.setColor(Color.orange);
                graph.drawString(Messages.getString("BoardView1.STUCK"), 25, 60); //$NON-NLS-1$
                
            }

            // If this unit is currently unknown to the enemy, say so.
            if (trackThisEntitiesVisibilityInfo(entity)) {
                if (!entity.isSeenByEnemy()) {
                    // draw "U"
                    graph.setColor(Color.darkGray);
                    graph.drawString("U", 30, 71); //$NON-NLS-1$
                    graph.setColor(Color.black);
                    graph.drawString("U", 29, 70); //$NON-NLS-1$
                } else if (!entity.isVisibleToEnemy() && !GUIPreferences.getInstance().getBoolean(GUIPreferences.ADVANCED_TRANSLUCENT_HIDDEN_UNITS)) {
                    // If this unit is currently hidden from the enemy, say so.
                    // draw "H"
                    graph.setColor(Color.darkGray);
                    graph.drawString("H", 30, 71); //$NON-NLS-1$
                    graph.setColor(Color.black);
                    graph.drawString("H", 29, 70); //$NON-NLS-1$
                }
            }
            
            // If hull down, show 
            if (entity.isHullDown()) {
                // draw "D"
                graph.setColor(Color.darkGray);
                graph.drawString("D", 40, 71); //$NON-NLS-1$
                graph.setColor(Color.black);
                graph.drawString("D", 39, 70); //$NON-NLS-1$                
            }
            else if (entity instanceof Infantry) {
                int dig = ((Infantry)entity).getDugIn();
                if(dig == Infantry.DUG_IN_COMPLETE) {
                    // draw "D"
                    graph.setColor(Color.darkGray);
                    graph.drawString("D", 40, 71); //$NON-NLS-1$
                    graph.setColor(Color.black);
                    graph.drawString("D", 39, 70); //$NON-NLS-1$                    
                }
                else if(dig != Infantry.DUG_IN_NONE) {
                    // draw "W"
                    graph.setColor(Color.darkGray);
                    graph.drawString("W", 40, 71); //$NON-NLS-1$
                    graph.setColor(Color.black);
                    graph.drawString("W", 39, 70); //$NON-NLS-1$                    
                }
            }

            //Lets draw our armor and internal status bars
            int baseBarLength = 23;
            int barLength = 0;
            double percentRemaining = 0.00;

            percentRemaining = entity.getArmorRemainingPercent();
            barLength = (int)(baseBarLength * percentRemaining);

            graph.setColor(Color.darkGray);
            graph.fillRect(56, 7, 23, 3);
            graph.setColor(Color.lightGray);
            graph.fillRect(55, 6, 23, 3);
            graph.setColor(getStatusBarColor(percentRemaining));
            graph.fillRect(55, 6, barLength, 3);

            if (!ge) {
                // Gun emplacements don't have internal structure
                percentRemaining = entity.getInternalRemainingPercent();
                barLength = (int)(baseBarLength * percentRemaining);
                
                graph.setColor(Color.darkGray);
                graph.fillRect(56, 11, 23, 3);
                graph.setColor(Color.lightGray);
                graph.fillRect(55, 10, 23, 3);
                graph.setColor(getStatusBarColor(percentRemaining));
                graph.fillRect(55, 10, barLength, 3);
            }

            // create final image
            image = createImage(new FilteredImageSource(tempImage.getSource(),
                    new KeyAlphaFilter(TRANSPARENT)));
            graph.dispose();
            tempImage.flush();
        }

        /*
         * We only want to show double-blind visibility indicators on
         * our own mechs and teammates mechs (assuming team vision option).
         */
        private boolean trackThisEntitiesVisibilityInfo(Entity e) {
            if (getLocalPlayer() == null) {
                return false;
            }

            if (game.getOptions().booleanOption("double_blind") //$NON-NLS-1$
                && (e.getOwner().getId() == getLocalPlayer().getId()
                    || (game.getOptions().booleanOption("team_vision") //$NON-NLS-1$
                        && e.getOwner().getTeam() == getLocalPlayer().getTeam()))) {
                return true;
            }
            return false;
        }

        private Color getStatusBarColor(double percentRemaining) {
            if ( percentRemaining <= .25 )
                return Color.red;
            else if ( percentRemaining <= .75 )
                return Color.yellow;
            else
                return new Color(16, 196, 16);
        }

        /**
         * Overrides to provide for a smaller sensitive area.
         */
        public boolean isInside(Point point) {
            return entityRect.contains(point.x,
                                        point.y);
        }

        public String[] getTooltip() {
            String[] tipStrings = new String[3];
            StringBuffer buffer;

            buffer = new StringBuffer();
            buffer.append( entity.getChassis() )
                .append( " (" ) //$NON-NLS-1$
                .append( entity.getOwner().getName() )
                .append( "); " ) //$NON-NLS-1$
                .append( entity.getCrew().getGunnery() )
                .append( "/" ) //$NON-NLS-1$
                .append( entity.getCrew().getPiloting() )
                .append( Messages.getString("BoardView1.pilot") ); //$NON-NLS-1$
            int numAdv = entity.getCrew().countAdvantages();
            if (numAdv > 0) {
                buffer.append( " <" ) //$NON-NLS-1$
                    .append( numAdv )
                    .append( Messages.getString("BoardView1.advs") ); //$NON-NLS-1$
            }
            tipStrings[0] = buffer.toString();
            
            GunEmplacement ge = null;
            if (entity instanceof GunEmplacement) {
                ge = (GunEmplacement) entity;
            }

            buffer = new StringBuffer();
            if (ge == null) {
                buffer.append( Messages.getString("BoardView1.move") ) //$NON-NLS-1$
                    .append( entity.getMovementAbbr(entity.moved) )
                    .append( ":" ) //$NON-NLS-1$
                    .append( entity.delta_distance )
                    .append( " (+" ) //$NON-NLS-1$
                    .append( Compute.getTargetMovementModifier
                             (game, entity.getId()).getValue() )
                    .append( ");" ) //$NON-NLS-1$
                    .append( Messages.getString("BoardView1.Heat") ) //$NON-NLS-1$
                    .append( entity.heat );
                if (entity.isCharging()) {
                    buffer.append(" ") //$NON-NLS-1$
                        .append( Messages.getString("BoardView1.charge1")); //$NON-NLS-1$
                }
                if (entity.isMakingDfa()) {
                    buffer.append(" ") //$NON-NLS-1$
                        .append( Messages.getString("BoardBiew1.DFA1")); //$NON-NLS-1$
                }
            } else {
                if (ge.hasTurret() && ge.isTurretLocked()) {
                    buffer.append(Messages.getString("BoardView1.TurretLocked"));
                    if (ge.getFirstWeapon() == -1) {
                        buffer.append(",");
                        buffer.append(Messages.getString("BoardView1.WeaponsDestroyed"));
                    }
                } else if (ge.getFirstWeapon() == -1) {
                    buffer.append(Messages.getString("BoardView1.WeaponsDestroyed"));
                } else {
                    buffer.append(Messages.getString("BoardView1.Operational"));
                }
            }
            if (entity.isDone())
                buffer.append(" (").append(Messages.getString("BoardView1.done")).append(")");
            tipStrings[1] = buffer.toString();

            buffer = new StringBuffer();
            if (ge == null) {
                buffer.append( Messages.getString("BoardView1.Armor") ) //$NON-NLS-1$
                    .append( entity.getTotalArmor() )
                    .append( Messages.getString("BoardView1.internal") ) //$NON-NLS-1$
                    .append( entity.getTotalInternal() );
            } else {
                buffer.append( Messages.getString("BoardView1.cf") ) //$NON-NLS-1$
                    .append( ge.getCurrentCF() )
                    .append( Messages.getString("BoardView1.turretArmor") ) //$NON-NLS-1$
                    .append( ge.getCurrentTurretArmor() );
            }
            tipStrings[2] = buffer.toString();

            return tipStrings;
        }
    }

    /**
     * Sprite for a step in a movement path.  Only one sprite should exist for
     * any hex in a path.  Contains a colored number, and arrows indicating
     * entering, exiting or turning.
     */
    private class StepSprite extends Sprite
    {
        private MoveStep step;

        public StepSprite(MoveStep step) {
            this.step = step;

            // step is the size of the hex that this step is in
            bounds = new Rectangle(getHexLocation(step.getPosition()), hex_size);
            this.image = null;
        }

        public void prepare() {
            // create image for buffer
            Image tempImage = createImage(bounds.width, bounds.height);
            Graphics graph = tempImage.getGraphics();

            // fill with key color
            graph.setColor(new Color(TRANSPARENT));
            graph.fillRect(0, 0, bounds.width, bounds.height);

            // setup some variables
            final Point stepPos = getHexLocation(step.getPosition());
            stepPos.translate(-bounds.x, -bounds.y);
            final Polygon facingPoly = facingPolys[step.getFacing()];
            final Polygon movePoly = movementPolys[step.getFacing()];
            Point offsetCostPos;
            Polygon myPoly;
            Color col;
            // set color
            switch (step.getMovementType()) {
                case IEntityMovementType.MOVE_RUN:
                case IEntityMovementType.MOVE_VTOL_RUN:
                    if (step.isUsingMASC()) {
                        col = GUIPreferences.getInstance().getColor("AdvancedMoveMASCColor");
                    } else {
                        col = GUIPreferences.getInstance().getColor("AdvancedMoveRunColor");
                    }
                    break;
                case IEntityMovementType.MOVE_JUMP :
                    col = GUIPreferences.getInstance().getColor("AdvancedMoveJumpColor");
                    break;
                case IEntityMovementType.MOVE_ILLEGAL :
                    col = GUIPreferences.getInstance().getColor("AdvancedMoveIllegalColor");
                    break;
                default :
                    if (step.getType()==MovePath.STEP_BACKWARDS) {
                        col = GUIPreferences.getInstance().getColor("AdvancedMoveBackColor");
                    } else {
                        col = GUIPreferences.getInstance().getColor("AdvancedMoveDefaultColor");
                    }
                    break;
            }

            // draw arrows and cost for the step
            switch (step.getType()) {
            case MovePath.STEP_FORWARDS :
            case MovePath.STEP_SWIM :
            case MovePath.STEP_BACKWARDS :
            case MovePath.STEP_CHARGE :
            case MovePath.STEP_DFA :
            case MovePath.STEP_LATERAL_LEFT :
            case MovePath.STEP_LATERAL_RIGHT :
            case MovePath.STEP_LATERAL_LEFT_BACKWARDS :
            case MovePath.STEP_LATERAL_RIGHT_BACKWARDS :
                // draw arrows showing them entering the next
                myPoly = new Polygon(movePoly.xpoints, movePoly.ypoints,
                                     movePoly.npoints);
                graph.setColor(Color.darkGray);
                myPoly.translate(stepPos.x + 1, stepPos.y + 1);
                graph.drawPolygon(myPoly);
                graph.setColor(col);
                myPoly.translate(-1, -1);
                graph.drawPolygon(myPoly);
                // draw movement cost
                drawMovementCost(step, stepPos, graph, col, true);
                break;
            case MovePath.STEP_GO_PRONE:
            case MovePath.STEP_HULL_DOWN:
            case MovePath.STEP_DOWN:
            case MovePath.STEP_DIG_IN:
            case MovePath.STEP_FORTIFY:
                // draw arrow indicating dropping prone
                // also doubles as the descent indication
                Polygon downPoly = movementPolys[7];
                myPoly = new Polygon(downPoly.xpoints, downPoly.ypoints, downPoly.npoints);
                graph.setColor(Color.darkGray);
                myPoly.translate(stepPos.x, stepPos.y);
                graph.drawPolygon(myPoly);
                graph.setColor(col);
                myPoly.translate(-1, -1);
                graph.drawPolygon(myPoly);
                offsetCostPos = new Point(stepPos.x + 1, stepPos.y + 15);
                drawMovementCost(step, offsetCostPos, graph, col, false);
                break;
            case MovePath.STEP_GET_UP:
            case MovePath.STEP_UP:
                // draw arrow indicating standing up
                // also doubles as the climb indication
                Polygon upPoly = movementPolys[6];
                myPoly = new Polygon(upPoly.xpoints, upPoly.ypoints, upPoly.npoints);
                graph.setColor(Color.darkGray);
                myPoly.translate(stepPos.x, stepPos.y);
                graph.drawPolygon(myPoly);
                graph.setColor(col);
                myPoly.translate(-1, -1);
                graph.drawPolygon(myPoly);
                offsetCostPos = new Point(stepPos.x, stepPos.y + 15);
                drawMovementCost(step, offsetCostPos, graph, col, false);
                break;
            case MovePath.STEP_CLIMB_MODE_ON:
                // draw climb mode indicator
                String climb = Messages.getString("BoardView1.Climb"); //$NON-NLS-1$
                if (step.isPastDanger()) {
                    climb = "(" + climb + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                }
                graph.setFont(new Font("SansSerif", Font.PLAIN, 12)); //$NON-NLS-1$
                int climbX = stepPos.x + 42 - (graph.getFontMetrics(graph.getFont()).stringWidth(climb) / 2);
                graph.setColor(Color.darkGray);
                graph.drawString(climb, climbX, stepPos.y + 39);
                graph.setColor(col);
                graph.drawString(climb, climbX - 1, stepPos.y + 38);
                break;
            case MovePath.STEP_CLIMB_MODE_OFF:
                // cancel climb mode indicator
                String climboff = Messages.getString("BoardView1.ClimbOff"); //$NON-NLS-1$
                if (step.isPastDanger()) {
                    climboff = "(" + climboff + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                }
                graph.setFont(new Font("SansSerif", Font.PLAIN, 12)); //$NON-NLS-1$
                int climboffX = stepPos.x + 42 - (graph.getFontMetrics(graph.getFont()).stringWidth(climboff) / 2);
                graph.setColor(Color.darkGray);
                graph.drawString(climboff, climboffX, stepPos.y + 39);
                graph.setColor(col);
                graph.drawString(climboff, climboffX - 1, stepPos.y + 38);
                break;
            case MovePath.STEP_TURN_LEFT:
            case MovePath.STEP_TURN_RIGHT:
                // draw arrows showing the facing
                myPoly = new Polygon(facingPoly.xpoints, facingPoly.ypoints,
                                     facingPoly.npoints);
                graph.setColor(Color.darkGray);
                myPoly.translate(stepPos.x + 1, stepPos.y + 1);
                graph.drawPolygon(myPoly);
                graph.setColor(col);
                myPoly.translate(-1, -1);
                graph.drawPolygon(myPoly);
                break;
            case MovePath.STEP_LOAD:
                // Announce load.
                String load = Messages.getString("BoardView1.Load"); //$NON-NLS-1$
                if (step.isPastDanger()) {
                    load = "(" + load + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                }
                graph.setFont(new Font("SansSerif", Font.PLAIN, 12)); //$NON-NLS-1$
                int loadX = stepPos.x + 42 - (graph.getFontMetrics(graph.getFont()).stringWidth(load) / 2);
                graph.setColor(Color.darkGray);
                graph.drawString(load, loadX, stepPos.y + 39);
                graph.setColor(col);
                graph.drawString(load, loadX - 1, stepPos.y + 38);
                break;
            case MovePath.STEP_UNLOAD:
                // Announce unload.
                String unload = Messages.getString("BoardView1.Unload"); //$NON-NLS-1$
                if (step.isPastDanger()) {
                    unload = "(" + unload + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                }
                graph.setFont(new Font("SansSerif", Font.PLAIN, 12)); //$NON-NLS-1$
                int unloadX = stepPos.x + 42 - (graph.getFontMetrics(graph.getFont()).stringWidth(unload) / 2);
                int unloadY = stepPos.y + 38 + graph.getFontMetrics(graph.getFont()).getHeight();
                graph.setColor(Color.darkGray);
                graph.drawString(unload, unloadX, unloadY + 1);
                graph.setColor(col);
                graph.drawString(unload, unloadX - 1, unloadY);
                break;

            default :
                break;
            }

            // create final image
            image = createImage(new FilteredImageSource(tempImage.getSource(),
                    new KeyAlphaFilter(TRANSPARENT)));
            graph.dispose();
            tempImage.flush();
        }

        public Rectangle getBounds(){
            bounds = new Rectangle(getHexLocation(step.getPosition()), hex_size);
            return bounds;
        }
        
        public MoveStep getStep() {
            return step;
        }

        private void drawMovementCost(MoveStep step, Point stepPos, Graphics graph, Color col, boolean shiftFlag) {
            String costString = null;
            StringBuffer costStringBuf = new StringBuffer();
            costStringBuf.append( step.getMpUsed() );

            // If the step is using a road bonus, mark it.
            if ( step.isPavementStep() && step.getParent().getEntity() instanceof Tank ) {
                costStringBuf.append( "+" ); //$NON-NLS-1$
            }

            // If the step is dangerous, mark it.
            if ( step.isDanger() ) {
                costStringBuf.append( "*" ); //$NON-NLS-1$
            }

            // If the step is past danger, mark that.
            if (step.isPastDanger()) {
                costStringBuf.insert( 0, "(" ); //$NON-NLS-1$
                costStringBuf.append( ")" ); //$NON-NLS-1$
            }

            if (step.isUsingMASC()) {
                costStringBuf.append("["); //$NON-NLS-1$
                costStringBuf.append(step.getTargetNumberMASC());
                costStringBuf.append("+]"); //$NON-NLS-1$
            }
            
            if (step.getMovementType() == IEntityMovementType.MOVE_VTOL_WALK
                    || step.getMovementType() == IEntityMovementType.MOVE_VTOL_RUN
                    || step.getMovementType() == IEntityMovementType.MOVE_SUBMARINE_WALK
                    || step.getMovementType() == IEntityMovementType.MOVE_SUBMARINE_RUN
                    || step.getElevation() != 0) {
                costStringBuf.append("{")
                    .append(step.getElevation())
                    .append("}");
            }

            // Convert the buffer to a String and draw it.
            costString = costStringBuf.toString();
            graph.setFont(new Font("SansSerif", Font.PLAIN, 12)); //$NON-NLS-1$
            int costX = stepPos.x + 42;
            if (shiftFlag) {
                costX -= (graph.getFontMetrics(graph.getFont()).stringWidth(costString) / 2);
            }
            graph.setColor(Color.darkGray);
            graph.drawString(costString, costX, stepPos.y + 39);
            graph.setColor(col);
            graph.drawString(costString, costX - 1, stepPos.y + 38);
        }

    }

    /**
     * Sprite and info for a C3 network.  Does not actually use the image buffer
     * as this can be horribly inefficient for long diagonal lines.
     */
    private class C3Sprite extends Sprite
    {
        private Polygon C3Poly;

        protected int entityId;
        protected int masterId;
        protected Entity entityE;
        protected Entity entityM;
        
        Color spriteColor;

        public C3Sprite(Entity e, Entity m) {
            this.entityE = e;
            this.entityM = m;
            this.entityId = e.getId();
            this.masterId = m.getId();
            this.spriteColor = PlayerColors.getColor(e.getOwner().getColorIndex());

            if(e.getPosition() == null || m.getPosition() == null) {
                C3Poly = new Polygon();
                C3Poly.addPoint(0, 0);
                C3Poly.addPoint(1,0);
                C3Poly.addPoint(0,1);
                this.bounds = new Rectangle(C3Poly.getBounds());
                bounds.setSize(bounds.getSize().width + 1, bounds.getSize().height + 1);
                this.image = null;
                return;
            }

            makePoly();

            // set bounds
            this.bounds = new Rectangle(C3Poly.getBounds());
            bounds.setSize(bounds.getSize().width + 1, bounds.getSize().height + 1);

            // move poly to upper right of image
            C3Poly.translate(-bounds.getLocation().x, -bounds.getLocation().y);

            // set names & stuff

            // nullify image
            this.image = null;
        }

        public void prepare() {
        }

        private void makePoly( ){
            // make a polygon
            final Point a = getHexLocation(entityE.getPosition());
            final Point t = getHexLocation(entityM.getPosition());

            final double an = (entityE.getPosition().radian(entityM.getPosition()) + (Math.PI * 1.5)) % (Math.PI * 2); // angle
            final double lw = scale*C3_LINE_WIDTH; // line width
            
            C3Poly = new Polygon();
            C3Poly.addPoint(
                    a.x + (int)(scale*(HEX_W/2) - (int)Math.round(Math.sin(an) * lw)),
                    a.y + (int)(scale*(HEX_H/2) + (int)Math.round(Math.cos(an) * lw)));
            C3Poly.addPoint(
                    a.x + (int)(scale*(HEX_W/2) + (int)Math.round(Math.sin(an) * lw)), 
                    a.y + (int)(scale*(HEX_H/2) - (int)Math.round(Math.cos(an) * lw)));
            C3Poly.addPoint(
                    t.x + (int)(scale*(HEX_W/2) + (int)Math.round(Math.sin(an) * lw)), 
                    t.y + (int)(scale*(HEX_H/2) - (int)Math.round(Math.cos(an) * lw)));
            C3Poly.addPoint(
                    t.x + (int)(scale*(HEX_W/2) - (int)Math.round(Math.sin(an) * lw)), 
                    t.y + (int)(scale*(HEX_H/2) + (int)Math.round(Math.cos(an) * lw)));
        }

        public Rectangle getBounds(){
            makePoly();
            // set bounds
            this.bounds = new Rectangle(C3Poly.getBounds());
            bounds.setSize(bounds.getSize().width + 1, bounds.getSize().height + 1);

            // move poly to upper right of image
            C3Poly.translate(-bounds.getLocation().x, -bounds.getLocation().y);
            this.image = null;
            
            return bounds;
        }
        
        public boolean isReady() {
            return true;
        }

        public void drawOnto(Graphics g, int x, int y, ImageObserver observer) {
            //makePoly();
            
            Polygon drawPoly = new Polygon(C3Poly.xpoints, C3Poly.ypoints, C3Poly.npoints);
            drawPoly.translate(x, y);

            g.setColor(spriteColor);
            g.fillPolygon(drawPoly);
            g.setColor(Color.black);
            g.drawPolygon(drawPoly);
        }

        /**
         * Return true if the point is inside our polygon
         */
        public boolean isInside(Point point) {
            return C3Poly.contains(point.x - bounds.x,
                                   point.y - bounds.y);
        }

    }

    /**
     * Sprite and info for an attack.  Does not actually use the image buffer
     * as this can be horribly inefficient for long diagonal lines.
     *
     * Appears as an arrow. Arrow becoming cut in half when two Meks attacking
     * each other.
     */
    private class AttackSprite extends Sprite
    {
        private ArrayList<AttackAction> attacks = new ArrayList<AttackAction>();
        private Point a;
        private Point t;
        private double an;
        private StraightArrowPolygon attackPoly;
        private Color attackColor;
        private int entityId;
        private int targetType;
        private int targetId;
        private String attackerDesc;
        private String targetDesc;
        private ArrayList<String> weaponDescs = new ArrayList<String>();
        private final Entity ae;
        private final Targetable target;

        public AttackSprite(AttackAction attack) {
            this.attacks.add(attack);
            this.entityId = attack.getEntityId();
            this.targetType = attack.getTargetType();
            this.targetId = attack.getTargetId();
            this.ae = game.getEntity(attack.getEntityId());
            this.target = game.getTarget(targetType, targetId);

            // color?
            attackColor = PlayerColors.getColor(ae.getOwner().getColorIndex());
            //angle of line connecting two hexes
            this.an = (ae.getPosition().radian(target.getPosition()) + (Math.PI * 1.5)) % (Math.PI * 2); // angle
            makePoly();

            // set bounds
            this.bounds = new Rectangle(attackPoly.getBounds());
            bounds.setSize(bounds.getSize().width + 1, bounds.getSize().height + 1);
            // move poly to upper right of image
            attackPoly.translate(-bounds.getLocation().x, -bounds.getLocation().y);

            // set names & stuff
            attackerDesc = ae.getDisplayName();
            targetDesc = target.getDisplayName();
            if (attack instanceof WeaponAttackAction) {
                addWeapon((WeaponAttackAction)attack);
            }
            if (attack instanceof KickAttackAction) {
                addWeapon((KickAttackAction)attack);
            }
            if (attack instanceof PunchAttackAction) {
                addWeapon((PunchAttackAction)attack);
            }
            if (attack instanceof PushAttackAction) {
                addWeapon((PushAttackAction)attack);
            }
            if (attack instanceof ClubAttackAction) {
                addWeapon((ClubAttackAction)attack);
            }
            if (attack instanceof ChargeAttackAction) {
                addWeapon((ChargeAttackAction)attack);
            }
            if (attack instanceof DfaAttackAction) {
                addWeapon((DfaAttackAction)attack);
            }
            if (attack instanceof ProtomechPhysicalAttackAction) {
                addWeapon((ProtomechPhysicalAttackAction)attack);
            }
            if (attack instanceof SearchlightAttackAction) {
                addWeapon((SearchlightAttackAction)attack);
            }

            // nullify image
            this.image = null;
        }

        private void makePoly(){
            // make a polygon
            this.a = getHexLocation(ae.getPosition());
            this.t = getHexLocation(target.getPosition());
            // OK, that is actually not good. I do not like hard coded figures.
            // HEX_W/2 - x distance in pixels from origin of hex bounding box to the center of hex.
            // HEX_H/2 - y distance in pixels from origin of hex bounding box to the center of hex.
            // 18 - is actually 36/2 - we do not want arrows to start and end directly
            // in the centes of hex and hiding mek under.

            a.x = a.x + (int)(HEX_W/2*scale) + (int)Math.round(Math.cos(an) * (int)(18*scale));
            t.x = t.x + (int)(HEX_W/2*scale) - (int)Math.round(Math.cos(an) * (int)(18*scale));
            a.y = a.y + (int)(HEX_H/2*scale) + (int)Math.round(Math.sin(an) * (int)(18*scale));
            t.y = t.y + (int)(HEX_H/2*scale) - (int)Math.round(Math.sin(an) * (int)(18*scale));

            // Checking if given attack is mutual. In this case we building halved arrow
            if (isMutualAttack()){
                attackPoly = new StraightArrowPolygon(a, t, (int)(8*scale), (int)(12*scale), true);
            } else {
                attackPoly = new StraightArrowPolygon(a, t, (int)(4*scale), (int)(8*scale), false);
            }
        }
        
        public Rectangle getBounds(){
            makePoly();
            // set bounds
            this.bounds = new Rectangle(attackPoly.getBounds());
            bounds.setSize(bounds.getSize().width + 1, bounds.getSize().height + 1);
            // move poly to upper right of image
            attackPoly.translate(-bounds.getLocation().x, -bounds.getLocation().y);
            
            return bounds;
        }

        /** If we have build full arrow already with single attack and have got
         * counter attack from our target lately - lets change arrow to halved.
         */
        public void rebuildToHalvedPolygon(){
            attackPoly = new StraightArrowPolygon(a, t, (int)(8*scale), (int)(12*scale), true);
            // set bounds
            this.bounds = new Rectangle(attackPoly.getBounds());
            bounds.setSize(bounds.getSize().width + 1, bounds.getSize().height + 1);
            // move poly to upper right of image
            attackPoly.translate(-bounds.getLocation().x, -bounds.getLocation().y);
        }
        /** Cheking if attack is mutual and changing target arrow to half-arrow
         */
        private boolean isMutualAttack(){
            for (final Iterator<AttackSprite> i = attackSprites.iterator(); i.hasNext();) {
                final AttackSprite sprite = i.next();
                if (sprite.getEntityId() == this.targetId && sprite.getTargetId() == this.entityId) {
                    sprite.rebuildToHalvedPolygon();
                    return true;
                }
            }
            return false;
        }

        public void prepare() {
        }

        public boolean isReady() {
            return true;
        }

        public void drawOnto(Graphics g, int x, int y, ImageObserver observer) {
            Polygon drawPoly = new Polygon(attackPoly.xpoints, attackPoly.ypoints, attackPoly.npoints);
            drawPoly.translate(x, y);

            g.setColor(attackColor);
            g.fillPolygon(drawPoly);
            g.setColor(Color.gray.darker());
            g.drawPolygon(drawPoly);
        }

        /**
         * Return true if the point is inside our polygon
         */
        public boolean isInside(Point point) {
            return attackPoly.contains(point.x - bounds.x,
                                       point.y - bounds.y);
        }

        public int getEntityId() {
            return entityId;
        }

        public int getTargetId() {
            return targetId;
        }

        /**
         * Adds a weapon to this attack
         */
        public void addWeapon(WeaponAttackAction attack) {
            final Entity entity = game.getEntity(attack.getEntityId());
            final WeaponType wtype = (WeaponType)entity.getEquipment(attack.getWeaponId()).getType();
            final String roll = attack.toHit(game).getValueAsString();
            final String table = attack.toHit(game).getTableDesc();
            weaponDescs.add( wtype.getName() + Messages.getString("BoardView1.needs") + roll + " " + table ); //$NON-NLS-1$
        }

        public void addWeapon(KickAttackAction attack) {
            String bufer = ""; //$NON-NLS-1$
            String rollLeft = ""; //$NON-NLS-1$
            String rollRight = ""; //$NON-NLS-1$
            final int leg = attack.getLeg();
            switch (leg){
            case KickAttackAction.BOTH:
                rollLeft = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.LEFT).getValueAsString();
                rollRight = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.RIGHT).getValueAsString();
                bufer = Messages.getString("BoardView1.kickBoth", new Object[]{rollLeft,rollRight}); //$NON-NLS-1$
                break;
            case KickAttackAction.LEFT:
                rollLeft = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.LEFT).getValueAsString();
                bufer = Messages.getString("BoardView1.kickLeft", new Object[]{rollLeft}); //$NON-NLS-1$
                break;
            case KickAttackAction.RIGHT:
                rollRight = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.RIGHT).getValueAsString();
                bufer = Messages.getString("BoardView1.kickRight", new Object[]{rollRight}); //$NON-NLS-1$
                break;
            }
            weaponDescs.add(bufer);
        }

        public void addWeapon(PunchAttackAction attack) {
            String bufer = ""; //$NON-NLS-1$
            String rollLeft = ""; //$NON-NLS-1$
            String rollRight = ""; //$NON-NLS-1$
            final int arm = attack.getArm();
            switch (arm){
            case PunchAttackAction.BOTH:
                rollLeft = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.LEFT).getValueAsString();
                rollRight = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.RIGHT).getValueAsString();
                bufer = Messages.getString("BoardView1.punchBoth", new Object[]{rollLeft,rollRight}); //$NON-NLS-1$
                break;
            case PunchAttackAction.LEFT:
                rollLeft = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.LEFT).getValueAsString();
                bufer = Messages.getString("BoardView1.punchLeft", new Object[]{rollLeft}); //$NON-NLS-1$
                break;
            case PunchAttackAction.RIGHT:
                rollRight = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.RIGHT).getValueAsString();
                bufer = Messages.getString("BoardView1.punchRight", new Object[]{rollRight}); //$NON-NLS-1$
                break;
            }
            weaponDescs.add(bufer);
        }

        public void addWeapon(PushAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            weaponDescs.add(Messages.getString("BoardView1.push", new Object[]{roll})); //$NON-NLS-1$
        }

        public void addWeapon(ClubAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            final String club = attack.getClub().getName();
            weaponDescs.add(Messages.getString("BoardView1.hit", new Object[]{club,roll})); //$NON-NLS-1$
        }

        public void addWeapon(ChargeAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            weaponDescs.add(Messages.getString("BoardView1.charge", new Object[]{roll})); //$NON-NLS-1$
        }
        public void addWeapon(DfaAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            weaponDescs.add(Messages.getString("BoardView1.DFA", new Object[]{roll})); //$NON-NLS-1$
        }
        public void addWeapon(ProtomechPhysicalAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            weaponDescs.add(Messages.getString("BoardView1.proto", new Object[]{roll})); //$NON-NLS-1$
        }
        public void addWeapon(SearchlightAttackAction attack) {
            weaponDescs.add(Messages.getString("BoardView1.Searchlight"));
        }

        public String[] getTooltip() {
            String[] tipStrings = new String[1 + weaponDescs.size()];
            int tip = 1;
            tipStrings[0] = attackerDesc + " "+Messages.getString("BoardView1.on")+" " + targetDesc; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            for (Iterator<String> i = weaponDescs.iterator(); i.hasNext();) {
                tipStrings[tip++] = i.next();
            }
            return tipStrings;
        }
    }

    /**
     * Determine if the tile manager's images have been loaded.
     *
     * @return  <code>true</code> if all images have been loaded.
     *          <code>false</code> if more need to be loaded.
     */
    public boolean isTileImagesLoaded() {
        return this.tileManager.isLoaded();
    }

    public void setUseLOSTool(boolean use) {
        useLOSTool = use;
    }

    public TilesetManager getTilesetManager() {
        return tileManager;
    }

    /**
     * @param lastCursor The lastCursor to set.
     */
    public void setLastCursor(Coords lastCursor) {
        this.lastCursor = lastCursor;
    }

    /**
     * @return Returns the lastCursor.
     */
    public Coords getLastCursor() {
        return lastCursor;
    }

    /**
     * @param highlighted The highlighted to set.
     */
    public void setHighlighted(Coords highlighted) {
        this.highlighted = highlighted;
    }

    /**
     * @return Returns the highlighted.
     */
    public Coords getHighlighted() {
        return highlighted;
    }

    /**
     * @param selected The selected to set.
     */
    public void setSelected(Coords selected) {
        this.selected = selected;
    }

    /**
     * @return Returns the selected.
     */
    public Coords getSelected() {
        return selected;
    }

    /**
     * @param firstLOS The firstLOS to set.
     */
    public void setFirstLOS(Coords firstLOS) {
        this.firstLOS = firstLOS;
    }

    /**
     * @return Returns the firstLOS.
     */
    public Coords getFirstLOS() {
        return firstLOS;
    }
    
    /**
     * Determines if this Board contains the Coords,
     * and if so, "selects" that Coords.
     *
     * @param coords the Coords.
     */
    public void select(Coords coords) {
        if(coords == null || game.getBoard().contains(coords)) {
            setSelected(coords);
            processBoardViewEvent(new BoardViewEvent(this, coords, null, BoardViewEvent.BOARD_HEX_SELECTED,0));
        }
    }
    
    /**
     * "Selects" the specified Coords.
     *
     * @param x the x coordinate.
     * @param y the y coordinate.
     */
    public void select(int x, int y) {
        select(new Coords(x, y));
    }
    
    /**
     * Determines if this Board contains the Coords,
     * and if so, highlights that Coords.
     *
     * @param coords the Coords.
     */
    public void highlight(Coords coords) {
        if(coords == null || game.getBoard().contains(coords)) {
            setHighlighted(coords);
            processBoardViewEvent(new BoardViewEvent(this, coords, null, BoardViewEvent.BOARD_HEX_HIGHLIGHTED, 0));
        }
    }
    
    /**
     * Highlights the specified Coords.
     *
     * @param x the x coordinate.
     * @param y the y coordinate.
     */
    public void highlight(int x, int y) {
        highlight(new Coords(x, y));
    }
    
    /**
     * Determines if this Board contains the Coords,
     * and if so, "cursors" that Coords.
     *
     * @param coords the Coords.
     */
    public void cursor(Coords coords) {
        if(coords == null || game.getBoard().contains(coords)) {
            if(getLastCursor() == null || coords == null || !coords.equals(getLastCursor())) {
                setLastCursor(coords);
                processBoardViewEvent(new BoardViewEvent(this, coords, null, BoardViewEvent.BOARD_HEX_CURSOR, 0));
            } else {
                setLastCursor(coords);
            }
        }
    }
    
    /**
     * "Cursors" the specified Coords.
     *
     * @param x the x coordinate.
     * @param y the y coordinate.
     */
    public void cursor(int x, int y) {
        cursor(new Coords(x, y));
    }

    public void checkLOS(Coords c) {
        if(c == null || game.getBoard().contains(c)) {
            if (getFirstLOS() == null) {
                setFirstLOS(c);
                processBoardViewEvent(new BoardViewEvent(this, c, null, BoardViewEvent.BOARD_FIRST_LOS_HEX, 0));
            } else {
                secondLOSHex(c,getFirstLOS());
                processBoardViewEvent(new BoardViewEvent(this, c, null, BoardViewEvent.BOARD_SECOND_LOS_HEX, 0));
                setFirstLOS(null);
            }
        }
    }

    /**
     * Determines if this Board contains the (x, y) Coords,
     * and if so, notifies listeners about the specified mouse
     * action.
     */
    public void mouseAction(int x, int y, int mtype, int modifiers) {
        if(game.getBoard().contains(x, y)) {
            Coords c = new Coords(x, y);
            switch(mtype) {
            case BOARD_HEX_CLICK :
                if ((modifiers & java.awt.event.InputEvent.CTRL_MASK) != 0) {
                    checkLOS(c);
                } else {
                    processBoardViewEvent(new BoardViewEvent(this, c, null, BoardViewEvent.BOARD_HEX_CLICKED, modifiers));
                }
                break;
            case BOARD_HEX_DOUBLECLICK :
                processBoardViewEvent(new BoardViewEvent(this, c, null, BoardViewEvent.BOARD_HEX_DOUBLECLICKED, modifiers));
                break;
            case BOARD_HEX_DRAG :
                processBoardViewEvent(new BoardViewEvent(this, c, null, BoardViewEvent.BOARD_HEX_DRAGGED, modifiers));
                break;
            }
        }
    }
    
    /**
     * Notifies listeners about the specified mouse action.
     *
     * @param coords the Coords.
     */
    public void mouseAction(Coords coords, int mtype, int modifiers) {
        mouseAction(coords.x, coords.y, mtype, modifiers);
    }
    
    /* (non-Javadoc)
     * @see megamek.common.BoardListener#boardNewBoard(megamek.common.BoardEvent)
     */
    public void boardNewBoard(BoardEvent b) {        
        updateBoard();
    }

    /* (non-Javadoc)
     * @see megamek.common.BoardListener#boardChangedHex(megamek.common.BoardEvent)
     */
    public synchronized void boardChangedHex(BoardEvent b) {
        IHex hex = game.getBoard().getHex(b.getCoords());
        tileManager.clearHex(hex);
        tileManager.waitForHex(hex);
        repaint();
    }

    private GameListener gameListener = new GameListenerAdapter(){
        
        public void gameEntityNew(GameEntityNewEvent e) {
            updateEcmList();
            redrawAllEntities();            
        }

        public void gameEntityRemove(GameEntityRemoveEvent e) {
            updateEcmList();
            redrawAllEntities();            
        }

        public void gameEntityChange(GameEntityChangeEvent e) {
            Vector<UnitLocation> mp = e.getMovePath();
            updateEcmList();
            if (mp != null && mp.size() > 0 && GUIPreferences.getInstance().getShowMoveStep()) {
                addMovingUnit(e.getEntity(), mp);
            }else {
                redrawEntity(e.getEntity());
            }
        }

        public void gameNewAction(GameNewActionEvent e) {
            EntityAction ea = e.getAction();
            if (ea instanceof AttackAction) {            
                addAttack((AttackAction)ea);
            }
        }

        public void gameBoardNew(GameBoardNewEvent e) {
            IBoard b = e.getOldBoard();
            if (b != null) {
                b.removeBoardListener(BoardView1.this);
            }
            b = e.getNewBoard();
            if (b != null) {
                b.addBoardListener(BoardView1.this);
            }
            updateBoard();
        }        

        public void gameBoardChanged(GameBoardChangeEvent e) {
            boardChanged();
        }

        public void gamePhaseChange(GamePhaseChangeEvent e) {
            refreshAttacks();
            switch (e.getNewPhase()) {
            case IGame.PHASE_MOVEMENT :
            case IGame.PHASE_FIRING :
            case IGame.PHASE_PHYSICAL :
                refreshAttacks();
                break;
            case IGame.PHASE_INITIATIVE :
                clearAllAttacks();
                break;                
            case IGame.PHASE_END:
            case IGame.PHASE_VICTORY:    
                clearSprites();                
            }
        }
    };

    private synchronized void boardChanged() {
        redrawAllEntities();        
    }

    private void clearSprites() {
        pathSprites.clear();
        attackSprites.clear();
        C3Sprites.clear();
        
    }

    protected synchronized void updateBoard() {
        updateBoardSize();
        redrawAllEntities();
    }
    
    /**
     *  the old redrawworker converted to a runnable which is called
     *  now and then from the event thread
     */
    protected class RedrawWorker implements Runnable {

        protected long lastTime = System.currentTimeMillis();
        protected long currentTime = System.currentTimeMillis();
        
        public void run() {
            currentTime = System.currentTimeMillis();
            if (isShowing()) {
                boolean redraw = false;
                for (int i = 0; i < displayables.size(); i++) {
                    Displayable disp = displayables.get(i);
                    if (!disp.isSliding()) {
                        disp.setIdleTime(currentTime - lastTime, true);
                    } else {
                        redraw = redraw || disp.slide();
                    }
                }
                redraw = redraw || doMoveUnits(currentTime - lastTime);
                if (redraw) {
                    repaint();
                }
            }
            lastTime = currentTime;
        }
    }

    public synchronized void WeaponSelected(MechDisplayEvent b) {
        selectedEntity = b.getEntity();
        selectedWeapon = b.getEquip();
        repaint();
    }

    private class EcmBubble extends Coords {
        int range;
        int tint;
        public EcmBubble(Coords c, int range, int tint) {
            super(c);
            this.range = range;
            this.tint = tint;
        }
    }
    
    //This is expensive, so precalculate when entity changes
    public void updateEcmList() {
        ArrayList<EcmBubble> list = new ArrayList<EcmBubble>();
        for(Enumeration e = game.getEntities();e.hasMoreElements();) {
            Entity ent = (Entity)e.nextElement();
            if(ent.getPosition() == null || !ent.isDeployed() || ent.isOffBoard()) {
                continue;
            }
            int range = ent.getECMRange();
            if(range != Entity.NONE) {
                int tint = PlayerColors.getColorRGB(ent.getOwner().getColorIndex());
                list.add(new EcmBubble(ent.getPosition(), range, tint));
            }
        }
        HashMap<Coords, Integer> table = new HashMap<Coords, Integer>();
        for(EcmBubble b : list) {
            Integer col = new Integer(b.tint);
            for(int x=-b.range;x<=b.range;x++) {
                for(int y=-b.range;y<=b.range;y++) {
                    Coords c = new Coords(x+b.x,y+b.y);
                    //clip rectangle to hexagon
                    if(b.distance(c) <= b.range) {
                        Integer tint = table.get(c); 
                        if(tint == null) {
                            table.put(c, col);
                        } else if (tint.intValue() != b.tint) {
                            int red1 = (tint.intValue() >> 16) & 0xff;
                            int green1 = (tint.intValue() >> 8) & 0xff;
                            int blue1 = tint.intValue() & 0xff;
                            int red2 = (b.tint >> 16) & 0xff;
                            int green2 = (b.tint >> 8) & 0xff;
                            int blue2 = b.tint & 0xff;
                            red1 = (red1 + red2) / 2;
                            green1 = (green1 + green2) / 2;
                            blue1 = (blue1 + blue2) / 2;
                            table.put(c, new Integer((red1 << 16) | (green1 << 8) | blue1));
                        }
                    }
                }
            }
        }
        synchronized(this) {
            ecmHexes = table;
        }
        repaint();
    }

	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	public int getScrollableBlockIncrement(Rectangle arg0, int arg1, int arg2) {
        final Dimension size = scrollpane.getViewport().getSize();
		if (arg1 == SwingConstants.VERTICAL)
			return size.height;
		return size.width;
	}

	public boolean getScrollableTracksViewportHeight() {
		return false;
	}

	public boolean getScrollableTracksViewportWidth() {
		return false;
	}

	public int getScrollableUnitIncrement(Rectangle arg0, int arg1, int arg2) {
		if (arg1 == SwingConstants.VERTICAL)
			return (int) (scale * HEX_H / 2.0);
		return (int) (scale * HEX_W / 2.0);
	}
	
	public Dimension getPreferredSize() {
		return boardSize;
	}
}