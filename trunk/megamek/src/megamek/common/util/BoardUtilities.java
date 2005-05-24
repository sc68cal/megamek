/*
 * MegaMek - Copyright (C) 2005 Ben Mazur (bmazur@sev.org)
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

package megamek.common.util;

import megamek.common.Board;
import megamek.common.Compute;
import megamek.common.Hex;
import megamek.common.IBoard;
import megamek.common.IHex;
import megamek.common.ITerrain;
import megamek.common.ITerrainFactory;
import megamek.common.MapSettings;
import megamek.common.Terrains;

import com.sun.java.util.collections.HashMap;
import com.sun.java.util.collections.HashSet;
import com.sun.java.util.collections.Iterator;

public class BoardUtilities {
    
    /**
     * Combines one or more boards into one huge megaboard!
     *
     * @param width the width of each individual board, before the combine
     * @param height the height of each individual board, before the combine
     * @param sheetWidth how many sheets wide the combined map is
     * @param sheetHeight how many sheets tall the combined map is
     * @param boards an array of the boards to be combined
     */
    public static IBoard combine(int width, int height, int sheetWidth, int sheetHeight, IBoard[] boards) {
        
        IBoard result = new Board(width * sheetWidth, height * sheetHeight);
        
        // Copy the data from the sub-boards.
        for (int i = 0; i < sheetHeight; i++) {
            for (int j = 0; j < sheetWidth; j++) {
                copyBoardInto(result, j * width, i * height, boards[i * sheetWidth + j]);
                // Copy in the other board's options.
                if ( boards[i * sheetWidth + j].getRoadsAutoExit() == false ) {
                    result.setRoadsAutoExit(false);
                }
            }
        }

        //Initialize all hexes - buildings, exits, etc
        result.initializeAll();

        return result;
    }
    
    /**
     * Copies the data of another board into this one, offset by the specified
     * x and y.
     *
     * Currently just shallowly copies the boards.
     *
     */
    protected static void copyBoardInto(IBoard dest, int x, int y, IBoard copied) {
        for (int i = 0; i < copied.getHeight(); i++) {
            for (int j = 0; j < copied.getWidth(); j++) {
                dest.setHex(j+x, i+y, copied.getHex(j,i));
            }
        }
    }
    
    /**
     Generates a Random Board
     @param width The width of the generated Board.
     @param height The height of the gernerated Board.
     @param steps how often the iterative method should be repeated
     */
    public static IBoard generateRandom(MapSettings mapSettings) {
        int elevationMap[][] = new int[mapSettings.getBoardWidth()][mapSettings.getBoardHeight()];
        double sizeScale = (double)(mapSettings.getBoardWidth() * mapSettings.getBoardHeight()) / ((double)(16 * 17));
        
        generateElevation(mapSettings.getHilliness(),
                mapSettings.getBoardWidth(), 
                mapSettings.getBoardHeight(),
                mapSettings.getRange() + 1, 
                mapSettings.getProbInvert(),
                elevationMap,
                mapSettings.getAlgorithmToUse());
        
        IHex[] nb = new IHex[mapSettings.getBoardWidth() * mapSettings.getBoardHeight()];
        int index = 0;
        for (int h = 0; h < mapSettings.getBoardHeight(); h++) {
            for (int w = 0; w < mapSettings.getBoardWidth(); w++) {
                nb[index++] = new Hex(elevationMap[w][h],"","");
            }
        }
        
        IBoard result = new Board(mapSettings.getBoardWidth(),mapSettings.getBoardHeight(), nb);
        /* initalize reverseHex */
        HashMap reverseHex = new HashMap(2 * mapSettings.getBoardWidth() * mapSettings.getBoardHeight());
        for (int y = 0; y < mapSettings.getBoardHeight(); y++) {
            for (int x = 0; x < mapSettings.getBoardWidth(); x++) {
                reverseHex.put(result.getHex(x, y),new Point(x, y));
            }
        }
        
        /* Add the woods */
        int count = mapSettings.getMinForestSpots();
        if (mapSettings.getMaxForestSpots() > 0) {
            count += Compute.randomInt(mapSettings.getMaxForestSpots());
        }
        count *= sizeScale;
        for (int i = 0; i < count; i++) {
            placeSomeTerrain(result, Terrains.WOODS, mapSettings.getProbHeavy() ,
                    mapSettings.getMinForestSize(), 
                    mapSettings.getMaxForestSize(),
                    reverseHex);
        }
        /* Add the water */
        count = mapSettings.getMinWaterSpots();
        if (mapSettings.getMaxWaterSpots() > 0) { 
            count += Compute.randomInt(mapSettings.getMaxWaterSpots());
        }
        count *= sizeScale;
        for (int i = 0; i < count; i++) {
            placeSomeTerrain(result, Terrains.WATER, mapSettings.getProbDeep() ,
                    mapSettings.getMinWaterSize(), 
                    mapSettings.getMaxWaterSize(),
                    reverseHex);
        }
        
        /* Add the rough */
        count = mapSettings.getMinRoughSpots();
        if (mapSettings.getMaxRoughSpots() > 0) {
            count += Compute.randomInt(mapSettings.getMaxRoughSpots());
        }
        count *= sizeScale;
        for (int i = 0; i < count; i++) {
            placeSomeTerrain(result, Terrains.ROUGH, 0,
                    mapSettings.getMinRoughSize(), 
                    mapSettings.getMaxRoughSize(),
                    reverseHex);
        }
        /* Add the swamp */
        count = mapSettings.getMinSwampSpots();
        if (mapSettings.getMaxSwampSpots() > 0) {
            count += Compute.randomInt(mapSettings.getMaxSwampSpots());
        }
        count *= sizeScale;
        for (int i = 0; i < count; i++) {
            placeSomeTerrain(result, Terrains.SWAMP, 0,
                    mapSettings.getMinSwampSize(), 
                    mapSettings.getMaxSwampSize(),
                    reverseHex);
        }
        /* Add the craters */
        if (Compute.randomInt(100) < mapSettings.getProbCrater()) {
            addCraters(result, mapSettings.getMinRadius(), mapSettings.getMaxRadius(),
                    (int)(mapSettings.getMinCraters()*sizeScale),
                    (int)(mapSettings.getMaxCraters()*sizeScale));
        }
        
        /* Add the river */
        if (Compute.randomInt(100)<mapSettings.getProbRiver()) {
            addRiver(result, reverseHex);
        }
        
        /* Add the road */
        if (Compute.randomInt(100)<mapSettings.getProbRoad()) {
            addRoad(result, reverseHex);
        }
        return result;
    }
    
    /**
     * Places randomly some connected Woods.
     * @param probHeavy The probability that a wood is a heavy wood (in %).
     * @param maxWoods Maximum Number of Woods placed.
     */
    protected static void placeSomeTerrain(IBoard board, int terrainType, int probMore,int minHexes, int maxHexes, HashMap reverseHex) {
        Point p = new Point(Compute.randomInt(board.getWidth()),Compute.randomInt(board.getHeight()));
        int count = minHexes;
        if ((maxHexes - minHexes) > 0) {
            count += Compute.randomInt(maxHexes-minHexes);
        }
        IHex field;
        
        HashSet alreadyUsed = new HashSet();
        HashSet unUsed = new HashSet();
        field = board.getHex(p.x, p.y);
        if (!field.containsTerrain(terrainType)) {
            unUsed.add(field);
        } else {
            findAllUnused(board, terrainType, alreadyUsed,
                    unUsed, field, reverseHex);
        }
        ITerrainFactory f = Terrains.getTerrainFactory();        
        for (int i = 0; i < count; i++) {
            if (unUsed.isEmpty()) {
                return;
            }
            int which = Compute.randomInt(unUsed.size());
            Iterator iter = unUsed.iterator();
            for (int n = 0; n < (which - 1); n++)
                iter.next();
            field = (IHex)iter.next();
            field.removeAllTerrains();
            int tempInt = (Compute.randomInt(100) < probMore)? 2 : 1;
            ITerrain tempTerrain = f.createTerrain(terrainType, tempInt);
            field.addTerrain(tempTerrain);
            unUsed.remove(field);
            findAllUnused(board, terrainType, alreadyUsed, unUsed, field, reverseHex);
        }
        
        if (terrainType == Terrains.WATER) {
            /* if next to an Water Hex is an lower lvl lower the hex.
             First we search for lowest Hex next to the lake */
            int min = Integer.MAX_VALUE;
            Iterator iter = unUsed.iterator();
            while (iter.hasNext()) {
                field = (IHex)iter.next();
                if (field.getElevation() < min) {
                    min = field.getElevation();
                }
            }
            iter = alreadyUsed.iterator();
            while (iter.hasNext()) {
                field = (IHex)iter.next();
                field.setElevation(min);
            }
            
        }
    }
    /**
     * Searching starting from one Hex, all Terrains not matching
     * terrainType, next to one of terrainType.
     * @param terrainType The terrainType which the searching hexes
     * should not have.
     * @param alreadyUsed The hexes which should not looked at
     * (because they are already supposed to visited in some way) 
     * @param unUsed In this set the resulting hexes are stored. They
     * are stored in addition to all previously stored.
     * @param searchFrom The Hex where to start
     */
    private static void findAllUnused(IBoard board, int terrainType, HashSet alreadyUsed,
            HashSet unUsed, IHex searchFrom, HashMap reverseHex) {
        IHex field;
        HashSet notYetUsed = new HashSet();
        
        notYetUsed.add(searchFrom);
        do {
            Iterator iter = notYetUsed.iterator();
            field = (IHex)iter.next();
            if (field == null) {
                continue;
            }
            for (int dir = 0; dir < 6; dir++) {
                Point loc = (Point) reverseHex.get(field);
                IHex newHex = board.getHexInDir(loc.x, loc.y, dir);
                if ((newHex != null) && 
                        (!alreadyUsed.contains(newHex)) &&
                        (!notYetUsed.contains(newHex)) &&
                        (!unUsed.contains(newHex))) {
                    ((newHex.containsTerrain(terrainType)) ? notYetUsed : unUsed ).add(newHex);
                }
            }
            notYetUsed.remove(field);
            alreadyUsed.add(field);
        } while (!notYetUsed.isEmpty());
    }
    
    /** 
     * add a crater to the board
     */
    public static void addCraters(IBoard board, int minRadius, int maxRadius,int minCraters, int maxCraters) {
        int numberCraters = minCraters;
        if (maxCraters > minCraters) {
            numberCraters += Compute.randomInt(maxCraters - minCraters);
        }
        for (int i = 0; i < numberCraters; i++) {
            int width = board.getWidth();
            int height = board.getHeight();
            Point center = new Point(Compute.randomInt(width), Compute.randomInt(height));
            
            int radius = Compute.randomInt(maxRadius - minRadius + 1) + minRadius;
            int maxLevel = 3;
            if (radius < 3) { 
                maxLevel = 1;
            }
            if ((radius >= 3) && (radius <= 8)) {
                maxLevel = 2;
            }
            if (radius > 14) {
                maxLevel = 4;
            }
            int maxHeight = Compute.randomInt(maxLevel) + 1;
            /* generate CraterProfile */
            int cratHeight[] = new int[radius];
            for (int x = 0; x < radius; x++) {
                cratHeight[x] = craterProfile((double)x / (double)radius, maxHeight);
            }
            /* btw, I am interested if someone actually reads
             this comments, so send me and email to f.stock@tu-bs.de, if
             you do ;-) */
            /* now recalculate every hex */
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    int distance = (int)distance(center, new Point(w,h));
                    if (distance < radius) {
                        double fac = (double)distance / (double)radius;
                        IHex field = board.getHex(w, h);
                        field.setElevation(//field.getElevation() +
                                cratHeight[distance]);
                    } 
                }   
            }     
        }
    }
    
    /**
     * The profile of a crater: interior is exp-function, exterior cos function.
     * @param x The x value of the function. range 0..1. 
     *        0=center of crater. 1=border of outer wall.
     *  @param scale Apply this scale before returning the result
     *         (recommend instead of afterwards scale, cause this way the intern
     *         floating values are scaled, instead of int result).
     *  @return The height of the crater at the position x from
     *          center. Unscaled, the results are between -0.5 and 1 (that
     *          means, if no scale is applied -1, 0 or 1).
     */
    public static int craterProfile(double x, int scale) {
        double result = 0;
        
        result = (x < 0.75) ? 
                ((Math.exp(x * 5.0 / 0.75 - 3) - 0.04979) * 1.5 / 7.33926) - 0.5 : 
                    ((Math.cos((x-0.75)*4.0)+1.0) / 2.0);
        
        return (int)(result * (double)scale);
    }
    
    /**
     * calculate the distance between two points
     */
    private static double distance(Point p1, Point p2) {
        double x = p1.x - p2.x;
        double y = p1.y - p2.y;
        return Math.sqrt(x*x + y*y);
    }
    
    /** 
     * Adds an River to the map (if the map is at least 5x5 hexes
     * big). The river has an width of 1-3 hexes (everything else is
     * no more a river). The river goes from one border to another.
     * Nor Params, no results.
     */
    public static void addRiver(IBoard board, HashMap reverseHex) {
        int minElevation = Integer.MAX_VALUE;
        HashSet riverHexes = new HashSet();
        IHex field, rightHex, leftHex;
        Point p = null;
        int direction = 0;
        int nextLeft = 0;
        int nextRight = 0;
        
        int width = board.getWidth();
        int height = board.getHeight();
        
        /* if map is smaller than 5x5 no real space for an river */
        if ((width < 5) || (height < 5)) {
            return;
        }
        /* First select start and the direction */
        switch (Compute.randomInt(4)) {
        case 0:
            p = new Point(0, Compute.randomInt(5) - 2 + height / 2);
            direction = Compute.randomInt(2) + 1;
            nextLeft = direction - 1;
            nextRight = direction + 1;
            break;
        case 1:
            p = new Point(width - 1, Compute.randomInt(5) - 2 + height / 2);
            direction = Compute.randomInt(2) + 4;
            nextLeft = direction - 1;
            nextRight = (direction + 1) % 6;
            break;
        case 2:
        case 3:
            p = new Point(Compute.randomInt(5) - 2 + width / 2, 0);
            direction = 2;
            nextRight = 3;
            nextLeft = 4;
            break;
        } // switch
        /* place the river */
        field = board.getHex(p.x, p.y);
        ITerrainFactory f = Terrains.getTerrainFactory();        
        do {
            /* first the hex itself */
            field.removeAllTerrains();
            field.addTerrain(f.createTerrain(Terrains.WATER, 1));
            riverHexes.add(field);
            p = (Point)reverseHex.get(field);
            /* then maybe the left and right neighbours */
            riverHexes.addAll(extendRiverToSide(board, p, Compute.randomInt(3), 
                    nextLeft, reverseHex));
            riverHexes.addAll(extendRiverToSide(board, p, Compute.randomInt(3),
                    nextRight, reverseHex));
            switch (Compute.randomInt(4)) {
            case 0: 
                field = board.getHexInDir(p.x, p.y, (direction + 5) % 6);
                break;
            case 1: 
                field = board.getHexInDir(p.x, p.y, (direction + 1) % 6);
                break;
            default:
                field = board.getHexInDir(p.x, p.y, direction);
            break;
            }
            
        } while (field != null); 
        
        /* search the elevation for the river */
        HashSet tmpRiverHexes = (HashSet)riverHexes.clone();
        while (!tmpRiverHexes.isEmpty()) {
            Iterator iter = tmpRiverHexes.iterator();
            field = (IHex)iter.next();
            if (field.getElevation() < minElevation) {
                minElevation = field.getElevation();
            }
            tmpRiverHexes.remove(field);
            Point thisHex = (Point)reverseHex.get(field);
            /* and now the six neighbours */
            for (int i = 0; i < 6; i++) {
                field = board.getHexInDir(thisHex.x, thisHex.y, i);
                if ((field != null) && (field.getElevation() < minElevation)) {
                    minElevation = field.getElevation();
                }
                tmpRiverHexes.remove(field);
            }
        }
        
        /* now adjust the elevation to same height */
        Iterator iter = riverHexes.iterator();
        while (iter.hasNext()) {
            field = (IHex)iter.next();
            field.setElevation(minElevation);
        }
        
        return;
    }
    
    /** 
     * Extends a river hex to left and right sides.
     * @param hexloc The location of the river hex,
     * from which it should get started.
     * @param width The width to wich the river should extend in
     * the direction. So the actual width of the river is
     * 2*width+1. 
     * @param direction Direction too which the riverhexes should be
     * extended. 
     * @return Hashset with the hexes from the side.
     */
    private static HashSet extendRiverToSide(IBoard board, Point hexloc, int width, int direction, HashMap reverseHex) {
        Point current = new Point(hexloc);
        HashSet result = new HashSet();
        IHex hex;
        
        hex = board.getHexInDir(current.x, current.y, direction);
        while ((hex != null) && (width-- > 0)) {
            hex.removeAllTerrains();
            hex.addTerrain(Terrains.getTerrainFactory().createTerrain(Terrains.WATER, 1));
            result.add(hex);        
            current = (Point)reverseHex.get(hex);
            hex = board.getHexInDir(current.x, current.y, direction);
        }        
        return result;
    }
    
    
    /** 
     * Adds an Road to the map. Goes from one border to another, and
     * has one turn in it. Map must be at least 3x3.
     */
    public static void addRoad(IBoard board, HashMap reverseHex) {
        int width = board.getWidth();
        int height = board.getHeight();
        
        if ((width < 3) || (height < 3)) {
            return;
        }
        /* first determine the turning hex, and then the direction
         of the doglegs */
        Point start = new Point(Compute.randomInt(width - 2) + 1, 
                Compute.randomInt(height - 2) + 1);
        Point p = null;
        int[] side = new int[2];
        IHex field = null;
        int lastLandElevation = 0;
        
        side[0] = Compute.randomInt(6);
        side[1] = Compute.randomInt(5);
        if (side[1] >= side[0]) {
            side[1]++;
        }
        ITerrainFactory f = Terrains.getTerrainFactory();
        for (int i = 0; i < 2; i++) {
            field = board.getHex(start.x, start.y);
            do {
                if (field.containsTerrain(Terrains.WATER)) {
                    field.addTerrain(f.createTerrain(Terrains.WATER, 0));
                    field.setElevation(lastLandElevation);
                } else {
                    lastLandElevation = field.getElevation();
                }
                field.addTerrain(f.createTerrain(Terrains.ROAD, 1));
                p = (Point)reverseHex.get(field);
                field = board.getHexInDir(p.x, p.y, side[i]);
            } while (field != null); 
        } 
    }
    
    /** 
     * Generates the elevations 
     * @param hilliness The Hilliness
     * @param width The Width of the map.
     * @param height The Height of the map.
     * @param range Max difference betweenn highest and lowest level.
     * @param invertProb Probability for the invertion of the map (0..100)
     * @param elevationMap here is the result stored
     */
    public static void generateElevation(int hilliness, int width, int height,
            int range, int invertProb,
            int elevationMap[][], int algorithm) {
        int minLevel = 0;
        int maxLevel = range;
        boolean invert = (Compute.randomInt(100) < invertProb);
        
        
        /* init elevation map with 0 */
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                elevationMap[w][h] = 0;
            }
        }
        /* generate landscape */
        switch (algorithm) {
        case 0: 
            cutSteps(hilliness, width, height, elevationMap);
            break;
        case 1: 
            midPoint(hilliness, width, height, elevationMap);
            break;
        case 2:
            cutSteps(hilliness, width, height, elevationMap);
            midPoint(hilliness, width, height, elevationMap);
            break;
        }
        
        /* and now normalize it */
        int min = elevationMap[0][0];
        int max = elevationMap[0][0];
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                if (elevationMap[w][h] > max) {
                    max = elevationMap[w][h];
                }
                if (elevationMap[w][h] < min) {
                    min = elevationMap[w][h];
                }
            }
        }
        
        double scale = (double)(maxLevel - minLevel) / (double)(max - min);
        int inc = (int)(-scale * min + minLevel);
        int[] elevationCount = new int[maxLevel + 1];
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                elevationMap[w][h] *= scale;
                elevationMap[w][h] += inc;
                elevationCount[elevationMap[w][h]]++;
            }
        }
        int mostElevation = 0;
        for (int lvl = 1; lvl <= range; lvl++) {
            if (elevationCount[lvl] > elevationCount[mostElevation]) {
                mostElevation = lvl;
            }
        }
        for (int w=0; w<width; w++)   {
            for (int h=0; h<height; h++) {
                elevationMap[w][h]-=mostElevation;
                if (invert) {
                    elevationMap[w][h] *= -1;
                }
            }
        }
    }

    /**
     * Flips the board around the vertical axis (North-for-South) and/or
     * the horizontal axis (East-for-West).  The dimensions of the board
     * will remain the same, but the terrain of the hexes will be swiched.
     *
     * @param   horiz - a <code>boolean</code> value that, if <code>true</code>,
     *          indicates that the board is being flipped North-for-South.
     * @param   vert - a <code>boolean</code> value that, if <code>true</code>,
     *          indicates that the board is being flipped East-for-West.
     */
    public static void flip(IBoard board, boolean horiz, boolean vert ) {
        // If we're not flipping around *some* axis, do nothing.
        if ( !vert && !horiz ) {
            return;
        }

        // We only walk through half the board, but *which* half?
        int stopX;
        int stopY;
        int width = board.getWidth();
        int height = board.getHeight();
        
        if ( horiz ) {
            // West half of board.
            stopX = width/2;
            stopY = height;
        } else {
            // North half of board.
            stopX = width;
            stopY = height/2;
        }

        // Walk through the current data array and build a new one.
        int newX;
        int newY;
        int newIndex;
        int oldIndex;
        IHex tempHex;
        ITerrain terr;
        for ( int oldX = 0; oldX < stopX; oldX++ ) {
            // Calculate the new X position of the flipped hex.
            if (horiz) {
                newX = width - oldX - 1;
            } else {
                newX = oldX;
            }
            for ( int oldY = 0; oldY < stopY; oldY++ ) {
                // Calculate the new Y position of the flipped hex.
                if (vert) {
                    newY = height - oldY - 1;
                } else {
                    newY = oldY;
                }

                // Swap the old hex for the new hex.
                tempHex = board.getHex(oldX, oldY);
                board.setHex(oldX, oldY, board.getHex(newX, newY));
                board.setHex(newX, newY,tempHex);

                IHex newHex = board.getHex(newX, newY);
                IHex oldHex = board.getHex(oldX, oldY);

                // Update the road exits in the swapped hexes.
                terr = newHex.getTerrain(Terrains.ROAD);
                if ( null != terr ) {
                    terr.flipExits(horiz, vert);
                }
                terr = oldHex.getTerrain(Terrains.ROAD);
                if ( null != terr ) {
                    terr.flipExits(horiz, vert);
                }

                // Update the building exits in the swapped hexes.
                terr = newHex.getTerrain( Terrains.BUILDING );
                if ( null != terr ) {
                    terr.flipExits( horiz, vert );
                }
                terr = oldHex.getTerrain(Terrains.BUILDING);
                if ( null != terr ) {
                    terr.flipExits( horiz, vert );
                }

                // Update the bridge exits in the swapped hexes.
                terr = newHex.getTerrain( Terrains.BRIDGE );
                if ( null != terr ) {
                    terr.flipExits( horiz, vert );
                }
                terr = oldHex.getTerrain( Terrains.BRIDGE );
                if ( null != terr ) {
                    terr.flipExits( horiz, vert );
                }
            }
        }
    }
    
    /**
     * one of the landscape generation algorithms
     */
    protected static void cutSteps(int hilliness, int width, int height, int elevationMap[][]) {
        Point p1, p2;
        int sideA, sideB;
        int type;
        
        p1 = new Point(0,0);
        p2 = new Point(0,0);
        for (int step = 0; step < 20 * hilliness; step++) {
            /* select which side should be decremented, and which
             increemented */
            sideA = (Compute.randomInt(2) == 0)? -1 : 1;
            sideB =- sideA;
            type = Compute.randomInt(6);
            /* 6 different lines in rectangular area from border to
             border possible */
            switch (type) {
            case 0: /* left to upper border */
                p1.setLocation(0, Compute.randomInt(height));
                p2.setLocation(Compute.randomInt(width), height-1);
                markSides(p1, p2, sideB, sideA, elevationMap, height);
                markRect(p2.x, width-1, sideA, elevationMap, height);
                break;
            case 1: /* upper to lower border */
                p1.setLocation(Compute.randomInt(width), 0);
                p2.setLocation(Compute.randomInt(width), height-1);
                if (p1.x < p2.x) {
                    markSides(p1, p2, sideA, sideB, elevationMap, height);
                } else {
                    markSides(p2, p1, sideB, sideA, elevationMap, height);
                }
                markRect(0, p1.x, sideA, elevationMap, height);
                markRect(p2.x, width, sideB, elevationMap, height);
                break;
            case 2: /* upper to right border */
                p1.setLocation(Compute.randomInt(width), height-1);
                p2.setLocation(width, Compute.randomInt(height));
                markSides(p1, p2, sideB, sideA, elevationMap, height);
                markRect(0, p1.x, sideA, elevationMap, height);
                break;
            case 3: /* left to right border */
                p1.setLocation(0, Compute.randomInt(height));
                p2.setLocation(width-1, Compute.randomInt(height));
                markSides(p1, p2, sideA, sideB, elevationMap, height);
                break;
            case 4: /* left to lower border */
                p1.setLocation(0, Compute.randomInt(height));
                p2.setLocation(Compute.randomInt(width), 0);
                markSides(p1, p2, sideB, sideA, elevationMap, height);
                markRect(p2.x, width-1, sideB, elevationMap, height);
                break;
            case 5: /* lower to right border */
                p1.setLocation(Compute.randomInt(width), 0);
                p2.setLocation(width, Compute.randomInt(height));
                markSides(p1, p2, sideB, sideA, elevationMap, height);
                markRect(0, p1.x, sideB, elevationMap, height);
                break;
            }
            
        }
    }
    
    /**
     * Helper function for the map generator 
     * increased a heightmap my a given value 
     */
    protected static void markRect(int x1, int x2, int inc, int elevationMap [][], int height) {
        for (int x = x1; x < x2; x++) {
            for (int y = 0; y < height; y++) {
                elevationMap[x][y] += inc;
            }
        }
    } 
    
    /**
     * Helper function for map generator
     * inreases all of one side and decreased on other side
     */
    protected static void markSides(Point p1, Point p2, int upperInc, int lowerInc, int elevationMap [][], int height) {
        for (int x = p1.x; x < p2.x; x++) {
            for (int y = 0; y < height; y++) {
                int point =(p2.y - p1.y) / (p2.x - p1.x) * (x - p1.x) + p1.y;
                if (y > point) {
                    elevationMap[x][y] += upperInc;
                } else if (y < point) {
                    elevationMap[x][y] += lowerInc;
                }
            }
        }
    } 
    
    
    /** 
     * midpoint algorithm for landscape generartion 
     */
    protected static void midPoint(int hilliness, int width, int height, int elevationMap[][]) {
        int size;
        int steps = 1;
        int tmpElevation[][];
        
        size = (width > height) ? width : height;
        while (size > 0) {
            steps++;
            size /= 2;
        }
        size = (1 << steps) + 1;
        tmpElevation = new int[size + 1][size + 1];
        /* init elevation map with 0 */
        for (int w = 0; w < size; w++)
            for (int h = 0; h < size; h++)
                if ((w < width) && (h < height)) {
                    tmpElevation[w][h] = elevationMap[w][h];
                } else {
                    tmpElevation[w][h] = 0;
                }
        for (int i = steps; i > 0; i--) {
            midPointStep((double)hilliness/1000, size, 1000,
                    tmpElevation, i, true);
        }
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                elevationMap[w][h] = tmpElevation[w][h];
            }
        }
    }
    
    /**
     * Helper function for landscape generation 
     */
    protected static void midPointStep(double fracdim, int size, int delta, int elevationMap[][], int step, boolean newBorder) {
        int d1, d2;
        int delta5;
        int x,y;
        
        d1 = size >> (step - 1);
        d2 = d1 / 2;
        fracdim = (1.0 - fracdim) / 2.0;
        delta = (int)(delta * Math.exp(-0.6931 * fracdim * (2.0 * (double)step - 1)));
        delta5 = delta << 5;
        x = d2;
        do {
            y = d2;
            do {
                elevationMap[x][y] = middleValue(elevationMap[x + d2][y + d2],
                        elevationMap[x + d2][y - d2],
                        elevationMap[x - d2][y + d2],
                        elevationMap[x - d2][y - d2],
                        delta5);
                y += d1;
            } while (y < size - d2);
            x += d1;
        } while (x < size - d2);
        
        delta = (int)(delta * Math.exp(-0.6931 * fracdim ));
        delta5 = delta << 5;
        if (newBorder) {
            x = d2;
            do {
                y = x;
                elevationMap[0][x] = middleValue(elevationMap[0][x + d2],
                        elevationMap[0][x - d2],
                        elevationMap[d2][x], delta5);
                elevationMap[size][x] = middleValue(elevationMap[size - 1][x + d2],
                        elevationMap[size - 1][x - d2],
                        elevationMap[size - d2 - 1][x], 
                        delta5);
                y = 0;
                elevationMap[x][0] = middleValue(elevationMap[x + d2][0],
                        elevationMap[x - d2][0],
                        elevationMap[x][d2], 
                        delta5);
                elevationMap[x][size] = middleValue(elevationMap[x + d2][size - 1],
                        elevationMap[x - d2][size - 1],
                        elevationMap[x][size - d2 - 1], 
                        delta5);
                x += d1;
            } while (x < size - d2);
        }
        diagMid(new Point(d2, d1), d1, d2, delta5, size, elevationMap);
        diagMid(new Point(d1, d2), d1, d2, delta5, size, elevationMap);
    }
    
    /** 
     * calculates the diagonal middlepoints with new values
     * @param p Starting point.
     */
    protected static void diagMid(Point p, int d1, int d2, int delta, int size, int elevationMap[][]) {
        int x = p.x;
        int y;
        int hx = x + d2;
        int hy;
        
        while ((x < size - d2) && (hx < size)) {
            y = p.y;
            hy = y + d2;
            while ( (y < size-d2) && (hy < size)) {
                elevationMap[x][y] = middleValue(elevationMap[x][hy],
                        elevationMap[x][y - d2],
                        elevationMap[hx][y],
                        elevationMap[x - d2][y],
                        delta);
                y += d1;
                hy = y + d2;
            }
            x += d1;
            hx = x + d2;
        }
    } 
    
    /** 
     * calculates the arithmetic medium of 3 values and add random
     * value in range of delta.
     */
    protected static int middleValue(int a,  int b, int c, int delta) {
        int result=(((a + b + c) / 3) + normRNG(delta));
        return result;
    }
    
    /**
     * calculates the arithmetic medium of 4 values and add random
     * value in range of delta.
     */
    protected static int middleValue(int a,  int b, int c, int d, int delta) {
        int result = (((a + b + c + d) / 4) + normRNG(delta));
        return result;
    }
    
    /** 
     * Gives a normal distributed Randomvalue, with mediumvalue from
     * 0 and a Varianz of factor.
     * @param factor varianz of of the distribution.
     * @return Random number, most times in the range -factor .. +factor,
     * at most in the range of -3*factor .. +3*factor.
     */
    private static int normRNG(int factor) {
        factor++;
        return (2 * (Compute.randomInt(factor) + Compute.randomInt(factor) +
                Compute.randomInt(factor)) - 3 * (factor - 1)) / 32;
    }
    
    protected static class Point {
        
        public int x;
        public int y;
        
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public Point(Point other) {
            this.x = other.x;
            this.y = other.y;
        }
        
        /**
         * Set the location 
         * @param x x coordinate
         * @param y y coordinate
         */
        public void setLocation(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    
}
