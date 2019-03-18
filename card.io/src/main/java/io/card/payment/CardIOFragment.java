package io.card.payment;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.Date;

import io.card.payment.i18n.LocalizedStrings;
import io.card.payment.i18n.StringKey;

public class CardIOFragment extends Fragment implements CardIOScanDetection, CardIOCameraControl
{
    /**
     * Boolean extra. Optional. Defaults to <code>false</code>. If
     * set to <code>false</code>, expiry information will not be required.
     */
    public static final String EXTRA_REQUIRE_EXPIRY = "io.card.payment.requireExpiry";

    /**
     * Boolean extra. Optional. Defaults to <code>true</code>. If
     * set to <code>true</code>, and {@link #EXTRA_REQUIRE_EXPIRY} is <code>true</code>,
     * an attempt to extract the expiry from the card image will be made.
     */
    public static final String EXTRA_SCAN_EXPIRY = "io.card.payment.scanExpiry";

    /**
     * Integer extra. Optional. Defaults to <code>-1</code> (no blur). Privacy feature.
     * How many of the Card number digits NOT to blur on the resulting image.
     * Setting it to <code>4</code> will blur all digits except the last four.
     */
    public static final String EXTRA_UNBLUR_DIGITS = "io.card.payment.unblurDigits";

    /**
     * Boolean extra. Optional. Defaults to <code>false</code>. If set, the user will be prompted
     * for the card CVV.
     */
    public static final String EXTRA_REQUIRE_CVV = "io.card.payment.requireCVV";

    /**
     * Boolean extra. Optional. Defaults to <code>false</code>. If set, the user will be prompted
     * for the card billing postal code.
     */
    public static final String EXTRA_REQUIRE_POSTAL_CODE = "io.card.payment.requirePostalCode";

    /**
     * Boolean extra. Optional. Defaults to <code>false</code>. If set, the postal code will only collect numeric
     * input. Set this if you know the <a href="https://en.wikipedia.org/wiki/Postal_code">expected country's
     * postal code</a> has only numeric postal codes.
     */
    public static final String EXTRA_RESTRICT_POSTAL_CODE_TO_NUMERIC_ONLY = "io.card.payment.restrictPostalCodeToNumericOnly";

    /**
     * Boolean extra. Optional. Defaults to <code>false</code>. If set, the user will be prompted
     * for the cardholder name.
     */
    public static final String EXTRA_REQUIRE_CARDHOLDER_NAME = "io.card.payment.requireCardholderName";

    /**
     * String extra. Optional. The preferred language for all strings appearing in the user
     * interface. If not set, or if set to null, defaults to the device's current language setting.
     * <br><br>
     * Can be specified as a language code ("en", "fr", "zh-Hans", etc.) or as a locale ("en_AU",
     * "fr_FR", "zh-Hant_TW", etc.).
     * <br><br>
     * If the library does not contain localized strings for a specified locale, then will fall back
     * to the language. E.g., "es_CO" -&gt; "es".
     * <br><br>
     * If the library does not contain localized strings for a specified language, then will fall
     * back to American English.
     * <br><br>
     * If you specify only a language code, and that code matches the device's currently preferred
     * language, then the library will attempt to use the device's current region as well. E.g.,
     * specifying "en" on a device set to "English" and "United Kingdom" will result in "en_GB".
     * <br><br>
     * These localizations are currently included:
     * <br><br>
     * ar, da, de, en, en_AU, en_GB, es, es_MX, fr, he, is, it, ja, ko, ms, nb, nl, pl, pt, pt_BR, ru,
     * sv, th, tr, zh-Hans, zh-Hant, zh-Hant_TW.
     */
    public static final String EXTRA_LANGUAGE_OR_LOCALE = "io.card.payment.languageOrLocale";

    /**
     * Integer extra. Optional. Defaults to {@link Color#GREEN}. Changes the color of the guide overlay on the
     * camera.
     */
    public static final String EXTRA_GUIDE_COLOR = "io.card.payment.guideColor";

    /**
     * Float extra. Optional. Defaults to 12dp. Changes the corner radius of the guide overlay on the
     * camera.
     */
    public static final String EXTRA_GUIDE_CORNER_RADIUS = "io.card.payment.cornerRadius";

