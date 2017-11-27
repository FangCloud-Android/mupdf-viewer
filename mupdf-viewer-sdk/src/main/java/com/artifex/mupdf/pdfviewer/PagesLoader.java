/**
 * Copyright 2017 Bartosz Schiller
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

import android.util.Log;

import com.artifex.mupdf.fitz.Size;
import com.artifex.mupdf.pdfviewer.util.MathUtils;

import java.util.ArrayList;
import java.util.List;

class PagesLoader {

    private PDFView pdfView;
    private float xOffset;
    private float yOffset;

    PagesLoader(PDFView pdfView) {
        this.pdfView = pdfView;
    }

    private void loadVisible() {
        float zoom = pdfView.getZoom();
        PdfFile pdfFile = pdfView.pdfFile;
        int pageCurrent = 0;
        if (pdfView.isSwipeVertical()) {
            pageCurrent = pdfFile.getPageAtOffset(yOffset, zoom);
        } else {
            pageCurrent = pdfFile.getPageAtOffset(xOffset, zoom);
        }
        int pageStart = calcPageStart(pageCurrent, zoom);
        int pageEnd = calePageEnd(pageCurrent, zoom);

        Log.d("PagesLoader", " ========================>>>>>>>>> pageStart " + pageStart + " pageEnd " + pageEnd);
        for (int page=pageStart; page<=pageEnd; page++) {
            Size size = pdfFile.getPageSize(page);
            loadPage(page, (int)(size.width * zoom), (int)(size.height * zoom));
        }
    }

    /**
     * 计算页面开始加载位置
     * @param currentPage
     * @return
     */
    public int calcPageStart(int currentPage, float zoom) {
        PdfFile pdfFile = pdfView.pdfFile;
        int startOffset = 0;
        if (pdfView.isSwipeVertical()) {
            startOffset = (int)(pdfFile.getPageOffset(currentPage, zoom)) - (int) (1280 * zoom);
        } else {
            startOffset = (int)(pdfFile.getPageOffset(currentPage, zoom)) - (int) (720 * zoom);
        }
        if (startOffset < 0) {
            return 0;
        } else {
            int startPage = pdfFile.getPageAtOffset(startOffset, zoom) - 1;
            return startPage < 0 ? 0 : startPage;
        }
    }

    public int calePageEnd(int currentPage, float zoom) {
        PdfFile pdfFile = pdfView.pdfFile;
        int endOffset = 0;
        if (pdfView.isSwipeVertical()) {
            endOffset = (int) (pdfFile.getPageOffset(currentPage, zoom)) + (int) (1280 * zoom);
        } else {
            endOffset = (int) (pdfFile.getPageOffset(currentPage, zoom)) + (int) (720 * zoom);
        }
        int pageCount = pdfFile.getPagesCount();
        if (endOffset > pdfFile.getDocLen(zoom)) {
            return pageCount;
        } else {
            int endPage =  pdfFile.getPageAtOffset(endOffset, zoom) + 1;
            return endPage > pageCount ? pageCount : endPage;
        }
    }

    private boolean loadPage(int page, int renderWidth, int renderHeight) {
        if (renderWidth > 0 && renderHeight > 0) {
            pdfView.requestPage(page);

            return true;
        }
        return false;
    }

    void loadPages() {
        xOffset = -MathUtils.max(pdfView.getCurrentXOffset(), 0);
        yOffset = -MathUtils.max(pdfView.getCurrentYOffset(), 0);

        loadVisible();
    }

    /**
     * 根据当前显示的页码，检测有那些页面需要被显示
     * @param pageCurrent
     * @return
     */
    public List<Integer> shouldShowPages(int pageCurrent) {
        float zoom = pdfView.getZoom();
        int pageStart = calcPageStart(pageCurrent, zoom);
        int pageEnd = calePageEnd(pageCurrent, zoom);

        List<Integer> shownpages = new ArrayList<>();
        for (int i=pageStart; i<=pageEnd; i++) {
            shownpages.add(i);
        }
        return shownpages;
    }
}
