package com.artifex.mupdf.fitz;

import android.graphics.Bitmap;
import android.graphics.PointF;

import com.artifex.mupdf.fitz.android.AndroidDrawDevice;

public class Page {

	private long pointer;

	protected native void finalize();

	public void destroy() {
		finalize();
		pointer = 0;
	}

	protected Page(long p) {
		pointer = p;
	}

	public native Rect getBounds();

	public native Pixmap toPixmap(Matrix ctm, ColorSpace cs, boolean alpha);

	public native void run(Device dev, Matrix ctm, Cookie cookie);
	public native void runPageContents(Device dev, Matrix ctm, Cookie cookie);

	public void run(Device dev, Matrix ctm) {
		run(dev, ctm, null);
	}

	public native Annotation[] getAnnotations();
	public native Link[] getLinks();

	// FIXME: Later. Much later.
	//fz_transition *fz_page_presentation(fz_document *doc, fz_page *page, float *duration);

	public native DisplayList toDisplayList(boolean no_annotations);
	public native StructuredText toStructuredText(String options);

	public StructuredText toStructuredText() {
		return toStructuredText(null);
	}

	public native Rect[] search(String needle);

	public native byte[] textAsHtml();

	public native int countSeparations();
	public native Separation getSeparation(int index);
	public native void enableSeparation(int index, boolean enabled);

	private Size size;

	private Bitmap render;

	public Matrix matrix;

	public Bitmap getBitmap(Size newSize, Rect rect) {
		if (rect == null) {
			rect = new Rect(300, 400, newSize.width/2, newSize.height/2);
		}
		matrix = AndroidDrawDevice.fitPageWidth(this, newSize.width);
		return AndroidDrawDevice.drawPage(this, matrix, rect);
	}

	public boolean inValidBitmap() {
		return render == null || render.isRecycled();
	}

	/**
	 * 计算当前页面实际PDF页面的大小
	 * @param height
	 * @return
	 */
	public Size fitPageSize(int width, int height) {
		Rect fbox = getBounds().transform(AndroidDrawDevice.fitPage(this, width, height));
		RectI ibox = new RectI((int)fbox.x0, (int)fbox.y0, (int)fbox.x1, (int)fbox.y1);
		int w = ibox.x1 - ibox.x0;
		int h = ibox.y1 - ibox.y0;
		Size size = new Size(w, h);
		return size;
	}
}