    /**
     * Float extra. Optional. Defaults to 5dp. Changes the corner radius of the guide overlay on the
     * camera.
     */
    public static final String EXTRA_GUIDE_BORDER_THICKNESS = "io.card.payment.borderThickness";

    /**
     * Boolean extra. Optional. Defaults to false. Activated and deactivated fullscreen mode after open and close fragment.
     */
    public static final String EXTRA_FULLSCREEN_MODE = "io.card.payment.fullscreenMode";

    /**
     * String extra. Optional. Used to display instructions to the user while they are scanning
     * their card.
     */
    public static final String EXTRA_SCAN_INSTRUCTIONS = "io.card.payment.scanInstructions";

    /**
     * Integer extra. Optional. If this value is provided the view will be inflated and will overlay
     * the camera during the scan process. The integer value must be the id of a valid layout
     * resource.
     */
    public static final String EXTRA_SCAN_OVERLAY_LAYOUT_ID = "io.card.payment.scanOverlayLayoutId";

    private static int lastResult = 0xca8d10; // arbitrary. chosen to be well above
    // Activity.RESULT_FIRST_USER.

    public static final int RESULT_CARD_DETECTED = lastResult++;

    /**
     * result code supplied to {@link Activity#onActivityResult(int, int, Intent)} when the user presses the cancel
     * button.
     */
    public static final int RESULT_SCAN_CANCELED = lastResult++;


    public static final int RESULT_SCAN_NOT_AVAILABLE = lastResult++;

    private static final String TAG = CardIOFragment.class.getSimpleName();

    private static final int DEGREE_DELTA = 15;

    private static final int ORIENTATION_PORTRAIT = 1;
    private static final int ORIENTATION_PORTRAIT_UPSIDE_DOWN = 2;
    private static final int ORIENTATION_LANDSCAPE_RIGHT = 3;
    private static final int ORIENTATION_LANDSCAPE_LEFT = 4;

    private static final int FRAME_ID = 1;

    private static final long[] VIBRATE_PATTERN = {0, 70, 10, 40};

    private static final int TOAST_OFFSET_Y = -75;

    private OverlayView mOverlay;

    // TODO: the preview is accessed by the scanner. Not the best practice.
    Preview mPreview;

    private CreditCard mDetectedCard;
    private Rect mGuideFrame;
    private int mLastDegrees;
    private int mFrameOrientation;
    private LinearLayout customOverlayLayout;

    private FrameLayout mMainLayout;

    private CardScanner mCardScanner;

    private boolean manualEntryFallbackOrForced = false;

    /**
     * Static variable for the decorated card image. This is ugly, but works. Parceling and
     * unparceling card image data to pass to the next {@link android.app.Activity} does not work because the image
     * data
     * is too big and causes a somewhat misleading exception. Compressing the image data yields a
     * reduction to 30% of the original size, but still gives the same exception. An alternative
     * would be to persist the image data in a file. That seems like a pretty horrible idea, as we
     * would be persisting very sensitive data on the device.
     */
    static Bitmap markedCardImage = null;

    // ------------------------------------------------------------------------
    // ACTIVITY LIFECYCLE
    // ------------------------------------------------------------------------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle clientData = getArguments();

        LocalizedStrings.setLanguage(clientData);

