/*
 * MechSelectorDialog.java - Copyright (C) 2002,2004 Josh Yockey
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
 
import java.awt.*;
import java.awt.event.*;
import java.util.Hashtable;
import java.util.Vector;

import megamek.client.util.widget.BufferedPanel;
import megamek.common.*;
import megamek.common.loaders.EntityLoadingException;

import com.sun.java.util.collections.Arrays;
import com.sun.java.util.collections.Iterator;

/* 
 * Allows a user to sort through a list of MechSummaries and select one
 */

public class MechSelectorDialog 
    extends Dialog implements ActionListener, ItemListener, KeyListener, 
    Runnable, WindowListener
{
    // how long after a key is typed does a new search begin
    private final static int KEY_TIMEOUT = 1000;
     
    // these indices should match up with the static values in the MechSummaryComparator
    private String[] m_saSorts = { "Name", "Ref", "Weight", "BV", "Year" };
    
    private MechSummary[] m_mechsCurrent;
    private Client m_client;
    private ClientGUI m_clientgui;
    private UnitLoadingDialog unitLoadingDialog;
        
    private StringBuffer m_sbSearch = new StringBuffer();
    private long m_nLastSearch = 0;
    
    private Label m_labelWeightClass = new Label("Weight Class: ", Label.RIGHT);
    private Choice m_chWeightClass = new Choice();
    private Label m_labelType = new Label("Type: ", Label.RIGHT);
    private Choice m_chType = new Choice();
    private Label m_labelUnitType = new Label("Unit Type: ", Label.RIGHT);
    private Choice m_chUnitType = new Choice();
    private Label m_labelSort = new Label("Sort: ", Label.RIGHT);
    private Choice m_chSort = new Choice();
    private Panel m_pParams = new Panel();
    List m_mechList = new List(10);
    private Button m_bPick = new Button("Select Mech");
    private Button m_bCancel = new Button("Cancel");
    private Panel m_pButtons = new Panel();
    private TextArea m_mechViewLeft = new TextArea("",18,25,TextArea.SCROLLBARS_HORIZONTAL_ONLY);
    private TextArea m_mechViewRight = new TextArea(18,28);
    private Panel m_pLeft = new Panel();

    private Panel m_pUpper = new Panel();
	BufferedPanel m_pPreview = new BufferedPanel();
	
	private Label m_labelPlayer = new Label("Player:", Label.RIGHT);
	private Choice m_chPlayer = new Choice();

    public MechSelectorDialog(ClientGUI cl, UnitLoadingDialog uld)
    {
        super(cl.frame, "Select Mech...", true);
        m_client = cl.getClient();
        m_clientgui = cl;
        unitLoadingDialog = uld;
        
        for (int x = 0; x < m_saSorts.length; x++) {
            m_chSort.addItem(m_saSorts[x]);
        }
		updatePlayerChoice();
		
        m_pParams.setLayout(new GridLayout(4, 2));
        m_pParams.add(m_labelWeightClass);
        m_pParams.add(m_chWeightClass);
        m_pParams.add(m_labelType);
        m_pParams.add(m_chType);
        m_pParams.add(m_labelUnitType);
        m_pParams.add(m_chUnitType);
        m_pParams.add(m_labelSort);
        m_pParams.add(m_chSort);
        
        m_pButtons.setLayout(new FlowLayout(FlowLayout.CENTER));
        m_pButtons.add(m_bPick);
        m_pButtons.add(m_bCancel);
        m_pButtons.add(m_labelPlayer);
        m_pButtons.add(m_chPlayer);
        
        m_pUpper.setLayout(new BorderLayout());
        m_pPreview.setPreferredSize(84, 72);
        m_pUpper.add(m_pParams, BorderLayout.WEST);
        m_pUpper.add(m_pPreview, BorderLayout.CENTER);

        m_pLeft.setLayout(new BorderLayout());
        m_pLeft.add(m_pUpper, BorderLayout.NORTH);
        m_mechList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        m_mechList.addKeyListener(this);
        m_pLeft.add(m_mechList, BorderLayout.CENTER);
        m_pLeft.add(m_pButtons, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(m_pLeft, BorderLayout.WEST);
        m_mechViewLeft.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(m_mechViewLeft, BorderLayout.CENTER);
        m_mechViewRight.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(m_mechViewRight, BorderLayout.EAST);
        
        //clearMechPreview();
        
        m_chWeightClass.addItemListener(this);
        m_chType.addItemListener(this);
        m_chUnitType.addItemListener(this);
        m_chSort.addItemListener(this);
        m_mechList.addItemListener(this);
        m_bPick.addActionListener(this);
        m_bCancel.addActionListener(this);
        setSize(770, 320);
        setLocation(m_clientgui.frame.getLocation().x + m_clientgui.frame.getSize().width/2 - getSize().width/2,
                    m_clientgui.frame.getLocation().y + m_clientgui.frame.getSize().height/2 - getSize().height/2);
        populateChoices();
        addWindowListener(this);
    }

    private void updatePlayerChoice() {
        String lastChoice = m_chPlayer.getSelectedItem();
        m_chPlayer.removeAll();
        m_chPlayer.setEnabled(true);
        m_chPlayer.addItem(m_clientgui.getClient().getName());
		for (Iterator i = m_clientgui.getBots().values().iterator(); i.hasNext();) {
			m_chPlayer.addItem(((Client)i.next()).getName());
		}
		if (m_chPlayer.getItemCount() == 1) {
		    m_chPlayer.setEnabled(false);
		} else {
		    m_chPlayer.select(lastChoice);
		}
    }

    public void run() {
        // Loading mechs can take a while, so it will have its own thread.
        // This prevents the UI from freezing, and allows the
        // "Please wait..." dialog to behave properly on various Java VMs.
        filterMechs();

        unitLoadingDialog.hide();

        final Hashtable hFailedFiles = MechSummaryCache.getInstance().getFailedFiles();
        if (hFailedFiles != null && hFailedFiles.size() > 0) {
            new UnitFailureDialog(m_clientgui.frame, hFailedFiles); // self-showing dialog
        }
    }
    
    
    private void populateChoices() {
        /* If you change any of the strings below, be sure to check
         * the filterMechs method below as some strings may need to
         * be changed there as well.
         */

        m_chWeightClass.addItem("Light");
        m_chWeightClass.addItem("Medium");
        m_chWeightClass.addItem("Heavy");
        m_chWeightClass.addItem("Assault");
        m_chWeightClass.addItem("All");
        m_chWeightClass.select(0);

        m_chType.addItem("IS level 1");
        m_chType.addItem("IS level 2");
        m_chType.addItem("IS All");
        m_chType.addItem("Clan level 2");
        m_chType.addItem("IS and Clan");
        m_chType.addItem("Mixed (IS) level 2");
        m_chType.addItem("Mixed (Clan) level 2");
        // More than 8 items causes the drop down to sprout a vertical
        //  scroll bar.  I guess we'll sacrifice this next one to stay
        //  under the limit.  Stupid AWT Choice class!
        //        m_chType.addItem("Mixed All");
        m_chType.addItem("All");
        m_chType.select(0);

        m_chUnitType.addItem("Mek");
        m_chUnitType.addItem("Tank");
        m_chUnitType.addItem("Infantry");
        m_chUnitType.addItem("ProtoMek");
        m_chUnitType.addItem("All");
        m_chUnitType.select(0);
    }
    
    
    private void filterMechs()
    {
        Vector vMechs = new Vector();
        String sClass = m_chWeightClass.getSelectedItem();
        int nWeight = 0;

        if (sClass.equals("Light")) {
            nWeight = Entity.WEIGHT_LIGHT;
        }
        else if (sClass.equals("Medium")) {
            nWeight = Entity.WEIGHT_MEDIUM;
        }
        else if (sClass.equals("Heavy")) {
            nWeight = Entity.WEIGHT_HEAVY;
        }
        else if (sClass.equals("Assault")) {
            nWeight = Entity.WEIGHT_ASSAULT;
        }
        int nType = m_chType.getSelectedIndex();
        String sType = m_chType.getSelectedItem();
        String sUnitType = m_chUnitType.getSelectedItem();
        MechSummary[] mechs = MechSummaryCache.getInstance().getAllMechs();
        if ( mechs == null ) {
            System.err.println( "No units to filter!" );
            return;
        }
        for (int x = 0; x < mechs.length; x++) {
            if ( /* Weight */
                (sClass.equals("All") || mechs[x].getWeightClass() == nWeight)
                && /* Technology Level */
                (sType.equals("All")
                 || (sType.equals("IS All") &&
                     mechs[x].getType() <= TechConstants.T_IS_LEVEL_2)
                 || (sType.equals("IS and Clan") &&
                     (mechs[x].getType() <= TechConstants.T_IS_LEVEL_2 ||
                      mechs[x].getType() == TechConstants.T_CLAN_LEVEL_2))
                 || (sType.equals("Mixed All") &&
                     (mechs[x].getType() ==
                      TechConstants.T_MIXED_BASE_IS_LEVEL_2 ||
                      mechs[x].getType() ==
                      TechConstants.T_MIXED_BASE_CLAN_LEVEL_2))
                 || TechConstants.T_NAMES[mechs[x].getType()].equals(sType))
                && /* Unit Type (Mek, Infantry, etc.) */
                ( sUnitType.equals( "All" ) ||
                  mechs[x].getUnitType().equals(sUnitType) ) )
                {
                    vMechs.addElement(mechs[x]);
                }
        }
        m_mechsCurrent = new MechSummary[vMechs.size()];
        vMechs.copyInto(m_mechsCurrent);
        sortMechs();
    }
    
    private void sortMechs()
    {
        Arrays.sort(m_mechsCurrent, new MechSummaryComparator(m_chSort.getSelectedIndex()));
        m_mechList.removeAll();
        for (int x = 0; x < m_mechsCurrent.length; x++) {
            m_mechList.add(formatMech(m_mechsCurrent[x]));
        }
        repaint();
    }
    
    private void searchFor(String search) {
        for (int i = 0; i < m_mechsCurrent.length; i++) {
            if (m_mechsCurrent[i].getName().toLowerCase().startsWith(search)) {
                m_mechList.select(i);
                ItemEvent event = new ItemEvent(m_mechList,ItemEvent.ITEM_STATE_CHANGED,m_mechList,ItemEvent.SELECTED);
                itemStateChanged(event);
                break;
            }
        }
    }
    
    public void show() {
        updatePlayerChoice();
        setLocation(m_clientgui.frame.getLocation().x + m_clientgui.frame.getSize().width/2 - getSize().width/2,
                    m_clientgui.frame.getLocation().y + m_clientgui.frame.getSize().height/2 - getSize().height/2);
        super.show();
    }
    
    private String formatMech(MechSummary ms)
    {
        return makeLength(ms.getModel(), 10) + " " + 
                makeLength(ms.getChassis(), 20) + " " + 
                makeLength("" + ms.getTons(), 3) + " " + 
                makeLength("" + ms.getBV(),5)+""+
		    ms.getYear();
    }
    
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == m_bCancel) {
            this.setVisible(false);
        }
        else if (ae.getSource() == m_bPick) {
            int x = m_mechList.getSelectedIndex();
            if (x == -1) {
                return;
            }
            MechSummary ms = m_mechsCurrent[m_mechList.getSelectedIndex()];
            try {
                Entity e = new MechFileParser(ms.getSourceFile(), ms.getEntryName()).getEntity();
                Client c = null;
                if (m_chPlayer.getSelectedIndex() > 0) {
                    String name = m_chPlayer.getSelectedItem();
                    c = (Client)m_clientgui.getBots().get(name);
                }
                if (c == null) {
                    c = m_client;
                }
                e.setOwner(c.getLocalPlayer());
                c.sendAddEntity(e);
            } catch (EntityLoadingException ex) {
                System.out.println("Unable to load mech: " + ms.getSourceFile() + ": " + ms.getEntryName() + ": " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            this.setVisible(false);
        }
    }
    
    public void itemStateChanged(ItemEvent ie)
    {
        if (ie.getSource() == m_chSort) {
            clearMechPreview();
            sortMechs();
        }
        else if (ie.getSource() == m_chWeightClass
                 || ie.getSource() == m_chType
                 || ie.getSource() == m_chUnitType) {
            clearMechPreview();
            filterMechs();
        } else if (ie.getSource() == m_mechList) {
            int selected = m_mechList.getSelectedIndex();
            if (selected == -1) {
                clearMechPreview();
                return;
            }
            else {
                MechSummary ms = m_mechsCurrent[selected];
                try {
                    Entity entity = new MechFileParser(ms.getSourceFile(), ms.getEntryName()).getEntity();
                    previewMech(entity);
                } catch (EntityLoadingException ex) {
                    System.out.println("Unable to load mech: " + ms.getSourceFile() + ": " + ms.getEntryName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    clearMechPreview();
                    return;
                }
            }
        }
    }
    
    void clearMechPreview() {
        m_mechViewLeft.setEditable(false);
        m_mechViewRight.setEditable(false);
        m_mechViewLeft.setText("");
        m_mechViewRight.setText("");

        // Remove preview image.        
        if (MechSummaryCache.isInitialized()) {
            m_pPreview.removeBgDrawers();
            m_pPreview.paint(m_pPreview.getGraphics());
        }
    }
    
    void previewMech(Entity entity) {
        MechView mechView = new MechView(entity);
        m_mechViewLeft.setEditable(false);
        m_mechViewRight.setEditable(false);
        m_mechViewLeft.setText(mechView.getMechReadoutBasic());
        m_mechViewRight.setText(mechView.getMechReadoutLoadout());
        m_mechViewLeft.setCaretPosition(0);
        m_mechViewRight.setCaretPosition(0);

		// Preview image of the unit...
		m_clientgui.loadPreviewImage(m_pPreview, entity, m_client.getLocalPlayer());
		m_pPreview.paint(m_pPreview.getGraphics());
    }
    
    private static final String SPACES = "                        ";
    private String makeLength(String s, int nLength)
    {
        if (s.length() == nLength) {
            return s;
        }
        else if (s.length() > nLength) {
            return s.substring(0, nLength - 2) + "..";
        }
        else {
            return s + SPACES.substring(0, nLength - s.length());
        }
    }
        
    public void keyReleased(java.awt.event.KeyEvent ke) {
    }
    
    public void keyPressed(java.awt.event.KeyEvent ke) {
	if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
	    ActionEvent event = new ActionEvent(m_bPick,ActionEvent.ACTION_PERFORMED,"");
	    actionPerformed(event);
	}
        long curTime = System.currentTimeMillis();
        if (curTime - m_nLastSearch > KEY_TIMEOUT) {
            m_sbSearch = new StringBuffer();
        }
        m_nLastSearch = curTime;
        m_sbSearch.append(ke.getKeyChar());
        searchFor(m_sbSearch.toString().toLowerCase());
    }
    
    public void keyTyped(java.awt.event.KeyEvent ke) {
    }
        
    //
    // WindowListener
    //
    public void windowActivated(java.awt.event.WindowEvent windowEvent) {
    }    
    public void windowClosed(java.awt.event.WindowEvent windowEvent) {
    }    
    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
        this.setVisible(false);
    }    
    public void windowDeactivated(java.awt.event.WindowEvent windowEvent) {
    }    
    public void windowDeiconified(java.awt.event.WindowEvent windowEvent) {
    }    
    public void windowIconified(java.awt.event.WindowEvent windowEvent) {
    }    
    public void windowOpened(java.awt.event.WindowEvent windowEvent) {
    }
}
