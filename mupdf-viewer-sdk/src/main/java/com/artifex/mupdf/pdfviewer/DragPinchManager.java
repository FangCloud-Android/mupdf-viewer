/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.artifex.mupdf.pdfviewer;
import android.graphics.RectF;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Size;
import com.artifex.mupdf.pdfviewer.model.LinkTapEvent;
import com.artifex.mupdf.pdfviewer.scroll.ScrollHandle;
import com.artifex.mupdf.pdfviewer.util.Constants;

/**
 * This Manager takes care of moving the PDFView,
 * set its zoom track user actions.
 */
abstract class DragPinchManager implements GestureDetector.OnDoubleTapListener, View.OnTouchListener, GestureDetector.OnGestureListener {

    private PDFView pdfView;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    private ScaleGestureManager scaleGestureManager;

    private boolean scrolling = false;
    private boolean enabled = false;

    abstract ScrollHandle getScrollHandle();

    private AnimationManager animationManager;

    // 记录当前滚动方向
    enum Director {Left, Right, Up, Down, None}
    private Director director = Director.None;

    /**
     * 是否可以滚动到下一页
     */
    private boolean scrollNext = false;

    DragPinchManager(PDFView pdfView, AnimationManager animationManager) {
        this.pdfView = pdfView;
        this.animationManager = animationManager;
        gestureDetector = new GestureDetector(pdfView.getContext(), this);
        scaleGestureDetector = new ScaleGestureDetector(pdfView.getContext(),
                scaleGestureManager = new ScaleGestureManager(pdfView) {
                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        super.onScaleEnd(detector);
                        hideHandle();
                    }
                });
        pdfView.setOnTouchListener(this);
    }

    void enable() {
        enabled = true;
    }

    void disable() {
        enabled = false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        boolean onTapHandled = pdfView.callbacks.callOnTap(e);
        boolean linkTapped = checkLinkTapped(e.getX(), e.getY());
        if (!onTapHandled && !linkTapped) {
            ScrollHandle ps = pdfView.getScrollHandle();
            if (ps != null && !pdfView.documentFitsView()) {
                if (!ps.shown()) {
                    ps.show();
                } else {
                    ps.hide();
                }
            }
        }
        pdfView.performClick();
        return true;
    }

    private boolean checkLinkTapped(float x, float y) {
        PdfFile pdfFile = pdfView.pdfFile;
        float mappedX = -pdfView.getCurrentXOffset() + x;
        float mappedY = -pdfView.getCurrentYOffset() + y;
        int page = pdfFile.getPageAtOffset(pdfView.isSwipeVertical() ? mappedY : mappedX, pdfView.getZoom());
        Size pageSize = pdfFile.getScaledPageSize(page, pdfView.getZoom());
        int pageX, pageY;
        if (pdfView.isSwipeVertical()) {
            pageX = (int) getSecondaryOffset(pageSize);
            pageY = (int) pdfFile.getPageOffset(page, pdfView.getZoom());
        } else {
            pageY = (int) getSecondaryOffset(pageSize);
            pageX = (int) pdfFile.getPageOffset(page, pdfView.getZoom());
        }
        for (Link link : pdfFile.getPageLinks(page)) {
            RectF mapped = pdfFile.mapRectToDevice(page, pageX, pageY, (int) pageSize.getWidth(),
                    (int) pageSize.getHeight(), link.getBounds());
            if (mapped.contains(mappedX, mappedY)) {
                pdfView.callbacks.callLinkHandler(new LinkTapEvent(x, y, mappedX, mappedY, mapped, link));
                return true;
            }
        }
        return false;
    }

    private float getSecondaryOffset(Size pageSize) {
        if (pdfView.isSwipeVertical()) {
            float maxWidth = pdfView.pdfFile.getMaxPageWidth();
            return (pdfView.toCurrentScale(maxWidth) - pageSize.getWidth()) / 2; //x
        } else {
            float maxHeight = pdfView.pdfFile.getMaxPageHeight();
            return (pdfView.toCurrentScale(maxHeight) - pageSize.getHeight()) / 2; //y
        }
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!pdfView.isDoubletapEnabled()) {
            return false;
        }

        if (pdfView.getZoom() < Constants.Pinch.MID_ZOOM) {
            pdfView.zoomWithAnimation(e.getX(), e.getY(), Constants.Pinch.MID_ZOOM);
        } else if (pdfView.getZoom() < Constants.Pinch.MAXIMUM_ZOOM) {
            pdfView.zoomWithAnimation(e.getX(), e.getY(), Constants.Pinch.MAXIMUM_ZOOM);
        } else {
            pdfView.resetZoomWithAnimation();
        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!enabled) {
            return false;
        }

        boolean retVal = scaleGestureDetector.onTouchEvent(event);
        retVal = gestureDetector.onTouchEvent(event) || retVal;

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (scrolling) {
                scrolling = false;
                onScrollEnd(event);
            }
        }
        return retVal;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        animationManager.stopFling();
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        scrolling = true;
        if (pdfView.isSwipeVertical()) {
            if (distanceY > 0)
                director = Director.Down;
            else
                director = Director.Up;
        } else {
            if (distanceX > 0)
                director = Director.Right;
            else
                director = Director.Left;
        }
        if (pdfView.isZooming() || pdfView.isSwipeEnabled()) {
            pdfView.moveRelativeTo(-distanceX, -distanceY);
        }
        Log.d("DragPinchManager", "=====================================>>>>> onScroll distanceX " + distanceX + " distanceY " + distanceY);
        if (!scaleGestureManager.isScaling() || pdfView.doRenderDuringScale()) {
            pdfView.loadPageByOffset();
        }
        return true;
    }

    private void onScrollEnd(MotionEvent event) {
        Log.d("DragPinchManager", "=====================================>>>>> onScrollEnd " + event);
        if (pdfView.alwaysScrollToPageStart() && !pdfView.isZooming()) {
            checkLatestScrollPosition();
        }
        pdfView.loadPages();
        hideHandle();
        scrollNext = false;
    }

    /**
     * 滚动停止时，检测当前滚动位置是否未页面起始位置
     */
    private void checkLatestScrollPosition() {
        int xOffset = (int) pdfView.getCurrentXOffset();
        int yOffset = (int) pdfView.getCurrentYOffset();

        float zoom = pdfView.getZoom();
        boolean isSwipeVertical = pdfView.isSwipeVertical();
        PdfFile pdfFile = pdfView.pdfFile;

        float pageOffsetStart;
        int pageNumber;
        if (isSwipeVertical) {
            float absYoffset = Math.abs(yOffset);
            pageNumber = pdfFile.getPageAtOffset(absYoffset, zoom);
            pageOffsetStart = pdfFile.getPageOffset(pageNumber, zoom);
            Size size = pdfFile.getScaledPageSize(pageNumber, zoom);
            if (pageNumber < pdfFile.getPagesCount() && scrollNext) {
                if (director == Director.Down) {
                    animationManager.startScrollTo(xOffset, yOffset, xOffset, (int)(absYoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)));
                } else if (director == Director.Up) {
                    animationManager.startScrollTo(xOffset, yOffset, xOffset, (int)(absYoffset - pageOffsetStart));
                }
            } else if ((absYoffset - pageOffsetStart > (size.height / 2) || scrollNext)) {
                animationManager.startScrollTo(xOffset, yOffset, xOffset, (int)(absYoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)));
            } else {
                animationManager.startScrollTo(xOffset, yOffset, xOffset, (int)(absYoffset - pageOffsetStart));
            }
        }  else {
            float absXoffset = Math.abs(xOffset);
            pageNumber = pdfFile.getPageAtOffset(Math.abs(xOffset), zoom);
            pageOffsetStart = pdfFile.getPageOffset(pageNumber, zoom);
            Size size = pdfFile.getScaledPageSize(pageNumber, zoom);
            if (pageNumber < pdfFile.getPagesCount() && scrollNext) {
                if (director == Director.Right) {
                    animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)), yOffset);
                } else if (director == Director.Left) {
                    animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pageOffsetStart), yOffset);
                }
            } else if ((absXoffset - pageOffsetStart > (size.width / 2)) && pageNumber < pdfFile.getPagesCount()) {
                animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pdfFile.getPageOffset(pageNumber + 1, zoom)), yOffset);
            } else {
                animationManager.startScrollTo(xOffset, yOffset, (int)(absXoffset - pageOffsetStart), yOffset);
            }
        }
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (!pdfView.isSwipeEnabled()) {
            return false;
        }
        boolean isSwipeVertical = pdfView.isSwipeVertical();
        PdfFile pdfFile = pdfView.pdfFile;
        float zoom = pdfView.getZoom();

        // 当前已滚动到的位置
        int xOffset = (int) pdfView.getCurrentXOffset();
        int yOffset = (int) pdfView.getCurrentYOffset();

        // 是否为单页滚动
        if (pdfView.alwaysScrollToPageStart() && !pdfView.isZooming()) {
            if ((isSwipeVertical && (velocityY > Constants.MINI_VECTOR_DISTENCE || velocityY < -Constants.MINI_VECTOR_DISTENCE))
                    || (velocityX > Constants.MINI_VECTOR_DISTENCE || velocityX < -Constants.MINI_VECTOR_DISTENCE)) {
                scrollNext = true;
            }
            return true;
        }

        float minX, minY;
        if (isSwipeVertical) {
            minX = -(pdfView.toCurrentScale(pdfFile.getMaxPageWidth()) - pdfView.getWidth());
            minY = -(pdfFile.getDocLen(pdfView.getZoom()) - pdfView.getHeight());
        } else {
            minX = -(pdfFile.getDocLen(pdfView.getZoom()) - pdfView.getWidth());
            minY = -(pdfView.toCurrentScale(pdfFile.getMaxPageHeight()) - pdfView.getHeight());
        }

        Log.d("DragPinchManager", "=====================================>>>>> onFling final velocityX "
                + velocityX + " velocityY " + velocityY + " xOffset " + xOffset + " yOffset " + yOffset + " minX " + minX + " minY " + minY);
        animationManager.startFlingAnimation(xOffset, yOffset, (int) (velocityX), (int) (velocityY), (int) minX, 0, (int) minY, 0);
        return true;
    }

    private void hideHandle() {
        ScrollHandle scrollHandle = getScrollHandle();
        if (scrollHandle != null && scrollHandle.shown()) {
            scrollHandle.hideDelayed();
        }
    }
}
