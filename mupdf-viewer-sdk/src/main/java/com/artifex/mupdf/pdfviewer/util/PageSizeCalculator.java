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
package com.artifex.mupdf.pdfviewer.util;

import com.artifex.mupdf.fitz.Size;

public class PageSizeCalculator {

    private FitPolicy fitPolicy;
    private final Size originalMaxWidthPageSize;
    private final Size originalMaxHeightPageSize;
    private final Size viewSize;
    private Size optimalMaxWidthPageSize;
    private Size optimalMaxHeightPageSize;
    private float widthRatio;
    private float heightRatio;

    public PageSizeCalculator(FitPolicy fitPolicy, Size originalMaxWidthPageSize, Size originalMaxHeightPageSize,
                              Size viewSize) {
        this.fitPolicy = fitPolicy;
        this.originalMaxWidthPageSize = originalMaxWidthPageSize;
        this.originalMaxHeightPageSize = originalMaxHeightPageSize;
        this.viewSize = viewSize;
        calculateMaxPages();
    }

    public Size calculate(Size pageSize) {
        if (pageSize.getWidth() <= 0 || pageSize.getHeight() <= 0) {
            return new Size(0, 0);
        }
        switch (fitPolicy) {
            case HEIGHT:
                return fitHeight(pageSize, pageSize.getHeight() * heightRatio);
            case BOTH:
                return fitBoth(pageSize, pageSize.getWidth() * widthRatio, pageSize.getHeight() * heightRatio);
            default:
                return fitWidth(pageSize, pageSize.getWidth() * widthRatio);
        }
    }

    public Size getOptimalMaxWidthPageSize() {
        return optimalMaxWidthPageSize;
    }

    public Size getOptimalMaxHeightPageSize() {
        return optimalMaxHeightPageSize;
    }

    private void calculateMaxPages() {
        switch (fitPolicy) {
            case HEIGHT:
                optimalMaxWidthPageSize = fitHeight(originalMaxWidthPageSize, viewSize.getHeight());
                optimalMaxHeightPageSize = fitHeight(originalMaxHeightPageSize, viewSize.getHeight());
                heightRatio = optimalMaxHeightPageSize.getHeight() / originalMaxHeightPageSize.getHeight();
                break;
            case BOTH:
                Size localOptimalMaxWidth = fitBoth(originalMaxWidthPageSize, viewSize.getWidth(), viewSize.getHeight());
                float localWidthRatio = localOptimalMaxWidth.getWidth() / originalMaxWidthPageSize.getWidth();
                this.optimalMaxHeightPageSize = fitBoth(originalMaxHeightPageSize, originalMaxHeightPageSize.getWidth() * localWidthRatio,
                        viewSize.getHeight());
                heightRatio = optimalMaxHeightPageSize.getHeight() / originalMaxHeightPageSize.getHeight();
                optimalMaxWidthPageSize = fitBoth(originalMaxWidthPageSize, viewSize.getWidth(), originalMaxWidthPageSize.getHeight() * heightRatio);
                widthRatio = optimalMaxWidthPageSize.getWidth() / originalMaxWidthPageSize.getWidth();
                break;
            default:
                optimalMaxHeightPageSize = fitWidth(originalMaxHeightPageSize, viewSize.getWidth());
                optimalMaxWidthPageSize = fitWidth(originalMaxWidthPageSize, viewSize.getWidth());
                widthRatio = optimalMaxWidthPageSize.getWidth() / originalMaxWidthPageSize.getWidth();
                break;
        }
    }

    private Size fitWidth(Size pageSize, float maxWidth) {
        float w = pageSize.getWidth(), h = pageSize.getHeight();
        float ratio = w / h;
        w = maxWidth;
        h = (float) Math.floor(maxWidth / ratio);
        return new Size((int)w, (int)h);
    }

    private Size fitHeight(Size pageSize, float maxHeight) {
        float w = pageSize.getWidth(), h = pageSize.getHeight();
        float ratio = h / w;
        h = maxHeight;
        w = (float) Math.floor(maxHeight / ratio);
        return new Size((int)w, (int)h);
    }

    private Size fitBoth(Size pageSize, float maxWidth, float maxHeight) {
        float w = pageSize.getWidth(), h = pageSize.getHeight();
        float ratio = w / h;
        w = maxWidth;
        h = (float) Math.floor(maxWidth / ratio);
        if (h > maxHeight) {
            h = maxHeight;
            w = (float) Math.floor(maxHeight * ratio);
        }
        return new Size((int)w, (int)h);
    }

}
