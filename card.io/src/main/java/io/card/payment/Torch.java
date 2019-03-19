package io.card.payment;

/* Torch.java
 * See the file "LICENSE.md" for the full license governing this code.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;

class Torch {
    private static final String TAG = Torch.class.getSimpleName();

    private boolean mOn;
    private Drawable mOnDrawable;
    private Drawable mOffDrawable;

    public Torch(Context context, @DrawableRes int torchOnResId, @DrawableRes int torchOffResId) {
        mOn = false;

        mOnDrawable = ContextCompat.getDrawable(context, torchOnResId);
        mOffDrawable = ContextCompat.getDrawable(context, torchOffResId);

        int drawableSize = (int) context.getResources().getDimension(R.dimen.cio_torch_size);
        Rect bounds = new Rect(0, 0, drawableSize, drawableSize);

        mOnDrawable.setBounds(bounds);
        mOffDrawable.setBounds(bounds);
    }

    public void draw(Canvas canvas) {
        if (mOn) {
            mOnDrawable.draw(canvas);
        }
        else {
            mOffDrawable.draw(canvas);
        }
    }

    public void setOn(boolean on) {
        mOn = on;
    }
}
