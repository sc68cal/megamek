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

package megamek.client.ui.swing;

import megamek.client.Client;
import megamek.client.ui.AWT.MechView;
import megamek.common.Entity;
import megamek.common.EntityWeightClass;
import megamek.common.EquipmentType;
import megamek.common.Mech;
import megamek.common.MechFileParser;
import megamek.common.MechSummary;
import megamek.common.MechSummaryCache;
import megamek.common.MechSummaryComparator;
import megamek.common.MiscType;
import megamek.common.Mounted;
import megamek.common.Tank;
import megamek.common.TechConstants;
import megamek.common.UnitType;
import megamek.common.WeaponType;
import megamek.common.loaders.EntityLoadingException;
import megamek.common.verifier.EntityVerifier;
import megamek.common.verifier.TestEntity;
import megamek.common.verifier.TestMech;
import megamek.common.verifier.TestTank;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

/* 
 * Allows a user to sort through a list of MechSummaries and select one
 */

public class MechSelectorDialog
        extends JDialog implements ActionListener, ItemListener, KeyListener,
        Runnable, WindowListener, ListSelectionListener {
    // how long after a key is typed does a new search begin
    private final static int KEY_TIMEOUT = 1000;

    // these indices should match up with the static values in the MechSummaryComparator
    private String[] m_saSorts = {Messages.getString("MechSelectorDialog.0"), Messages.getString("MechSelectorDialog.1"), Messages.getString("MechSelectorDialog.2"), Messages.getString("MechSelectorDialog.3"), Messages.getString("MechSelectorDialog.4"), Messages.getString("MechSelectorDialog.5")}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

    private MechSummary[] m_mechsCurrent = new MechSummary[0];
    private Client m_client;
    private ClientGUI m_clientgui;
    private UnitLoadingDialog unitLoadingDialog;

    private StringBuffer m_sbSearch = new StringBuffer();
    private long m_nLastSearch = 0;

    private JLabel m_labelWeightClass = new JLabel(Messages.getString("MechSelectorDialog.m_labelWeightClass"), JLabel.RIGHT); //$NON-NLS-1$
    private JComboBox m_chWeightClass = new JComboBox();
    private JLabel m_labelType = new JLabel(Messages.getString("MechSelectorDialog.m_labelType"), JLabel.RIGHT); //$NON-NLS-1$
    private JComboBox m_chType = new JComboBox();
    private JLabel m_labelUnitType = new JLabel(Messages.getString("MechSelectorDialog.m_labelUnitType"), JLabel.RIGHT); //$NON-NLS-1$
    private JComboBox m_chUnitType = new JComboBox();
    private JLabel m_labelSort = new JLabel(Messages.getString("MechSelectorDialog.m_labelSort"), JLabel.RIGHT); //$NON-NLS-1$
    private JComboBox m_chSort = new JComboBox();
    private JPanel m_pParams = new JPanel();
    private JPanel m_pListOptions = new JPanel();
    private JLabel m_labelListOptions = new JLabel(Messages.getString("MechSelectorDialog.m_labelListOptions"));
    private JCheckBox m_cModel = new JCheckBox(Messages.getString("MechSelectorDialog.m_cModel"), GUIPreferences.getInstance().getMechSelectorIncludeModel());
    private JCheckBox m_cName = new JCheckBox(Messages.getString("MechSelectorDialog.m_cName"), GUIPreferences.getInstance().getMechSelectorIncludeName());
    private JCheckBox m_cTons = new JCheckBox(Messages.getString("MechSelectorDialog.m_cTons"), GUIPreferences.getInstance().getMechSelectorIncludeTons());
    private JCheckBox m_cBV = new JCheckBox(Messages.getString("MechSelectorDialog.m_cBV"), GUIPreferences.getInstance().getMechSelectorIncludeBV());
    private JCheckBox m_cYear = new JCheckBox(Messages.getString("MechSelectorDialog.m_cYear"), GUIPreferences.getInstance().getMechSelectorIncludeYear());
    private JCheckBox m_cLevel = new JCheckBox(Messages.getString("MechSelectorDialog.m_cLevel"), GUIPreferences.getInstance().getMechSelectorIncludeLevel());
    private JCheckBox m_cCost = new JCheckBox(Messages.getString("MechSelectorDialog.m_cCost"), GUIPreferences.getInstance().getMechSelectorIncludeCost());

    private JPanel m_pOpenAdvanced = new JPanel();
    private JButton m_bToggleAdvanced = new JButton("< Advanced Search >");
    private JPanel m_pSouthParams = new JPanel();

    JList m_mechList = new JList(new DefaultComboBoxModel());
    private JButton m_bPick = new JButton(Messages.getString("MechSelectorDialog.m_bPick")); //$NON-NLS-1$
    private JButton m_bPickClose = new JButton(Messages.getString("MechSelectorDialog.m_bPickClose")); //$NON-NLS-1$
    private JButton m_bCancel = new JButton(Messages.getString("Close")); //$NON-NLS-1$
    private JPanel m_pButtons = new JPanel();

    private JTextArea m_mechView = new JTextArea("", 36, 35);
    private JPanel m_pLeft = new JPanel();

    private JComboBox m_cWalk = new JComboBox();
    private JTextField m_tWalk = new JTextField(2);
    private JComboBox m_cJump = new JComboBox();
    private JTextField m_tJump = new JTextField(2);
    private JComboBox m_cArmor = new JComboBox();
    private JTextField m_tWeapons1 = new JTextField(2);
    private JComboBox m_cWeapons1 = new JComboBox();
    private JComboBox m_cOrAnd = new JComboBox();
    private JTextField m_tWeapons2 = new JTextField(2);
    private JComboBox m_cWeapons2 = new JComboBox();
    private JCheckBox m_chkEquipment = new JCheckBox();
    private JComboBox m_cEquipment = new JComboBox();
    private JButton m_bSearch = new JButton(Messages.getString("MechSelectorDialog.Search.Search"));
    private JButton m_bReset = new JButton(Messages.getString("MechSelectorDialog.Search.Reset"));
    private JLabel m_lCount = new JLabel();

    private int m_count;
    private int m_old_nType;
    private int m_old_nUnitType;

    private JPanel m_pUpper = new JPanel();
    JLabel m_pPreview = new JLabel();

    private JLabel m_labelPlayer = new JLabel(Messages.getString("MechSelectorDialog.m_labelPlayer"), JLabel.RIGHT); //$NON-NLS-1$
    private JComboBox m_chPlayer = new JComboBox();

    private boolean includeMaxTech;
    
    private EntityVerifier entityVerifier = new EntityVerifier(new File("data/mechfiles/UnitVerifierOptions.xml"));
    
    public MechSelectorDialog(ClientGUI cl, UnitLoadingDialog uld) {
        super(cl.frame, Messages.getString("MechSelectorDialog.title"), true); //$NON-NLS-1$
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

        m_pListOptions.add(m_labelListOptions);
        m_cModel.addItemListener(this);
        m_pListOptions.add(m_cModel);
        m_cName.addItemListener(this);
        m_pListOptions.add(m_cName);
        m_cTons.addItemListener(this);
        m_pListOptions.add(m_cTons);
        m_cBV.addItemListener(this);
        m_pListOptions.add(m_cBV);
        m_cYear.addItemListener(this);
        m_pListOptions.add(m_cYear);
        m_cLevel.addItemListener(this);
        m_pListOptions.add(m_cLevel);
        m_cCost.addItemListener(this);
        m_pListOptions.add(m_cCost);

        if (GUIPreferences.getInstance().getMechSelectorShowAdvanced()) {
            buildSouthParams(true);
        } else {
            buildSouthParams(false);
        }

        m_pButtons.setLayout(new FlowLayout(FlowLayout.CENTER));
        m_pButtons.add(m_bPick);
        m_pButtons.add(m_bPickClose);
        m_pButtons.add(m_bCancel);
        m_pButtons.add(m_labelPlayer);
        m_pButtons.add(m_chPlayer);

        m_pUpper.setLayout(new BorderLayout());
        m_pPreview.setPreferredSize(new Dimension(84, 72));
        m_pUpper.add(m_pParams, BorderLayout.WEST);
        m_pUpper.add(m_pPreview, BorderLayout.CENTER);
        m_pUpper.add(m_pSouthParams, BorderLayout.SOUTH);

        m_pLeft.setLayout(new BorderLayout());
        m_pLeft.add(m_pUpper, BorderLayout.NORTH);
        m_mechList.setFont(new Font("Monospaced", Font.PLAIN, 12)); //$NON-NLS-1$
        m_mechList.addKeyListener(this);
        m_pLeft.add(new JScrollPane(m_mechList, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        m_pLeft.add(m_pButtons, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(m_pLeft, BorderLayout.CENTER);
        m_mechView.setFont(new Font("Monospaced", Font.PLAIN, 12)); //$NON-NLS-1$
        m_mechView.setEditable(false);
        m_mechView.setOpaque(false);
        getContentPane().add(m_mechView, BorderLayout.EAST);

        //clearMechPreview();
        
        setSize(700, 350);
        setLocation(computeDesiredLocation());
        populateChoices();
        m_chWeightClass.addItemListener(this);
        m_chType.addItemListener(this);
        m_chUnitType.addItemListener(this);
        m_chSort.addItemListener(this);
        m_mechList.addListSelectionListener(this);
        m_bPick.addActionListener(this);
        m_bPickClose.addActionListener(this);
        m_bCancel.addActionListener(this);
        m_bSearch.addActionListener(this);
        m_bReset.addActionListener(this);
        m_bToggleAdvanced.addActionListener(this);
        addWindowListener(this);
        updateWidgetEnablements();
    }

    private void buildSouthParams(boolean showAdvanced) {
        if (showAdvanced) {
            m_bToggleAdvanced.setText(Messages.getString("MechSelectorDialog.Search.Hide"));
            m_pOpenAdvanced.add(m_bToggleAdvanced);

            m_pSouthParams.setLayout(new GridLayout(11, 1));
            m_pSouthParams.add(m_pListOptions);
            m_pSouthParams.add(m_pOpenAdvanced);

            JPanel row1 = new JPanel();
            row1.setLayout(new FlowLayout(FlowLayout.LEFT));
            row1.add(new JLabel(Messages.getString("MechSelectorDialog.Search.Walk")));
            row1.add(m_cWalk);
            row1.add(m_tWalk);
            m_pSouthParams.add(row1);

            JPanel row2 = new JPanel();
            row2.setLayout(new FlowLayout(FlowLayout.LEFT));
            row2.add(new JLabel(Messages.getString("MechSelectorDialog.Search.Jump")));
            row2.add(m_cJump);
            row2.add(m_tJump);
            m_pSouthParams.add(row2);

            JPanel row3 = new JPanel();
            row3.setLayout(new FlowLayout(FlowLayout.LEFT));
            row3.add(new JLabel(Messages.getString("MechSelectorDialog.Search.Armor")));
            row3.add(m_cArmor);
            m_pSouthParams.add(row3);

            JPanel row4 = new JPanel();
            row4.setLayout(new FlowLayout(FlowLayout.LEFT));
            row4.add(new JLabel(Messages.getString("MechSelectorDialog.Search.Weapons")));
            m_pSouthParams.add(row4);

            JPanel row5 = new JPanel();
            row5.setLayout(new FlowLayout(FlowLayout.LEFT));
            row5.add(new JLabel(Messages.getString("MechSelectorDialog.Search.WeaponsAtLeast")));
            row5.add(m_tWeapons1);
            row5.add(m_cWeapons1);
            m_pSouthParams.add(row5);

            JPanel row6 = new JPanel();
            row6.setLayout(new FlowLayout(FlowLayout.LEFT));
            row6.add(m_cOrAnd);
            row6.add(new JLabel(Messages.getString("MechSelectorDialog.Search.WeaponsAtLeast")));
            row6.add(m_tWeapons2);
            row6.add(m_cWeapons2);
            m_pSouthParams.add(row6);

            JPanel row7 = new JPanel();
            row7.setLayout(new FlowLayout(FlowLayout.LEFT));
            row7.add(new JLabel(Messages.getString("MechSelectorDialog.Search.Equipment")));
            m_pSouthParams.add(row7);

            JPanel row8 = new JPanel();
            row8.setLayout(new FlowLayout(FlowLayout.LEFT));
            row8.add(m_chkEquipment);
            row8.add(m_cEquipment);
            m_pSouthParams.add(row8);

            JPanel row9 = new JPanel();
            row9.add(m_bSearch);
            row9.add(m_bReset);
            row9.add(m_lCount);
            m_pSouthParams.add(row9);
        } else {
            m_bToggleAdvanced.setText(Messages.getString("MechSelectorDialog.Search.Show"));
            m_pOpenAdvanced.add(m_bToggleAdvanced);

            m_pSouthParams.setLayout(new GridLayout(2, 1));
            m_pSouthParams.add(m_pListOptions);
            m_pSouthParams.add(m_pOpenAdvanced);
        }
    }

    private void toggleAdvanced() {
        m_pUpper.remove(m_pSouthParams);
        m_pSouthParams = new JPanel();
        if (GUIPreferences.getInstance().getMechSelectorShowAdvanced()) {
            buildSouthParams(false);
            GUIPreferences.getInstance().setMechSelectorShowAdvanced(false);
        } else {
            buildSouthParams(true);
            GUIPreferences.getInstance().setMechSelectorShowAdvanced(true);
        }
        m_pUpper.add(m_pSouthParams, BorderLayout.SOUTH);
        invalidate();
        pack();
    }

    private void updateTechChoice() {
        boolean maxTechOption = m_client.game.getOptions().booleanOption("allow_level_3_units");
        int maxTech = (maxTechOption ? TechConstants.SIZE : TechConstants.SIZE_LEVEL_2);
        if (includeMaxTech == maxTechOption) {
            return;
        }
        includeMaxTech = maxTechOption;
        m_chType.removeAll();
        for (int i = 0; i < maxTech; i++) {
            m_chType.addItem(TechConstants.getLevelDisplayableName(i));
        }
    }

    private void updatePlayerChoice() {
        String lastChoice = (String) m_chPlayer.getSelectedItem();
        m_chPlayer.removeAllItems();
        m_chPlayer.setEnabled(true);
        m_chPlayer.addItem(m_clientgui.getClient().getName());
        for (Iterator i = m_clientgui.getBots().values().iterator(); i.hasNext();) {
            m_chPlayer.addItem(((Client) i.next()).getName());
        }
        if (m_chPlayer.getItemCount() == 1) {
            m_chPlayer.setEnabled(false);
        } else {
            m_chPlayer.setSelectedItem(lastChoice);
        }
    }

    public void run() {
        // Loading mechs can take a while, so it will have its own thread.
        // This prevents the UI from freezing, and allows the
        // "Please wait..." dialog to behave properly on various Java VMs.
        filterMechs(false);
        m_mechList.invalidate();  // force re-layout of window
        pack();
        setLocation(computeDesiredLocation());

        unitLoadingDialog.setVisible(false);

        final Hashtable hFailedFiles = MechSummaryCache.getInstance().getFailedFiles();
        if (hFailedFiles != null && hFailedFiles.size() > 0) {
            new UnitFailureDialog(m_clientgui.frame, hFailedFiles); // self-showing dialog
        }
    }

    private void populateChoices() {

        for (int i = 0; i < EntityWeightClass.SIZE; i++) {
            m_chWeightClass.addItem(EntityWeightClass.getClassName(i));
        }
        m_chWeightClass.addItem(Messages.getString("MechSelectorDialog.All")); //$NON-NLS-1$
        m_chWeightClass.setSelectedIndex(0);

        includeMaxTech = m_client.game.getOptions().booleanOption("allow_level_3_units");
        int maxTech = (includeMaxTech ? TechConstants.SIZE : TechConstants.SIZE_LEVEL_2);
        for (int i = 0; i < maxTech; i++) {
            m_chType.addItem(TechConstants.getLevelDisplayableName(i));
        }
        // m_chType.addItem(Messages.getString("MechSelectorDialog.ISAll")); //$NON-NLS-1$
        // m_chType.addItem(Messages.getString("MechSelectorDialog.ISAndClan")); //$NON-NLS-1$
        // More than 8 items causes the drop down to sprout a vertical
        //  scroll bar.  I guess we'll sacrifice this next one to stay
        //  under the limit.  Stupid AWT Choice class!
        // m_chType.addItem("Mixed All");
        // m_chType.addItem(Messages.getString("MechSelectorDialog.All")); //$NON-NLS-1$
        m_chType.setSelectedIndex(0);

        for (int i = 0; i < UnitType.SIZE; i++) {
            m_chUnitType.addItem(UnitType.getTypeDisplayableName(i));
        }
        m_chUnitType.addItem(Messages.getString("MechSelectorDialog.All")); //$NON-NLS-1$
        m_chUnitType.setSelectedIndex(0);

        m_cWalk.addItem(Messages.getString("MechSelectorDialog.Search.AtLeast"));
        m_cWalk.addItem(Messages.getString("MechSelectorDialog.Search.EqualTo"));
        m_cWalk.addItem(Messages.getString("MechSelectorDialog.Search.NoMoreThan"));
        m_cJump.addItem(Messages.getString("MechSelectorDialog.Search.AtLeast"));
        m_cJump.addItem(Messages.getString("MechSelectorDialog.Search.EqualTo"));
        m_cJump.addItem(Messages.getString("MechSelectorDialog.Search.NoMoreThan"));
        m_cArmor.addItem(Messages.getString("MechSelectorDialog.Search.Any"));
        m_cArmor.addItem(Messages.getString("MechSelectorDialog.Search.Armor25"));
        m_cArmor.addItem(Messages.getString("MechSelectorDialog.Search.Armor50"));
        m_cArmor.addItem(Messages.getString("MechSelectorDialog.Search.Armor75"));
        m_cArmor.addItem(Messages.getString("MechSelectorDialog.Search.Armor90"));
        m_cOrAnd.addItem(Messages.getString("MechSelectorDialog.Search.or"));
        m_cOrAnd.addItem(Messages.getString("MechSelectorDialog.Search.and"));
        populateWeaponsAndEquipmentChoices();
    }

    private void populateWeaponsAndEquipmentChoices() {
        m_cWeapons1.removeAll();
        m_cWeapons2.removeAll();
        m_cEquipment.removeAll();
        m_tWeapons1.setText("");
        m_tWeapons2.setText("");
        m_chkEquipment.setSelected(false);
        int nType = m_chType.getSelectedIndex();
        int nUnitType = m_chUnitType.getSelectedIndex();
        for (Enumeration e = EquipmentType.getAllTypes(); e.hasMoreElements();) {
            EquipmentType et = (EquipmentType) e.nextElement();
            if (et instanceof WeaponType
                    && (et.getTechLevel() == nType
                    || ((nType == TechConstants.T_LEVEL_2_ALL)
                    && ((et.getTechLevel() == TechConstants.T_IS_LEVEL_1)
                    || (et.getTechLevel() == TechConstants.T_IS_LEVEL_2)
                    || (et.getTechLevel() == TechConstants.T_CLAN_LEVEL_2)))
                    || ((nType == TechConstants.T_IS_LEVEL_2_ALL
                    || nType == TechConstants.T_IS_LEVEL_2)
                    && ((et.getTechLevel() == TechConstants.T_IS_LEVEL_1)
                    || (et.getTechLevel() == TechConstants.T_IS_LEVEL_2))))) {
                if (!(nUnitType == UnitType.SIZE) && ((UnitType.getTypeName(nUnitType).equals("Mek")
                        || UnitType.getTypeName(nUnitType).equals("Tank"))
                        && (et.hasFlag(WeaponType.F_PROTOMECH)
                        || et.hasFlag(WeaponType.F_INFANTRY)
                        || et.hasFlag(WeaponType.F_BATTLEARMOR)))) {
                    continue;
                }
                m_cWeapons1.addItem(et.getName());
                m_cWeapons2.addItem(et.getName());
            }
            if (et instanceof MiscType
                    && (et.getTechLevel() == nType
                    || ((nType == TechConstants.T_LEVEL_2_ALL)
                    && ((et.getTechLevel() == TechConstants.T_IS_LEVEL_1)
                    || (et.getTechLevel() == TechConstants.T_IS_LEVEL_2)
                    || (et.getTechLevel() == TechConstants.T_CLAN_LEVEL_2)))
                    || ((nType == TechConstants.T_IS_LEVEL_2_ALL
                    || nType == TechConstants.T_IS_LEVEL_2)
                    && ((et.getTechLevel() == TechConstants.T_IS_LEVEL_1)
                    || (et.getTechLevel() == TechConstants.T_IS_LEVEL_2))))) {
                m_cEquipment.addItem(et.getName());
            }
        }
        m_cWeapons1.invalidate();
        m_cWeapons2.invalidate();
        m_cEquipment.invalidate();
        pack();
    }

    private void filterMechs(boolean calledByAdvancedSearch) {
        ArrayList<MechSummary> vMechs = new ArrayList<MechSummary>();
        int nClass = m_chWeightClass.getSelectedIndex();
        int nType = m_chType.getSelectedIndex();
        int nUnitType = m_chUnitType.getSelectedIndex();
        MechSummary[] mechs = MechSummaryCache.getInstance().getAllMechs();
        if (mechs == null) {
            System.err.println("No units to filter!"); //$NON-NLS-1$
            return;
        }
        for (int x = 0; x < mechs.length; x++) {
            if (/* Weight */
                    (nClass == EntityWeightClass.SIZE || mechs[x].getWeightClass() == nClass)
                    && /* Technology Level */
                    ((nType == TechConstants.T_ALL)
                    || (nType == mechs[x].getType())
                    || ((nType == TechConstants.T_LEVEL_2_ALL)
                    && ((mechs[x].getType() == TechConstants.T_IS_LEVEL_1)
                    || (mechs[x].getType() == TechConstants.T_IS_LEVEL_2)
                    || (mechs[x].getType() == TechConstants.T_CLAN_LEVEL_2)))
                    || ((nType == TechConstants.T_IS_LEVEL_2_ALL)
                    && ((mechs[x].getType() == TechConstants.T_IS_LEVEL_1)
                    || (mechs[x].getType() == TechConstants.T_IS_LEVEL_2))))
                    && /* Unit Type (Mek, Infantry, etc.) */
                    (nUnitType == UnitType.SIZE ||
                    mechs[x].getUnitType().equals(UnitType.getTypeName(nUnitType)))
                    && /*canon required*/ (!m_client.game.getOptions().booleanOption("canon_only") || mechs[x].isCanon())) {
                vMechs.add(mechs[x]);
            }
        }
        m_mechsCurrent = vMechs.toArray(new MechSummary[0]);
        m_count = vMechs.size();
        if (!calledByAdvancedSearch
                && (m_old_nType != nType || m_old_nUnitType != nUnitType)) {
            populateWeaponsAndEquipmentChoices();
        }
        m_old_nType = nType;
        m_old_nUnitType = nUnitType;
        sortMechs();
    }

    private void sortMechs() {
        Arrays.sort(m_mechsCurrent, new MechSummaryComparator(m_chSort.getSelectedIndex()));
        ((DefaultComboBoxModel) m_mechList.getModel()).removeAllElements();
        try {
            m_mechList.setEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            for (int x = 0; x < m_mechsCurrent.length; x++) {
                ((DefaultComboBoxModel) m_mechList.getModel()).addElement(formatMech(m_mechsCurrent[x]));
            }
        } finally {
            setCursor(Cursor.getDefaultCursor());
            m_mechList.setEnabled(true);
            //workaround for bug 1263380
            m_mechList.setFont(m_mechList.getFont());
        }
        updateWidgetEnablements();
        m_lCount.setText(m_mechsCurrent.length + "/" + m_count);
        m_mechList.setPreferredSize(new Dimension(180, m_mechsCurrent.length * 19));
        repaint();
    }

    private void searchFor(String search) {
        for (int i = 0; i < m_mechsCurrent.length; i++) {
            if (m_mechsCurrent[i].getName().toLowerCase().startsWith(search)) {
                m_mechList.setSelectedIndex(i);
//                ItemEvent event = new ItemEvent(m_mechList, ItemEvent.ITEM_STATE_CHANGED, m_mechList, ItemEvent.SELECTED);
//                itemStateChanged(event);
                break;
            }
        }
    }

    private void advancedSearch() {
        String s = m_lCount.getText();
        int first = Integer.parseInt(s.substring(0, s.indexOf('/')));
        int second = Integer.parseInt(s.substring(s.indexOf('/') + 1));
        if (first != second) {
            //Search already active, reset list before starting new one.
            filterMechs(true);
        }

        ArrayList<MechSummary> vMatches = new ArrayList<MechSummary>();
        for (int i = 0; i < m_mechsCurrent.length; i++) {
            MechSummary ms = m_mechsCurrent[i];
            try {
                Entity entity = new MechFileParser(ms.getSourceFile(), ms.getEntryName()).getEntity();
                if (isMatch(entity)) {
                    vMatches.add(ms);
                }
            } catch (EntityLoadingException ex) {
                //do nothing, I guess
            }
        }
        m_mechsCurrent = vMatches.toArray(new MechSummary[0]);
        clearMechPreview();
        sortMechs();
    }

    private boolean isMatch(Entity entity) {
        int walk = -1;
        try {
            walk = Integer.parseInt(m_tWalk.getText());
        } catch (NumberFormatException ne) {
            //never get here
        }
        if (walk > -1) {
            if (m_cWalk.getSelectedIndex() == 0) { //at least
                if (entity.getWalkMP() < walk)
                    return false;
            } else if (m_cWalk.getSelectedIndex() == 1) { //equal to
                if (walk != entity.getWalkMP())
                    return false;
            } else if (m_cWalk.getSelectedIndex() == 2) { //not more than
                if (entity.getWalkMP() > walk)
                    return false;
            }
        }

        int jump = -1;
        try {
            jump = Integer.parseInt(m_tJump.getText());
        } catch (NumberFormatException ne) {
            //never get here
        }
        if (jump > -1) {
            if (m_cJump.getSelectedIndex() == 0) { //at least
                if (entity.getJumpMP() < jump)
                    return false;
            } else if (m_cJump.getSelectedIndex() == 1) { //equal to
                if (jump != entity.getJumpMP())
                    return false;
            } else if (m_cJump.getSelectedIndex() == 2) { //not more than
                if (entity.getJumpMP() > jump)
                    return false;
            }
        }

        int sel = m_cArmor.getSelectedIndex();
        if (sel > 0) {
            int armor = entity.getTotalArmor();
            int maxArmor = entity.getTotalInternal() * 2 + 3;
            if (sel == 1) {
                if (armor < (maxArmor * .25))
                    return false;
            } else if (sel == 2) {
                if (armor < (maxArmor * .5))
                    return false;
            } else if (sel == 3) {
                if (armor < (maxArmor * .75))
                    return false;
            } else if (sel == 4) {
                if (armor < (maxArmor * .9))
                    return false;
            }
        }

        boolean weaponLine1Active = false;
        boolean weaponLine2Active = false;
        boolean foundWeapon1 = false;
        boolean foundWeapon2 = false;

        int count = 0;
        int weapon1 = -1;
        try {
            weapon1 = Integer.parseInt(m_tWeapons1.getText());
        } catch (NumberFormatException ne) {
            //never get here
        }
        if (weapon1 > -1) {
            weaponLine1Active = true;
            for (int i = 0; i < entity.getWeaponList().size(); i++) {
                WeaponType wt = (WeaponType) (entity.getWeaponList().get(i)).getType();
                if (wt.getName().equals(m_cWeapons1.getSelectedItem())) {
                    count++;
                }
            }
            if (count >= weapon1)
                foundWeapon1 = true;
        }

        count = 0;
        int weapon2 = -1;
        try {
            weapon2 = Integer.parseInt(m_tWeapons2.getText());
        } catch (NumberFormatException ne) {
            //never get here
        }
        if (weapon2 > -1) {
            weaponLine2Active = true;
            for (int i = 0; i < entity.getWeaponList().size(); i++) {
                WeaponType wt = (WeaponType) (entity.getWeaponList().get(i)).getType();
                if (wt.getName().equals(m_cWeapons2.getSelectedItem())) {
                    count++;
                }
            }
            if (count >= weapon2)
                foundWeapon2 = true;
        }

        if (weaponLine1Active && !weaponLine2Active && !foundWeapon1)
            return false;
        if (weaponLine2Active && !weaponLine1Active && !foundWeapon2)
            return false;
        if (weaponLine1Active && weaponLine2Active) {
            if (m_cOrAnd.getSelectedIndex() == 0 /* 0 is "or" choice */) {
                if (!foundWeapon1 && !foundWeapon2)
                    return false;
            } else { //"and" choice in effect
                if (!foundWeapon1 || !foundWeapon2)
                    return false;
            }
        }

        count = 0;
        if (m_chkEquipment.isSelected()) {
            for (Mounted m : entity.getMisc()) {
                MiscType mt = (MiscType) m.getType();
                if (mt.getName().equals(m_cEquipment.getSelectedItem())) {
                    count++;
                }
            }
            if (count < 1)
                return false;
        }

        return true;
    }

    private void resetSearch() {
        m_cWalk.setSelectedIndex(0);
        m_tWalk.setText("");
        m_cJump.setSelectedIndex(0);
        m_tJump.setText("");
        m_cArmor.setSelectedIndex(0);
        m_tWeapons1.setText("");
        m_cWeapons1.setSelectedIndex(0);
        m_cOrAnd.setSelectedIndex(0);
        m_tWeapons2.setText("");
        m_cWeapons2.setSelectedIndex(0);
        m_chkEquipment.setSelected(false);
        m_cEquipment.setSelectedIndex(0);

        filterMechs(false);
    }

    private Point computeDesiredLocation() {
        int desiredX = m_clientgui.frame.getLocation().x + m_clientgui.frame.getSize().width / 2 - getSize().width / 2;
        if (desiredX < 0)
            desiredX = 0;
        int desiredY = m_clientgui.frame.getLocation().y + m_clientgui.frame.getSize().height / 2 - getSize().height / 2;
        if (desiredY < 0)
            desiredY = 0;
        return new Point(desiredX, desiredY);
    }

    public void setVisible(boolean visible) {
        updatePlayerChoice();
        updateTechChoice();
        setLocation(computeDesiredLocation());
        super.setVisible(visible);
    }

    private String formatMech(MechSummary ms) {
        String val = "";
        String levelOrValid;

        if (!ms.getLevel().equals("F")) {
            levelOrValid = TechConstants.T_SIMPLE_LEVEL[ms.getType()];
        } else {
            levelOrValid = "F";
        }
        if (GUIPreferences.getInstance().getMechSelectorIncludeModel())
            val += makeLength(ms.getModel(), 10) + " "; //$NON-NLS-1$ //$NON-NLS-2$
        if (GUIPreferences.getInstance().getMechSelectorIncludeName())
            val += makeLength(ms.getChassis(), 20) + " "; //$NON-NLS-1$ //$NON-NLS-2$
        if (GUIPreferences.getInstance().getMechSelectorIncludeTons())
            val += makeLength("" + ms.getTons(), 3) + " "; //$NON-NLS-1$ //$NON-NLS-2$
        if (GUIPreferences.getInstance().getMechSelectorIncludeBV())
            val += makeLength("" + ms.getBV(), 5) + " "; //$NON-NLS-1$ //$NON-NLS-2$
        if (GUIPreferences.getInstance().getMechSelectorIncludeYear())
            val += ms.getYear() + " ";
        if (GUIPreferences.getInstance().getMechSelectorIncludeLevel())
            val += levelOrValid + " ";
        if (GUIPreferences.getInstance().getMechSelectorIncludeCost())
            val += ms.getCost() + " ";
        return val;
    }

    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource().equals(m_bCancel)) {
            setVisible(false);
        } else if (ae.getSource().equals(m_bPick) ||
                ae.getSource().equals(m_bPickClose)) {
            int x = m_mechList.getSelectedIndex();
            if (x == -1) {
                return;
            }
            MechSummary ms = m_mechsCurrent[m_mechList.getSelectedIndex()];
            try {
                Entity e = new MechFileParser(ms.getSourceFile(), ms.getEntryName()).getEntity();
                Client c = null;
                if (m_chPlayer.getSelectedIndex() > 0) {
                    String name = (String) m_chPlayer.getSelectedItem();
                    c = (Client) m_clientgui.getBots().get(name);
                }
                if (c == null) {
                    c = m_client;
                }
                e.setOwner(c.getLocalPlayer());
                c.sendAddEntity(e);
            } catch (EntityLoadingException ex) {
                System.out.println("Unable to load mech: " + ms.getSourceFile() + ": " + ms.getEntryName() + ": " + ex.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                ex.printStackTrace();
                return;
            }
            if (ae.getSource().equals(m_bPickClose)) {
                setVisible(false);
            }
        } else if (ae.getSource().equals(m_bSearch)) {
            advancedSearch();
        } else if (ae.getSource().equals(m_bReset)) {
            resetSearch();
        } else if (ae.getSource().equals(m_bToggleAdvanced)) {
            toggleAdvanced();
        }
    }

    public void itemStateChanged(ItemEvent ie) {
        if (ie.getSource().equals(m_chSort)) {
            clearMechPreview();
            sortMechs();
        } else if (ie.getSource().equals(m_chWeightClass)
                || ie.getSource().equals(m_chType)
                || ie.getSource().equals(m_chUnitType)) {
            clearMechPreview();
            filterMechs(false);
        } else if (ie.getSource().equals(m_cModel) ||
                ie.getSource().equals(m_cName) ||
                ie.getSource().equals(m_cTons) ||
                ie.getSource().equals(m_cBV) ||
                ie.getSource().equals(m_cYear) ||
                ie.getSource().equals(m_cLevel) ||
                ie.getSource().equals(m_cCost)) {
            GUIPreferences.getInstance().setMechSelectorIncludeModel(m_cModel.isSelected());
            GUIPreferences.getInstance().setMechSelectorIncludeName(m_cName.isSelected());
            GUIPreferences.getInstance().setMechSelectorIncludeTons(m_cTons.isSelected());
            GUIPreferences.getInstance().setMechSelectorIncludeBV(m_cBV.isSelected());
            GUIPreferences.getInstance().setMechSelectorIncludeYear(m_cYear.isSelected());
            GUIPreferences.getInstance().setMechSelectorIncludeLevel(m_cLevel.isSelected());
            GUIPreferences.getInstance().setMechSelectorIncludeCost(m_cCost.isSelected());
            clearMechPreview();
            sortMechs(); // sorting has side-effect of repopulating list
            m_mechList.invalidate();  // force re-layout of window
            pack();
            setLocation(computeDesiredLocation());
        }
    }

    void clearMechPreview() {
        m_mechView.setEditable(false);
        m_mechView.setText(""); //$NON-NLS-1$

        // Remove preview image.        
        if (MechSummaryCache.getInstance().isInitialized()) {
            //m_pPreview.removeBgDrawers();
            m_pPreview.repaint();
        }
    }

    void previewMech(Entity entity) {
        MechView mechView = new MechView(entity);
        m_mechView.setEditable(false);
        String readout = mechView.getMechReadout();
        StringBuffer sb = new StringBuffer(readout);
        m_mechView.setText(readout);
        if(entity instanceof Mech || entity instanceof Tank) {
            TestEntity testEntity = null;
            if (entity instanceof Mech)
                testEntity = new TestMech((Mech)entity, entityVerifier.mechOption, null);
            if (entity instanceof Tank)
                testEntity = new TestTank((Tank)entity, entityVerifier.tankOption, null);
            if (!testEntity.correctEntity(sb, !m_clientgui.getClient().game.getOptions().booleanOption("is_eq_limits"))) {
                m_mechView.setText(sb.toString());
            }
        }
        m_mechView.setCaretPosition(0);

        // Preview image of the unit...
        m_clientgui.loadPreviewImage(m_pPreview, entity, m_client.getLocalPlayer());
        m_pPreview.repaint();
    }

    private static final String SPACES = "                        "; //$NON-NLS-1$

    private String makeLength(String s, int nLength) {
        if (s.length() == nLength) {
            return s;
        } else if (s.length() > nLength) {
            return s.substring(0, nLength - 2) + ".."; //$NON-NLS-1$
        } else {
            return s + SPACES.substring(0, nLength - s.length());
        }
    }

    public void keyReleased(KeyEvent ke) {
    }

    public void keyPressed(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
            ActionEvent event = new ActionEvent(m_bPick, ActionEvent.ACTION_PERFORMED, ""); //$NON-NLS-1$
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

    public void keyTyped(KeyEvent ke) {
    }

    //
    // WindowListener
    //
    public void windowActivated(WindowEvent windowEvent) {
    }

    public void windowClosed(WindowEvent windowEvent) {
    }

    public void windowClosing(WindowEvent windowEvent) {
        setVisible(false);
    }

    public void windowDeactivated(WindowEvent windowEvent) {
    }

    public void windowDeiconified(WindowEvent windowEvent) {
    }

    public void windowIconified(WindowEvent windowEvent) {
    }

    public void windowOpened(WindowEvent windowEvent) {
    }

    private void updateWidgetEnablements() {
        final boolean enable = m_mechList.getSelectedIndex() != -1;
        m_bPick.setEnabled(enable);
        m_bPickClose.setEnabled(enable);
    }

    public void valueChanged(ListSelectionEvent event) {
        if (event.getSource().equals(m_mechList)) {
            updateWidgetEnablements();
            int selected = m_mechList.getSelectedIndex();
            if (selected == -1) {
                clearMechPreview();
                return;
            }
			MechSummary ms = m_mechsCurrent[selected];
			try {
			    Entity entity = new MechFileParser(ms.getSourceFile(), ms.getEntryName()).getEntity();
			    previewMech(entity);
			} catch (EntityLoadingException ex) {
			    System.out.println("Unable to load mech: " + ms.getSourceFile() + ": " + ms.getEntryName() + ": " + ex.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			    ex.printStackTrace();
			    clearMechPreview();
			    return;
			}
        }
    }
}
