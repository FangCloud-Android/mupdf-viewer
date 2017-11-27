package com.artifex.mupdf.fitz.android;

import android.graphics.Bitmap;
import android.util.Log;

import com.artifex.mupdf.fitz.NativeDevice;
import com.artifex.mupdf.fitz.Rect;

import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Matrix;

public final class AndroidDrawDevice extends NativeDevice
{
	private native long newNative(Bitmap bitmap, int xOrigin, int yOrigin, int patchX0, int patchY0, int patchX1, int patchY1);

	public AndroidDrawDevice(Bitmap bitmap, int xOrigin, int yOrigin, int patchX0, int patchY0, int patchX1, int patchY1) {
		super(0);
		pointer = newNative(bitmap, xOrigin, yOrigin, patchX0, patchY0, patchX1, patchY1);
	}

	public AndroidDrawDevice(Bitmap bitmap, int xOrigin, int yOrigin) {
		this(bitmap, xOrigin, yOrigin, 0, 0, bitmap.getWidth(), bitmap.getHeight());
	}

	public AndroidDrawDevice(Bitmap bitmap) {
		this(bitmap, 0, 0);
	}

	/**
	 * 按照区域绘制Page
	 * @param page 绘制页
	 * @param ctm
	 * @param rect 绘制区域
	 * @return
	 */
	public static Bitmap drawPage(Page page, Matrix ctm, Rect rect) {
		Rect fbox = page.getBounds().transform(ctm);
		int w = (int) (rect.x1 - rect.x0);
		int h = (int) (rect.y1 - rect.y0);
		Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_4444);
		Log.d("AndroidDrawDevice", "======================>>>>>>>>>>> drawPage w " + w + " h " + h + " rect.x0 " + rect.x0 + " (int) rect.y0 " + (int) rect.y0);
		AndroidDrawDevice dev = new AndroidDrawDevice(bmp, (int) fbox.x0, (int) fbox.y0, 0, (int)0, w, h);
//		AndroidDrawDevice dev = new AndroidDrawDevice(bmp, ibox.x0, ibox.y0);
		page.run(dev, ctm, null);
		dev.close();
		dev.destroy();
		return bmp;
	}

	public static Matrix fitPage(Page page, int fitW, int fitH) {
		Rect bbox = page.getBounds();
		float pageW = bbox.x1 - bbox.x0;
		float pageH = bbox.y1 - bbox.y0;
		float scaleH = (float)fitW / pageW;
		float scaleV = (float)fitH / pageH;
		float scale = scaleH < scaleV ? scaleH : scaleV;
		scaleH = (float)Math.floor(pageW * scale) / pageW;
		scaleV = (float)Math.floor(pageH * scale) / pageH;
		return new Matrix(scaleH, scaleV);
	}

	public static Matrix fitPageWidth(Page page, int fitW) {
		Rect bbox = page.getBounds();
		float pageW = bbox.x1 - bbox.x0;
		float scale = (float)fitW / pageW;
		scale = (float)Math.floor(pageW * scale) / pageW;
		return new Matrix(scale);
	}
}
