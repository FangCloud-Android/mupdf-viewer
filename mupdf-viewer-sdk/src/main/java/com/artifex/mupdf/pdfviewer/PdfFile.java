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
import android.graphics.RectF;

import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.Size;
import com.artifex.mupdf.pdfviewer.util.FitPolicy;
import com.artifex.mupdf.pdfviewer.util.PageSizeCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class PdfFile {

    private static final Object lock = new Object();
    protected MuPDFCore pdfDocument;
    private int pagesCount = 0;
    /** Original page sizes */
    private List<Size> originalPageSizes = new ArrayList<>();
    /** Scaled page sizes */
    private List<Size> pageSizes = new ArrayList<>();
    /** Page with maximum width */
    private Size originalMaxWidthPageSize = new Size(0, 0);
    /** Page with maximum height */
    private Size originalMaxHeightPageSize = new Size(0, 0);
    /** Scaled page with maximum height */
    private Size maxHeightPageSize = new Size(0, 0);
    /** Scaled page with maximum width */
    private Size maxWidthPageSize = new Size(0, 0);
    private boolean isVertical = true;
    private int spacingPx = 0;
    /** Calculated offsets for pages */
    private List<Float> pageOffsets = new ArrayList<>();
    /** Calculated document length (width or height, depending on swipe mode) */
    private float documentLength = 0;
    private final FitPolicy pageFitPolicy;

    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    private int[] originalUserPages;

    private HashMap<Integer, Page> cachePages = new HashMap<>();

    PdfFile(MuPDFCore document, FitPolicy pageFitPolicy, Size viewSize, int[] originalUserPages,
            boolean isVertical, int spacing) {
        this.pdfDocument = document;
        this.pageFitPolicy = pageFitPolicy;
        this.originalUserPages = originalUserPages;
        this.isVertical = isVertical;
        this.spacingPx = spacing;
        setup(viewSize);
    }

    private void setup(Size viewSize) {
        if (originalUserPages != null) {
            pagesCount = originalUserPages.length;
        } else {
            pagesCount = pdfDocument.countPages();
        }

        for (int i = 0; i < pagesCount; i++) {
            synchronized (lock) {
                if (!cachePages.containsKey(i)) {
                    try {
                        Page page = pdfDocument.loadPage(i);
                        Size pageSize = page.fitPageSize(viewSize.width, viewSize.height);
                        if (pageSize.getWidth() > originalMaxWidthPageSize.getWidth()) {
                            originalMaxWidthPageSize = pageSize;
                        }
                        if (pageSize.getHeight() > originalMaxHeightPageSize.getHeight()) {
                            originalMaxHeightPageSize = pageSize;
                        }
                        originalPageSizes.add(pageSize);
                        cachePages.put(i, page);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        recalculatePageSizes(viewSize);
    }

    /**
     * Call after view size change to recalculate page sizes, offsets and document length
     *
     * @param viewSize new size of changed view
     */
    public void recalculatePageSizes(Size viewSize) {
        pageSizes.clear();
        PageSizeCalculator calculator = new PageSizeCalculator(pageFitPolicy, originalMaxWidthPageSize,
                originalMaxHeightPageSize, viewSize);
        maxWidthPageSize = calculator.getOptimalMaxWidthPageSize();
        maxHeightPageSize = calculator.getOptimalMaxHeightPageSize();

        for (Size size : originalPageSizes) {
            pageSizes.add(calculator.calculate(size));
        }

        prepareDocLen();
        preparePagesOffset();
    }

    public Page getPage(int pageNumber) {
        return cachePages.get(pageNumber);
    }

    public int getPagesCount() {
        return pagesCount;
    }

    public Size getPageSize(int pageIndex) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return new Size(0, 0);
        }
        return pageSizes.get(pageIndex);
    }

    public Size getScaledPageSize(int pageIndex, float zoom) {
        int docPage = documentPage(pageIndex);
        Size size = getPageSize(docPage);
        return new Size((int)(size.getWidth() * zoom), (int)(size.getHeight() * zoom));
    }

    /**
     * get page size with biggest dimension (width in vertical mode and height in horizontal mode)
     *
     * @return size of page
     */
    public Size getMaxPageSize() {
        return isVertical ? maxWidthPageSize : maxHeightPageSize;
    }

    public float getMaxPageWidth() {
        return getMaxPageSize().getWidth();
    }

    public float getMaxPageHeight() {
        return getMaxPageSize().getHeight();
    }

    private void prepareDocLen() {
        float length = 0;
        for (Size pageSize : pageSizes) {
            length += isVertical ? pageSize.getHeight() : pageSize.getWidth();
        }
        int spacing = spacingPx * (pageSizes.size() - 1);
        documentLength = length + spacing;
    }

    private void preparePagesOffset() {
        pageOffsets.clear();
        float offset = 0;
        for (int i = 0; i < getPagesCount(); i++) {
            float spacing = i * spacingPx;
            pageOffsets.add(offset + spacing);
            Size size = pageSizes.get(i);
            offset += isVertical ? size.getHeight() : size.getWidth();
        }
    }

    public float getDocLen(float zoom) {
        return documentLength * zoom;
    }

    public float getPageOffset(int pageIndex, float zoom) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return 0;
        }
        return pageOffsets.get(pageIndex) * zoom;
    }

    public int getPageAtOffset(float offset, float zoom) {
        int currentPage = 0;
        for (float off : pageOffsets) {
            if ((int)(off * zoom) >= (int) offset) {
                break;
            }
            currentPage++;
        }

        return --currentPage >= 0 ? currentPage : 0;
    }

    public Link[] getPageLinks(int pageIndex) {
        Link[] links;
        int docPage = documentPage(pageIndex);
        if (!cachePages.containsKey(docPage)) {
            links = new Link[0];
        } else {
            Page page = cachePages.get(docPage);
            links = page.getLinks();
        }
        return links != null ? links : new Link[0];
    }

    public RectF mapRectToDevice(int pageIndex, int startX, int startY, int sizeX, int sizeY,
                                 RectF rect) {
        int docPage = documentPage(pageIndex);
//        return pdfiumCore.mapRectToDevice(pdfDocument, docPage, startX, startY, sizeX, sizeY, 0, rect);
        return null;
    }

    public void dispose() {
        pdfDocument.onDestroy();
        pdfDocument = null;
        originalUserPages = null;
    }

    /**
     * Given the UserPage number, this method restrict it
     * to be sure it's an existing page. It takes care of
     * using the user defined pages if any.
     *
     * @param userPage A page number.
     * @return A restricted valid page number (example : -2 => 0)
     */
    public int determineValidPageNumberFrom(int userPage) {
        if (userPage <= 0) {
            return 0;
        }
        if (originalUserPages != null) {
            if (userPage >= originalUserPages.length) {
                return originalUserPages.length - 1;
            }
        } else {
            if (userPage >= getPagesCount()) {
                return getPagesCount() - 1;
            }
        }
        return userPage;
    }

    public int documentPage(int userPage) {
        int documentPage = userPage;
        if (originalUserPages != null) {
            if (userPage < 0 || userPage >= originalUserPages.length) {
                return -1;
            } else {
                documentPage = originalUserPages[userPage];
            }
        }

        if (documentPage < 0 || userPage >= getPagesCount()) {
            return -1;
        }

        return documentPage;
    }
}
