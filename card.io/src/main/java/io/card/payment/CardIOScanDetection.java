package io.card.payment;

import android.content.Context;
import android.graphics.Bitmap;

public interface CardIOScanDetection {

    Context getContext();

    void onFirstFrame();

    void onCardDetected(Bitmap detectedBitmap, DetectionInfo dInfo);

    void onEdgeUpdate(DetectionInfo dInfo);
}