        if (!CardScanner.processorSupported()) {
            manualEntryFallbackOrForced = true;
        }
        else {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED) {
                        checkCamera();
                        android23AndAboveHandleCamera();
                    }
                    else {
                        manualEntryFallbackOrForced = true;
                    }
                }
                else {
                    checkCamera();
                    android22AndBelowHandleCamera();
                }
            }
            catch (Exception e) {
                handleGeneralExceptionError(e);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mMainLayout;
    }

    private void android23AndAboveHandleCamera() {
        if (manualEntryFallbackOrForced) {
            scanResult(RESULT_SCAN_NOT_AVAILABLE, mDetectedCard);
        }
        else {
            // Guaranteed to be called in API 23+
            showCameraScannerOverlay();
        }
    }


    private void android22AndBelowHandleCamera() {
        if (manualEntryFallbackOrForced) {
            scanResult(RESULT_SCAN_NOT_AVAILABLE, mDetectedCard);
        }
        else {
            // guaranteed to be called in onCreate on API < 22, so it's ok that we're removing the window feature here
//            requestWindowFeature(Window.FEATURE_NO_TITLE);

            showCameraScannerOverlay();
        }
    }

    private void checkCamera() {
        try {
            if (!Util.hardwareSupported()) {
                StringKey errorKey = StringKey.ERROR_NO_DEVICE_SUPPORT;
                String localizedError = LocalizedStrings.getString(errorKey);
                Log.w(Util.PUBLIC_LOG_TAG, errorKey + ": " + localizedError);
                manualEntryFallbackOrForced = true;
            }
        }
        catch (CameraUnavailableException e) {
            StringKey errorKey = StringKey.ERROR_CAMERA_CONNECT_FAIL;
            String localizedError = LocalizedStrings.getString(errorKey);

            Log.e(Util.PUBLIC_LOG_TAG, errorKey + ": " + localizedError);
            Toast toast = Toast.makeText(getContext(), localizedError, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, TOAST_OFFSET_Y);
            toast.show();
            manualEntryFallbackOrForced = true;
        }
    }

    private void showCameraScannerOverlay() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && isFullScreenMode()) {
            final Activity attachedActivity = getActivity();

            if (attachedActivity != null) {
                View decorView = attachedActivity.getWindow().getDecorView();
                // Hide the status bar.
                int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
                decorView.setSystemUiVisibility(uiOptions);
                // Remember that you should never show the action bar if the
                // status bar is hidden, so hide that too if necessary.
                ActionBar actionBar = getActivity().getActionBar();
                if (null != actionBar) {
                    actionBar.hide();
                }
            }
        }

        try {
            mGuideFrame = new Rect();

            mFrameOrientation = ORIENTATION_PORTRAIT;

            mCardScanner = new CardScanner(this, mFrameOrientation);

            final Bundle arguments = getArguments();
            if (arguments != null) {
                boolean mScanExpiry = arguments.getBoolean(CardIOFragment.EXTRA_REQUIRE_EXPIRY, false)
                        && arguments.getBoolean(CardIOFragment.EXTRA_SCAN_EXPIRY, true);
                int mUnblurDigits = arguments.getInt(CardIOFragment.EXTRA_UNBLUR_DIGITS, -1);

                mCardScanner.setScanExpiry(mScanExpiry);
                mCardScanner.setUnblurDigits(mUnblurDigits);
            }

            mCardScanner.prepareScanner();

            setPreviewLayout();

        }
        catch (Exception e) {
            handleGeneralExceptionError(e);
        }
    }

    private void handleGeneralExceptionError(Exception e) {
        StringKey errorKey = StringKey.ERROR_CAMERA_UNEXPECTED_FAIL;
        String localizedError = LocalizedStrings.getString(errorKey);

        Log.e(Util.PUBLIC_LOG_TAG, "Unknown exception, please post the stack trace as a GitHub issue", e);
        Toast toast = Toast.makeText(getContext(), localizedError, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, TOAST_OFFSET_Y);
        toast.show();
        manualEntryFallbackOrForced = true;
    }

    /**
     * Suspend/resume camera preview as part of the {@link android.app.Activity} life cycle (side note: we reuse the
     * same buffer for preview callbacks to greatly reduce the amount of required GC).
     */
    @Override
    public void onResume() {
        super.onResume();

        if (manualEntryFallbackOrForced) {
            scanResult(RESULT_SCAN_NOT_AVAILABLE, mDetectedCard);
            return;
        }

        Util.logNativeMemoryStats();

        if (isFullScreenMode()) {
            final Activity attachedActivity = getActivity();
            if (attachedActivity != null) {
                attachedActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                attachedActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        if (!restartPreview()) {
            StringKey error = StringKey.ERROR_CAMERA_UNEXPECTED_FAIL;
            showErrorMessage(LocalizedStrings.getString(error));
            scanResult(RESULT_SCAN_NOT_AVAILABLE, mDetectedCard);
        }
        else {
            // Turn flash off
            setFlashOn(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        setFlashOn(false);

        if (mCardScanner != null) {
            mCardScanner.pauseScanning();
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mOverlay = null;

        setFlashOn(false);

        if (mCardScanner != null) {
            mCardScanner.endScanning();
            mCardScanner = null;
        }

        super.onDestroy();
    }

    // ------------------------------------------------------------------------
    // STATIC METHODS
    // ------------------------------------------------------------------------

    /**
     * Determine if the device supports card scanning.
     * <br><br>
     * An ARM7 processor and Android SDK 8 or later are required. Additional checks for specific
     * misbehaving devices may also be added.
     *
     * @return <code>true</code> if camera is supported. <code>false</code> otherwise.
     */
    public static boolean canReadCardWithCamera() {
        try {
            return Util.hardwareSupported();
        }
        catch (CameraUnavailableException e) {
            return false;
        }
        catch (RuntimeException e) {
            Log.w(TAG, "RuntimeException accessing Util.hardwareSupported()");
            return false;
        }
    }

    /**
     * Returns the String version of this SDK.  Please include the return value of this method in any support requests.
     *
     * @return The String version of this SDK
     */
    public static String sdkVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * @deprecated Always returns {@code new Date()}.
     */
    @Deprecated
    public static Date sdkBuildDate() {
        return new Date();
    }

    // end static

    @Override
    public void onFirstFrame() {
        SurfaceView sv = mPreview.getSurfaceView();
        if (mOverlay != null) {
            mOverlay.setCameraPreviewRect(new Rect(sv.getLeft(), sv.getTop(), sv.getRight(), sv
                    .getBottom()));
        }
        mFrameOrientation = ORIENTATION_PORTRAIT;
        setDeviceDegrees(0);

        onEdgeUpdate(new DetectionInfo());
    }

    public void onEdgeUpdate(DetectionInfo dInfo) {
        mOverlay.setDetectionInfo(dInfo);
    }

    public void onCardDetected(Bitmap detectedBitmap, DetectionInfo dInfo) {
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_PATTERN, -1);
        }
        catch (SecurityException e) {
            Log.e(Util.PUBLIC_LOG_TAG,
                    "Could not activate vibration feedback. Please add <uses-permission android:name=\"android.permission.VIBRATE\" /> to your application's manifest.");
        }
        catch (Exception e) {
            Log.w(Util.PUBLIC_LOG_TAG, "Exception while attempting to vibrate: ", e);
        }

        mCardScanner.pauseScanning();

        if (dInfo.predicted()) {
            mDetectedCard = dInfo.creditCard();
            mOverlay.setDetectedCard(mDetectedCard);
        }

        float sf;
        if (mFrameOrientation == ORIENTATION_PORTRAIT
                || mFrameOrientation == ORIENTATION_PORTRAIT_UPSIDE_DOWN) {
            sf = mGuideFrame.right / (float) CardScanner.CREDIT_CARD_TARGET_WIDTH * .95f;
        }
        else {
            sf = mGuideFrame.right / (float) CardScanner.CREDIT_CARD_TARGET_WIDTH * 1.15f;
        }

        Matrix m = new Matrix();
        m.postScale(sf, sf);

        Bitmap scaledCard = Bitmap.createBitmap(detectedBitmap, 0, 0, detectedBitmap.getWidth(),
                detectedBitmap.getHeight(), m, false);
        mOverlay.setBitmap(scaledCard);

        scanResult(RESULT_CARD_DETECTED, mDetectedCard);
    }

    /**
     * Result callback.
     */
    protected void scanResult(int resultCode, @Nullable CreditCard card) {
        markedCardImage = null;
    }

    /**
     * Show an error message using toast.
     */
    private void showErrorMessage(final String msgStr) {
        Log.e(Util.PUBLIC_LOG_TAG, "error display: " + msgStr);
        Toast toast = Toast.makeText(getContext(), msgStr, Toast.LENGTH_LONG);
        toast.show();
    }

    private boolean restartPreview() {
        mDetectedCard = null;
        assert mPreview != null;
        boolean success = mCardScanner.resumeScanning(mPreview.getSurfaceHolder());

        return success;
    }

    private void setDeviceDegrees(int degrees) {
        View sv;

        sv = mPreview.getSurfaceView();

        if (sv == null) {
            return;
        }

        mGuideFrame = mCardScanner.getGuideFrame(sv.getWidth(), sv.getHeight());

        // adjust for surface view y offset
        mGuideFrame.top += sv.getTop();
        mGuideFrame.bottom += sv.getTop();
        mOverlay.setGuideAndRotation(mGuideFrame, degrees);
        mLastDegrees = degrees;
    }

    // Called by OverlayView
    public void toggleFlash() {
        setFlashOn(!mCardScanner.isFlashOn());
    }

    void setFlashOn(boolean b) {
        boolean success = (mPreview != null && mOverlay != null && mCardScanner.setFlashOn(b));
        if (success) {
            mOverlay.setTorchOn(b);
        }
    }

    public void triggerAutoFocus() {
        mCardScanner.triggerAutoFocus(true);
    }

    /**
     * Manually set up the layout for this {@link android.app.Activity}. It may be possible to use the standard xml
     * layout mechanism instead, but to know for sure would require more work
     */
    private void setPreviewLayout() {

        // top level container
        mMainLayout = new FrameLayout(getContext());
        mMainLayout.setBackgroundColor(Color.BLACK);
        mMainLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        FrameLayout previewFrame = new FrameLayout(getContext());
        previewFrame.setId(FRAME_ID);

        mPreview = new Preview(getContext(), null, mCardScanner.mPreviewWidth, mCardScanner.mPreviewHeight);
        mPreview.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, Gravity.TOP));
        previewFrame.addView(mPreview);

        mOverlay = new OverlayView(getContext(), this,null, Util.deviceSupportsTorch(getContext()));
        mOverlay.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        if (getArguments() != null) {

            int color = getArguments().getInt(EXTRA_GUIDE_COLOR, 0);

            if (color != 0) {
                // force 100% opaque guide colors.
                int alphaRemovedColor = color | 0xFF000000;
                mOverlay.setGuideColor(alphaRemovedColor);
            }
            else {
                // default to greeeeeen
                mOverlay.setGuideColor(Color.GREEN);
            }

            float defaultBorderThickness = getResources().getDimension(R.dimen.cio_guide_stroke_width);
            float borderThickness = getArguments().getFloat(EXTRA_GUIDE_BORDER_THICKNESS, defaultBorderThickness);

            mOverlay.setGuideStrokeWidth(borderThickness);

            float defaultCornerRadius = getResources().getDimension(R.dimen.cio_guide_stroke_corner_radius);
            float cornerRadius = getArguments().getFloat(EXTRA_GUIDE_CORNER_RADIUS, defaultCornerRadius);

            mOverlay.setGuideStrokeCornerRadius(cornerRadius);

            String scanInstructions = getArguments().getString(EXTRA_SCAN_INSTRUCTIONS);
            if (scanInstructions != null) {
                mOverlay.setScanInstructions(scanInstructions);
            }
        }

        previewFrame.addView(mOverlay);

        RelativeLayout.LayoutParams previewParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        previewParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        previewParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        mMainLayout.addView(previewFrame, previewParams);

        if (getArguments() != null) {
            if (customOverlayLayout != null) {
                mMainLayout.removeView(customOverlayLayout);
                customOverlayLayout = null;
            }

            int resourceId = getArguments().getInt(EXTRA_SCAN_OVERLAY_LAYOUT_ID, -1);
            if (resourceId != -1) {
                customOverlayLayout = new LinearLayout(getContext());
                customOverlayLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));

                LayoutInflater inflater = LayoutInflater.from(getContext());

                inflater.inflate(resourceId, customOverlayLayout);
                mMainLayout.addView(customOverlayLayout);
            }
        }
    }

    private boolean isFullScreenMode() {
        final Bundle arguments = getArguments();

        boolean result = false;

        if (arguments != null) {
            result = arguments.getBoolean(EXTRA_FULLSCREEN_MODE, false);
        }

        return result;
    }
}

