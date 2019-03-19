package io.card.payment;

/* OverlayView.java
 * See the file "LICENSE.md" for the full license governing this code.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.support.annotation.DrawableRes;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;

import io.card.payment.i18n.LocalizedStrings;
import io.card.payment.i18n.StringKey;

/**
 * This class implements a transparent overlay that is drawn over the raw camera capture frames.
 * <p/>
 * OverlayView draws the guide frame which indicates to the user where to hold the card. For debug
 * builds, it also displays the frame rate and the focus score. Once a card is detected, the class
 * displays a still image of the card.
 * <p/>
 * There are two stages of mark-up that are applied to the card image. When the image is first
 * passed into this class, a negative rounded rectangle mask is used to block out the image
 * background behind the rounded corners of the card. Then, a light gray rounded rectangle outline
 * is drawn along the card edges.
 * <p/>
 * Once the digits are detected for the credit card number, those digits are drawn above the
 * respective digits of the card.
 * <p/>
 * An instance of this class is created when the owning CardIOActivity is created. Its lifecycle is
 * the same as that of the owning activity.
 * <p/>
 * <p/>
 * A couple of technical notes:
 * <p/>
 * the drawing code is not optimized for performance. It re-computes values for each frame that
 * could be cached instead (such as guide tick locations). However, I have measured performance for
 * the app, including drawing performance, and drawing is not at all a bottleneck. So, fixing this
 * seems like a low priority item.
 * <p/>
 * It is not clear whether the rotation animation currently implemented is really needed. We should
 * figure out a strategy for this, and then change the code accordingly.
 * <p/>
 * To prevent race conditions & memory leaks, setting new card images happens in the context of the
 * UI thread.
 * <p/>
 * The class has an instance float field displayScale that holds the value screen dimensions are
 * scaled by. This field is used for instance to achieve consistent guide frame thickness
 * independent of screen scale.
 * <p/>
 */
class OverlayView extends View {
    private static final String TAG = OverlayView.class.getSimpleName();

    private static final float GUIDE_FONT_SIZE = 26.0f;
    private static final float GUIDE_LINE_PADDING = 8.0f;
    private static final float GUIDE_LINE_HEIGHT = GUIDE_FONT_SIZE + GUIDE_LINE_PADDING;
    private static final float CARD_NUMBER_MARKUP_FONT_SIZE = GUIDE_FONT_SIZE + 2;

    private static final Orientation[] GRADIENT_ORIENTATIONS = { Orientation.TOP_BOTTOM,
            Orientation.LEFT_RIGHT, Orientation.BOTTOM_TOP, Orientation.RIGHT_LEFT };

    private static final int GUIDE_STROKE_WIDTH = 17;

    private static final float CORNER_RADIUS_SIZE = 1000 / 15.0f;

    private static final int TORCH_WIDTH = 70;
    private static final int TORCH_HEIGHT = 50;

    private static final int BUTTON_TOUCH_TOLERANCE = 20;

    private final WeakReference<CardIOCameraControl> mScanActivityRef;
    private DetectionInfo mDInfo;
    private Bitmap mBitmap;
    GradientDrawable mScanLineDrawable;
    private Rect mGuide;
    private CreditCard mDetectedCard;
    private int mRotation;
    private int mState;
    private int guideColor;

    private String scanInstructions;

    // Keep paint objects around for high frequency methods to avoid re-allocating them.
    private GradientDrawable mGradientDrawable;
    private final Paint mGuidePaint;
    private final Paint mLockedBackgroundPaint;
    private Path mLockedBackgroundPath;
    private Rect mCameraPreviewRect;
    private Torch mTorch;
    private Rect mTorchRect;
    private final boolean mShowTorch;
    private int mRotationFlip;
    private float mScale = 1;
    private float mGuideStrokeCornerRadius = 0;
    private float mGuideStrokeWidth;
    private RectF mGuideRoundedRect;
    private Typeface mScanInstructionTypeFace = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private float mScanInstructionFontSize;
    private float mScanInstructionLineHeight;

    public OverlayView(Context context, CardIOCameraControl cameraControl, AttributeSet attributeSet, @DrawableRes int torchOnResId, @DrawableRes int torchOffResId, boolean showTorch) {
        super(context, attributeSet);

        mShowTorch = showTorch;
        mScanActivityRef = new WeakReference<>(cameraControl);

        mRotationFlip = 1;

        // card.io is designed for an hdpi screen (density = 1.5);
        mScale = getResources().getDisplayMetrics().density / 1.5f;

        if (torchOnResId != -1 && torchOffResId != -1) {
            mTorch = new Torch(context, torchOnResId, torchOffResId);
        }

        mGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mLockedBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLockedBackgroundPaint.clearShadowLayer();
        mLockedBackgroundPaint.setStyle(Paint.Style.FILL);
        mLockedBackgroundPaint.setColor(0xbb000000); // 75% black

        scanInstructions = LocalizedStrings.getString(StringKey.SCAN_GUIDE);

        mScanInstructionFontSize = getResources().getDimension(R.dimen.cio_scan_instruction_text_size);
        mScanInstructionLineHeight = getResources().getDimension(R.dimen.cio_scan_instruction_line_height);
    }

