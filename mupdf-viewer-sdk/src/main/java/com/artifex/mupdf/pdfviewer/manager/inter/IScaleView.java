package com.artifex.mupdf.pdfviewer.manager.inter;

import android.graphics.PointF;
import android.view.ScaleGestureDetector;

/**
 * @author leixin
 * 可支持缩放的View
 */

public interface IScaleView {

    float getZoom();

    void zoomCenteredRelativeTo(ScaleGestureDetector detector, float dzoom, PointF pivot);

    void scaleEnd(ScaleGestureDetector detector);
}
