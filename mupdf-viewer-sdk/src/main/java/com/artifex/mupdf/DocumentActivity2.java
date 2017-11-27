package com.artifex.mupdf;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;

import com.artifex.mupdf.pdfviewer.PDFView;
import com.artifex.mupdf.pdfviewer.listener.OnPageChangeListener;
import com.artifex.mupdf.pdfviewer.listener.OnPageErrorListener;

/**
 * @author leixin
 */
public class DocumentActivity2 extends FragmentActivity implements OnPageChangeListener, OnPageErrorListener {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_content);
//        MupdfViewer pdfViewer = (MupdfViewer) findViewById(R.id.mupdfViewer);
//
        Uri uri = getIntent().getData();
//        try {
//            pdfViewer.load(uri.getEncodedPath(), "");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        PDFView pdfView = (PDFView) findViewById(R.id.pdfView);
        pdfView.fromUri(uri)
                .swipeHorizontal(false)
                .alwaysScrollToPageStart(true)
                .load();
    }

    @Override
    public void onPageError(int page, Throwable t) {

    }

    @Override
    public void onPageChanged(int page, int pageCount) {

    }
}
