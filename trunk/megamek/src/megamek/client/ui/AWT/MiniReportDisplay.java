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

package megamek.client;

import java.awt.*;
import java.awt.event.*;

/**
 * Shows a Report, with an Okay Button
 */
public class MiniReportDisplay extends Dialog 
    implements ActionListener
{
    private Button butOkay;
    private TextArea taData;

    public MiniReportDisplay(Frame parent, String sReport) {
        super(parent, "Turn Report", true);
        
        taData = new TextArea(sReport, 20, 48);
        taData.setEditable(false);
        butOkay = new Button("Okay");
        butOkay.addActionListener(this);
        setLayout(new BorderLayout());
        
        add(BorderLayout.CENTER, taData);
        add(BorderLayout.SOUTH, butOkay);
        setSize(400, 300);
        doLayout();
        setLocation(parent.getLocation().x + parent.getSize().width/2 - getSize().width/2,
                    parent.getLocation().y + parent.getSize().height/2 - getSize().height/2);
    }
    
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == butOkay) {
            hide();
        }
    }
}
