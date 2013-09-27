package megamek.client.util.widget;

import java.awt.*;
import java.awt.event.*;
import megamek.client.*;

public class MechPanelTabStrip extends PicMap {
	
	private PMPicPolygonalArea[] tabs = new PMPicPolygonalArea[5];
    private static final Image[] idleImage = new Image[5];
    private static final Image[] activeImage = new Image[5];
    private Image idleCorner, selectedCorner;
    private int activeTab = 0;
    MechDisplay md;
    
    private Polygon firstTab = new Polygon( new int[] {0, 43, 59, 59, 0},
                                            new int[] {0, 0, 16, 17, 17}, 5);
    private int[] pointsX = new int[] {0, 43,59,59,13,0};
    private int[] pointsY = new int[] {0, 0, 16,17,17,4};
    
    public MechPanelTabStrip (MechDisplay md){
    	super();
    	this.md = md;
    }
    
    public void setTab(int i){
    	if (i > 4) i = 4;
    	activeTab = i; redrawImages(); update();
    }
    
    public void addNotify(){
        super.addNotify();
    	setImages();
		setAreas();
		setListeners();		
		update();
	}
	
	private void setImages(){
		MediaTracker mt = new MediaTracker(this);
		Toolkit tk = getToolkit();
		idleImage[0] = tk.getImage("data/widgets/tab_general_idle.gif");
		idleImage[1] = tk.getImage("data/widgets/tab_armor_idle.gif");
		idleImage[2] = tk.getImage("data/widgets/tab_systems_idle.gif");
		idleImage[3] = tk.getImage("data/widgets/tab_weapon_idle.gif");
		idleImage[4] = tk.getImage("data/widgets/tab_extras_idle.gif");
		activeImage[0] = tk.getImage("data/widgets/tab_general_active.gif");
		activeImage[1] = tk.getImage("data/widgets/tab_armor_active.gif");
		activeImage[2] = tk.getImage("data/widgets/tab_systems_active.gif");
		activeImage[3] = tk.getImage("data/widgets/tab_weapon_active.gif");
		activeImage[4] = tk.getImage("data/widgets/tab_extras_active.gif");
		idleCorner = tk.getImage("data/widgets/idle_corner.gif");
		selectedCorner = tk.getImage("data/widgets/active_corner.gif");
	    
	    for (int i = 0; i < 5 ; i++){
	    	mt.addImage(idleImage[i],0);
	    	mt.addImage(activeImage[i],0);
	    }	
		mt.addImage(idleCorner,0);
		mt.addImage(selectedCorner,0);
		try{
            mt.waitForAll();
        } catch (InterruptedException e){
            System.out.println("TabStrip: Error while image loading.");
        }
        if(mt.isErrorID(0)){
            System.out.println("TabStrip: Could Not load Image.");
        }
	}

	
	private void setAreas(){
		
		int stepX = 47;
		
		tabs[0] = new PMPicPolygonalArea(firstTab,
		                                 createImage(47,18));
		for (int i = 1; i < 4 ; i++){
			tabs[i] = new PMPicPolygonalArea(new Polygon(pointsX, pointsY, 6),
			                                 createImage(47,18));
		}
		
		tabs[4] = new PMPicPolygonalArea(new Polygon(pointsX, pointsY, 6),
		                                 createImage(60,18));
		for (int i = 0; i < 5; i++){
			drawIdleImage(i);
			tabs[i].translate(i * stepX, 0);
			addElement(tabs[i]);
		}
	}
	
	
	private void setListeners(){
		tabs[0].addActionListener( new ActionListener()
		               { 
		                 public void actionPerformed(ActionEvent e)
		                     { 
		                       if (e.getActionCommand() == PMHotArea.MOUSE_DOWN){
		                       	  setTab(0);
		                       	  md.showPanel("movement");
		                       } 
		                     }
		               } );
		tabs[1].addActionListener( new ActionListener()
		               { 
		                 public void actionPerformed(ActionEvent e)
		                      {
		                      	if (e.getActionCommand() == PMHotArea.MOUSE_DOWN){
		                       	  setTab(1);
		                       	  md.showPanel("armor");
		                        } 
		                      }
		               } );
		tabs[2].addActionListener( new ActionListener()
		               { 
		                 public void actionPerformed(ActionEvent e)
		                      {
		                      	if (e.getActionCommand() == PMHotArea.MOUSE_DOWN){
		                       	  setTab(2);
		                       	  md.showPanel("systems");
		                        } 
		                      }
		               } );
		tabs[3].addActionListener( new ActionListener()
		               { 
		                 public void actionPerformed(ActionEvent e)
		                      {
		                      	if (e.getActionCommand() == PMHotArea.MOUSE_DOWN){
		                       	  setTab(3);
		                       	  md.showPanel("weapons");
		                        } 
		                      }
		               } );
		tabs[4].addActionListener( new ActionListener()
		               { 
		                 public void actionPerformed(ActionEvent e)
		                      {
		                      	if (e.getActionCommand() == PMHotArea.MOUSE_DOWN){
		                       	  setTab(4);
		                       	  md.showPanel("extras");
		                        } 
		                      }
		               } );              
		               
	}
	
	private void redrawImages(){
		for (int i = 0; i < 5; i++){
			drawIdleImage(i);
		}
	}
	
	private void drawIdleImage(int tab){
        if (tabs[tab] == null) {
            // hmm, display not initialized yet...
            return;
        }
		Graphics g = tabs[tab].getIdleImage().getGraphics();
		
		if( activeTab == tab){
			g.drawImage(activeImage[tab], 0, 0, null);
		} else {
			g.drawImage(idleImage[tab], 0, 0, null);
			if((tab - activeTab) == 1){
				g.drawImage(selectedCorner, 0, 4, null);
			} else if (tab > 0) {
				g.drawImage(idleCorner, 0, 4, null);
			}
		}
		g.dispose();
	}
	
   public void onResize(){}	
	
}