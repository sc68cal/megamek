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





package megamek.client.util.widget;

import java.awt.*;


/**
 * Set of usefull function. 
 */

public final class PMUtil {
	
	/**
     * Ensures that Images is completely loaded
     */
	
	public static boolean setImage(Image im, Component c){
		boolean b = true;
		MediaTracker mt = new MediaTracker(c);
        mt.addImage(im,0);
        try{
            mt.waitForID(0);
        } catch (InterruptedException e){
            System.out.println("Error while image loading.");
            b = false;
        }
        if(mt.isErrorID(0)){
            System.out.println("Could Not load Image.");
            b = false;
        }
        
        return b;
	}
	
	/**
     * Returns Window class that contains given component
     */
	
	public static Window getParentWindow(Component c){
		Container container = c.getParent();
		while (!(container instanceof Window) || (container == null)){
			container = container.getParent(); 
		}
		return (Window) container;
	}
	
	
}