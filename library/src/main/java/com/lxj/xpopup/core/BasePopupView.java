package com.lxj.xpopup.core;

import android.content.Context;
import android.graphics.Rect;
import android.opengl.ETC1;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.lxj.xpopup.animator.PopupAnimator;
import com.lxj.xpopup.animator.ScaleAlphaAnimator;
import com.lxj.xpopup.animator.ScrollScaleAnimator;
import com.lxj.xpopup.animator.ShadowBgAnimator;
import com.lxj.xpopup.animator.TranslateAlphaAnimator;
import com.lxj.xpopup.animator.TranslateAnimator;
import com.lxj.xpopup.enums.PopupStatus;
import com.lxj.xpopup.interfaces.PopupInterface;
import com.lxj.xpopup.util.KeyboardUtils;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.ArrayList;

import static com.lxj.xpopup.enums.PopupAnimation.ScaleAlphaFromCenter;
import static com.lxj.xpopup.enums.PopupAnimation.ScrollAlphaFromLeftTop;
import static com.lxj.xpopup.enums.PopupAnimation.TranslateFromBottom;

/**
 * Description: 弹窗基类
 * Create by lxj, at 2018/12/7
 */
public abstract class BasePopupView extends FrameLayout implements PopupInterface {
    public PopupInfo popupInfo;
    protected PopupAnimator popupContentAnimator;
    protected ShadowBgAnimator shadowBgAnimator;
    private int touchSlop;
    public PopupStatus popupStatus = PopupStatus.Dismiss;

    public BasePopupView(@NonNull Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        shadowBgAnimator = new ShadowBgAnimator(this);

        //  添加Popup窗体内容View
        View contentView = LayoutInflater.from(context).inflate(getPopupLayoutId(), this, false);
        // 事先隐藏，等测量完毕恢复。避免View影子跳动现象
        contentView.setAlpha(0);
        addView(contentView);
    }

