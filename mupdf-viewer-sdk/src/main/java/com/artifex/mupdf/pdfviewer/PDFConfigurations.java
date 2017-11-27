package com.artifex.mupdf.pdfviewer;

import com.artifex.mupdf.pdfviewer.util.FitPolicy;

/**
 * @author leixin 当前pdf预览参数
 */

public class PDFConfigurations {

    public int[] pageNumbers = null;

    public int defaultPage = 0;

    public boolean enableDoubletap;

    public boolean swipeHorizontal = false;

    public boolean annotationRendering = false;

    public boolean antialiasing = true;

    public int spacing = 0;

    public FitPolicy pageFitPolicy = FitPolicy.WIDTH;

    public boolean enableSwipe = true;

    // 滚动后，始终停留在页面开始位置
    public boolean alwaysScrollToPageStart = false;

    // 整体缩放
    public boolean scaleGroble = true;
}