    public int getGuideColor() {
        return guideColor;
    }

    public void setGuideColor(int color) {
        guideColor = color;
    }

    public float getGuideStrokeCornerRadius() {
        return mGuideStrokeCornerRadius;
    }

    public void setGuideStrokeCornerRadius(float guideStrokeCornerRadius) {
        mGuideStrokeCornerRadius = guideStrokeCornerRadius;
    }

    public float getGuideStrokeWidth() {
        return mGuideStrokeWidth;
    }

    public void setGuideStrokeWidth(float guideStrokeWidth) {
        mGuideStrokeWidth = guideStrokeWidth;
    }

    public String getScanInstructions() {
        return scanInstructions;
    }

    public void setScanInstructions(String scanInstructions) {
        this.scanInstructions = scanInstructions;
    }

    public void setScanInstructionTypeFace(Typeface scanInstructionTypeFace) {
        mScanInstructionTypeFace = scanInstructionTypeFace;
    }

    public void setScanInstructionFontSize(float scanInstructionFontSize) {
        mScanInstructionFontSize = scanInstructionFontSize;
    }

    public void setScanInstructionLineHeight(float scanInstructionLineHeight) {
        mScanInstructionLineHeight = scanInstructionLineHeight;
    }

    // Public methods used by CardIOActivity
    public void setGuideAndRotation(Rect rect, int rotation) {
        mRotation = rotation;
        mGuide = rect;
        mGuideRoundedRect = new RectF(rect);

        invalidate();

        Point topEdgeUIOffset;
        if (mRotation % 180 != 0) {
            topEdgeUIOffset = new Point((int) (40 * mScale), (int) (60 * mScale));
            mRotationFlip = -1;
        } else {
            topEdgeUIOffset = new Point((int) (60 * mScale), (int) (40 * mScale));
            mRotationFlip = 1;
        }
        if (mCameraPreviewRect != null) {

            // mTorchRect used only for touch lookup, not layout
            int torchSize = (int) getResources().getDimension(R.dimen.cio_torch_size);
            int torchMargin = (int) getResources().getDimension(R.dimen.cio_torch_margin_top);
            int widthCenter = mGuide.width() / 2 + mGuide.left;

            mTorchRect = new Rect(widthCenter - torchSize / 2, mGuide.bottom + torchMargin, widthCenter + torchSize / 2, mGuide.bottom + torchSize + torchMargin);

            int[] gradientColors = { Color.WHITE, Color.BLACK };
            Orientation gradientOrientation = GRADIENT_ORIENTATIONS[(mRotation / 90) % 4];
            mGradientDrawable = new GradientDrawable(gradientOrientation, gradientColors);
            mGradientDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            mGradientDrawable.setBounds(mGuide);
            mGradientDrawable.setAlpha(50);
            mGradientDrawable.setCornerRadius(mGuideStrokeCornerRadius);

            mLockedBackgroundPath = new Path();
            mLockedBackgroundPath.addRect(new RectF(mCameraPreviewRect), Path.Direction.CW);
            mLockedBackgroundPath.addRoundRect(new RectF(mGuide), mGuideStrokeCornerRadius, mGuideStrokeCornerRadius, Path.Direction.CCW);
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (mBitmap != null) {
            mBitmap.recycle();
        }
        mBitmap = bitmap;
        if (mBitmap != null) {
            decorateBitmap();
        }
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setDetectionInfo(DetectionInfo dinfo) {
        if (mDInfo != null && !mDInfo.sameEdgesAs(dinfo)) {
            invalidate();
        }
        this.mDInfo = dinfo;
    }

    public int getCardX() {
        return mGuide.centerX() - mBitmap.getWidth() / 2;
    }

    public int getCardY() {
        return mGuide.centerY() - mBitmap.getHeight() / 2;
    }

    public Bitmap getCardImage() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            return Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        } else {
            return null;
        }
    }

