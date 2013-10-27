/*
 * MegaMek - Copyright (C) 2000-2003 Ben Mazur (bmazur@sev.org)
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

import megamek.common.*;
import megamek.client.util.*;

/**
 * A simple yes/no confirmation dialog.
 */
public class ConfirmDialog
    extends Dialog implements ActionListener {
    
    private GridBagLayout gridbag = new GridBagLayout();
    private GridBagConstraints c = new GridBagConstraints();

    private boolean useCheckbox;
    private Checkbox botherCheckbox;

    private Panel panButtons = new Panel();
    private Button butYes = new Button(Messages.getString("Yes")); //$NON-NLS-1$
    private Button butNo = new Button(Messages.getString("No")); //$NON-NLS-1$
    private Button defaultButton = butYes;

    private boolean confirmation = false;

    private Component firstFocusable;


  /** Creates a new dialog window that lets the user answer Yes or No,
    * with the Yes button pre-focused
    *
    * @param   title            a title for the dialog window
    * @param   question         the text of the dialog
    */
    public ConfirmDialog(Frame p, String title, String question) {
        this(p, title, question, false);
    }


  /** Creates a new dialog window that lets the user answer Yes or No,
    * with either the Yes or No button pre-focused
    *
    * @param   title            a title for the dialog window
    * @param   question         the text of the dialog
    * @param   defButton        set it to 'n' to make the No button pre-focused (Yes button is focused by default)
    */
    public ConfirmDialog(Frame p, String title, String question, char defButton) {
        this(p, title, question, false, defButton);
    }

  /** Creates a new dialog window that lets the user answer Yes or No,
    * with an optional checkbox to specify future behaviour,
    * and the Yes button pre-focused
    *
    * @param   title            a title for the dialog window
    * @param   question         the text of the dialog
    * @param   includeCheckbox  whether the dialog includes a "bother me" checkbox for the user to tick
    */
    public ConfirmDialog(Frame p, String title, String question, boolean includeCheckbox) {
        this(p, title, question, includeCheckbox, 'y');
    }

  /** Creates a new dialog window that lets the user answer Yes or No,
    * with an optional checkbox to specify future behaviour,
    * and either the Yes or No button pre-focused
    *
    * @param   title            a title for the dialog window
    * @param   question         the text of the dialog
    * @param   includeCheckbox  whether the dialog includes a "bother me" checkbox for the user to tick
    * @param   defButton        set it to 'n' to make the No button pre-focused (Yes button is focused by default)
    */
    public ConfirmDialog(Frame p, String title, String question, boolean includeCheckbox, char defButton) {
        super(p, title, true);

        if ('n'==defButton) {
            defaultButton = butNo;
        };
        
        super.setResizable(false);
        useCheckbox = includeCheckbox;

        setLayout(gridbag);
        addQuestion(question);
        addInputs();
        finishSetup(p);
    };

    private void addQuestion(String question) {
        AdvancedLabel questionLabel = new AdvancedLabel(question);
        c.gridheight = 2;
        c.insets = new Insets(5, 5, 5, 5);
        gridbag.setConstraints(questionLabel, c);
        add(questionLabel);
    }

    private void addInputs() {
        int y = 2;

        c.gridheight = 1;

        if (useCheckbox) {
            botherCheckbox = new Checkbox(Messages.getString("ConfirmDialog.dontBother")); //$NON-NLS-1$
        
            c.gridy = y++;
            gridbag.setConstraints(botherCheckbox, c);
            add(botherCheckbox);
        }

        butYes.addActionListener(this);
        butNo.addActionListener(this);

        GridBagLayout buttonGridbag = new GridBagLayout();
        GridBagConstraints bc = new GridBagConstraints();
        panButtons.setLayout(buttonGridbag);
        bc.insets = new Insets(5, 5, 5, 5);
        bc.ipadx = 20;    bc.ipady = 5;
        buttonGridbag.setConstraints(butYes, bc);
        panButtons.add(butYes);
        buttonGridbag.setConstraints(butNo, bc);
        panButtons.add(butNo);

        c.gridy = y;

        gridbag.setConstraints(panButtons, c);
        add(panButtons);
    }

    private void finishSetup(Frame p) {
        addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    setVisible(false);
                }
            });

        pack();
        
        Dimension size = getSize();
        boolean updateSize = false;
        if ( size.width < Settings.minimumSizeWidth ) {
            size.width = Settings.minimumSizeWidth;
            updateSize = true;
        }
        if ( size.height < Settings.minimumSizeHeight ) {
            size.height = Settings.minimumSizeHeight; 
            updateSize = true;
        }
        if ( updateSize ) {
            setSize( size );
            size = getSize();
        }
        setLocation(p.getLocation().x + p.getSize().width/2 - size.width/2,
                    p.getLocation().y + p.getSize().height/2 - size.height/2);


        // work out which component will get the focus in the window
        if (useCheckbox) {
            firstFocusable=botherCheckbox;
        } else {
            firstFocusable=butYes;
        };
        // we'd like the default button to have focus, but that can only be done on displayed
        // dialogs in Windows. So, this rather elaborate setup: as soon as the first focusable
        // component receives the focus, it shunts the focus to the OK button, and then
        // removes the FocusListener to prevent this happening again

        if (!firstFocusable.equals(defaultButton)) {
            firstFocusable.addFocusListener(new FocusListener() {
                public void focusGained(FocusEvent e) {
                    defaultButton.requestFocus();
                }
                public void focusLost(FocusEvent e) {
                    firstFocusable.removeFocusListener(this); // refers to listener
                }
            });
        };
    }
    
    public boolean getAnswer() {
        return confirmation;
    }

    public boolean getShowAgain() {
        if (botherCheckbox == null) {
            return true;
        }
        return !botherCheckbox.getState();
    }

    public void actionPerformed(ActionEvent actionEvent) {
        if (actionEvent.getSource() == butYes) {
            confirmation = true;
        } else if (actionEvent.getSource() == butNo) {
            confirmation = false;
        }
        this.setVisible(false);
    }
}