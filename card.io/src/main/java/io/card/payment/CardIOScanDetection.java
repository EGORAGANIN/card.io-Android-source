package io.card.payment;

import android.app.Activity;
import android.graphics.Bitmap;

public interface CardIOScanDetection {

    Activity getActivity();

    void onFirstFrame();

    void onCardDetected(Bitmap detectedBitmap, DetectionInfo dInfo);

    void onEdgeUpdate(DetectionInfo dInfo);
}
