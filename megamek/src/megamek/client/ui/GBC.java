/*
 * MegaMek -
 * Copyright (C) 2008 Ben Mazur (bmazur@sev.org)
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
package megamek.client.ui;

import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * A helper class for setting line-wise GridBagLayouts
 * Do not use this if you need a Component to span two rows
 * @author beerockxs
 *
 */
public class GBC extends GridBagConstraints {
    /**
     *
     */
    private static final long serialVersionUID = 6653886439201996453L;

    private GBC() {
        anchor = WEST;
    }

    /**
     * @return a standard <code>GridBagConstraints</code>,
     *         anchored to <code>GridBagConstraints.WEST</code>
     */
    public static GBC std() {
        return new GBC();
    }

    /**
     * @return a <code>GridBagConstraints</code> that will have a component
     *         fill a line
     */
    public static GBC eol() {
        GBC c = std();
        c.gridwidth = REMAINDER;
        return c;
    }

    /**
     * @return a <code>GridBagConstraints</code> that will have a component fill
     *         a line, and have a 10 pixel inset to the south (ie. a paragraph)
     */
    public static GBC eop() {
        return eol().insets(0, 0, 0, 10);
    }

    /**
     * change the anchor of this <code>GridBagConstraints</code> to
     * @param a
     * and
     * @return <code>this</code>
     */
    public GBC anchor(int a) {
        anchor = a;
        return this;
    }

    /**
     * change the insets of this <code>GridBagConstraints</code> to
     * @param left
     * @param top
     * @param right
     * @param bottom
     *  and
     * @returns <code>this</code>
     */
    public GBC insets(int left, int top, int right, int bottom) {
        insets = new Insets(top, left, bottom, right);
        return this;
    }

    /**
     * set this <code>GridBagConstraints</code> so that the corresponding
     * Component will fill horizontally and vertically.
     * @return <code>this</code>
     */
    public GBC fill() {
        return fill(BOTH);
    }

    /**
     * set this <code>GridBagConstraints</code> so that the corresponding
     * will fill according to the
     * @param value
     * either <code>GridBagConstraints.HORIZONTAL</code>,
     * <code>GridBagConstraints.VERTICAL</code> or
     * <code>GridBagConstraints.BOTH</code> and
     * @return <code>this</code>
     */
    public GBC fill(int value) {
        fill = value;
        return this;
    }

    /**
     * set the gridwidth of this <code>GridBagConstraints</code>
     * @param value the <code>int</code> gridwidth to set
     * @return <code>this</code>
     */
    public GBC width(int value) {
        gridwidth = value;
        return this;
    }

    /**
     * Set the padding of this <code>GridBagConstraints</code>
     * @param padx the <code>int</code> ipadx to set
     * @param pady the <code>int</code> ipady to set
     * @return <code>this</code>
     */
    public GBC pad(int padx, int pady) {
        ipadx = padx;
        ipady = pady;
        return this;
    }

    /**
     * Set the gridx of this <code>GridBagConstraints</code>
     * @param gridx the <code>int</code> gridx to set
     * @return <code>this</code>
     */
    public GBC gridx(int gridx) {
        this.gridx = gridx;
        return this;
    }

    /**
     * Set the gridy of this <code>GridBagConstraints</code>
     * @param gridy the <code>int</code> gridy to set
     * @return <code>this</code>
     */
    public GBC gridy(int gridy) {
        this.gridy = gridy;
        return this;
    }
}

