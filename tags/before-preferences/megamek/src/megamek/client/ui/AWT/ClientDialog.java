/*
 * MegaMek - Copyright (C) 2004, 2005 Ben Mazur (bmazur@sev.org)
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

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Window;

/**
 * A MegaMek Dialog box.
 */
public class ClientDialog extends Dialog {

    private static final int SCREEN_BORDER = 10;
    private static final int CONTAINER_BUFFER = 10;

    /**
     * @param owner -
     *            the <code>Frame</code> that owns this dialog.
     * @param string -
     *            the title of this Dialog window
     */
    public ClientDialog(Frame owner, String title) {
        super(owner, title);
    }
    public ClientDialog(Frame owner, String title, boolean modal) {
        super(owner, title, modal);
    }

    /**
     * Set the size and location to something sane (always within the screen).
     * We try to fit the dialog in the middle of its owner, if it is smaller,
     * but allow it to eclipse the parent if it is larger, still keeping all on
     * the screen.
     *
     * @param desiredX
     *            the desired width of this dialog (you might not get it)
     * @param desiredY
     *            the desired height of this dialog (you might not get it)
     */
    public void setLocationAndSize(int desiredX, int desiredY){
    	setLocationAndSize(new Dimension(desiredX,desiredY));
    }

    /**
     * Set the size and location to something sane (always within the screen).
     * We try to fit the dialog in the middle of its owner, if it is smaller,
     * but allow it to eclipse the parent if it is larger, still keeping all on
     * the screen.
     *
     * @param desiredDimension
     *            the desired dimension of this dialog (you might not get it)
     */
    protected void setLocationAndSize(Dimension desiredDimension) {
        int yLoc, xLoc, height, width;

        Window owner = this.getOwner();
        Dimension screenSize = owner.getToolkit().getScreenSize();

        width = Math.min( desiredDimension.width + CONTAINER_BUFFER,
                          screenSize.width );
        height = Math.min( desiredDimension.height + CONTAINER_BUFFER,
                           screenSize.height );

        //shrink the dialog if it will go bigger than page:
        if (height > screenSize.height)
            height = screenSize.height - SCREEN_BORDER;
        if (width > screenSize.width)
            width = screenSize.width - SCREEN_BORDER;

        Dimension ownerCenter = getOwnersCenter();
        yLoc = ownerCenter.height - height / 2;
        xLoc = ownerCenter.width - width / 2;

        if (yLoc < SCREEN_BORDER)
            yLoc = SCREEN_BORDER;
        if (xLoc < SCREEN_BORDER)
            xLoc = SCREEN_BORDER;

        //now check if the window goes past the end of the screen
        if ((yLoc + height) > screenSize.height) {
            yLoc = (screenSize.height - SCREEN_BORDER) - height;
        }
        if ((xLoc + width) > screenSize.width) {
            xLoc = (screenSize.width - SCREEN_BORDER) - width;
        }

        setSize(width, height);
        setLocation(xLoc, yLoc);
    }

    private Dimension getOwnersCenter() {
        Dimension center = new Dimension();
        center.height = this.getOwner().getLocation().y
            + this.getOwner().getSize().height / 2;
        center.width = this.getOwner().getLocation().x
            + this.getOwner().getSize().width / 2;
        return center;
    }

}