package com.artifex.mupdf.fitz;

import java.io.Serializable;

/**
 * @author leixin
 */
public class Size implements Serializable{

    public int width;
    public int height;

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
