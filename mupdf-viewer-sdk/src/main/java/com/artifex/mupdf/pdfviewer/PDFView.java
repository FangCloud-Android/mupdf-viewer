package com.artifex.mupdf.pdfviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Size;
import com.artifex.mupdf.pdfviewer.link.DefaultLinkHandler;
import com.artifex.mupdf.pdfviewer.link.LinkHandler;
import com.artifex.mupdf.pdfviewer.listener.Callbacks;
import com.artifex.mupdf.pdfviewer.listener.OnPageChangeListener;
import com.artifex.mupdf.pdfviewer.listener.OnPageErrorListener;
import com.artifex.mupdf.pdfviewer.listener.OnPageScrollListener;
import com.artifex.mupdf.pdfviewer.listener.OnRenderListener;
import com.artifex.mupdf.pdfviewer.listener.OnTapListener;
import com.artifex.mupdf.pdfviewer.manager.inter.IScaleView;
import com.artifex.mupdf.pdfviewer.scroll.ScrollHandle;
import com.artifex.mupdf.pdfviewer.source.ByteArraySource;
import com.artifex.mupdf.pdfviewer.source.DocumentSource;
import com.artifex.mupdf.pdfviewer.source.FileSource;
import com.artifex.mupdf.pdfviewer.source.UriSource;
import com.artifex.mupdf.pdfviewer.util.Constants;
import com.artifex.mupdf.pdfviewer.util.FitPolicy;
import com.artifex.mupdf.pdfviewer.util.MathUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PDFView extends FrameLayout implements IScaleView {

    private static final String TAG = PDFView.class.getSimpleName();

    /**
     * START - scrolling in first page direction
     * END - scrolling in last page direction
     * NONE - not scrolling
     */
    enum ScrollDir {
        NONE, START, END
    }

    private ScrollDir scrollDir = ScrollDir.NONE;

    /** Animation manager manage all offset and zoom animation */
    private AnimationManager animationManager;

    /** Drag manager manage all touch events */
    private DragPinchManager dragPinchManager;

    PdfFile pdfFile;

    /** The index of the current sequence */
    private int currentPage;

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    private float currentXOffset = 0;

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    private float currentYOffset = 0;

    /** The zoom level, always >= 1 */
    private float zoom = 1f;

    /** True if the PDFView has been recycled */
    private boolean recycled = true;

    /** Current state of the view */
    private State state = State.DEFAULT;

    /** Async task used during the loading phase to decode a PDF document */
    private DecodingAsyncTask decodingAsyncTask;

    private PagesLoader pagesLoader;

    Callbacks callbacks = new Callbacks();

    PDFConfigurations configurations = new PDFConfigurations();

    private boolean isScrollHandleInit = false;

    private ScrollHandle scrollHandle;

    public ScrollHandle getScrollHandle() {
        return scrollHandle;
    }

    /**
     * True if the view should render during scaling<br/>
     * Can not be forced on older API versions (< Build.VERSION_CODES.KITKAT) as the GestureDetector does
     * not detect scrolling while scaling.<br/>
     * False otherwise
     */
    private boolean renderDuringScale = false;

    /** Construct the initial view */
    public PDFView(Context context, AttributeSet set) {
        super(context, set);

        if (isInEditMode()) {
            return;
        }

        animationManager = new AnimationManager(this);
        dragPinchManager = new DragPinchManager(this, animationManager) {
            @Override
            ScrollHandle getScrollHandle() {
                return PDFView.this.getScrollHandle();
            }
        };
        pagesLoader = new PagesLoader(this);

        setWillNotDraw(false);
    }

    private void load(DocumentSource docSource, String password) {
        load(docSource, password, null);
    }

    private void load(DocumentSource docSource, String password, int[] userPages) {
        if (!recycled) {
            throw new IllegalStateException("Don't call load on a PDF View without recycling it first.");
        }

        recycled = false;
        // Start decoding document
        decodingAsyncTask = new DecodingAsyncTask(docSource, password, userPages, this);
        decodingAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Go to the given page.
     * @param page Page index.
     */
    public void jumpTo(int page, boolean withAnimation) {
        if (pdfFile == null) {
            return;
        }

        page = pdfFile.determineValidPageNumberFrom(page);
        float offset = -pdfFile.getPageOffset(page, zoom);
        if (isSwipeVertical()) {
            if (withAnimation) {
                animationManager.startYAnimation(currentYOffset, offset);
            } else {
                moveTo(currentXOffset, offset);
            }
        } else {
            if (withAnimation) {
                animationManager.startXAnimation(currentXOffset, offset);
            } else {
                moveTo(offset, currentYOffset);
            }
        }
        showPage(page);
    }

    public void jumpTo(int page) {
        jumpTo(page, false);
    }

    void showPage(int pageNb) {
        if (recycled) {
            return;
        }

        // Check the page number and makes the
        // difference between UserPages and DocumentPages
        pageNb = pdfFile.determineValidPageNumberFrom(pageNb);
        currentPage = pageNb;

        loadPages();

        if (scrollHandle != null && !documentFitsView()) {
            scrollHandle.setPageNum(currentPage + 1);
        }

        callbacks.callOnPageChange(currentPage, pdfFile.getPagesCount());
    }

    /**
     * Get current position as ratio of document length to visible area.
     * 0 means that document start is visible, 1 that document end is visible
     *
     * @return offset between 0 and 1
     */
    public float getPositionOffset() {
        float offset;
        if (isSwipeVertical()) {
            offset = -currentYOffset / (pdfFile.getDocLen(zoom) - getHeight());
        } else {
            offset = -currentXOffset / (pdfFile.getDocLen(zoom) - getWidth());
        }
        return MathUtils.limit(offset, 0, 1);
    }

    /**
     * @param progress   must be between 0 and 1
     * @param moveHandle whether to move scroll handle
     * @see PDFView#getPositionOffset()
     */
    public void setPositionOffset(float progress, boolean moveHandle) {
        if (isSwipeVertical()) {
            moveTo(currentXOffset, (-pdfFile.getDocLen(zoom) + getHeight()) * progress, moveHandle);
        } else {
            moveTo((-pdfFile.getDocLen(zoom) + getWidth()) * progress, currentYOffset, moveHandle);
        }
        loadPageByOffset();
    }

    public void setPositionOffset(float progress) {
        setPositionOffset(progress, true);
    }

    public void stopFling() {
        animationManager.stopFling();
    }

    public int getPageCount() {
        if (pdfFile == null) {
            return 0;
        }
        return pdfFile.getPagesCount();
    }

    public void recycle() {
        animationManager.stopAll();
        dragPinchManager.disable();

        if (decodingAsyncTask != null) {
            decodingAsyncTask.cancel(true);
        }

        if (scrollHandle != null && isScrollHandleInit) {
            scrollHandle.destroyLayout();
        }

        if (pdfFile != null) {
            pdfFile.dispose();
            pdfFile = null;
        }

        scrollHandle = null;
        isScrollHandleInit = false;
        currentXOffset = currentYOffset = 0;
        zoom = 1f;
        recycled = true;
        callbacks = new Callbacks();
        state = State.DEFAULT;
    }

    public boolean isRecycled() {
        return recycled;
    }

    /** Handle fling animation */
    @Override
    public void computeScroll() {
        super.computeScroll();
        if (isInEditMode()) {
            return;
        }
        animationManager.computeFling();
    }

    @Override
    protected void onDetachedFromWindow() {
        recycle();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (isInEditMode() || state != State.SHOWN) {
            return;
        }
        animationManager.stopAll();
        pdfFile.recalculatePageSizes(new Size(w, h));
        if (isSwipeVertical()) {
            moveTo(currentXOffset, -pdfFile.getPageOffset(currentPage, zoom));
        } else {
            moveTo(-pdfFile.getPageOffset(currentPage, zoom), currentYOffset);
        }
        loadPageByOffset();
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (isSwipeVertical()) {
            if (direction < 0 && currentXOffset < 0) {
                return true;
            } else if (direction > 0 && currentXOffset + toCurrentScale(pdfFile.getMaxPageWidth()) > getWidth()) {
                return true;
            }
        } else {
            if (direction < 0 && currentXOffset < 0) {
                return true;
            } else if (direction > 0 && currentXOffset + pdfFile.getDocLen(zoom) > getWidth()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canScrollVertically(int direction) {
        if (isSwipeVertical()) {
            if (direction < 0 && currentYOffset < 0) {
                return true;
            } else if (direction > 0 && currentYOffset + pdfFile.getDocLen(zoom) > getHeight()) {
                return true;
            }
        } else {
            if (direction < 0 && currentYOffset < 0) {
                return true;
            } else if (direction > 0 && currentYOffset + toCurrentScale(pdfFile.getMaxPageHeight()) > getHeight()) {
                return true;
            }
        }
        return false;
    }

    private PointF getTranslationPosition(int page, Size size) {
        float localTranslationX = 0;
        float localTranslationY = 0;

        if (isSwipeVertical()) {
            localTranslationY = pdfFile.getPageOffset(page, zoom);
            float maxWidth = pdfFile.getMaxPageWidth();
            localTranslationX = toCurrentScale(maxWidth - size.getWidth()) / 2;
        } else {
            localTranslationX = pdfFile.getPageOffset(page, zoom);
            float maxHeight = pdfFile.getMaxPageHeight();
            localTranslationY = toCurrentScale(maxHeight - size.getHeight()) / 2;
        }
        return new PointF(localTranslationX, localTranslationY);
    }

    private       Bitmap mSharedHqBm;


    /**
     * 检测元素是否需要新增或者刷新到页面
     * @param pageNumber
     * @param pageView
     */
    protected synchronized void addPageToView(int pageNumber, PageView pageView) {
        // 创建新PageView
        Size size = pdfFile.getPageSize(pageNumber);
        PointF localTranslation = getTranslationPosition(pageNumber, size);
        float fWidth = size.width * zoom;
        float fHeight = size.height * zoom;
        FrameLayout.LayoutParams params;
        if (pageView == null) {
            if (mSharedHqBm == null || mSharedHqBm.getWidth() != size.getWidth() || mSharedHqBm.getHeight() != size.getHeight()) {
                mSharedHqBm = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
            }
            pageView = new PageView(getContext(), pdfFile.pdfDocument, new Point(size.width, size.height), mSharedHqBm);

            params = new FrameLayout.LayoutParams((int)fWidth, (int)fHeight);
            pageView.setPage(pageNumber, new PointF(size.width, size.height));
            pageView.setTag(pageTag(pageNumber));
            addView(pageView, params);

//            params = (FrameLayout.LayoutParams) pageView.getLayoutParams();
            params.topMargin = (int) localTranslation.y;
            params.leftMargin = (int) localTranslation.x;
        }

//        if (pageView != null) {
//            pageView.setPage(pageNumber, new PointF(fWidth, fHeight));
//            pageView.updateHq(false);
//        }

        // 检测需要移除的page
        checkShouldRemovePage();
    }

    /**
     * 检测超过屏幕的页面，并移除元素
     */
    private synchronized void checkShouldRemovePage() {
        List<Integer> shownPages = pagesLoader.shouldShowPages(currentPage);
        List<View> shouldRemove = new ArrayList<>();
        int pageCount = getChildCount();
        for (int i=0; i<pageCount; i++) {
            PageView pageView = (PageView) getChildAt(i);
            if (!shownPages.contains(pageView.getPage())) {
                shouldRemove.add(pageView);
            }
        }
        for (View view : shouldRemove) {
            removeView(view);
        }
    }

    /**
     * Load all the parts around the center of the screen,
     * taking into account X and Y offsets, zoom level, and
     * the current page displayed
     */
    public void loadPages() {
        if (pdfFile == null) {
            return;
        }

        pagesLoader.loadPages();
        invalidate();
    }

    /**
     * 开始请求页面预览
     * @param pageNumber
     */
    public void requestPage(final int pageNumber) {
        PageView pageView = (PageView) findViewWithTag(pageTag(pageNumber));
        addPageToView(pageNumber, pageView);
        if (pageView != null) {
            pageView.updateHq(false);
        }
    }

    private String pageTag(int page) {
        return "page_number_" + page;
    }

    /** Called when the PDF is loaded */
    void loadComplete(PdfFile pdfFile) {
        state = State.LOADED;

        this.pdfFile = pdfFile;

        if (scrollHandle != null) {
            scrollHandle.setupLayout(this);
            isScrollHandleInit = true;
        }

        dragPinchManager.enable();

        callbacks.callOnLoadComplete(pdfFile.getPagesCount());

        jumpTo(configurations.defaultPage, false);
    }

    void loadError(Throwable t) {
        state = State.ERROR;
        recycle();
        invalidate();
        if (!callbacks.callOnError(t)) {
            Log.e("PDFView", "load pdf error", t);
        }
    }

    public void moveTo(float offsetX, float offsetY) {
        moveTo(offsetX, offsetY, true);
    }

    /**
     * Move to the given X and Y offsets, but check them ahead of time
     * to be sure not to go outside the the big strip.
     *
     * @param offsetX    The big strip X offset to use as the left border of the screen.
     * @param offsetY    The big strip Y offset to use as the right border of the screen.
     * @param moveHandle whether to move scroll handle or not
     */
    public void moveTo(float offsetX, float offsetY, boolean moveHandle) {
        if (isSwipeVertical()) {
            // Check X offset
            float scaledPageWidth = toCurrentScale(pdfFile.getMaxPageWidth());
            if (scaledPageWidth < getWidth()) {
                offsetX = getWidth() / 2 - scaledPageWidth / 2;
            } else {
                if (offsetX > 0) {
                    offsetX = 0;
                } else if (offsetX + scaledPageWidth < getWidth()) {
                    offsetX = getWidth() - scaledPageWidth;
                }
            }

            // Check Y offset
            float contentHeight = pdfFile.getDocLen(zoom);
            if (contentHeight < getHeight()) { // whole document height visible on screen
                offsetY = (getHeight() - contentHeight) / 2;
            } else {
                if (offsetY > 0) { // top visible
                    offsetY = 0;
                } else if (offsetY + contentHeight < getHeight()) { // bottom visible
                    offsetY = -contentHeight + getHeight();
                }
            }

            if (offsetY < currentYOffset) {
                scrollDir = ScrollDir.END;
            } else if (offsetY > currentYOffset) {
                scrollDir = ScrollDir.START;
            } else {
                scrollDir = ScrollDir.NONE;
            }
        } else {
            // Check Y offset
            float scaledPageHeight = toCurrentScale(pdfFile.getMaxPageHeight());
            if (scaledPageHeight < getHeight()) {
                offsetY = getHeight() / 2 - scaledPageHeight / 2;
            } else {
                if (offsetY > 0) {
                    offsetY = 0;
                } else if (offsetY + scaledPageHeight < getHeight()) {
                    offsetY = getHeight() - scaledPageHeight;
                }
            }

            // Check X offset
            float contentWidth = pdfFile.getDocLen(zoom);
            if (contentWidth < getWidth()) { // whole document width visible on screen
                offsetX = (getWidth() - contentWidth) / 2;
            } else {
                if (offsetX > 0) { // left visible
                    offsetX = 0;
                } else if (offsetX + contentWidth < getWidth()) { // right visible
                    offsetX = -contentWidth + getWidth();
                }
            }

            if (offsetX < currentXOffset) {
                scrollDir = ScrollDir.END;
            } else if (offsetX > currentXOffset) {
                scrollDir = ScrollDir.START;
            } else {
                scrollDir = ScrollDir.NONE;
            }
        }

        currentXOffset = offsetX;
        currentYOffset = offsetY;
        float positionOffset = getPositionOffset();

        if (moveHandle && scrollHandle != null && !documentFitsView()) {
            scrollHandle.setScroll(positionOffset);
        }

        callbacks.callOnPageScroll(getCurrentPage(), positionOffset);
        scrollTo((int) -currentXOffset, (int) -currentYOffset);
    }

    void loadPageByOffset() {
        if (0 == pdfFile.getPagesCount()) {
            return;
        }

        float offset, screenCenter;
        if (isSwipeVertical()) {
            offset = currentYOffset;
            screenCenter = ((float) getHeight()) / 2;
        } else {
            offset = currentXOffset;
            screenCenter = ((float) getWidth()) / 2;
        }

        int page = pdfFile.getPageAtOffset(-(offset - screenCenter), zoom);

        if (page >= 0 && page <= pdfFile.getPagesCount() - 1 && page != getCurrentPage()) {
            showPage(page);
        } else {
            loadPages();
        }
    }

    /**
     * Move relatively to the current position.
     *
     * @param dx The X difference you want to apply.
     * @param dy The Y difference you want to apply.
     * @see #moveTo(float, float)
     */
    public void moveRelativeTo(float dx, float dy) {
        moveTo(currentXOffset + dx, currentYOffset + dy);
    }

    /**
     * Change the zoom level
     */
    public void zoomTo(float zoom) {
        this.zoom = zoom;
    }

    /**
     * Change the zoom level, relatively to a pivot point.
     * It will call moveTo() to make sure the given point stays
     * in the middle of the screen.
     *
     * @param zoom  The zoom level.
     * @param pivot The point on the screen that should stays.
     */
    public void zoomCenteredTo(float zoom, PointF pivot) {
        float dzoom = zoom / this.zoom;
        zoomTo(zoom);
        float baseX = currentXOffset * dzoom;
        float baseY = currentYOffset * dzoom;
        baseX += (pivot.x - pivot.x * dzoom);
        baseY += (pivot.y - pivot.y * dzoom);
        moveTo(baseX, baseY);
        scaleAndLayoutByZoom(zoom);
    }

    /**
     * 根据zoom参数，重新layout界面元素
     * @param zoom
     */
    private void scaleAndLayoutByZoom(float zoom) {
       List<Integer> showPages = pagesLoader.shouldShowPages(currentPage);
       for (Integer page : showPages) {
           PageView pageView = (PageView) findViewWithTag(pageTag(page));
           if (pageView != null) {
               Size size = pdfFile.getPageSize(page);
               PointF localTranslation = getTranslationPosition(page, size);
               FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pageView.getLayoutParams();
               params.topMargin = (int) localTranslation.y;
               params.leftMargin = (int) localTranslation.x;
               params.width = (int) (size.width * zoom);
               params.height = (int) (size.height * zoom);
               pageView.setLayoutParams(params);
           }
       }
       invalidate();
    }

    /**
     * @see #zoomCenteredTo(float, PointF)
     */
    public void zoomCenteredRelativeTo(ScaleGestureDetector detector, float dzoom, PointF pivot) {
        zoomCenteredTo(zoom * dzoom, pivot);
    }

    /**
     * Checks if whole document can be displayed on screen, doesn't include zoom
     * @return true if whole document can displayed at once, false otherwise
     */
    public boolean documentFitsView() {
        float len = pdfFile.getDocLen(1);
        if (isSwipeVertical()) {
            return len < getHeight();
        } else {
            return len < getWidth();
        }
    }

    public void fitToWidth(int page) {
        if (state != State.SHOWN) {
            Log.e(TAG, "Cannot fit, document not rendered yet");
            return;
        }
        zoomTo(getWidth() / pdfFile.getPageSize(page).getWidth());
        jumpTo(page);
    }

    public Size getPageSize(int pageIndex) {
        if (pdfFile == null) {
            return new Size(0, 0);
        }
        return pdfFile.getPageSize(pageIndex);
    }

    public Size getViewSize() {
        return new Size(getWidth(), getHeight());
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public float getCurrentXOffset() {
        return currentXOffset;
    }

    public float getCurrentYOffset() {
        return currentYOffset;
    }

    public float toRealScale(float size) {
        return size / zoom;
    }

    public float toCurrentScale(float size) {
        return size * zoom;
    }

    public float getZoom() {
        return zoom;
    }

    public boolean isZooming() {
        return zoom != Constants.Pinch.MINIMUM_ZOOM;
    }

    @Override
    public void scaleEnd(ScaleGestureDetector detector) {
        loadPages();
        int pageStart = pagesLoader.calcPageStart(currentPage, zoom);
        int pageEnd = pagesLoader.calePageEnd(currentPage, zoom);
        for (int i = pageStart; i<pageEnd; i++) {
            PageView pageView = (PageView) findViewWithTag(pageTag(i));
            if (pageView != null && pdfFile != null) {
//                Size size = pdfFile.getPageSize(currentPage);
//                pageView.setPage(currentPage, new PointF(size.width * zoom, size.height * zoom));
                pageView.updateHq(false);
            }
        }
    }

    public void resetZoomWithAnimation() {
        zoomWithAnimation(Constants.Pinch.MINIMUM_ZOOM);
    }

    public void zoomWithAnimation(float centerX, float centerY, float scale) {
        animationManager.startZoomAnimation(centerX, centerY, zoom, scale);
    }

    public void zoomWithAnimation(float scale) {
        animationManager.startZoomAnimation(getWidth() / 2, getHeight() / 2, zoom, scale);
    }

    public float getPageOffset(int page) {
        return pdfFile.getPageOffset(page, zoom);
    }

    public boolean isSwipeVertical() {
        return !configurations.swipeHorizontal;
    }

    public boolean isSwipeEnabled() {
        return configurations.enableSwipe;
    }

    public boolean alwaysScrollToPageStart() {
        return configurations.alwaysScrollToPageStart;
    }

    int getSpacingPx() {
        return configurations.spacing;
    }

    public FitPolicy getPageFitPolicy() {
        return configurations.pageFitPolicy;
    }

    public boolean isDoubletapEnabled() {
        return configurations.enableDoubletap;
    }

    public boolean doRenderDuringScale() {
        return renderDuringScale;
    }

    /** Will be empty until document is loaded */
    public Link[] getLinks(int page) {
        if (pdfFile == null) {
            return new Link[0];
        }
        return pdfFile.getPageLinks(page);
    }

    /** Use a file as the pdf source */
    public Configurator fromFile(File file) {
        return new Configurator(new FileSource(file));
    }

    /** Use URI as the pdf source, for use with content providers */
    public Configurator fromUri(Uri uri) {
        return new Configurator(new UriSource(uri));
    }

    /** Use bytearray as the pdf source, documents is not saved */
    public Configurator fromBytes(byte[] bytes) {
        return new Configurator(new ByteArraySource(bytes));
    }

    /** Use custom source as pdf source */
    public Configurator fromSource(DocumentSource docSource) {
        return new Configurator(docSource);
    }

    private enum State {DEFAULT, LOADED, SHOWN, ERROR}

    public class Configurator {

        private final DocumentSource documentSource;

        private String password = null;

        private OnPageChangeListener onPageChangeListener;

        private OnPageScrollListener onPageScrollListener;

        private OnRenderListener onRenderListener;

        private OnTapListener onTapListener;

        private OnPageErrorListener onPageErrorListener;

        public LinkHandler linkHandler = new DefaultLinkHandler(PDFView.this);

        private Configurator(DocumentSource documentSource) {
            this.documentSource = documentSource;
        }

        public Configurator onPageError(OnPageErrorListener onPageErrorListener) {
            this.onPageErrorListener = onPageErrorListener;
            return this;
        }

        public Configurator onPageChange(OnPageChangeListener onPageChangeListener) {
            this.onPageChangeListener = onPageChangeListener;
            return this;
        }

        public Configurator onRender(OnRenderListener onRenderListener) {
            this.onRenderListener = onRenderListener;
            return this;
        }

        public Configurator onTap(OnTapListener onTapListener) {
            this.onTapListener = onTapListener;
            return this;
        }

        public Configurator password(String password) {
            this.password = password;
            return this;
        }

        public Configurator linkHandler(LinkHandler linkHandler) {
            this.linkHandler = linkHandler;
            return this;
        }

        PDFConfigurations config = new PDFConfigurations();

        public Configurator pages(int... pageNumbers) {
            config.pageNumbers = pageNumbers;
            return this;
        }

        public Configurator alwaysScrollToPageStart(boolean alwaysScrollToPageStart) {
            config.alwaysScrollToPageStart = alwaysScrollToPageStart;
            return this;
        }

        public Configurator enableAnnotationRendering(boolean annotationRendering) {
            config.annotationRendering = annotationRendering;
            return this;
        }

        public Configurator defaultPage(int defaultPage) {
            config.defaultPage = defaultPage;
            return this;
        }

        public Configurator swipeHorizontal(boolean swipeHorizontal) {
            config.swipeHorizontal = swipeHorizontal;
            return this;
        }

        public Configurator enableAntialiasing(boolean antialiasing) {
            config.antialiasing = antialiasing;
            return this;
        }

        public Configurator spacing(int spacing) {
            config.spacing = spacing;
            return this;
        }

        public Configurator pageFitPolicy(FitPolicy pageFitPolicy) {
            config.pageFitPolicy = pageFitPolicy;
            return this;
        }

        public Configurator enableSwipe(boolean enableSwipe) {
            config.enableSwipe = enableSwipe;
            return this;
        }

        public Configurator enableDoubletap(boolean enableDoubletap) {
            config.enableDoubletap = enableDoubletap;
            return this;
        }

        public void load() {
            PDFView.this.recycle();
            PDFView.this.callbacks.setOnPageChange(onPageChangeListener);
            PDFView.this.callbacks.setOnPageScroll(onPageScrollListener);
            PDFView.this.callbacks.setOnRender(onRenderListener);
            PDFView.this.callbacks.setOnTap(onTapListener);
            PDFView.this.callbacks.setOnPageError(onPageErrorListener);
            PDFView.this.callbacks.setLinkHandler(linkHandler);
            PDFView.this.configurations = config;
            PDFView.this.post(new Runnable() {
                @Override
                public void run() {
                    if (config.pageNumbers != null) {
                        PDFView.this.load(documentSource, password, config.pageNumbers);
                    } else {
                        PDFView.this.load(documentSource, password);
                    }
                }
            });
        }
    }
}