package com.artifex.mupdf.pdfviewer;

import android.graphics.PointF;
import android.view.ScaleGestureDetector;

import com.artifex.mupdf.pdfviewer.manager.inter.IScaleView;

import static com.artifex.mupdf.pdfviewer.util.Constants.Pinch.MAXIMUM_ZOOM;
import static com.artifex.mupdf.pdfviewer.util.Constants.Pinch.MINIMUM_ZOOM;

/**
 * @author leixin
 * 缩放事件管理器
 */
public class ScaleGestureManager implements ScaleGestureDetector.OnScaleGestureListener {

    private IScaleView scaleView;

    public ScaleGestureManager (IScaleView scaleView) {
        this.scaleView = scaleView;
    }

    private boolean scaling = false;

    public boolean isScaling() {
        return scaling;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float dr = detector.getScaleFactor();
        float zoom = scaleView.getZoom();
        float wantedZoom = zoom * dr;
        if (wantedZoom < MINIMUM_ZOOM) {
            dr = MINIMUM_ZOOM / zoom;
        } else if (wantedZoom > MAXIMUM_ZOOM) {
            dr = MAXIMUM_ZOOM / zoom;
        }
        scaleView.zoomCenteredRelativeTo(detector, dr, new PointF(detector.getFocusX(), detector.getFocusY()));
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        scaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        // 刷新方法后的控件大小
        scaleView.scaleEnd(detector);
        scaling = false;
    }
}