    public BasePopupView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BasePopupView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void focusAndProcessBackPress(){
        // 处理返回按键
        setFocusableInTouchMode(true);
        requestFocus();
        // 此处焦点可能被内容的EditText抢走，也需要给EditText也设置返回按下监听
        setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (popupInfo.isDismissOnBackPressed)
                        dismiss();
                    return true;
                }
                return false;
            }
        });

        //let all EditText can process back pressed.
        ArrayList<EditText> list = new ArrayList<>();
        XPopupUtils.findAllEditText(list, (ViewGroup) getPopupContentView());
        for (int i = 0; i < list.size(); i++) {
            final View et = list.get(i);
            if(i==0){
                et.setFocusable(true);
                et.setFocusableInTouchMode(true);
                et.requestFocus();
                if(popupInfo.autoOpenSoftInput){
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            KeyboardUtils.showSoftInput(et);
                        }
                    }, 405);
                }
            }
            et.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (popupInfo.isDismissOnBackPressed)
                            dismiss();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    Runnable afterShow,afterDismiss;

    /**
     * 执行初始化
     *
     * @param afterShow
     */
    public void init(Runnable afterShow, Runnable afterDismiss) {
        if (popupStatus != PopupStatus.Dismiss) return;
        popupStatus = PopupStatus.Showing;
        this.afterShow = afterShow;
        this.afterDismiss = afterDismiss;
        //1. 初始化Popup
        initPopupContent();
        post(new Runnable() {
            @Override
            public void run() {
                // 如果有导航栏，则不能覆盖导航栏，
                if (XPopupUtils.isNavBarVisible(getContext())) {
                    setPadding(0, 0, 0, XPopupUtils.getNavBarHeight());
                }
                getPopupContentView().setAlpha(1f);

                //2. 收集动画执行器
                // 优先使用自定义的动画器
                if (popupInfo.customAnimator != null) {
                    popupContentAnimator = popupInfo.customAnimator;
                    popupContentAnimator.targetView = getPopupContentView();
                } else {
                    // 根据PopupInfo的popupAnimation字段来生成对应的动画执行器，如果popupAnimation字段为null，则返回null
                    popupContentAnimator = genAnimatorByPopupType();
                    if (popupContentAnimator == null) {
                        // 使用默认的animator
                        popupContentAnimator = getPopupAnimator();
                    }
                }

                //3. 初始化动画执行器
                shadowBgAnimator.initAnimator();
                if (popupContentAnimator != null) {
                    popupContentAnimator.initAnimator();
                    shadowBgAnimator.animateDuration = popupContentAnimator.animateDuration;
                }


                //4. 执行动画
                doShowAnimation();
                focusAndProcessBackPress();

                // call xpopup init.
                doAfterShow();
            }
        });

    }

    protected void doAfterShow(){
        postDelayed(new Runnable() {
            @Override
            public void run() {
                popupStatus = PopupStatus.Show;
                onShow();
                afterShow.run();
            }
        }, getAnimationDuration());
    }

    /**
     * 根据PopupInfo的popupAnimation字段来生成对应的内置的动画执行器
     */
    protected PopupAnimator genAnimatorByPopupType() {
        if (popupInfo == null || popupInfo.popupAnimation == null) return null;
        switch (popupInfo.popupAnimation) {
            case ScaleAlphaFromCenter:
            case ScaleAlphaFromLeftTop:
            case ScaleAlphaFromRightTop:
            case ScaleAlphaFromLeftBottom:
            case ScaleAlphaFromRightBottom:
                return new ScaleAlphaAnimator(getPopupContentView(), popupInfo.popupAnimation);

            case TranslateAlphaFromLeft:
            case TranslateAlphaFromTop:
            case TranslateAlphaFromRight:
            case TranslateAlphaFromBottom:
                return new TranslateAlphaAnimator(getPopupContentView(), popupInfo.popupAnimation);

            case TranslateFromLeft:
            case TranslateFromTop:
            case TranslateFromRight:
            case TranslateFromBottom:
                return new TranslateAnimator(getPopupContentView(), popupInfo.popupAnimation);

            case ScrollAlphaFromLeft:
            case ScrollAlphaFromLeftTop:
            case ScrollAlphaFromTop:
            case ScrollAlphaFromRightTop:
            case ScrollAlphaFromRight:
            case ScrollAlphaFromRightBottom:
            case ScrollAlphaFromBottom:
            case ScrollAlphaFromLeftBottom:
                return new ScrollScaleAnimator(getPopupContentView(), popupInfo.popupAnimation);
        }
        return null;
    }

    protected abstract int getPopupLayoutId();

    /**
     * 如果你自己继承BasePopupView来做，这个不用实现
     * @return
     */
    protected int getImplLayoutId() {
        return -1;
    }

    /**
     * 获取PopupAnimator，每种类型的PopupView可以选择返回一个动画器，
     * 父类默认实现是根据popupType字段返回一个默认最佳的动画器
     *
     * @return
     */
    protected PopupAnimator getPopupAnimator() {
        if (popupInfo == null || popupInfo.popupType == null) return null;
        switch (popupInfo.popupType) {
            case Center:
                return new ScaleAlphaAnimator(getPopupContentView(), ScaleAlphaFromCenter);
            case Bottom:
                return new TranslateAnimator(getPopupContentView(), TranslateFromBottom);
            case AttachView:
                return new ScrollScaleAnimator(getPopupContentView(), ScrollAlphaFromLeftTop);
        }
        return null;
    }

    // 执行初始化Popup的content
    protected void initPopupContent() {}

    /**
     * 执行显示动画：动画由2部分组成，一个是背景渐变动画，一个是Content的动画；
     * 背景动画由父类实现，Content由子类实现
     */
    @Override
    public void doShowAnimation() {
        if (popupInfo.hasShadowBg) {
            shadowBgAnimator.animateShow();
        }
        if (popupContentAnimator != null)
            popupContentAnimator.animateShow();
    }

    /**
     * 执行消失动画：动画由2部分组成，一个是背景渐变动画，一个是Content的动画；
     * 背景动画由父类实现，Content由子类实现
     */
    @Override
    public void doDismissAnimation() {
        if (popupInfo.hasShadowBg) {
            shadowBgAnimator.animateDismiss();
        }
        if (popupContentAnimator != null)
            popupContentAnimator.animateDismiss();
    }

    /**
     * 获取内容View，本质上PopupView显示的内容都在这个View内部。
     * 而且我们对PopupView执行的动画，也是对它执行的动画
     *
     * @return
     */
    @Override
    public View getPopupContentView() {
        return getChildAt(0);
    }

    public View getPopupImplView() {
        return ((ViewGroup) getPopupContentView()).getChildAt(0);
    }

    @Override
    public int getAnimationDuration() {
        return popupContentAnimator == null ? 400 : popupContentAnimator.animateDuration;
    }

    protected int getMaxWidth() {
        return 0;
    }

    protected int getMaxHeight() {
        return popupInfo.maxHeight;
    }

    /**
     * 消失
     */
    public void dismiss() {
        if (popupStatus == PopupStatus.Dismissing) return;
        popupStatus = PopupStatus.Dismissing;
        doDismissAnimation();
        doAfterDismiss();
    }

    protected void doAfterDismiss(){
        KeyboardUtils.hideSoftInput(this);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                popupStatus = PopupStatus.Dismiss;
                onDismiss();
                if(afterDismiss!=null)afterDismiss.run();
            }
        }, getAnimationDuration());
    }

    /**
     * 消失动画执行完毕后执行
     */
    protected void onDismiss(){}
    /**
     * 显示动画执行完毕后执行
     */
    protected void onShow(){}

    private float x, y;
    private long downTime;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 如果自己接触到了点击，并且不在PopupContentView范围内点击，则进行判断是否是点击事件
        // 如果是，则dismiss
        Rect rect = new Rect();
        getPopupContentView().getGlobalVisibleRect(rect);
        if (!XPopupUtils.isInRect(event.getX(), event.getY(), rect)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = event.getX();
                    y = event.getY();
                    downTime = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - x;
                    float dy = event.getY() - y;
                    float distance = (float) Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
                    if (distance < touchSlop && (System.currentTimeMillis() - downTime) < 350) {
                        if (popupInfo.isDismissOnTouchOutside)
                            dismiss();
                    }
                    x = 0;
                    y = 0;
                    downTime = 0;
                    break;
            }
        }
        return true;
    }


}
