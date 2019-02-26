package com.lxj.xpopup.widget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.lxj.xpopup.animator.ShadowBgAnimator;
import com.lxj.xpopup.enums.LayoutStatus;

/**
 * Description: 根据手势拖拽子View的layout，这种类型的弹窗比较特殊，不需要额外的动画器，因为
 * 动画是根据手势滑动而发生的
 * Create by dance, at 2018/12/20
 */
public class PopupDrawerLayout extends FrameLayout {

    public enum Position {
        Left, Right
    }

    LayoutStatus status = null;
    ViewDragHelper dragHelper;
    View child;
    Position position = Position.Left;
    ShadowBgAnimator bgAnimator = new ShadowBgAnimator();

    public PopupDrawerLayout(Context context) {
        this(context, null);
    }

    public PopupDrawerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PopupDrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        dragHelper = ViewDragHelper.create(this, callback);
    }

    public void setDrawerPosition(Position position) {
        this.position = position;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        child = getChildAt(0);
    }

    boolean hasLayout = false;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!hasLayout) {
            if (position == Position.Left) {
                child.layout(-child.getMeasuredWidth(), 0, 0, getMeasuredHeight());
            } else {
                child.layout(getMeasuredWidth(), 0, getMeasuredWidth() + child.getMeasuredWidth(), getMeasuredHeight());
            }
            hasLayout = true;
        } else {
            child.layout(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        }
    }

    boolean isIntercept = false;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        isIntercept = dragHelper.shouldInterceptTouchEvent(ev);
        return isIntercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        dragHelper.processTouchEvent(event);
        return super.onTouchEvent(event);
    }

    ViewDragHelper.Callback callback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(@NonNull View view, int i) {
            return !dragHelper.continueSettling(true);
        }

        @Override
        public int getViewHorizontalDragRange(@NonNull View child) {
            return 1;
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            if (position == Position.Left) {
                if (left < -child.getMeasuredWidth()) left = -child.getMeasuredWidth();
                if (left > 0) left = 0;
            } else {
                if (left < (getMeasuredWidth() - child.getMeasuredWidth()))
                    left = (getMeasuredWidth() - child.getMeasuredWidth());
                if (left > getMeasuredWidth()) left = getMeasuredWidth();
            }
            return left;
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            float fraction = 0f;
            if (position == Position.Left) {
                // fraction = (now - start)*1f / (end - start)
                fraction = (left + child.getMeasuredWidth()) * 1f / child.getMeasuredWidth();
                if (left == -child.getMeasuredWidth() && listener != null && status != LayoutStatus.Close) {
                    status = LayoutStatus.Close;
                    listener.onClose();
                }
            } else {
                fraction = (left - getMeasuredWidth()) * 1f / -child.getMeasuredWidth();
                if (left == getMeasuredWidth() && listener != null)
                    listener.onClose();
            }
            setBackgroundColor(bgAnimator.calculateBgColor(fraction));
            if (listener != null) {
                listener.onDismissing(fraction);
                if (fraction == 1f && status != LayoutStatus.Open) {
                    status = LayoutStatus.Open;
                    listener.onOpen();
                }
            }
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);
            int centerLeft = 0;
            int finalLeft = 0;
            if (position == Position.Left) {
                if (xvel < -1000) {
                    finalLeft = -child.getMeasuredWidth();
                } else {
                    centerLeft = -child.getMeasuredWidth() / 2;
                    finalLeft = child.getLeft() < centerLeft ? -child.getMeasuredWidth() : 0;
                }
            } else {
                if (xvel > 1000) {
                    finalLeft = getMeasuredWidth();
                } else {
                    centerLeft = getMeasuredWidth() - child.getMeasuredWidth() / 2;
                    finalLeft = releasedChild.getLeft() < centerLeft ? getMeasuredWidth() - child.getMeasuredWidth() : getMeasuredWidth();
                }
            }
            dragHelper.smoothSlideViewTo(releasedChild, finalLeft, releasedChild.getTop());
            ViewCompat.postInvalidateOnAnimation(PopupDrawerLayout.this);
        }
    };

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        status = null;
    }

    /**
     * 打开Drawer
     */
    public void open() {
        post(new Runnable() {
            @Override
            public void run() {
                dragHelper.smoothSlideViewTo(child, position == Position.Left ? 0 : (getMeasuredWidth() - child.getMeasuredWidth()), getTop());
                ViewCompat.postInvalidateOnAnimation(PopupDrawerLayout.this);
            }
        });
    }

    /**
     * 关闭Drawer
     */
    public void close() {
        post(new Runnable() {
            @Override
            public void run() {
                dragHelper.smoothSlideViewTo(child, position == Position.Left ? -child.getMeasuredWidth() : getMeasuredWidth(), getTop());
                ViewCompat.postInvalidateOnAnimation(PopupDrawerLayout.this);
            }
        });
    }

    private OnCloseListener listener;

    public void setOnCloseListener(OnCloseListener listener) {
        this.listener = listener;
    }

    public interface OnCloseListener {
        void onClose();

        void onOpen();

        /**
         * 关闭过程中执行
         *
         * @param fraction 关闭的百分比
         */
        void onDismissing(float fraction);
    }
}