    @Override
    public void onDraw(Canvas canvas) {

        if (mGuide == null || mCameraPreviewRect == null) {
            return;
        }
        canvas.save();

        // Draw background rect
        mGradientDrawable.draw(canvas);

        if (mDInfo != null && mDInfo.numVisibleEdges() == 4) {
            // draw lock shadow.
            canvas.drawPath(mLockedBackgroundPath, mLockedBackgroundPaint);
        }

        // Draw guide lines
        mGuidePaint.clearShadowLayer();
        mGuidePaint.setStyle(Style.STROKE);
        mGuidePaint.setColor(guideColor);
        mGuidePaint.setStrokeWidth(mGuideStrokeWidth);

        canvas.drawRoundRect(mGuideRoundedRect, mGuideStrokeCornerRadius, mGuideStrokeCornerRadius, mGuidePaint);

        if (mDInfo != null) {

            if (mDInfo.numVisibleEdges() < 3) {
                // Draw guide text
                // Set up paint attributes
                setupTextPaintStyle(mGuidePaint);
                mGuidePaint.setTextAlign(Align.CENTER);
                mGuidePaint.setTextSize(mScanInstructionFontSize);

                // Translate and rotate text
                canvas.translate(mGuide.left + mGuide.width() / 2, mGuide.top);
                canvas.rotate(mRotationFlip * mRotation);

                if (scanInstructions != null && scanInstructions != "") {
                    String[] lines = scanInstructions.split("\n");
                    float marginTop = getResources().getDimension(R.dimen.cio_scan_instruction_margin_bottom);
                    float y = -marginTop;

                    for (int i = lines.length - 1; i > -1; i--) {
                        canvas.drawText(lines[i], 0, y, mGuidePaint);
                        y -= mScanInstructionLineHeight;
                    }
                }
            }
        }
        canvas.restore();

        if (mShowTorch) {
            // draw torch
            canvas.save();
            canvas.translate(mTorchRect.left, mTorchRect.top);
            canvas.rotate(mRotationFlip * mRotation);
            mTorch.draw(canvas);
            canvas.restore();
        }
    }

    public void setDetectedCard(CreditCard creditCard) {
        mDetectedCard = creditCard;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            int action;
            action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {

                Point p = new Point((int) event.getX(), (int) event.getY());
                Rect r = Util.rectGivenCenter(p, BUTTON_TOUCH_TOLERANCE, BUTTON_TOUCH_TOLERANCE);
                if (mShowTorch && mTorchRect != null && Rect.intersects(mTorchRect, r)) {
                    mScanActivityRef.get().toggleFlash();
                } else {
                    mScanActivityRef.get().triggerAutoFocus();
                }
            }
        } catch (NullPointerException e) {
            // Un-reproducible NPE reported on device without flash where flash detected and flash
            // button pressed (see https://github.com/paypal/PayPal-Android-SDK/issues/27)
        }

        return false;
    }

    /* create the card image with inside a rounded rect */
    private void decorateBitmap() {

        RectF roundedRect = new RectF(2, 2, mBitmap.getWidth() - 2, mBitmap.getHeight() - 2);
        float cornerRadius = mBitmap.getHeight() * CORNER_RADIUS_SIZE;

        // Alpha canvas with white rounded rect
        Bitmap maskBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(maskBitmap);
        maskCanvas.drawColor(Color.TRANSPARENT);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.BLACK);
        maskPaint.setStyle(Paint.Style.FILL);
        maskCanvas.drawRoundRect(roundedRect, cornerRadius, cornerRadius, maskPaint);

        Paint paint = new Paint();
        paint.setFilterBitmap(false);

        // Draw mask onto mBitmap
        Canvas canvas = new Canvas(mBitmap);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(maskBitmap, 0, 0, paint);

        // Now re-use the above bitmap to do a shadow.
        paint.setXfermode(null);

        maskBitmap.recycle();
    }

    // TODO - move this into RequestTask, so we just get back a card image ready to go
    public void markupCard() {

        if (mBitmap == null) {
            return;
        }

        if (mDetectedCard.flipped) {
            Matrix m = new Matrix();
            m.setRotate(180, mBitmap.getWidth() / 2, mBitmap.getHeight() / 2);

            mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(),
                    m, false);
        }

        Canvas bc = new Canvas(mBitmap);
        Paint paint = new Paint();
        setupTextPaintStyle(paint);
        paint.setTextSize(CARD_NUMBER_MARKUP_FONT_SIZE * mScale);

        int len = mDetectedCard.cardNumber.length();
        float sf = mBitmap.getWidth() / (float)CardScanner.CREDIT_CARD_TARGET_WIDTH;
        int yOffset = (int) ((mDetectedCard.yoff * sf - 6));
        for (int i = 0; i < len; i++) {
            int xOffset = (int) (mDetectedCard.xoff[i] * sf);
            bc.drawText("" + mDetectedCard.cardNumber.charAt(i), xOffset, yOffset, paint);
        }
    }

    public boolean isAnimating() {
        return (mState != 0);
    }

    public void setCameraPreviewRect(Rect rect) {
        mCameraPreviewRect = rect;
    }

    public void setTorchOn(boolean b) {
        if (mTorch != null) {
            mTorch.setOn(b);
            invalidate();
        }
    }

    private void setupTextPaintStyle(Paint paint) {
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(mScanInstructionTypeFace);
        paint.setAntiAlias(true);
        float[] black = { 0f, 0f, 0f };
        paint.setShadowLayer(1.5f, 0.5f, 0f, Color.HSVToColor(200, black));
    }
}
