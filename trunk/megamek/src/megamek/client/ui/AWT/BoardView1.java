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

// Defines Iterator class for JDK v1.1
import com.sun.java.util.collections.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.Enumeration;
import megamek.client.util.*;
import megamek.common.*;
import megamek.common.actions.*;

import java.util.Properties;

/**
 * Displays the board; lets the user scroll around and select points on it.
 */
public class BoardView1
    extends Canvas
    implements BoardListener, MouseListener, MouseMotionListener, KeyListener, Runnable, AdjustmentListener
{
    private static final int        TRANSPARENT = 0xFFFF00FF;

    // the dimensions of megamek's hex images
    private static final int        HEX_W = 84;
    private static final int        HEX_H = 72;
    private static final int        HEX_WC = HEX_W - HEX_W/4;
    
    // The list of valid zoom factors.  Other values cause map aliasing,
    // I can't be bothered figuring out why.  - Ben
    private static final float[] ZOOM_FACTORS =
    		{	0.30f, 0.41f, 0.50f,
    			0.60f, 0.68f, 0.79f,
				0.90f, 1.00f
				//1.09f, 1.17f
    		};

    // the index of zoom factor 1.00f
    private static final int BASE_ZOOM_INDEX = 7;

    // line width of the c3 network lines
    private static final int C3_LINE_WIDTH = 1;

    private static Font FONT_7 = new Font("SansSerif", Font.PLAIN, 7);
    private static Font FONT_8 = new Font("SansSerif", Font.PLAIN, 8);
    private static Font FONT_9 = new Font("SansSerif", Font.PLAIN, 9);
    private static Font FONT_10 = new Font("SansSerif", Font.PLAIN, 10);
    private static Font FONT_12 = new Font("SansSerif", Font.PLAIN, 12);

    private Dimension  	hex_size = null;
    private boolean		isJ2RE;
    
    private Font       font_hexnum 		= FONT_10;
    private Font       font_elev 		= FONT_9;
    private Font       font_minefield 	= FONT_12;

    private Game game;
    private Frame frame;

    private Point 		mousePos = new Point();
    private Rectangle 	view = new Rectangle();
    private Point 		offset = new Point();
    private Dimension boardSize;

    // scrolly stuff:
    private Scrollbar vScrollbar = null;
    private Scrollbar hScrollbar = null;
    private boolean isScrolling = false;
    private Thread scroller = new Thread(this, "BoardView Scroller Thread");
    private Point scroll = new Point();
    private boolean initCtlScroll;
    private boolean ctlKeyHeld = false;
    private int previousMouseX;
    private int previousMouseY;

    // back buffer to draw to
    private Image backImage;
    private Dimension backSize;
    private Graphics backGraph;

    // buffer for all the hexes you can possibly see
    private Image boardImage;
    private Rectangle boardRect;
    private Graphics boardGraph;

    // entity sprites
    private Vector entitySprites = new Vector();
    private Hashtable entitySpriteIds = new Hashtable();

    // sprites for the three selection cursors
    private CursorSprite cursorSprite;
    private CursorSprite highlightSprite;
    private CursorSprite selectedSprite;
    private CursorSprite firstLOSSprite;
    private CursorSprite secondLOSSprite;

    // sprite for current movement
    private Vector pathSprites = new Vector();

    // vector of sprites for all firing lines
    private Vector attackSprites = new Vector();

    // vector of sprites for C3 network lines
    private Vector C3Sprites = new Vector();

    // tooltip stuff
    private Window tipWindow;
    private boolean isTipPossible = false;
    private long lastIdle;

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
    private boolean				hasZoomed = false;
    public 	int					zoomIndex;
    private float 				scale;
    private Hashtable scaledImageCache = new Hashtable();
        
    // Displayables (Chat box, etc.)
    private Vector               displayables = new Vector();

    // Move units step by step
    private Vector                           movingUnits = new Vector();
    private long                             moveWait = 0;

    // moving entity sprites
    private Vector movingEntitySprites = new Vector();
    private Hashtable movingEntitySpriteIds = new Hashtable();
    private Vector ghostEntitySprites = new Vector();
    protected transient Vector boardViewListeners = new Vector();

    // wreck sprites
    private Vector wreckSprites = new Vector();

    private Coords rulerStart; // added by kenn
    private Coords rulerEnd; // added by kenn
    private Color rulerStartColor; // added by kenn
    private Color rulerEndColor; // added by kenn

    // Position of the mouse before right mouse button was pressed. Used to have an anchor for scrolling 
    private Point oldMousePosition = null;
    
    // Indicate that a scrolling took place, so no popup should be drawn on right mouse button release
    private boolean scrolled = false;
    
    /**
     * Construct a new board view for the specified game
     */
    public BoardView1(Game game, Frame frame) throws java.io.IOException {
        this.game = game;
        this.frame = frame;

        tileManager = new TilesetManager(this);

        game.getBoard().addBoardListener(this);
        scroller.start();
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        /* MouseWheelListener isn't a v1.3.1 API **
        try{
            addMouseWheelListener( new MouseWheelListener(){
                public void mouseWheelMoved(MouseWheelEvent we){
                    if (we.getWheelRotation() > 0){
                            zoomIn();
                    } else {
                            zoomOut();
                    }
                }
            });
        } catch ( Throwable error ){
            System.out.println("Mouse wheel not supported by this jvm");
        }
        /* MouseWheelListener isn't a v1.3.1 API */
     	
     	// only use scaling if we're using Java 2, otherwise we get memory leaks etc.
     	Properties p = System.getProperties();
     	String javaVersion = p.getProperty( "java.version" );
     	if ( javaVersion.charAt(2) == '1' ){
     		isJ2RE = false;
     		zoomIndex = BASE_ZOOM_INDEX;
     	} else {
     		isJ2RE = true;
     		zoomIndex = Settings.mapZoomIndex;
     		checkZoomIndex();
	        hasZoomed = true;
     	}
	    scale = ZOOM_FACTORS[ zoomIndex ];
        
	    updateFontSizes();
        updateBoardSize();

        // tooltip
        tipWindow = new Window(frame);
        
        hex_size = new Dimension((int)(HEX_W*scale), (int)(HEX_H*scale));
        
        initPolys();

        cursorSprite = new CursorSprite(Color.cyan);
        highlightSprite = new CursorSprite(Color.white);
        selectedSprite = new CursorSprite(Color.blue);
        firstLOSSprite = new CursorSprite(Color.red);
        secondLOSSprite = new CursorSprite(Color.red);
    }

    public void addBoardViewListener(BoardViewListener l) {
        boardViewListeners.addElement(l);
    }

    public void removeBoardViewListener(BoardViewListener l) {
        boardViewListeners.removeElement(l);
    }

    public void processBoardViewEvent(BoardViewEvent be) {
        if (boardViewListeners == null) {
            return;
        }
        for(Enumeration e = boardViewListeners.elements(); e.hasMoreElements();) {
            BoardViewListener l = (BoardViewListener)e.nextElement();
            switch(be.getType()) {
            case BoardViewEvent.FINISHED_MOVING_UNITS :
                l.finishedMovingUnits(be);
                break;
            case BoardViewEvent.SELECT_UNIT :
                l.selectUnit(be);
                break;
            }
        }
    }
    
    public void addMovingUnit(Entity entity, java.util.Vector movePath) {
        if ( !movePath.isEmpty() ) {
            Object[] o = new Object[2];
            o[0] = entity;
            o[1] = movePath;
            movingUnits.addElement(o);

            GhostEntitySprite ghostSprite = new GhostEntitySprite(entity);
            ghostEntitySprites.addElement(ghostSprite);

            // Center on the starting hex of the moving unit.
            int j = ((Integer) movePath.elementAt(0)).intValue();
            int y = j & 255;
            int x = (j >> 8) & 255;
            centerOnHex(new Coords(x, y));
        }
    }

    public void addDisplayable(Displayable disp) {
        displayables.addElement(disp);
    }

    public void removeDisplayable(Displayable disp) {
        displayables.removeElement(disp);
    }

    /**
     * Specify the scrollbars that control this view's positioning.
     *
     * @param   vertical - the vertical <code>Scrollbar</code>
     * @param   horizontal - the horizontal <code>Scrollbar</code>
     */
    public void setScrollbars (Scrollbar vertical, Scrollbar horizontal) {
        this.vScrollbar = vertical;
        this.hScrollbar = horizontal;

        // When the scroll bars are adjusted, update our offset.
        this.vScrollbar.addAdjustmentListener (this);
        this.hScrollbar.addAdjustmentListener (this);
    }

    /**
     * Update ourself when a scroll bar is adjusted.
     *
     * @param   event - the <code>AdjustmentEvent</code> that caused this call.
     */
    public void adjustmentValueChanged (AdjustmentEvent event) {
        Point oldPt = this.scroll;
        Point newPt = new Point (oldPt.x, oldPt.y);
        if (event.getAdjustable().getOrientation() == Adjustable.VERTICAL) {
            newPt.y = event.getValue();
        } else {
            newPt.x = event.getValue();
        }
        this.scroll.setLocation (newPt);
        this.repaint();
    }

    public void paint(Graphics g) {
        update(g);
    }

    /**
     * Draw the screen!
     */
    public void update(Graphics g) {
        // Limit our size to the viewport of the scroll pane.
        final Dimension size = getSize();
        //         final long startTime = System.currentTimeMillis(); // commentme

        // Make sure our scrollbars have the right sizes.
        // N.B. A buggy Sun implementation makes me to do this here instead 
        // of updateBoardSize() (which is where *I* think it belongs).
        if (null != this.vScrollbar) {
            this.vScrollbar.setVisibleAmount (size.height);
            this.vScrollbar.setBlockIncrement (size.height);
            this.vScrollbar.setUnitIncrement ((int) (scale * HEX_H / 2.0));
            this.vScrollbar.setMaximum (boardSize.height);
        }
        if (null != this.hScrollbar) {
            this.hScrollbar.setVisibleAmount (size.width);
            this.hScrollbar.setBlockIncrement (size.width);
            this.hScrollbar.setUnitIncrement ((int) (scale * HEX_W / 2.0));
            this.hScrollbar.setMaximum (boardSize.width);
        }

        // update view, offset
        view.setLocation(scroll);
        view.setSize(getOptimalView(size));
        offset.setLocation(getOptimalOffset(size));

        if (!this.isTileImagesLoaded()) {
            g.drawString("loading images...", 20, 50);
            if (!tileManager.isStarted()) {
                System.out.println("boardview1: loading images for board");
                tileManager.loadNeededImages(game);
            }
            return;
        }

        // make sure back buffer is valid
        if (backGraph == null || !view.getSize().equals(backSize)) {
            // make new back buffer
            backSize = view.getSize();
            backImage = createImage(backSize.width, backSize.height);
            backGraph = backImage.getGraphics();
        }
        
        // make sure board rectangle contains our current view rectangle
        if (boardImage == null || !boardRect.union(view).equals(boardRect)) {
            updateBoardImage();
        }

        // draw onto the back buffer:

        // draw the board
        backGraph.drawImage(boardImage, 0, 0, this);

        // draw wrecks
        if (Settings.showWrecks) {
            drawSprites(wreckSprites);
        }

        // Minefield signs all over the place!
        drawMinefields();

        // draw highlight border
        drawSprite(highlightSprite);

        // draw cursors
        drawSprite(cursorSprite);
        drawSprite(selectedSprite);
        drawSprite(firstLOSSprite);
        drawSprite(secondLOSSprite);

        // draw deployment indicators
        if (m_plDeployer != null) {
            drawDeployment();
        }

        // draw C3 links
        drawSprites(C3Sprites);

        // draw onscreen entities
        drawSprites(entitySprites);

        // draw moving onscreen entities
        drawSprites(movingEntitySprites);

        // draw ghost onscreen entities
        drawSprites(ghostEntitySprites);

        // draw onscreen attacks
        drawSprites(attackSprites);

        // draw movement, if valid
        drawSprites(pathSprites);

        // added by kenn
        // draw the ruler line
        if (rulerStart != null) {
            Point start =  getCentreHexLocation(rulerStart);
            if (rulerEnd != null) {
                Point end = getCentreHexLocation(rulerEnd);
                backGraph.setColor(Color.yellow);
                backGraph.drawLine(start.x - boardRect.x, start.y - boardRect.y, end.x - boardRect.x, end.y - boardRect.y);

                backGraph.setColor(rulerEndColor);
                backGraph.fillRect(end.x - boardRect.x - 1, end.y - boardRect.y - 1, 2, 2);
            }

            backGraph.setColor(rulerStartColor);
            backGraph.fillRect(start.x - boardRect.x - 1, start.y - boardRect.y - 1, 2, 2);
        }
        // end kenn

        // draw all the "displayables"
        for (int i = 0; i < displayables.size(); i++) {
            Displayable disp = (Displayable) displayables.elementAt(i);
            disp.draw(backGraph, backSize);
        }

        // draw the back buffer onto the screen
        // first clear the entire view if the map has been zoomed
        if ( hasZoomed == true ){
        	Image tmpImage = createImage( size.width, size.height );
        	Graphics tmpGraphics = tmpImage.getGraphics();
        	tmpGraphics.drawImage(backImage, offset.x, offset.y, this);
        	g.drawImage(tmpImage, 0, 0, this);
        	hasZoomed=false;
        } else {
        	g.drawImage(backImage, offset.x, offset.y, this);
        }
        //g.drawString(""+scale, 10, 10);

        //         final long finishTime = System.currentTimeMillis();//commentme
        //         System.out.println("BoardView1: updated screen in " + (finishTime - startTime) + " ms."); //commentme
    }

    /**
     * Updates the boardSize variable with the proper values for this board.
     */
    private void updateBoardSize() {
    	int width = game.getBoard().width * (int)(HEX_WC*scale) + (int)(HEX_W/4*scale);
        int height = game.getBoard().height * (int)(HEX_H*scale) + (int)(HEX_H/2*scale);
        boardSize = new Dimension(width, height);
    }

    /**
     * Think up the size of the view rectangle based on the size of the component
     * and the size of board
     */
    private Dimension getOptimalView(Dimension size) {
        return new Dimension(
        		Math.min(size.width, boardSize.width),
        		Math.min(size.height, boardSize.height));
    }

    /**
     * Where should the offset be for this screen size?
     */
    private Point getOptimalOffset(Dimension size) {
        int ox = 0;
        int oy = 0;
        if (size.width > boardSize.width) {
            ox = (size.width - boardSize.width) / 2;
        }
        if (size.height > boardSize.height) {
            oy = (size.height - boardSize.height) / 2;
        }
        return new Point(ox, oy);
    }

    /**
     * Repaint the bounds of a sprite, offset by view
     */
    private void repaintBounds(Rectangle bounds) {
        if (view != null) {
            repaint(bounds.x - view.x + offset.x, bounds.y - view.y + offset.y, bounds.width, bounds.height);
        }
    }

    /**
     * Looks through a vector of buffered images and draws them if they're
     * onscreen.
     */
    private synchronized void drawSprites(Vector spriteVector) {
        for (int i = 0; i < spriteVector.size(); i++) {
            final Sprite sprite = (Sprite)spriteVector.get(i);
            drawSprite(sprite);
        }
    }

    /**
     * Draws a sprite, if it is in the current view
     */
    private final void drawSprite(Sprite sprite) {
        if (view.intersects(sprite.getBounds())) {
            final int drawX = sprite.getBounds().x - view.x;
            final int drawY = sprite.getBounds().y - view.y;
            if (!sprite.isReady()) {
                sprite.prepare();
            }
            sprite.drawOnto(backGraph, drawX, drawY, this);
        }
    }

    /**
     * Manages a cache of scaled images.
     */
    private Image getScaledImage(Image base, double scale ) {
        if (base == null) {
        	return null;
        }
        if ( zoomIndex == BASE_ZOOM_INDEX ) {
        	return base;
        }
        Dimension d = getImageBounds(base).getSize();
        d.width *= scale;
        d.height *= scale;
        ScaledCacheKey key = new ScaledCacheKey(base, d);
        Image scaled = (Image) scaledImageCache.get(key);
        if (scaled == null) {
        	scaled = scale(base, d.width, d.height);

	        MediaTracker tracker = new MediaTracker(this);
	        tracker.addImage( scaled, 1 );
	        // Wait for image to load
	        try{
	        	tracker.waitForID( 1 );
	        } catch ( InterruptedException e ){ e.printStackTrace(); }
	          
	        scaledImageCache.put(key, scaled);
        }
        return scaled;
    }
   
    /**
     * The actual scaling code.
     */
    private Image scale(Image img, int width, int height) {
		ImageFilter filter;

		filter = new ImprovedAveragingScaleFilter(img.getWidth(null),
		                                          img.getHeight(null),
		                                          width, height);
		
		ImageProducer prod;
		prod = new FilteredImageSource(img.getSource(), filter);
		return Toolkit.getDefaultToolkit().createImage(prod);
//    	BufferedImage tmp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB );
//    	Graphics g = tmp.getGraphics();
//    	g.drawImage(img,
//                    width,
//                    height,
//                    this);
//    	return (Image)tmp;
    }
    
    private static Rectangle getImageBounds(Image im) {
        return new Rectangle(-im.getWidth(null) / 2, -im.getHeight(null) / 2, im.getWidth(null), im.getHeight(null));
    }

    /**
     * The key assigned to each scaled and cached image. Enables easy
     * retrieval from the hash table.
     */
    private static class ScaledCacheKey {
        private Image base;
        private Dimension bounds;

        public ScaledCacheKey(Image base, Dimension bounds) {
          this.bounds = bounds;
          this.base = base;
        }

        public boolean equals(Object o) {
          if (this == o) return true;
          if (!(o instanceof ScaledCacheKey)) return false;

          final ScaledCacheKey scaledCacheKey = (ScaledCacheKey) o;

          if (!base.equals(scaledCacheKey.base)) return false;
          if (!bounds.equals(scaledCacheKey.bounds)) return false;

          return true;
        }

        public int hashCode() {
          int result;
          result = base.hashCode();
          result = 29 * result + bounds.hashCode();
          return result;
        }
      }
    
    /**
     * Draw an outline around legal deployment hexes
     */
    private void drawDeployment() {
        // only update visible hexes
        int drawX = view.x / (int)(HEX_WC*scale) - 1;
        int drawY = view.y / (int)(HEX_H*scale) - 1;

        int drawWidth = view.width / (int)(HEX_WC*scale) + 3;
        int drawHeight = view.height / (int)(HEX_H*scale) + 3;

        // loop through the hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                Point p = getHexLocation(c);
                p.translate(-(view.x), -(view.y));
                if (game.board.isLegalDeployment(c, m_plDeployer)) {
                    backGraph.setColor(Color.yellow);
                    int[] xcoords = { p.x + (int)(21*scale), p.x + (int)(62*scale), p.x + (int)(83*scale), p.x + (int)(83*scale),
                            p.x + (int)(62*scale), p.x + (int)(21*scale), p.x, p.x };
                    int[] ycoords = { p.y, p.y, p.y + (int)(35*scale), p.y + (int)(36*scale), p.y + (int)(71*scale),
                            p.y + (int)(71*scale), p.y + (int)(36*scale), p.y + (int)(35*scale) };
                    backGraph.drawPolygon(xcoords, ycoords, 8);
                }
            }
        }
    }

    /**
     * Writes "MINEFIELD" in minefield hexes...
     */
    private void drawMinefields() {
        // only update visible hexes
        int drawX = view.x / (int)(HEX_WC*scale) - 1;
        int drawY = view.y / (int)(HEX_H*scale) - 1;

        int drawWidth = view.width / (int)(HEX_WC*scale) + 3;
        int drawHeight = view.height / (int)(HEX_H*scale) + 3;

        // loop through the hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                Point p = getHexLocation(c);
                p.translate(-(view.x), -(view.y));
                
                if (!game.board.contains(c)){ continue; }
		        if (!game.containsMinefield(c)){ continue; }
		        
		        Minefield mf = (Minefield) game.getMinefields(c).elementAt(0);
		        
		        Image tmpImage = getScaledImage( tileManager.getMinefieldSign(), scale );
		        backGraph.drawImage(
		        		tmpImage,
		        		p.x + (int)(13*scale), 
						p.y + (int)(13*scale), 
						this);
		        
				backGraph.setColor(Color.black);
		        int nbrMfs = game.getNbrMinefields(c);
	        	if (nbrMfs > 1) {
	        		drawCenteredString("Multiple", 
				        		p.x,
								p.y + (int)(51*scale),
								font_minefield,
								backGraph);
	        	} else if (nbrMfs == 1) {
		    		switch (mf.getType()) {
		    			case (Minefield.TYPE_CONVENTIONAL) :
		    				drawCenteredString(
		    						"Conventional",
		    						p.x,
									p.y + (int)(51*scale),
									font_minefield,
									backGraph);
		    				break;
		    			case (Minefield.TYPE_THUNDER) :
		    				drawCenteredString(
					        		"Thunder (" + mf.getDamage() + ")", 
									p.x, 
									p.y + (int)(51*scale),
									font_minefield,
									backGraph);
		    			break;
		    			case (Minefield.TYPE_THUNDER_INFERNO) :
		    				drawCenteredString(
		    						"Thunder-Inf (" + mf.getDamage() + ")", 
									p.x,
									p.y + (int)(51*scale),
									font_minefield,
									backGraph);
		    			break;
		    			case (Minefield.TYPE_THUNDER_ACTIVE) :
		    				drawCenteredString(
					        		"Thunder-Actv (" + mf.getDamage() + ")", 
									p.x,
									p.y + (int)(51*scale),
									font_minefield,
									backGraph);
		    			break;
		    			case (Minefield.TYPE_COMMAND_DETONATED) :
		    				drawCenteredString(
					        		"Command-", 
									p.x,
									p.y + (int)(51*scale),
									font_minefield,
									backGraph);
		    				drawCenteredString(
					        		"detonated", 
									p.x,
									p.y + (int)(60*scale),
									font_minefield,
									backGraph);
		    			break;
		    			case (Minefield.TYPE_VIBRABOMB) :
		    				drawCenteredString(
					        		"Vibrabomb", 
									p.x,
									p.y + (int)(51*scale),
									font_minefield,
									backGraph);
                  if (mf.getPlayerId() == localPlayer.getId()) {
                      drawCenteredString(
					        		"(" + mf.getSetting() + ")", 
									    p.x,
									    p.y + (int)(60*scale),
									    font_minefield,
									    backGraph);
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
    	
    	x += (int)((hex_size.width - stringWidth)/2);
    	
    	graph.setFont(font);
    	graph.drawString( string, x, y );
    }
    
    /**
     * Updates the board buffer to contain all the hexes needed by the view.
     */
    private void updateBoardImage() {
        // check to make sure image is big enough
        if (boardGraph == null || view.width > boardRect.width
            || view.height > boardRect.height) {
            /* Ok, some history here.  Before the zoom patch, the
               boardImage was created with the same size as the view.
               After the zoom patch, the boardImage was created with
               the same size as the entire board (all maps).  This
               change ate up a hideous amount of memory (eg: in a 3x3
               map set test with one mech, memory usage went from
               about 15MB to 60MB).  I have now changed it back to the
               old way, and the zoom feature *seems* to still work.
               Why the zoom author made the change, I cannot say. */
            boardImage = createImage(view.width, view.height);
            //boardImage = createImage(boardSize.width, boardSize.height);
            /* ----- */

            boardGraph = boardImage.getGraphics();

            // Handle resizes correctly.
            checkScrollBounds();
            boardRect = new Rectangle(view);
            System.out.println("boardview1: made a new board buffer " + boardRect);
            drawHexes(view);
        }
        if (!boardRect.union(view).equals(boardRect)) {
            moveBoardImage();
        }
    }

    /** 
     * This method creates an image the size of the entire board (all
     * mapsheets), draws the hexes onto it, and returns that image.
     */
    public Image getEntireBoardImage() {
        Image entireBoard = createImage(boardSize.width, boardSize.height);
        Graphics temp = boardImage.getGraphics();
        boardGraph = entireBoard.getGraphics();
        drawHexes(new Rectangle(boardSize));
        boardGraph = temp;
        return entireBoard;
    }

    /**
     * Moves the board view to another area.
     */
    private void moveBoardImage() {
        // salvage the old

        boardGraph.setClip(0, 0, boardRect.width, boardRect.height);
        boardGraph.copyArea(0, 0, boardRect.width, boardRect.height,
                            boardRect.x - view.x, boardRect.y - view.y);

        // what's left to paint?
        int midX = Math.max(view.x, boardRect.x);
        int midWidth = view.width - Math.abs(view.x - boardRect.x);
        Rectangle unLeft = new Rectangle(view.x, view.y, boardRect.x - view.x, view.height);
        Rectangle unRight = new Rectangle(boardRect.x + boardRect.width, view.y, view.x -boardRect.x, view.height);
        Rectangle unTop = new Rectangle(midX, view.y, midWidth, boardRect.y - view.y);
        Rectangle unBottom = new Rectangle(midX, boardRect.y + boardRect.height, midWidth, view.y - boardRect.y);

        // update boardRect
        boardRect = new Rectangle(view);

        // paint needed areas
        if (unLeft.width > 0) {
            drawHexes(unLeft);
        } else if (unRight.width > 0) {
            drawHexes(unRight);
        }
        if (unTop.height > 0) {
            drawHexes(unTop);
        } else if (unBottom.height > 0) {
            drawHexes(unBottom);
        }
    }

    /**
     * Redraws all hexes in the specified rectangle
     */
    private void drawHexes(Rectangle rect) {
    	
    	// rect is the view
        int drawX = (int)(rect.x / (HEX_WC*scale))-1;
        int drawY = (int)(rect.y / (HEX_H*scale))-1;

        int drawWidth = (int)(rect.width / (HEX_WC*scale))+3;
        int drawHeight = (int)(rect.height / (HEX_H*scale))+3;

        // only draw what we came to draw
        boardGraph.setClip(rect.x - boardRect.x, rect.y - boardRect.y,
                           rect.width, rect.height);

        // clear, if we need to
        if (rect.x < (21*scale)) {
            boardGraph.clearRect(
            		rect.x - boardRect.x, rect.y - boardRect.y,
            		(int)(21*scale) - rect.x, rect.height);
        }
        if (rect.y < (36*scale)) {
            boardGraph.clearRect(
            		rect.x - boardRect.x, rect.y - boardRect.y,
            		rect.width, (int)(36*scale) - rect.y);
        }
        if (rect.x > boardSize.width - view.width - (21*scale)) {
            boardGraph.clearRect(
            		boardRect.width - (int)(21*scale), rect.y - boardRect.y,
					(int)(21*scale), rect.height);
        }
        if (rect.y > boardSize.height - view.height - (int)(36*scale)) {
            boardGraph.clearRect(
            		rect.x - boardRect.x, boardRect.height - (int)(36*scale),
            		rect.width, (int)(36*scale));
        }

        // draw some hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                drawHex(new Coords(j + drawX, i + drawY));
            }
        }
    }

    /**
     * Redraws a hex and all the hexes immediately around it.  Used when the
     * hex is on the screen, as opposed to when it is scrolling onto the screen,
     * so it resets the clipping rectangle before drawing.
     */
    private void redrawAround(Coords c) {
        boardGraph.setClip(0, 0, boardRect.width, boardRect.height);
        drawHex(c);
        drawHex(c.translated(0));
        drawHex(c.translated(1));
        drawHex(c.translated(2));
        drawHex(c.translated(3));
        drawHex(c.translated(4));
        drawHex(c.translated(5));
    }

    /**
     * Draws a hex onto the board buffer.  This assumes that boardRect is
     * current, and does not check if the hex is visible.
     */
    private void drawHex(Coords c) {
        if (!game.board.contains(c)) {
            return;
        }

        final Hex hex = game.board.getHex(c);
        final Point hexLoc = getHexLocation(c);

        int level = hex.getElevation();
        int depth = hex.depth();

        // offset drawing point
        
        int drawX = hexLoc.x - boardRect.x;
        int drawY = hexLoc.y - boardRect.y;

        // draw picture
        Image baseImage = tileManager.baseFor(hex);
        Image scaledImage = getScaledImage(baseImage, scale);
        
        boardGraph.drawImage(scaledImage, drawX, drawY, this);
        
        if (tileManager.supersFor(hex) != null) {
            for (Iterator i = tileManager.supersFor(hex).iterator(); i.hasNext();){
                scaledImage = getScaledImage((Image)i.next(), scale);
                boardGraph.drawImage(scaledImage, drawX, drawY, this);
            }
        }
        
        boardGraph.setColor(Settings.mapTextColor);
        
        // draw hex number
        if (scale >= 0.5){
        	drawCenteredString(
        			c.getBoardNum(),
					drawX,
					drawY + (int)(12*scale),
					font_hexnum,
					boardGraph);
        }
        // level | depth
        if (level != 0 && depth == 0 && zoomIndex > 3) {
        	drawCenteredString(
        			"LEVEL " + level,
        			drawX,
        			drawY + (int)(70*scale),
					font_elev,
					boardGraph);
        } else if (depth != 0 && level == 0  && zoomIndex > 3 ) {
        	drawCenteredString(
        			"DEPTH " + depth,
        			drawX,
					drawY + (int)(70*scale),
					font_elev,
					boardGraph);
        } else if (level != 0 && depth != 0  && zoomIndex > 3) {
        	drawCenteredString(
       				"LEVEL " + level,
        			drawX,
					drawY + (int)(60*scale),
					font_elev,
					boardGraph);
       		drawCenteredString(
       				"DEPTH " + depth,
        			drawX,
					drawY + (int)(70*scale),
					font_elev,
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

    }

    /**
     * Returns true if an elevation line should be drawn between the starting
     * hex and the hex in the direction specified.  Results should be
     * transitive, that is, if a line is drawn in one direction, it should be
     * drawn in the opposite direction as well.
     */
    private final boolean drawElevationLine(Coords src, int direction) {
        final Hex srcHex = game.board.getHex(src);
        final Hex destHex = game.board.getHexInDir(src, direction);
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
    // end kenn

    /**
     * Returns the coords at the specified point
     */
    Coords getCoordsAt(Point p) {
        final int x = (p.x + scroll.x - offset.x) / (int)(HEX_WC*scale);
        final int y = ((p.y + scroll.y - offset.y) - ((x & 1) == 1 ? (int)(HEX_H/2*scale) : 0)) / (int)(HEX_H*scale);
        return new Coords(x, y);
    }
    
    /**
     * Shows the tooltip thinger
     */
    private void showTooltip() {
        try {
            final Point tipLoc = new Point(getLocationOnScreen());
            // retrieve tip text
            String[] tipText = getTipText(mousePos);
            if (tipText == null) {
                return;
            }

            // update tip text
            tipWindow.removeAll();
            tipWindow.add(new TooltipCanvas(tipText));
            tipWindow.pack();

            tipLoc.translate(mousePos.x, mousePos.y + 20);

            // adjust horizontal location for the tipWindow if it goes off the frame
            if (frame.getLocation().x + frame.getSize().width < tipLoc.x + tipWindow.getSize().width + 10) {
                if (frame.getSize().width > tipWindow.getSize().width) {
                    // bound it by the right edge of the frame
                    tipLoc.x -= tipLoc.x + tipWindow.getSize().width + 10 - frame.getSize().width - frame.getLocation().x;
                }
                else {
                    // too big to fit, left justify to the frame (roughly).
                    // how do I extract the first term of HEX_SIZE to use here?--LDE
                	tipLoc.x = getLocationOnScreen().x + hex_size.width;
                }
            }

            // set tip location
            tipWindow.setLocation(tipLoc);

            tipWindow.show();
        } catch (Exception e) {
            tipWindow = new Window(frame);
        }
    }

    /**
     * The text to be displayed when the mouse is at a certain point
     */
    private String[] getTipText(Point point) {

        int stringsSize = 0;
        Hex mhex = null;

        // first, we have to determine how much text we are going to have
        // are we on a hex?
        final Coords mcoords = getCoordsAt(point);
        if (Settings.showMapHexPopup && game.board.contains(mcoords)) {
            mhex = game.board.getHex(mcoords);
            stringsSize += 1;
        }

        // check if it's on any entities
        for (Iterator i = entitySprites.iterator(); i.hasNext();) {
            final EntitySprite eSprite = (EntitySprite)i.next();
            if (eSprite.isInside(point)) {
                stringsSize += 3;
            }
        }

        // check if it's on any attacks
        for (Iterator i = attackSprites.iterator(); i.hasNext();) {
            final AttackSprite aSprite = (AttackSprite)i.next();
            if (aSprite.isInside(point)) {
                stringsSize += 1 + aSprite.weaponDescs.size();
            }
        }

        // If the hex contains a building or rubble, make more space.
        if ( mhex != null &&
             (mhex.contains(Terrain.RUBBLE) ||
              mhex.contains(Terrain.BUILDING)) ) {
            stringsSize += 1;
        }

        stringsSize += game.getNbrMinefields(mcoords);

        // if the size is zip, you must a'quit
        if (stringsSize == 0) {
            return null;
        }

        // now we can allocate an array of strings
        String[] strings = new String[stringsSize];
        int stringsIndex = 0;

        // are we on a hex?
        if (mhex != null) {
            strings[stringsIndex] = "Hex " + mcoords.getBoardNum()
                + "; level " + mhex.getElevation();
            stringsIndex += 1;

            // Do we have rubble?
            if ( mhex.contains(Terrain.RUBBLE) ) {
                strings[stringsIndex] = "Rubble";
                stringsIndex += 1;
            }

            // Do we have a building?
            else if ( mhex.contains(Terrain.BUILDING) ) {
                // Get the building.
                Building bldg = game.board.getBuildingAt( mcoords );
                StringBuffer buf = new StringBuffer( "Height " );
                // Each hex of a building has its own elevation.
                buf.append( mhex.levelOf(Terrain.BLDG_ELEV) );
                buf.append( " " );
                buf.append( bldg.toString() );
                buf.append( ", CF: " );
                buf.append( bldg.getCurrentCF() );
                strings[stringsIndex] = buf.toString();
                stringsIndex += 1;
            }

            if (game.containsMinefield(mcoords)) {
                java.util.Vector minefields = game.getMinefields(mcoords);
                for (int i = 0; i < minefields.size(); i++){
                    Minefield mf = (Minefield) minefields.elementAt(i);
                    String owner =  " (" + game.getPlayer(mf.getPlayerId()).getName() + ")";

                    switch (mf.getType()) {
                    case (Minefield.TYPE_CONVENTIONAL) :
                        strings[stringsIndex] = "Conventional minefield " + owner;
                        break;
                    case (Minefield.TYPE_THUNDER) :
                        strings[stringsIndex] = "Thunder minefield(" + mf.getDamage() + ")" + owner;
                        break;
                    case (Minefield.TYPE_COMMAND_DETONATED) :
                        strings[stringsIndex] = "Command-detonated minefield " + owner;
                        break;
                    case (Minefield.TYPE_VIBRABOMB) :
                        if (mf.getPlayerId() == localPlayer.getId()) {
                            strings[stringsIndex] = "Vibrabomb minefield(" + mf.getSetting() + ") " + owner;
                        } else {
                            strings[stringsIndex] = "Vibrabomb minefield" + owner;
                        }
                        break;
                    case (Minefield.TYPE_THUNDER_ACTIVE) :
                    	strings[stringsIndex] = "Thunder-Active minefield(" + mf.getDamage() + ")" + owner;
                    	break;
					case (Minefield.TYPE_THUNDER_INFERNO) :
						strings[stringsIndex] = "Thunder-Inferno minefield(" + mf.getDamage() + ")" + owner;
						break;
                    	
                    }
                    stringsIndex++;
                }
            }
        }
        // check if it's on any entities
        for (Iterator i = entitySprites.iterator(); i.hasNext();) {
            final EntitySprite eSprite = (EntitySprite)i.next();
            if (eSprite.isInside(point)) {
                final String[] entityStrings = eSprite.getTooltip();
                java.lang.System.arraycopy(entityStrings, 0, strings, stringsIndex, entityStrings.length);
                stringsIndex += entityStrings.length;
            }
        }

        // check if it's on any attacks
        for (Iterator i = attackSprites.iterator(); i.hasNext();) {
            final AttackSprite aSprite = (AttackSprite)i.next();
            if (aSprite.isInside(point)) {
                final String[] attackStrings = aSprite.getTooltip();
                java.lang.System.arraycopy(attackStrings, 0, strings, stringsIndex, attackStrings.length);
                stringsIndex += 1 + aSprite.weaponDescs.size();
            }
        }

        return strings;
    }

    /**
     * Hides the tooltip thinger
     */
    public void hideTooltip() {
        tipWindow.setVisible(false);
    }

    /**
     * Returns true if the tooltip is showing
     */
    private boolean isTipShowing() {
        return tipWindow.isShowing();
    }

    /**
     * Checks if the mouse has been idling for a while and if so, shows the
     * tooltip window
     */
    private void checkTooltip() {
        if (isTipShowing()) {
            if (!isTipPossible) {
                hideTooltip();
            }
        } else if (isTipPossible && System.currentTimeMillis() - lastIdle > Settings.tooltipDelay) {
            showTooltip();
        }
    }

    public void redrawMovingEntity(Entity entity, Coords position, int facing) {
        Integer entityId = new Integer( entity.getId() );
        EntitySprite sprite = (EntitySprite) entitySpriteIds.get( entityId );
        Vector newSprites;
        Hashtable newSpriteIds;

        if (sprite != null) {
            newSprites = new Vector(entitySprites);
            newSpriteIds = new Hashtable(entitySpriteIds);

            newSprites.removeElement(sprite);

            entitySprites = newSprites;
            entitySpriteIds = newSpriteIds;
        }

        MovingEntitySprite mSprite =
            (MovingEntitySprite) movingEntitySpriteIds.get( entityId );
        newSprites = new Vector(movingEntitySprites);
        newSpriteIds = new Hashtable(movingEntitySpriteIds);


        if (mSprite != null) {
            newSprites.removeElement(mSprite);
        }

        if (entity.getPosition() != null) {
            mSprite = new MovingEntitySprite(entity, position, facing);
            newSprites.addElement(mSprite);
            newSpriteIds.put( entityId, mSprite );
        }

        movingEntitySprites = newSprites;
        movingEntitySpriteIds = newSpriteIds;
    }

    public boolean isMovingUnits() {
        return movingUnits.size() > 0;
    }

    /**
     * Clears the sprite for an entity and prepares it to be re-drawn.  Replaces
     * the old sprite with the new!
     *
     *  Try to prevent annoying ConcurrentModificationExceptions
     */
    public void redrawEntity(Entity entity) {
        Integer entityId = new Integer( entity.getId() );
        EntitySprite sprite = (EntitySprite)entitySpriteIds.get( entityId );
        Vector newSprites = new Vector(entitySprites);
        Hashtable newSpriteIds = new Hashtable(entitySpriteIds);


        if (sprite != null) {
            newSprites.removeElement(sprite);
        }

        if (entity.getPosition() != null) {
            sprite = new EntitySprite(entity);
            newSprites.addElement(sprite);
            newSpriteIds.put( entityId, sprite );
        }

        entitySprites = newSprites;
        entitySpriteIds = newSpriteIds;

        for (java.util.Enumeration i = C3Sprites.elements(); i.hasMoreElements();) {
            final C3Sprite c3sprite = (C3Sprite)i.nextElement();
            if (c3sprite.entityId == entity.getId())
                C3Sprites.removeElement(c3sprite);
            else if(c3sprite.masterId == entity.getId()) {
                // Only redraw client-to-master; otherwise
                // we leave stray lines when we move.
                if(entity.hasC3()) {
                    C3Sprites.addElement(new C3Sprite(game.getEntity(c3sprite.entityId), game.getEntity(c3sprite.masterId)));
                }
                C3Sprites.removeElement(c3sprite);

            }
        }

        if(entity.hasC3() || entity.hasC3i()) addC3Link(entity);

        repaint(100);
    }

    /**
     * Clears all old entity sprites out of memory and sets up new ones.
     */
    private void redrawAllEntities() {
        Vector newSprites = new Vector(game.getNoOfEntities());
        Hashtable newSpriteIds = new Hashtable(game.getNoOfEntities());
        Vector newWrecks = new Vector();

        Enumeration e = game.getWreckedEntities();
        while (e.hasMoreElements()) {
            Entity entity = (Entity) e.nextElement();
            if (!(entity instanceof Infantry) && (entity.getPosition() != null)) {
                WreckSprite ws = new WreckSprite(entity);
                newWrecks.addElement(ws);
            }
        }

        clearC3Networks();
        for (java.util.Enumeration i = game.getEntities(); i.hasMoreElements();) {
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

        repaint(100);
    }

    /**
     * Moves the cursor to the new position, or hides it, if newPos is null
     */
    private void moveCursor(CursorSprite cursor, Coords newPos) {
        final Rectangle oldBounds = new Rectangle(cursor.getBounds());
        if (newPos != null) {
            //cursor.setLocation(getHexLocation(newPos));
            cursor.setHexLocation(newPos);
        } else {
            cursor.setOffScreen();
        }
        // repaint affected area
        repaintBounds(oldBounds);
        repaintBounds(cursor.getBounds());
    }


    public void centerOnHex(Coords c) {
        if ( null == c ) return;
        scroll.setLocation(getHexLocation(c));
        scroll.translate((int)(42*scale) - (view.width / 2), (int)(36*scale) - (view.height / 2));

        isScrolling = false;
        checkScrollBounds();
        repaint();
    }

    /**
     * Clears the old movement data and draws the new.  Since it's less
     * expensive to check for and reuse old step sprites than to make a whole
     * new one, we do that.
     */
    public void drawMovementData(Entity entity, MovePath md) {
        Vector temp = pathSprites;

        clearMovementData();

        for (java.util.Enumeration i = md.getSteps(); i.hasMoreElements();) {
            final MoveStep step = (MoveStep)i.nextElement();
            // check old movement path for reusable step sprites
            boolean found = false;
            for (Iterator j = temp.iterator(); j.hasNext();) {
                final StepSprite sprite = (StepSprite)j.next();
                if (sprite.getStep().canReuseSprite(step)) {
                    pathSprites.addElement(sprite);
                    found = true;
                }
            }
            if (!found) {
                pathSprites.addElement(new StepSprite(step));
            }
        }
    }

    /**
     * Clears current movement data from the screen
     */
    public void clearMovementData() {
        Vector temp = pathSprites;
        pathSprites = new Vector();
        for (Iterator i = temp.iterator(); i.hasNext();) {
            final Sprite sprite = (Sprite)i.next();
            repaintBounds(sprite.getBounds());
        }
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
                    C3Sprites.addElement(new C3Sprite(e, fe));
                }
            }
        }
        else if(e.getC3Master() != null) {
            Entity eMaster = e.getC3Master();
            if (eMaster.getPosition() == null) return;

            // ECM cuts off the network
            if (!Compute.isAffectedByECM(e, e.getPosition(), eMaster.getPosition())) {
                C3Sprites.addElement(new C3Sprite(e, e.getC3Master()));
            }
        }
    }

    public void boardNewAttack(BoardEvent e) {
        AttackAction aa = (AttackAction)e.getSource();
        addAttack(aa);
    }
    
    /**
     * Adds an attack to the sprite list.
     */
    public void addAttack(AttackAction aa) {
        // do not make a sprite unless we're aware of both entities
        // this is not a great solution but better than a crash
        Entity ae = game.getEntity(aa.getEntityId());
        Targetable t = game.getTarget(aa.getTargetType(), aa.getTargetId());
        if (ae == null || t == null || t.getTargetType() == Targetable.TYPE_INARC_POD) {
            return;
        }

        for (final Iterator i = attackSprites.iterator(); i.hasNext();) {
            final AttackSprite sprite = (AttackSprite)i.next();

            // can we just add this attack to an existing one?
            if (sprite.getEntityId() == aa.getEntityId()
                && sprite.getTargetId() == aa.getTargetId()) {
                // use existing attack, but add this weapon
                if (aa instanceof WeaponAttackAction) {
                    WeaponAttackAction waa = (WeaponAttackAction)aa;
                    if ( aa.getTargetType() != Targetable.TYPE_HEX_ARTILLERY 
                        && aa.getTargetType() != Targetable.TYPE_HEX_FASCAM
                        && aa.getTargetType() != Targetable.TYPE_HEX_INFERNO_IV
                        && aa.getTargetType() != Targetable.TYPE_HEX_VIBRABOMB_IV) {
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
                return;
            }
        }
        // no re-use possible, add a new one
        // don't add a sprite for an artillery attack made by the other player
        if (aa instanceof WeaponAttackAction) {
            WeaponAttackAction waa = (WeaponAttackAction)aa;
            if ( aa.getTargetType() != Targetable.TYPE_HEX_ARTILLERY 
                && aa.getTargetType() != Targetable.TYPE_HEX_FASCAM
                && aa.getTargetType() != Targetable.TYPE_HEX_INFERNO_IV
                && aa.getTargetType() != Targetable.TYPE_HEX_VIBRABOMB_IV) {
                attackSprites.addElement(new AttackSprite(aa));
            } else if ( waa.getEntity(game).getOwner().getId() == localPlayer.getId()) {
                attackSprites.addElement(new AttackSprite(aa));
            }
        } else {
            attackSprites.addElement(new AttackSprite(aa));
        }
    }

    /** Removes all attack sprites from a certain entity */
    public void removeAttacksFor(int entityId) {
        // or rather, only keep sprites NOT for that entity
        Vector toKeep = new Vector(attackSprites.size());
        for (Iterator i = attackSprites.iterator(); i.hasNext();) {
            AttackSprite sprite = (AttackSprite)i.next();
            if (sprite.getEntityId() != entityId) {
                toKeep.addElement(sprite);
            }
        }
        this.attackSprites = toKeep;
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
            if (ea instanceof AttackAction) {
                addAttack((AttackAction)ea);
            }
        }
    }

    public void clearC3Networks() {
        C3Sprites.removeAllElements();
    }

    /**
     * Clears out all attacks that were being drawn
     */
    public void clearAllAttacks() {
        attackSprites.removeAllElements();
    }

    public Image baseFor(Hex hex) {
        return tileManager.baseFor(hex);
    }

    public com.sun.java.util.collections.List supersFor(Hex hex) {
        return tileManager.supersFor(hex);
    }



    public void boardHexMoused(BoardEvent b) {
    }
    public void boardHexCursor(BoardEvent b) {
        moveCursor(cursorSprite, b.getCoords());
        moveCursor(firstLOSSprite, null);
        moveCursor(secondLOSSprite, null);
    }
    public void boardHexSelected(BoardEvent b) {
        moveCursor(selectedSprite, b.getCoords());
        moveCursor(firstLOSSprite, null);
        moveCursor(secondLOSSprite, null);
    }
    public void boardHexHighlighted(BoardEvent b) {
        moveCursor(highlightSprite, b.getCoords());
        moveCursor(firstLOSSprite, null);
        moveCursor(secondLOSSprite, null);
    }
    public void boardFirstLOSHex(BoardEvent b) {
        if (useLOSTool) {
            moveCursor(secondLOSSprite, null);
            moveCursor(firstLOSSprite, b.getCoords());
        }
    }
    public void boardSecondLOSHex(BoardEvent b, Coords c1) {
        if (useLOSTool) {
            Coords c2 = b.getCoords();
            moveCursor(firstLOSSprite, c1);
            moveCursor(secondLOSSprite, c2);

            LosEffects.AttackInfo ai = new LosEffects.AttackInfo();
            ai.attackPos = c1;
            ai.targetPos = c2;
            ai.attackHeight = game.getMechInFirst()?1:0;
            ai.targetHeight = game.getMechInSecond()?1:0;
            ai.attackAbsHeight = game.getBoard().getHex(c1).floor() + ai.attackHeight;
            ai.targetAbsHeight = game.getBoard().getHex(c2).floor() + ai.targetHeight;

            LosEffects le = LosEffects.calculateLos(game, ai);
            StringBuffer message = new StringBuffer();
            message.append( "Attacker(")
                .append(game.getMechInFirst() ? "Mech" : "non Mech")
                .append(") hex is " )
                .append( c1.getBoardNum() )
                .append( ".\n" );
            message.append( "Target(")
                .append(game.getMechInSecond() ? "Mech" : "non Mech")
                .append(") hex is " )
                .append( c2.getBoardNum() )
                .append( ".\n" );
            if (!le.canSee()) {
                message.append( "Line of sight is blocked.\n" );
                message.append( "Range is " )
                    .append( c1.distance(c2) )
                    .append( " hex(es).\n");
            } else {
                message.append( "Line of sight is not blocked.\n" );
                message.append( "Range is " )
                    .append( c1.distance(c2) )
                    .append( " hex(es).\n");
                if (le.getHeavyWoods() > 0) {
                    message.append( le.getHeavyWoods() )
                        .append( " heavy wood hex(es) in line of sight.\n" );
                }
                if (le.getLightWoods() > 0) {
                    message.append( le.getLightWoods() )
                        .append( " light wood hex(es) in line of sight.\n" );
                }
                if (le.getLightSmoke() > 0) {
                    message.append( le.getLightSmoke() )
                        .append( " light smoke hex(es) in line of sight.\n" );
                }
                if (le.getHeavySmoke() > 0) {
                    message.append( le.getHeavySmoke() );
                    if (game.getOptions().booleanOption("maxtech_fire"))
                        message.append( " heavy");
                    message.append( " smoke hex(es) in line of sight.\n" );
                }
                if (le.isTargetCover()) {
                    message.append("Target has partial cover.\n");
                }
                if (le.isAttackerCover()) {
                    message.append("Attacker has partial cover.\n");
                }
            }
            AlertDialog alert = new AlertDialog(frame,
                                                "Range / Line of Sight",
                                                message.toString(), false);
            alert.show();
        }
    }
    public void boardChangedHex(BoardEvent b) {
        tileManager.waitForHex(game.getBoard().getHex(b.getCoords()));
        if (boardGraph != null) {
            redrawAround(b.getCoords());
        }
    }
    public void boardNewBoard(BoardEvent b) {
        updateBoardSize();
        backGraph = null;
        backImage = null;
        backSize = null;
        boardImage = null;
        boardGraph = null;
        tileManager.reset();
    }

    public void boardNewEntities(BoardEvent e) {
        redrawAllEntities();
    }
    public void boardChangedEntity(BoardEvent e) {
        redrawEntity(e.getEntity());
    }



    /**
     * If the mouse is at the edges of the screen, this
     * scrolls the board image on the canvas.
     * NOTE: CTL scroll is handled in mouseMoved()
     */
    public boolean doScroll() {
        final Point oldScroll = new Point(scroll);
        boolean s = false;
        
        if ( isScrolling && Settings.rightDragScroll) {
            if (! (oldMousePosition == null || mousePos.equals(oldMousePosition)) ) {
                scroll.x -= Settings.getScrollSensitivity() * (mousePos.x - oldMousePosition.x);
                scroll.y -= Settings.getScrollSensitivity() * (mousePos.y - oldMousePosition.y);
                checkScrollBounds();
                oldMousePosition.setLocation(mousePos);
                s = !oldScroll.equals(scroll);
                scrolled = scrolled || s;
            }
        }

        if (isScrolling && (Settings.clickEdgeScroll || Settings.autoEdgeScroll) ) {
            final int sf = Settings.getScrollSensitivity(); // scroll factor
            // adjust x scroll
            // scroll when the mouse is at the edges
            if (mousePos.x < 100) {
                scroll.x -= (100 - mousePos.x) / sf;
            } else if (mousePos.x > (backSize.width - 100)) {
                scroll.x -= ((backSize.width - 100) - mousePos.x) / sf;
            }
            // scroll when the mouse is at the edges
            if (mousePos.y < 100) {
                scroll.y -= (100 - mousePos.y) / sf;
            } else if (mousePos.y > (backSize.height - 100)) {
                scroll.y -= ((backSize.height - 100) - mousePos.y) / sf;
            }
            checkScrollBounds();
            if (!oldScroll.equals(scroll)) {
                //            repaint();
                s = true;
                scrolled = s;
            }
        }

        return s;
    }

    /**
     * Makes sure that the scroll dimensions stay in bounds
     */
    public void checkScrollBounds() {
        if (scroll.x < 0) {
            scroll.x = 0;
        } else if (scroll.x > (boardSize.width - view.width)) {
            scroll.x = (boardSize.width - view.width);
        }

        if (scroll.y < 0) {
            scroll.y = 0;
        } else if (scroll.y > (boardSize.height - view.height)) {
            scroll.y = (boardSize.height - view.height);
        }

        // Update our scroll bars.
        if (null != this.vScrollbar) {
            this.vScrollbar.setValue (scroll.y);
        }
        if (null != this.hScrollbar) {
            this.hScrollbar.setValue (scroll.x);
        }
    }

    protected void stopScrolling() {
        isScrolling = false;
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


    //
    // Runnable
    //
    public void run() {
        final Thread currentThread = Thread.currentThread();
        long lastTime = System.currentTimeMillis();
        long currentTime = System.currentTimeMillis();
        while (scroller == currentThread) {
            try {
                Thread.sleep(20);
            } catch(InterruptedException ex) {
                // duh?
            }
            if (!isShowing()) {
                currentTime = System.currentTimeMillis();
                lastTime = currentTime;
                continue;
            }
            currentTime = System.currentTimeMillis();
            boolean redraw = false;
            for (int i = 0; i < displayables.size(); i++) {
                Displayable disp = (Displayable) displayables.elementAt(i);
                if (!disp.isSliding()) {
                    disp.setIdleTime(currentTime - lastTime, true);
                } else {
                    redraw = redraw || disp.slide();
                }
            }
            if (backSize != null) {
                redraw = redraw || doMoveUnits(currentTime - lastTime);
                redraw = redraw || doScroll();
                checkTooltip();
            } else {
                repaint(100);
            }
            if (redraw) {
                repaint();
            }
            lastTime = currentTime;
        }
    }


    private synchronized boolean doMoveUnits(long idleTime) {
        boolean movingSomething = false;

        if (movingUnits.size() > 0) {

            moveWait += idleTime;

            if (moveWait > Settings.moveStepDelay) {

                java.util.Vector spent = new java.util.Vector();

                for (int i = 0; i < movingUnits.size(); i++) {
                    Object[] move = (Object[]) movingUnits.elementAt(i);
                    Entity e = (Entity) move[0];
                    java.util.Vector movePath = (java.util.Vector) move[1];
                    movingSomething = true;

                    if (movePath.size() > 0) {
                        int j = ((Integer) movePath.elementAt(0)).intValue();
                        int y = j & 255;
                        int x = (j >> 8) & 255;
                        int facing = (j >> 16) & 255;

                        redrawMovingEntity(e, new Coords(x, y), facing);
                        movePath.removeElementAt(0);
                    } else {
                        redrawEntity(game.getEntity(e.getId()));
                        spent.addElement(move);
                    }

                }

                for (int i = 0; i < spent.size(); i++) {
                    Object[] move = (Object[]) spent.elementAt(i);
                    movingUnits.removeElement(move);
                }
                moveWait = 0;

                if (movingUnits.size() == 0) {
                    movingEntitySpriteIds.clear();
                    movingEntitySprites.removeAllElements();
                    ghostEntitySprites.removeAllElements();
                    processBoardViewEvent(new BoardViewEvent(this, BoardViewEvent.FINISHED_MOVING_UNITS));
                }
            }
        }
        return movingSomething;
    }

    //
    // KeyListener
    //
    public void keyPressed(KeyEvent ke) {
        switch(ke.getKeyCode()) {
        case KeyEvent.VK_NUMPAD7 :
            scroll.y -= 36;
            scroll.x -= 36;
            break;
        case KeyEvent.VK_NUMPAD8 :
            scroll.y -= 36;
            break;
        case KeyEvent.VK_NUMPAD9 :
            scroll.y -= 36;
            scroll.x += 36;
            break;
        case KeyEvent.VK_NUMPAD1 :
            scroll.y += 36;
            scroll.x -= 36;
            break;
        case KeyEvent.VK_NUMPAD2 :
            scroll.y += 36;
            break;
        case KeyEvent.VK_NUMPAD3 :
            scroll.y += 36;
            scroll.x += 36;
            break;
        case KeyEvent.VK_NUMPAD4 :
            scroll.x -= 36;
            break;
        case KeyEvent.VK_NUMPAD6 :
            scroll.x += 36;
            break;
        case KeyEvent.VK_NUMPAD5 :
            // center on the selected entity
            java.util.Vector v = game.getPlayerEntities(localPlayer);
            for (int i = 0; i < v.size(); i++) {
                Entity e = (Entity) v.elementAt(i);
                if (e.isSelected()) {
                    centerOnHex(e.getPosition());
                };
            };
            break;
        case KeyEvent.VK_CONTROL :
            ctlKeyHeld = true;
            initCtlScroll = true;
            break;
        }

        if (isTipShowing()) {
            hideTooltip();
        }
        lastIdle = System.currentTimeMillis();
        checkScrollBounds();
        repaint();
    }

    public void keyReleased(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_CONTROL) {
            ctlKeyHeld = false;
        }
    }
    public void keyTyped(KeyEvent ke) {
        ;
    }

    //
    // MouseListener
    //
    public void mousePressed(MouseEvent me) {
        scrolled = false; // not scrolled yet

        Point point = me.getPoint();
        if ( null == point ) {
            return;
        }
        oldMousePosition = point;

        isTipPossible = false;
        for (int i = 0; i < displayables.size(); i++) {
            Displayable disp = (Displayable) displayables.elementAt(i);
            if (disp.isHit(point, backSize)) {
                return;
            }
        }

        // Disable scrolling when ctrl or alt is held down, since this
        //  means the user wants to use the LOS/ruler tools.
        int mask = InputEvent.CTRL_MASK | InputEvent.ALT_MASK;
        if ( !Settings.rightDragScroll &&
            !Settings.alwaysRightClickScroll &&    
            game.getPhase() == Game.PHASE_FIRING ) {
            // In the firing phase, also disable scrolling if
            // the right or middle buttons are clicked, since
            // this means the user wants to activate the
            // popup menu or ruler tool.
            mask |= InputEvent.BUTTON2_MASK | InputEvent.BUTTON3_MASK;
        }

        // disable auto--edge-scrolling if no option set
        if ( !Settings.autoEdgeScroll ) {
            mask |= InputEvent.BUTTON1_MASK;
        };
        // disable edge-scrolling if no option set
        if ( !Settings.clickEdgeScroll ) {
            mask |= InputEvent.BUTTON3_MASK;
        };
        
        if ( Settings.rightDragScroll ) {
            mask |= InputEvent.BUTTON2_MASK;
        };

        if ( (me.getModifiers() & mask ) == 0 ) {
            isScrolling = true; //activate scrolling
        } else {
            isScrolling = false; //activate scrolling
        };

        if (isTipShowing()) {
            hideTooltip();
        }

        game.board.mouseAction(getCoordsAt(point), Board.BOARD_HEX_DRAG, me.getModifiers());
    }

    public void mouseReleased(MouseEvent me) {
        isTipPossible = true;
        oldMousePosition = mousePos;

        for (int i = 0; i < displayables.size(); i++) {
            Displayable disp = (Displayable) displayables.elementAt(i);
            if (disp.isReleased()) {
                return;
            }
        }
        isScrolling = false;

        // no click action triggered if click was for scrolling the map. Real clicks are without scrolling.
        if (scrolled)
            return;
        if (me.getClickCount() == 1) {
            game.board.mouseAction(getCoordsAt(me.getPoint()), Board.BOARD_HEX_CLICK, me.getModifiers());
        } else {
            game.board.mouseAction(getCoordsAt(me.getPoint()), Board.BOARD_HEX_DOUBLECLICK, me.getModifiers());
        }
    }

    public void mouseEntered(MouseEvent me) {
        ;
    }

    public void mouseExited(MouseEvent me) {
        isTipPossible = false;
    }
    public void mouseClicked(MouseEvent me) {
        ;
    }

    //
    // MouseMotionListener
    //
    public void mouseDragged(MouseEvent me) {
        isTipPossible = false;

        Point point = me.getPoint();
        if ( null == point ) {
            return;
        }

        for (int i = 0; i < displayables.size(); i++) {
            Displayable disp = (Displayable) displayables.elementAt(i);
            if (disp.isDragged(point, backSize)) {
                repaint();
                return;
            }
        }
        mousePos = point;

        // Disable scrolling when ctrl or alt is held down, since this
        //  means the user wants to use the LOS/ruler tools.
        int mask = InputEvent.CTRL_MASK | InputEvent.ALT_MASK;

        if ( !Settings.rightDragScroll &&
            !Settings.alwaysRightClickScroll &&
            game.getPhase() == Game.PHASE_FIRING) {
            // In the firing phase, also disable scrolling if
            //  the right or middle buttons are clicked, since
            //  this means the user wants to activate the
            //  popup menu or ruler tool.
            mask |= InputEvent.BUTTON2_MASK | InputEvent.BUTTON3_MASK;
        };

        if ( Settings.rightDragScroll ) {
            mask |= InputEvent.BUTTON1_MASK | InputEvent.BUTTON2_MASK;
        };

        // disable auto--edge-scrolling if no option set
        if ( !Settings.autoEdgeScroll ) {
            mask |= InputEvent.BUTTON1_MASK;
        };

        // disable edge-scrolling if no option set
        if ( !Settings.clickEdgeScroll && !Settings.rightDragScroll ) {
            mask |= InputEvent.BUTTON3_MASK;
        };
        
        if ( (me.getModifiers() & mask ) == 0 ) {
            isScrolling = true; //activate scrolling
        } else {
            isScrolling = false; //activate scrolling
        };

        game.board.mouseAction(getCoordsAt(point), Board.BOARD_HEX_DRAG, me.getModifiers());
    }

    public void mouseMoved(MouseEvent me) {
        Point point = me.getPoint();
        if ( null == point ) {
            return;
        }

        for (int i = 0; i < displayables.size(); i++) {
            Displayable disp = (Displayable) displayables.elementAt(i);
            if (disp.isBeingDragged()) {
                isTipPossible = false;
                return;
            }
            if (backSize != null) {
                disp.isMouseOver(point, backSize);
            }
        }
        mousePos = point;
        if (isTipShowing()) {
            hideTooltip();
        }
        if (ctlKeyHeld && Settings.ctlScroll) {
            if (initCtlScroll) {
                previousMouseX = me.getX();
                previousMouseY = me.getY();
                initCtlScroll = false;
            }
            scroll.x += Settings.getScrollSensitivity() * (me.getX() - previousMouseX);
            scroll.y += Settings.getScrollSensitivity() * (me.getY() - previousMouseY);
            previousMouseX = me.getX();
            previousMouseY = me.getY();
            checkScrollBounds();
            repaint();
        }
        lastIdle = System.currentTimeMillis();
        isTipPossible = true;
    }

    /**
     * Increases zoomIndex and refreshes the map.
     * 
     */
    public void zoomIn(){
    	int tmpZoomIndex = zoomIndex + 1;
    	if ( isJ2RE == true ){
    		zoomIndex++;
			zoom();
		}
    }

    /**
     * Decreases zoomIndex and refreshes the map.
     *
     */
    public void zoomOut(){
    	if ( isJ2RE == true ){
    		zoomIndex--;
			zoom();
		}
   	}

    /**
     * zoomIndex is a reference to a static array of scale factors.
     * The index ranges from 0 to 9 and by default is set to 7 which corresponds
     * to a scale of 1.0 (draws megamek images at normal size).  To zoom out the
     * index needs to be set to a lower value.  To zoom in make it larger.
     * If only zooming a step at a time use the zoomIn and zoomOut methods instead.
     * 
     * @param zoomIndex
     */
    public void setZoomIndex( int zoomIndex ){
 		if ( isJ2RE == true ){
 			this.zoomIndex = zoomIndex;
			zoom();
    	}
    }

    public int getZoomIndex(){
    	return zoomIndex;
    }
    
    private void checkZoomIndex(){
    	if ( zoomIndex > ZOOM_FACTORS.length-1 ) {
    		zoomIndex = ZOOM_FACTORS.length-1;
    	}
    	if ( zoomIndex < 0 ) zoomIndex = 0;
    }
    
    //
    // Changes hex dimensions and refreshes the map with the new scale
    //
	private void zoom(){
		checkZoomIndex();
    	scale = ZOOM_FACTORS[zoomIndex];
        Settings.mapZoomIndex = zoomIndex;

    	hex_size = new Dimension((int)(HEX_W*scale), (int)(HEX_H*scale));

    	final Dimension size = getSize();
        //Coords c = getCoordsAt(new Point((int)(size.width/2), (int)(size.height/2)));

    	boardGraph=null;
    	backGraph=null;
    	hasZoomed=true;

    	updateBoardSize();

    	view.setLocation(scroll);
    	view.setSize(getOptimalView(size));
    	offset.setLocation(getOptimalOffset(size));

    	updateFontSizes();
    	updateBoardImage();
    	
    	update(this.getGraphics());
    }
    
	private void updateFontSizes(){
	    if ( zoomIndex <= 4 ) {
	    		font_elev = FONT_7;
	    		font_hexnum = FONT_7;
	    		font_minefield = FONT_7;
	    }
	    if ( zoomIndex <= 5 & zoomIndex > 4 ){
	        	font_elev = FONT_8;
	        	font_hexnum = FONT_8;
	    		font_minefield = FONT_8;
	    }
	    if ( zoomIndex > 5 ){
	    	font_elev = FONT_9;
	    	font_hexnum = FONT_9;
    		font_minefield = FONT_9;
	    }
	}

    /**
     * Displays a bit of text in a box.
     *
     * TODO: make multi-line
     */
    private class TooltipCanvas extends Canvas
    {
        private String[] tipStrings;
        private Dimension size;

        public TooltipCanvas(String[] tipStrings) {
            this.tipStrings = tipStrings;

            // setup
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setBackground(SystemColor.info);
            setForeground(SystemColor.infoText);

            // determine size
            final FontMetrics fm = getFontMetrics(getFont());
            int width = 0;
            for (int i = 0; i < tipStrings.length; i++) {
                if (fm.stringWidth(tipStrings[i]) > width) {
                    width = fm.stringWidth(tipStrings[i]);
                }
            }
            size = new Dimension(width + 5, fm.getAscent() * tipStrings.length + 4);
            setSize(size);
        }

        public void paint(Graphics g) {
            final FontMetrics fm = getFontMetrics(getFont());
            g.setColor(getBackground());
            g.fillRect(0, 0, size.width, size.height);
            g.setColor(getForeground());
            g.drawRect(0, 0, size.width - 1, size.height - 1);
            for (int i = 0; i < tipStrings.length; i++) {
                g.drawString(tipStrings[i], 2, (i + 1) * fm.getAscent());
            }
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
            } else {
                return true;
            }
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
            	Image tmpImage;
            	if (zoomIndex == BASE_ZOOM_INDEX ){
    	            tmpImage = image;
            	} else {
                    tmpImage = getScaledImage( image, scale );
            	}
                if (makeTranslucent && isJ2RE) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                    g2.drawImage(tmpImage, x, y, observer);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                } else {
                    g.drawImage(tmpImage, x, y, observer);
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
        private String[] getTooltip() {
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
            this.image = createImage(new FilteredImageSource(tempImage.getSource(),
                                                             new KeyAlphaFilter(TRANSPARENT)));
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
        private Rectangle entityRect;
        private Rectangle modelRect;

        public GhostEntitySprite(Entity entity) {
            this.entity = entity;

            String shortName = entity.getShortName();
            Font font = new Font("SansSerif", Font.PLAIN, 10);
            modelRect = new Rectangle(47, 55,
                            getFontMetrics(font).stringWidth(shortName) + 1,
                            getFontMetrics(font).getAscent());
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));

            this.bounds = tempBounds;
            this.entityRect = new Rectangle(
            		bounds.x + (int)(20*scale),
            		bounds.y + (int)(14*scale), 
					(int)(44*scale), 
					(int)(44*scale));
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
            this.image = createImage(new FilteredImageSource(tempImage.getSource(),
                                                             new KeyAlphaFilter(TRANSPARENT)));
        }

        public Rectangle getBounds(){
        	Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
        	tempBounds.setLocation(getHexLocation(entity.getPosition()));
        	this.bounds = tempBounds;
        
        	this.entityRect = new Rectangle(bounds.x + (int)(20*scale),
        			bounds.y + (int)(14*scale),
					(int)(44*scale),
					(int)(44*scale));
        	
        	return bounds;
        }
        
        public void drawOnto(Graphics g, int x, int y, ImageObserver observer) {
            drawOnto(g, x, y, observer, true);
        }

    }

    private class MovingEntitySprite extends Sprite {
        private int facing;
        private Entity entity;
        private Rectangle entityRect;
        private Rectangle modelRect;

        public MovingEntitySprite(Entity entity, Coords position, int facing) {
            this.entity = entity;
            this.facing = facing;

            String shortName = entity.getShortName();
            Font font = new Font("SansSerif", Font.PLAIN, 10);
            modelRect = new Rectangle(47, 55,
                            getFontMetrics(font).stringWidth(shortName) + 1,
                            getFontMetrics(font).getAscent());
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(position));

            this.bounds = tempBounds;
            this.entityRect = new Rectangle(bounds.x + (int)(20*scale),
            		bounds.y + (int)(14*scale),
					(int)(44*scale),
					(int)(44*scale));
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
            this.image = createImage(new FilteredImageSource(tempImage.getSource(),
                                                             new KeyAlphaFilter(TRANSPARENT)));
        }
    }


    /**
     * Sprite for an wreck.  Consists
     * of an image, drawn from the Tile Manager and an identification label.
     */
    private class WreckSprite extends Sprite
    {
        private Entity entity;
        private Rectangle entityRect;
        private Rectangle modelRect;

        public WreckSprite(Entity entity) {
            this.entity = entity;

            String shortName = entity.getShortName();
            
            Font font = new Font("SansSerif", Font.PLAIN, 10);
            modelRect = new Rectangle(47, 55,
                            		getFontMetrics(font).stringWidth(shortName) + 1,
                            		getFontMetrics(font).getAscent());
            Rectangle tempBounds = new Rectangle(hex_size).union(modelRect);
            tempBounds.setLocation(getHexLocation(entity.getPosition()));

            this.bounds = tempBounds;
            this.entityRect = new Rectangle(
            		bounds.x + (int)(20*scale),
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
        
        /**
         * Creates the sprite for this entity.  It is an extra pain to
         * create transparent images in AWT.
         */
        public void prepare() {
            // figure out size
            String shortName = entity.getShortName();
            Font font = new Font("SansSerif", Font.PLAIN, 10);
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
            this.image = createImage
                (new FilteredImageSource(tempImage.getSource(),
                                         new KeyAlphaFilter(TRANSPARENT)));
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
            Font font = new Font("SansSerif", Font.PLAIN, 10);
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
                && !this.entity.isVisibleToEnemy()) {
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
            if (Settings.showUnitId) {
                shortName+=(" ID:"+entity.getId());
            }
            Font font = new Font("SansSerif", Font.PLAIN, 10);
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
            if (entity.getFacing() != -1) {
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

            // Determine if the entity is a tank with a locked turret.
            boolean turretLocked = false;
            if ( entity instanceof Tank &&
                 !( (Tank) entity ).hasNoTurret() &&
                 !entity.canChangeSecondaryFacing() ) {
                turretLocked = true;
            }

            // draw condition strings
            if ( entity.isImmobile() && !entity.isProne() && !turretLocked ) {
                // draw "IMMOBILE"
                graph.setColor(Color.darkGray);
                graph.drawString("IMMOBILE", 18, 39);
                graph.setColor(Color.red);
                graph.drawString("IMMOBILE", 17, 38);
            } else if (!entity.isImmobile() && entity.isProne()) {
                // draw "PRONE"
                graph.setColor(Color.darkGray);
                graph.drawString("PRONE", 26, 39);
                graph.setColor(Color.yellow);
                graph.drawString("PRONE", 25, 38);
            } else if ( !entity.isImmobile() && turretLocked ) {
                // draw "LOCKED"
                graph.setColor(Color.darkGray);
                graph.drawString("LOCKED", 22, 39);
                graph.setColor(Color.yellow);
                graph.drawString("LOCKED", 21, 38);
            } else if (entity.isImmobile() && entity.isProne()) {
                // draw "IMMOBILE" and "PRONE"
                graph.setColor(Color.darkGray);
                graph.drawString("IMMOBILE", 18, 35);
                graph.drawString("PRONE", 26, 48);
                graph.setColor(Color.red);
                graph.drawString("IMMOBILE", 17, 34);
                graph.setColor(Color.yellow);
                graph.drawString("PRONE", 25, 47);
            } else if ( entity.isImmobile() && turretLocked ) {
                // draw "IMMOBILE" and "LOCKED"
                graph.setColor(Color.darkGray);
                graph.drawString("IMMOBILE", 18, 35);
                graph.drawString("LOCKED", 22, 48);
                graph.setColor(Color.red);
                graph.drawString("IMMOBILE", 17, 34);
                graph.setColor(Color.yellow);
                graph.drawString("LOCKED", 21, 47);
            }

            // If this unit is being swarmed or is swarming another, say so.
            if ( Entity.NONE != entity.getSwarmAttackerId() ) {
                // draw "SWARMED"
                graph.setColor(Color.darkGray);
                graph.drawString("SWARMED", 17, 22);
                graph.setColor(Color.red);
                graph.drawString("SWARMED", 16, 21);
            }

            // If this unit is transporting another, say so.
            if ((entity.getLoadedUnits()).size() > 0) {
                // draw "T"
                graph.setColor(Color.darkGray);
                graph.drawString("T", 20, 71);
                graph.setColor(Color.black);
                graph.drawString("T", 19, 70);
            }
            
            // If this unit is stuck, say so.
            if ((entity.isStuck())) {
                graph.setColor(Color.darkGray);
                graph.drawString("STUCK", 26, 61);
                graph.setColor(Color.orange);
                graph.drawString("STUCK", 25, 60);
                
            }

            // If this unit is currently unknown to the enemy, say so.
            if (trackThisEntitiesVisibilityInfo(entity)) {
                if (!entity.isSeenByEnemy()) {
                    // draw "U"
                    graph.setColor(Color.darkGray);
                    graph.drawString("U", 30, 71);
                    graph.setColor(Color.black);
                    graph.drawString("U", 29, 70);
                } else if (!entity.isVisibleToEnemy() && !isJ2RE) {
                    // If this unit is currently hidden from the enemy, say so.
                    // draw "H"
                    graph.setColor(Color.darkGray);
                    graph.drawString("H", 30, 71);
                    graph.setColor(Color.black);
                    graph.drawString("H", 29, 70);
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

            percentRemaining = entity.getInternalRemainingPercent();
            barLength = (int)(baseBarLength * percentRemaining);

            graph.setColor(Color.darkGray);
            graph.fillRect(56, 11, 23, 3);
            graph.setColor(Color.lightGray);
            graph.fillRect(55, 10, 23, 3);
            graph.setColor(getStatusBarColor(percentRemaining));
            graph.fillRect(55, 10, barLength, 3);

            // create final image
            this.image = createImage(new FilteredImageSource(tempImage.getSource(),
                                                             new KeyAlphaFilter(TRANSPARENT)));
        }

        /*
         * We only want to show double-blind visibility indicators on
         * our own mechs and teammates mechs (assuming team vision option).
         */
        private boolean trackThisEntitiesVisibilityInfo(Entity e) {
            if (getLocalPlayer() == null) {
                return false;
            }

            if (game.getOptions().booleanOption("double_blind")
                && (e.getOwner().getId() == getLocalPlayer().getId()
                    || (game.getOptions().booleanOption("team_vision")
                        && e.getOwner().getTeam() == getLocalPlayer().getTeam()))) {
                return true;
            } else {
                return false;
            }
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
            return entityRect.contains(	point.x + view.x - offset.x,
            							point.y + view.y - offset.y);
        }

        private String[] getTooltip() {
            String[] tipStrings = new String[3];
            StringBuffer buffer;

            buffer = new StringBuffer();
            buffer.append( entity.getChassis() )
                .append( " (" )
                .append( entity.getOwner().getName() )
                .append( "); " )
                .append( entity.getCrew().getGunnery() )
                .append( "/" )
                .append( entity.getCrew().getPiloting() )
                .append( " pilot" );
            int numAdv = entity.getCrew().countAdvantages();
            if (numAdv > 0) {
                buffer.append( " <" )
                    .append( numAdv )
                    .append( " advs>" );
            }
            tipStrings[0] = buffer.toString();

            buffer = new StringBuffer();
            buffer.append( "Move " )
                .append( entity.getMovementAbbr(entity.moved) )
                .append( ":" )
                .append( entity.delta_distance )
                .append( " (+" )
                .append( Compute.getTargetMovementModifier
                         (game, entity.getId()).getValue() )
                .append( ");" )
                .append( " Heat " )
                .append( entity.heat );
            tipStrings[1] = buffer.toString();

            buffer = new StringBuffer();
            buffer.append( "Armor " )
                .append( entity.getTotalArmor() )
                .append( "; Internal " )
                .append( entity.getTotalInternal() );
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
            case Entity.MOVE_RUN :
                if (step.isUsingMASC()) {
                    col = Settings.moveMASCColor;
                } else {
                    col = Settings.moveRunColor;
                }
                break;
            case Entity.MOVE_JUMP :
                col = Settings.moveJumpColor;
                break;
            case Entity.MOVE_ILLEGAL :
                col = Settings.moveIllegalColor;
                break;
            default :
                col = Settings.moveDefaultColor;
                break;
            }

            // draw arrows and cost for the step
            switch (step.getType()) {
            case MovePath.STEP_FORWARDS :
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
                // draw arrow indicating dropping prone
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
                // draw arrow indicating standing up
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
                String load = "Load";
                if (step.isPastDanger()) {
                    load = "(" + load + ")";
                }
                graph.setFont(new Font("SansSerif", Font.PLAIN, 12));
                int loadX = stepPos.x + 42 - (graph.getFontMetrics(graph.getFont()).stringWidth(load) / 2);
                graph.setColor(Color.darkGray);
                graph.drawString(load, loadX, stepPos.y + 39);
                graph.setColor(col);
                graph.drawString(load, loadX - 1, stepPos.y + 38);
                break;
            case MovePath.STEP_UNLOAD:
                // Announce unload.
                String unload = "Unload";
                if (step.isPastDanger()) {
                    unload = "(" + unload + ")";
                }
                graph.setFont(new Font("SansSerif", Font.PLAIN, 12));
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
            this.image = createImage(new FilteredImageSource(tempImage.getSource(),
                                                             new KeyAlphaFilter(TRANSPARENT)));
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
                costStringBuf.append( "+" );
            }

            // If the step is dangerous, mark it.
            if ( step.isDanger() ) {
                costStringBuf.append( "*" );
            }

            // If the step is past danger, mark that.
            if (step.isPastDanger()) {
                costStringBuf.insert( 0, "(" );
                costStringBuf.append( ")" );
            }

            if (step.isUsingMASC()) {
                costStringBuf.append("[");
                costStringBuf.append(step.getTargetNumberMASC());
                costStringBuf.append("+]");
            }

            // Convert the buffer to a String and draw it.
            costString = costStringBuf.toString();
            graph.setFont(new Font("SansSerif", Font.PLAIN, 12));
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
            this.spriteColor = e.getOwner().getColor();

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
            ;
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
            return C3Poly.contains(point.x + view.x - bounds.x - offset.x,
                                   point.y + view.y - bounds.y - offset.y);
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
        private java.util.Vector attacks = new java.util.Vector();
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
        private Vector weaponDescs = new Vector();
        private final Entity ae;
        private final Targetable target;

        public AttackSprite(AttackAction attack) {
            this.attacks.addElement(attack);
            this.entityId = attack.getEntityId();
            this.targetType = attack.getTargetType();
            this.targetId = attack.getTargetId();
            this.ae = game.getEntity(attack.getEntityId());
            this.target = game.getTarget(targetType, targetId);

            // color?
            attackColor = ae.getOwner().getColor();
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
            for (final Iterator i = attackSprites.iterator(); i.hasNext();) {
                final AttackSprite sprite = (AttackSprite)i.next();
                if (sprite.getEntityId() == this.targetId && sprite.getTargetId() == this.entityId) {
                    sprite.rebuildToHalvedPolygon();
                    return true;
                }
            }
            return false;
        }

        public void prepare() {
            ;
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
            return attackPoly.contains(point.x + view.x - bounds.x - offset.x,
                                       point.y + view.y - bounds.y - offset.y);
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
            weaponDescs.addElement( wtype.getName() + "; needs " + roll );
        }

        public void addWeapon(KickAttackAction attack) {
            String bufer = "";
            String rollLeft = "";
            String rollRight = "";
            final int leg = attack.getLeg();
            switch (leg){
            case KickAttackAction.BOTH:
                rollLeft = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.LEFT).getValueAsString();
                rollRight = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.RIGHT).getValueAsString();
                bufer = "Kicks with both legs. Left needs " + rollLeft + "; Right needs " + rollRight;
                break;
            case KickAttackAction.LEFT:
                rollLeft = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.LEFT).getValueAsString();
                bufer = "Kicks with left leg. Needs " + rollLeft;
                break;
            case KickAttackAction.RIGHT:
                rollRight = KickAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.RIGHT).getValueAsString();
                bufer = "Kicks with right leg. Needs " + rollRight;
                break;
            }
            weaponDescs.addElement(bufer);
        }

        public void addWeapon(PunchAttackAction attack) {
            String bufer = "";
            String rollLeft = "";
            String rollRight = "";
            final int arm = attack.getArm();
            switch (arm){
            case PunchAttackAction.BOTH:
                rollLeft = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.LEFT).getValueAsString();
                rollRight = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.RIGHT).getValueAsString();
                bufer = "Punches with both arms. Left needs " + rollLeft + "; Right needs " + rollRight;
                break;
            case PunchAttackAction.LEFT:
                rollLeft = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.LEFT).getValueAsString();
                bufer = "Punches with left arm. Needs " + rollLeft;
                break;
            case PunchAttackAction.RIGHT:
                rollRight = PunchAttackAction.toHit( game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.RIGHT).getValueAsString();
                bufer = "Punches with right arm. Needs " + rollRight;
                break;
            }
            weaponDescs.addElement(bufer);
        }

        public void addWeapon(PushAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            weaponDescs.addElement("Pushes. Needs " + roll);
        }

        public void addWeapon(ClubAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            final String club = attack.getClub().getName();
            weaponDescs.addElement("Hits with " + club +". Needs " + roll);
        }

        public void addWeapon(ChargeAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            weaponDescs.addElement("Charges. Needs " + roll);
        }
        public void addWeapon(DfaAttackAction attack) {
            final String roll = attack.toHit(game).getValueAsString();
            weaponDescs.addElement("DFA. Needs " + roll);
        }
        public void addWeapon(ProtomechPhysicalAttackAction attack) {
        	final String roll = attack.toHit(game).getValueAsString();
        	weaponDescs.addElement("Makes a protomech physical attack. Needs " + roll);
        }

        private String[] getTooltip() {
            String[] tipStrings = new String[1 + weaponDescs.size()];
            int tip = 1;
            tipStrings[0] = attackerDesc + " on " + targetDesc;
            for (Iterator i = weaponDescs.iterator(); i.hasNext();) {
                tipStrings[tip++] = (String)i.next();
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

    // added by kenn
    public void drawRuler(Coords s, Coords e, Color sc, Color ec) {
        rulerStart = s;
        rulerEnd = e;
        rulerStartColor = sc;
        rulerEndColor = ec;

        repaint();
    }
    // end kenn

    /**
     * Return, whether a popup may be drawn, this currently means, whether no scrolling took place.
     */
    public boolean mayDrawPopup() {
    	return !scrolled;
    }
    
}
