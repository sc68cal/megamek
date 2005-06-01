/*
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

/*
 * TechConstants.java
 *
 * Created on June 11, 2002, 4:35 PM
 */

package megamek.common;

/**
 * Contains some constants representing equipment/unit tech levels
 *
 * @author  Ben
 * @version 
 */
public class TechConstants {

    /*
     * These can apply to entities or individual pieces of equipment
     */
    public static final int         T_TECH_UNKNOWN      = -1;
    public static final int         T_IS_LEVEL_1        = 0;
    public static final int         T_IS_LEVEL_2        = 1;
    public static final int         T_CLAN_LEVEL_2      = 2;

    //These two are only for filtering selections in the MechSelectorDialog
    public static final int         T_IS_LEVEL_2_ALL    = 3;
    public static final int         T_LEVEL_2_ALL       = 4;

    public static final int         T_IS_LEVEL_3        = 5;
    public static final int         T_CLAN_LEVEL_3      = 6;

    public static final int         T_ALL               = 7;

    //Number of legal level 2 choices
    public static final int SIZE_LEVEL_2    = 5;

    // this made public because MekWars accesses it
    // It must match the index to the constant's value.
    public static final String[]    T_NAMES = {"IS level 1",
                                               "IS level 2",
                                               "Clan level 2",
                                               "IS level 1 and 2",
                                               "All level 2",
                                               "IS level 3",
                                               "Clan level 3",
                                                "All"};

    public static final int SIZE            = T_NAMES.length;

    //This translates the integer above into a simple level number.
    // The "all" selections return -1, since they don't apply to
    // individual units.
    public static final String[]    T_SIMPLE_LEVEL = {"1",
                                                      "2",
                                                      "2",
                                                      "-1",
                                                      "-1",
                                                      "3",
                                                      "3",
                                                      "-1"};

    public static String getLevelName(int level) {
        if (level >= 0 && level < SIZE) {
            return T_NAMES[level];
        }
        else
        {
            throw new IllegalArgumentException("Unknown tech level");            
        }
    }

    public static String getLevelDisplayableName(int level) {
        if (level >= 0 && level < SIZE) {
            return Messages.getString("TechLevel."+T_NAMES[level]);
        }
        else
        {
            throw new IllegalArgumentException("Unknown tech level");            
        }
    }

}

