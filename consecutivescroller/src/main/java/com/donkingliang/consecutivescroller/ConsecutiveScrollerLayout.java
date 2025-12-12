package com.donkingliang.consecutivescroller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild2;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent2;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ScrollingView;
import androidx.core.view.ViewCompat;
import androidx.core.widget.EdgeEffectCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @Author donkingliang QQ:1043214265 github:https://github.com/donkingliang
 * @Description ConsecutiveScrollerLayout
 * A layout that supports consecutive scrolling of multiple scrollable views.
 * 支持多个滑动布局的连续滑动。
 * @Date 2020/3/13
 * <p>
 * 修改：
 * 1. 新增越界滑动功能 (Over-scroll drag)
 * 2. 优化Fling逻辑 (Optimized Fling)
 * 3. 规范命名 (Standardized Naming)
 */
public class ConsecutiveScrollerLayout extends ViewGroup implements ScrollingView, NestedScrollingParent2, NestedScrollingChild2 {

    private final String TAG = this.getClass().getSimpleName();

    /**
     * 屏幕高度
     */
    protected int mScreenHeightPixels;

    /**
     * 越界拖动阻尼
     */
    protected float mDragRate = .5f;

    /**
     * 是否开启越界滚动模式
     */
    private boolean mOverDragMode;

    /**
     * 底部越界最大距离
     * 单位：像素
     */
    private int mOverDragMaxDistanceOfBottom;

    /**
     * 顶部越界最大距离
     * 单位：像素
     */
    private int mOverDragMaxDistanceOfTop;

    /**
     * 越界回弹动画时长
     */
    protected int mReboundDuration = 300;
    protected int mCurrentVelocity;
    protected Interpolator mReboundInterpolator;
    protected ValueAnimator mReboundAnimator;
    protected Runnable mAnimationRunnable;
    protected Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * 记录布局垂直的偏移量，它是包括了自己的偏移量(mScrollY)和所有子View的偏移量的总和，
     * 这个值不是真实的布局滑动偏移量，只是用于在滑动是记录和计算每次的滑动距离。
     * Total vertical scroll offset, including mScrollY and all children's scroll offsets.
     */
    private int mSecondScrollY;

    /**
     * 联动容器可滚动的范围
     * Scrollable range of the container.
     */
    int mScrollRange;

    /**
     * 联动容器滚动定位子view
     */
    private OverScroller mScroller;

    /**
     * VelocityTracker
     */
    private VelocityTracker mVelocityTracker;
    private VelocityTracker mAdjustVelocityTracker;
    private int mAdjustYVelocity;

    /**
     * MaximumVelocity
     */
    private int mMaximumVelocity;

    /**
     * MinimumVelocity
     */
    private int mMinimumVelocity;

    private int mTouchSlop;

    /**
     * 手指触摸屏幕时的触摸点
     */
    private int mTouchY;
    private int mEventX;
    private int mEventY;

    private HashMap<Integer, Float> mFixedYMap = new HashMap<>();

    /**
     * 记录手指按下时的位置
     */
    private final int[] mDownLocation = new int[2];

    /**
     * 是否处于触摸状态
     */
    private boolean mTouching = false;

    private static final int SCROLL_NONE = 0;
    private static final int SCROLL_VERTICAL = 1;
    private static final int SCROLL_HORIZONTAL = 2;
    private int mScrollOrientation = SCROLL_NONE;

    /**
     * 滑动监听
     */
    protected OnScrollChangeListener mOnScrollChangeListener;

    private int mActivePointerId = -1;

    private NestedScrollingParentHelper mParentHelper;
    private NestedScrollingChildHelper mChildHelper;
    private final int[] mScrollOffset = new int[2];
    private final int[] mScrollConsumed = new int[2];

    private View mScrollToTopView;
    private int mAdjust;

    /**
     * 滑动到指定view，目标view的index
     */
    private int mScrollToIndex = -1;

    /**
     * 滑动到指定view，平滑滑动时，每次滑动的距离
     */
    private int mSmoothScrollOffset = 0;

    private int mScrollToIndexWithOffset = 0;

    // 滑动到指定view时，为了防止滑动时间长或者死循环，限制最大循环次数
    private int mCycleCount = 0;
    private static final int MAX_CYCLE_COUNT = 1000;

    /**
     * 上边界阴影
     */
    private EdgeEffect mEdgeGlowTop;
    /**
     * 下边界阴影
     */
    private EdgeEffect mEdgeGlowBottom;

    /**
     * fling时，保存最后的滑动位置，在下一帧时通过对比新的滑动位置，判断滑动的方向。
     */
    private int mLastScrollerY;

    /**
     * 吸顶view是否常驻，不被推出屏幕
     */
    private boolean mIsPermanent;

    /**
     * 禁用子view的水平滑动，如果ConsecutiveScrollerLayout下没有水平滑动的下级view，应该把它设置为true
     * 为true时，将不会分发滑动事件给子view，而是由ConsecutiveScrollerLayout处理，可以优化ConsecutiveScrollerLayout的滑动
     */
    private boolean mDisableChildHorizontalScroll;

    /**
     * 自动调整底部view的高度，使它不被吸顶布局覆盖。
     * 为true时，底部view的最大高度不大于 (父布局高度 - (当前吸顶view高度总高度 + mAdjustHeightOffset))
     */
    private boolean mAutoAdjustHeightAtBottomView;

    /**
     * 自动调整底部view的高度时，额外的偏移量
     * 底部view需要调整高度 = 当前吸顶view高度总高度 + mAdjustHeightOffset
     */
    private int mAdjustHeightOffset = 0;

    /**
     * 吸顶view到顶部的偏移量
     */
    private int mStickyOffset = 0;

    /**
     * 保存当前吸顶的view(普通吸顶模式中，正在吸顶的view只有一个)
     */
    private View mCurrentStickyView;

    /**
     * 保存当前吸顶的view(常驻吸顶模式中，正在吸顶的view可能有多个)
     */
    private final List<View> mCurrentStickyViews = new ArrayList<>();
    // 临时保存吸顶的view，用于判断吸顶view是否改变了
    private final List<View> mTempStickyViews = new ArrayList<>();

    private int mOldScrollY = 0;

    private final List<View> mViews = new ArrayList<>();
    private int mNestedYOffset = 0;

    /**
     * 普通吸顶模式,监听吸顶变化
     */
    private OnStickyChangeListener mOnStickyChangeListener;

    /**
     * 常驻吸顶模式,监听吸顶变化
     */
    private OnPermanentStickyChangeListener mOnPermanentStickyChangeListener;

    /**
     * The RecyclerView is not currently scrolling.
     *
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * The RecyclerView is currently being dragged by outside input such as user touch input.
     *
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * The RecyclerView is currently animating to a final position while not under
     * outside control.
     *
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_SETTLING = 2;

    private int mScrollState = SCROLL_STATE_IDLE;

    // 这是RecyclerView的代码，让ConsecutiveScrollerLayout的fling效果更接近于RecyclerView。
    static final Interpolator sQuinticInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    /**
     * 是否触摸吸顶view并且不能触发布局滑动
     * 注意：它不仅会判断自己的吸顶view，也会判断下级ConsecutiveScrollerLayout的吸顶view
     */
    private boolean mIsTouchNotTriggerScrollStick = false;

    /**
     * 判断手指触摸的view是否需要拦截事件
     */
    private boolean mIsIntercept = false;

    /**
     * 在快速滑动的过程中，触摸停止滑动
     */
    private boolean mIsBrake = false;

    /**
     * 当前是否不允许拦截滑动事件
     */
    private boolean mIsDisallowInterceptTouchEvent = false;

    /**
     * 自动吸附的锚点列表
     * Snap points for auto-snapping during fling.
     */
    private List<Integer> mSnapPoints = new ArrayList<>();

    public ConsecutiveScrollerLayout(Context context) {
        this(context, null);
    }

    public ConsecutiveScrollerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConsecutiveScrollerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(attrs, R.styleable.ConsecutiveScrollerLayout);
            //是否开启越界滚动模式
            if (a.hasValue(R.styleable.ConsecutiveScrollerLayout_overDragMode)) {
                mOverDragMode = a.getBoolean(
                        R.styleable.ConsecutiveScrollerLayout_overDragMode, false);
                if (mOverDragMode) {
                    //默认最大越界拖动距离为 180dp
                    int defaultDistance = Util.dp2px(180);
                    mOverDragMaxDistanceOfTop = a.getDimensionPixelOffset(
                            R.styleable.ConsecutiveScrollerLayout_overDragMaxDistanceOfTop, defaultDistance);
                    mOverDragMaxDistanceOfBottom = a.getDimensionPixelOffset(
                            R.styleable.ConsecutiveScrollerLayout_overDragMaxDistanceOfBottom, defaultDistance);
                }
            }
            mIsPermanent = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_isPermanent, false);
            mDisableChildHorizontalScroll = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_disableChildHorizontalScroll, false);
            mStickyOffset = a.getDimensionPixelOffset(R.styleable.ConsecutiveScrollerLayout_stickyOffset, 0);
            mAutoAdjustHeightAtBottomView = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_autoAdjustHeightAtBottomView, false);
            mAdjustHeightOffset = a.getDimensionPixelOffset(R.styleable.ConsecutiveScrollerLayout_adjustHeightOffset, 0);
        } finally {
            if (a != null) {
                a.recycle();
            }
        }
        mScroller = new OverScroller(getContext(), sQuinticInterpolator);
        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mMaximumVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        mMinimumVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        mTouchSlop = ViewConfiguration.getTouchSlop();
        // 确保联动容器调用onDraw()方法
        setWillNotDraw(false);
        // enable vertical scrollbar
        setVerticalScrollBarEnabled(true);

        mParentHelper = new NestedScrollingParentHelper(this);
        mChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
        setChildrenDrawingOrderEnabled(true);

        setMotionEventSplittingEnabled(false);

        //越界拖动回弹动画差值器
        mReboundInterpolator = new OverScrollInterpolator(OverScrollInterpolator.INTERPOLATOR_VISCOUS_FLUID);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {

        if (params instanceof LayoutParams) {
            LayoutParamsUtils.invalidTopAndBottomMargin((LayoutParams) params);
        }

        super.addView(child, index, params);

        // 去掉子View的滚动条。选择在这里做这个操作，而不是在onFinishInflate方法中完成，是为了兼顾用代码add子View的情况
        if (ScrollUtils.isConsecutiveScrollerChild(child)) {
            View scrollChild = ScrollUtils.getScrollChild(child);
            disableChildScroll(scrollChild);
            if (scrollChild instanceof IConsecutiveScroller) {
                List<View> views = ((IConsecutiveScroller) scrollChild).getScrolledViews();
                if (views != null && !views.isEmpty()) {
                    int size = views.size();
                    for (int i = 0; i < size; i++) {
                        disableChildScroll(views.get(i));
                    }
                }
            }
        }

        if (child instanceof ViewGroup) {
            ((ViewGroup) child).setClipToPadding(false);
        }
    }

    /**
     * 禁用子view的一下滑动相关的属性
     *
     * @param child
     */
    private void disableChildScroll(View child) {
        child.setVerticalScrollBarEnabled(false);
        child.setHorizontalScrollBarEnabled(false);
        child.setOverScrollMode(OVER_SCROLL_NEVER);
        ViewCompat.setNestedScrollingEnabled(child, false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        resetScrollToTopView();

        int contentWidth = 0;
        int contentHeight = 0;

        // 测量子view
        List<View> children = getNonGoneChildren();
        int count = children.size();
        for (int i = 0; i < count; i++) {
            View child = children.get(i);

            // 父布局额外占用的高度空间，在测量子view时，子view的最大高度不大于父view的最大可用高度-heightUsed。
            int heightUsed = 0;

            // 测量底部view，并且需要自动调整高度时，计算吸顶部分占用的空间高度，作为测量子view的条件。
            heightUsed = getAdjustHeightForChild(child);

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, heightUsed);
            contentWidth = Math.max(contentWidth, getContentWidth(child));
            contentHeight += child.getMeasuredHeight();
        }

        setMeasuredDimension(measureSize(widthMeasureSpec, contentWidth + getPaddingLeft() + getPaddingRight()),
                measureSize(heightMeasureSpec, contentHeight + getPaddingTop() + getPaddingBottom()));
    }

    /**
     * 返回底部view需要调整的高度
     * 只有mAutoAdjustHeightAtBottomView=true，并且child是底部view时有值，否则返回0
     *
     * @param child
     * @return
     */
    private int getAdjustHeightForChild(View child) {
        if (mAutoAdjustHeightAtBottomView && child == getChildAt(getChildCount() - 1)) {
            return getAdjustHeight();
        }
        return 0;
    }

    /**
     * 返回底部view需要调整的高度
     * 普通吸顶模式：最后的吸顶view高度 + mAdjustHeightOffset
     * 常驻吸顶模式：所有吸顶view高度 + mAdjustHeightOffset
     * 需要过滤下沉吸顶
     *
     * @return
     */
    private int getAdjustHeight() {
        List<View> children = getStickyChildren();

        int adjustHeight = mAdjustHeightOffset;

        int count = children.size();
        if (mIsPermanent) {
            // 常驻吸顶模式
            for (int i = 0; i < count; i++) {
                View child = children.get(i);
                if (!isSink(child)) { // 过滤下沉吸顶View
                    adjustHeight += child.getMeasuredHeight();
                }
            }
        } else {
            // 普通吸顶模式
            for (int i = count - 1; i >= 0; i--) {
                View child = children.get(i);
                if (!isSink(child)) { // 过滤下沉吸顶View
                    adjustHeight += child.getMeasuredHeight();
                    break;
                }
            }
        }

        return adjustHeight;
    }

    private int getContentWidth(View child) {
        int contentWidth = child.getMeasuredWidth();
        MarginLayoutParams params = (LayoutParams) child.getLayoutParams();

        contentWidth += params.leftMargin;
        contentWidth += params.rightMargin;
        return contentWidth;
    }

    private int measureSize(int measureSpec, int size) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            result = size;
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        result = Math.max(result, getSuggestedMinimumWidth());
        result = resolveSizeAndState(result, measureSpec, 0);
        return result;
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        LayoutParamsUtils.invalidTopAndBottomMargin((LayoutParams) child.getLayoutParams());
        super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mScreenHeightPixels = getResources().getDisplayMetrics().heightPixels;
        mScrollRange = 0;
        int childTop = getPaddingTop();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int parentWidth = getMeasuredWidth();

        List<View> children = getNonGoneChildren();
        int count = children.size();
        for (int i = 0; i < count; i++) {
            View child = children.get(i);
            int bottom = childTop + child.getMeasuredHeight();
            int left = getChildLeft(child, parentWidth, paddingLeft, paddingRight);
            child.layout(left, childTop, left + child.getMeasuredWidth(), bottom);
            childTop = bottom;
            // 联动容器可滚动最大距离
            mScrollRange += child.getHeight();
        }
        // 联动容器可滚动range
        mScrollRange -= getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        // mScrollRange不能少于0
        if (mScrollRange < 0) {
            mScrollRange = 0;
        }

        // 布局发生变化，检测滑动位置
        checkLayoutChange(changed, false);

        sortViews();
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    private void sortViews() {
        List<View> list = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (!isStickyView(child) || isSink(child)) {
                list.add(child);
            }
        }

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (isStickyView(child) && !isSink(child)) {
                list.add(child);
            }
        }
        mViews.clear();
        mViews.addAll(list);
    }

    /**
     * 获取子view的left
     */
    private int getChildLeft(View child, int parentWidth, int paddingLeft, int paddingRight) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        switch (lp.align) {

            case RIGHT:
                return parentWidth - child.getMeasuredWidth() - paddingRight - lp.rightMargin;

            case CENTER:
                return paddingLeft + lp.leftMargin + ((parentWidth - child.getMeasuredWidth()
                        - paddingLeft - lp.leftMargin - paddingRight - lp.rightMargin) / 2);
            case LEFT:
            default:
                return paddingLeft + lp.leftMargin;
        }
    }

    private void resetScrollToTopView() {
        mScrollToTopView = findFirstVisibleView();
        if (mScrollToTopView != null) {
            mAdjust = getScrollY() - mScrollToTopView.getTop();
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        mIsDisallowInterceptTouchEvent = disallowIntercept;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int actionIndex = ev.getActionIndex();

        if (mScrollOrientation == SCROLL_HORIZONTAL) {
            // 如果是横向滑动，设置ev的y坐标始终为开始的坐标，避免子view自己消费了垂直滑动事件。
            if (mActivePointerId != -1 && mFixedYMap.get(mActivePointerId) != null) {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0 || pointerIndex >= ev.getPointerCount()) {
                    return false;
                }

                ev.offsetLocation(0, mFixedYMap.get(mActivePointerId) - ev.getY(pointerIndex));
            }
        }

        MotionEvent vtev = MotionEvent.obtain(ev);

        if (vtev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0;
        }
        vtev.offsetLocation(0, mNestedYOffset);

        /**
         * 打断动画
         */
        interceptAnimatorByAction(vtev.getAction());

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentVelocity = 0;
                mIsBrake = mScrollState == SCROLL_STATE_SETTLING;

                // 停止滑动
                stopScroll();
                mTouching = true;
                checkTargetsScroll(false, false);
                mScrollOrientation = SCROLL_NONE;
                mActivePointerId = ev.getPointerId(actionIndex);
                mFixedYMap.put(mActivePointerId, ev.getY(actionIndex));
                mEventY = (int) ev.getY(actionIndex);
                mEventX = (int) ev.getX(actionIndex);

                initOrResetAdjustVelocityTracker();
                mAdjustVelocityTracker.addMovement(vtev);

                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);

                mDownLocation[0] = ScrollUtils.getRawX(this, ev, actionIndex);
                mDownLocation[1] = ScrollUtils.getRawY(this, ev, actionIndex);
                mIsIntercept = isIntercept(mDownLocation[0], mDownLocation[1]);
                mIsTouchNotTriggerScrollStick = ScrollUtils.isTouchNotTriggerScrollStick(this, mDownLocation[0], mDownLocation[1]);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mActivePointerId = ev.getPointerId(actionIndex);
                mFixedYMap.put(mActivePointerId, ev.getY(actionIndex));
                mEventY = (int) ev.getY(actionIndex);
                mEventX = (int) ev.getX(actionIndex);
                if (!mIsDisallowInterceptTouchEvent) {
                    // 改变滑动的手指，如果能够拦截事件, 重新询问事件拦截
                    requestDisallowInterceptTouchEvent(false);
                }
                mDownLocation[0] = ScrollUtils.getRawX(this, ev, actionIndex);
                mDownLocation[1] = ScrollUtils.getRawY(this, ev, actionIndex);
                mIsIntercept = isIntercept(mDownLocation[0], mDownLocation[1]);
                mIsTouchNotTriggerScrollStick = ScrollUtils.isTouchNotTriggerScrollStick(this, mDownLocation[0], mDownLocation[1]);

                initAdjustVelocityTrackerIfNotExists();
                mAdjustVelocityTracker.addMovement(vtev);

                break;
            case MotionEvent.ACTION_MOVE:

                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0 || pointerIndex >= ev.getPointerCount()) {
                    return false;
                }

                initAdjustVelocityTrackerIfNotExists();
                mAdjustVelocityTracker.addMovement(vtev);

                int offsetY = (int) ev.getY(pointerIndex) - mEventY;
                int offsetX = (int) ev.getX(pointerIndex) - mEventX;
                if (mScrollOrientation == SCROLL_NONE
                        && (mIsIntercept || isIntercept(ev))) {
                    if (mDisableChildHorizontalScroll) {
                        if (Math.abs(offsetY) >= mTouchSlop) {
                            mScrollOrientation = SCROLL_VERTICAL;
                        }
                    } else {
                        if (Math.abs(offsetX) > Math.abs(offsetY)) {
                            if (Math.abs(offsetX) >= mTouchSlop) {
                                mScrollOrientation = SCROLL_HORIZONTAL;
                                // 如果是横向滑动，设置ev的y坐标始终为开始的坐标，避免子view自己消费了垂直滑动事件。
                                if (mActivePointerId != -1 && mFixedYMap.get(mActivePointerId) != null) {
                                    final int pointerIn = ev.findPointerIndex(mActivePointerId);
                                    if (pointerIn >= 0 && pointerIndex < ev.getPointerCount()) {
                                        ev.offsetLocation(0, mFixedYMap.get(mActivePointerId) - ev.getY(pointerIn));
                                    }
                                }
                            }
                        } else {
                            if (Math.abs(offsetY) >= mTouchSlop) {
                                mScrollOrientation = SCROLL_VERTICAL;
                            }
                        }
                    }

                    if (mScrollOrientation == SCROLL_NONE) {
                        return true;
                    }
                }

                mEventY = (int) ev.getY(pointerIndex);
                mEventX = (int) ev.getX(pointerIndex);

                break;
            case MotionEvent.ACTION_POINTER_UP:
                mFixedYMap.remove(ev.getPointerId(actionIndex));
                if (mActivePointerId == ev.getPointerId(actionIndex)) { // 如果松开的是活动手指, 让还停留在屏幕上的最后一根手指作为活动手指
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    // pointerIndex都是像0, 1, 2这样连续的
                    final int newPointerIndex = actionIndex == 0 ? 1 : 0;
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                    mFixedYMap.put(mActivePointerId, ev.getY(newPointerIndex));
                    mEventY = (int) ev.getY(newPointerIndex);
                    mEventX = (int) ev.getX(newPointerIndex);
                    mDownLocation[0] = ScrollUtils.getRawX(this, ev, newPointerIndex);
                    mDownLocation[1] = ScrollUtils.getRawY(this, ev, newPointerIndex);
                    mIsIntercept = isIntercept(mDownLocation[0], mDownLocation[1]);
                    mIsTouchNotTriggerScrollStick = ScrollUtils.isTouchNotTriggerScrollStick(this, mDownLocation[0], mDownLocation[1]);
                }
                initAdjustVelocityTrackerIfNotExists();
                mAdjustVelocityTracker.addMovement(vtev);

                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mAdjustVelocityTracker != null) {
                    mAdjustVelocityTracker.addMovement(vtev);
                    mAdjustVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int yVelocity = (int) mAdjustVelocityTracker.getYVelocity();
                    mCurrentVelocity = yVelocity;

                    // 记录AdjustVelocity的fling速度
                    mAdjustYVelocity = Math.max(-mMaximumVelocity, Math.min(yVelocity, mMaximumVelocity));
                    recycleAdjustVelocityTracker();
                    int touchX = ScrollUtils.getRawX(this, ev, actionIndex);
                    int touchY = ScrollUtils.getRawY(this, ev, actionIndex);
                    View targetView = getTouchTarget(touchX, touchY);
                    boolean canScrollVerticallyChild = ScrollUtils.canScrollVertically(targetView);
                    boolean canScrollHorizontallyChild = ScrollUtils.isHorizontalScroll(this, touchX, touchY);
                    if (mScrollOrientation != SCROLL_VERTICAL && canScrollVerticallyChild
                            && Math.abs(yVelocity) >= mMinimumVelocity
                            && !canScrollHorizontallyChild) {
                        //如果当前是横向滑动，但是触摸的控件可以垂直滑动，并且产生垂直滑动的fling事件，
                        // 为了不让这个控件垂直fling，把事件设置为MotionEvent.ACTION_CANCEL。
                        ev.setAction(MotionEvent.ACTION_CANCEL);
                    }

                    if (mScrollOrientation != SCROLL_VERTICAL && !ScrollUtils.isConsecutiveScrollParent(this)
                            && isIntercept(ev) && Math.abs(yVelocity) >= mMinimumVelocity) {
                        if (mScrollOrientation == SCROLL_NONE || !canScrollHorizontallyChild) {
                            fling(-mAdjustYVelocity);
                        }
                    }
                }

                mEventY = 0;
                mEventX = 0;
                mTouching = false;
                mDownLocation[0] = 0;
                mDownLocation[1] = 0;
                mIsTouchNotTriggerScrollStick = false;
                mIsIntercept = false;
                springBack();
                break;
        }

        vtev.recycle();

        boolean dispatch = super.dispatchTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mScrollOrientation = SCROLL_NONE;
                mAdjustYVelocity = 0;
                mFixedYMap.clear();
                mActivePointerId = -1;
                if (mScroller.isFinished() && getNestedScrollAxes() == 0) {
                    setScrollState(SCROLL_STATE_IDLE);
                    checkSnap();
                }
                break;
        }

        return dispatch;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                break;

            case MotionEvent.ACTION_MOVE:

                // 需要拦截事件
                if (mScrollOrientation != SCROLL_HORIZONTAL
                        && (mIsIntercept || isIntercept(ev))) {
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                stopNestedScroll(ViewCompat.TYPE_TOUCH);

                if (mIsBrake && mScrollOrientation == SCROLL_NONE) {
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (ScrollUtils.isConsecutiveScrollParent(this) // 如果父级容器设置isConsecutive：true，则自己不消费滑动
                || mIsTouchNotTriggerScrollStick) { // 触摸正在吸顶的view，不消费滑动
            return super.onTouchEvent(ev);
        }

        MotionEvent vtev = MotionEvent.obtain(ev);

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0;
        }
        vtev.offsetLocation(0, mNestedYOffset);

        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0 || pointerIndex >= ev.getPointerCount()) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);

            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                mTouchY = (int) ev.getY(pointerIndex);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTouchY == 0) {
                    mTouchY = (int) ev.getY(pointerIndex);
                    return true;
                }

                mScrollConsumed[1] = 0;
                int y = (int) ev.getY(pointerIndex);
                int deltaY = mTouchY - y;
                mTouchY = y;
                if (dispatchNestedPreScroll(0, deltaY, mScrollConsumed, mScrollOffset, ViewCompat.TYPE_TOUCH)) {
                    deltaY -= mScrollConsumed[1];
                    ev.offsetLocation(0, mScrollOffset[1]);
                    mNestedYOffset += mScrollOffset[1];
                    mTouchY -= mScrollOffset[1];
                    getParent().requestDisallowInterceptTouchEvent(true);
                }

                int oldScrollY = mSecondScrollY;

                if (mScrollState != SCROLL_STATE_DRAGGING) {
                    boolean startScroll = false;
                    if (canScrollVertically() && Math.abs(deltaY) > 0) {
                        startScroll = true;
                    }
                    if (startScroll) {
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }

                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    dispatchScroll(deltaY);
                }

                final int scrolledDeltaY = mSecondScrollY - oldScrollY;

                if (scrolledDeltaY != 0) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }

                deltaY = deltaY - scrolledDeltaY;
                if (dispatchNestedScroll(0, scrolledDeltaY, 0, deltaY, mScrollOffset,
                        ViewCompat.TYPE_TOUCH)) {
                    deltaY += mScrollOffset[1];
                    mTouchY -= mScrollOffset[1];
                    mNestedYOffset += mScrollOffset[1];
                    ev.offsetLocation(0, mScrollOffset[1]);
                    getParent().requestDisallowInterceptTouchEvent(true);
                }

                // 判断是否显示边界阴影 额外判断是否设置了越界拖动，如果设置了越界拖动，则不显示边界阴影
                final int range = getScrollRange();
                final int overscrollMode = getOverScrollMode();
                boolean canOverscroll = overscrollMode == View.OVER_SCROLL_ALWAYS
                        || (overscrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);
                if (canOverscroll) {
                    ensureGlows();
                    final int pulledToY = oldScrollY + deltaY;
                    if (pulledToY < 0 && mOverDragMaxDistanceOfTop <= 0) {
                        // 滑动距离超出顶部边界，设置阴影
                        EdgeEffectCompat.onPull(mEdgeGlowTop, (float) deltaY / getHeight(),
                                ev.getX(pointerIndex) / getWidth());
                        if (!mEdgeGlowBottom.isFinished()) {
                            mEdgeGlowBottom.onRelease();
                        }
                    } else if (pulledToY > range && mOverDragMaxDistanceOfBottom <= 0) {
                        // 滑动距离超出底部边界，设置阴影
                        EdgeEffectCompat.onPull(mEdgeGlowBottom, (float) deltaY / getHeight(),
                                1.f - ev.getX(pointerIndex)
                                        / getWidth());
                        if (!mEdgeGlowTop.isFinished()) {
                            mEdgeGlowTop.onRelease();
                        }
                    }
                    if (mEdgeGlowTop != null
                            && (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished())) {
                        ViewCompat.postInvalidateOnAnimation(this);
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                endDrag();
                mTouchY = 0;
                recycleVelocityTracker();
                setScrollState(SCROLL_STATE_IDLE);
                break;
            case MotionEvent.ACTION_UP:
                endDrag();
                mTouchY = 0;

                if (mVelocityTracker != null) {
                    mVelocityTracker.addMovement(vtev);
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int yVelocity = (int) mVelocityTracker.getYVelocity();
                    yVelocity = Math.max(-mMaximumVelocity, Math.min(yVelocity, mMaximumVelocity));
                    if (yVelocity == 0 && mAdjustYVelocity != 0) {
                        // 如果VelocityTracker没有检测到fling速度，并且mAdjustYVelocity记录到速度，就已mAdjustYVelocity为准，
                        // 避免快速上下滑动时，fling失效。
                        yVelocity = mAdjustYVelocity;
                    }
                    fling(-yVelocity);
                    recycleVelocityTracker();
                }

                mEventY = 0;
                mEventX = 0;
                mTouching = false;
                mDownLocation[0] = 0;
                mDownLocation[1] = 0;
                mIsTouchNotTriggerScrollStick = false;
                mIsIntercept = false;
                springBack();
                break;
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(vtev);
        }
        vtev.recycle();
        return true;
    }

    private boolean canScrollVertically() {
        //新增判断是否开启了越界滑动模式
        return !isScrollTop() || !isScrollBottom() || mOverDragMode;
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int drawingPosition) {
        if (mViews.size() > drawingPosition) {
            int index = indexOfChild(mViews.get(drawingPosition));
            if (index != -1) {
                return index;
            }
        }
        return super.getChildDrawingOrder(childCount, drawingPosition);
    }

    int getDrawingPosition(View child) {
        return mViews.indexOf(child);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mOldScrollY != getScrollY()) {
            mOldScrollY = getScrollY();
            resetSticky();
        }

        // 绘制边界阴影
        if (mEdgeGlowTop != null) {
            final int scrollY = getScrollY();
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                int width = getWidth();
                int height = getHeight();
                int xTranslation = 0;
                int yTranslation = scrollY;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || getClipToPadding()) {
                    width -= getPaddingLeft() + getPaddingRight();
                    xTranslation += getPaddingLeft();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getClipToPadding()) {
                    height -= getPaddingTop() + getPaddingBottom();
                    yTranslation += getPaddingTop();
                }
                canvas.translate(xTranslation, yTranslation);
                mEdgeGlowTop.setSize(width, height);
                if (mEdgeGlowTop.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                int width = getWidth();
                int height = getHeight();
                int xTranslation = 0;
                int yTranslation = scrollY + height;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || getClipToPadding()) {
                    width -= getPaddingLeft() + getPaddingRight();
                    xTranslation += getPaddingLeft();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getClipToPadding()) {
                    height -= getPaddingTop() + getPaddingBottom();
                    yTranslation -= getPaddingBottom();
                }
                canvas.translate(xTranslation - width, yTranslation);
                canvas.rotate(180, width, 0);
                mEdgeGlowBottom.setSize(width, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            int childSize = computeVerticalScrollRange();
            int parentSpace = getHeight() - getPaddingTop() - getPaddingBottom();
            scrollRange = Math.max(0, childSize - parentSpace);
        }
        return scrollRange;
    }

    private void fling(int velocityY) {
        if (Math.abs(velocityY) > mMinimumVelocity) {
            if (dispatchNestedPreFling(0, (float) velocityY)) {
                return;
            }
            boolean canScroll = (velocityY < 0 && !isScrollTop()) || (velocityY > 0 && !isScrollBottom());
            this.dispatchNestedFling(0, (float) velocityY, canScroll);

            // Predictive Snap Fling
            if (tryPredictiveSnap(velocityY)) {
                return;
            }

            mScroller.fling(0, mSecondScrollY,
                    1, velocityY,
                    Integer.MIN_VALUE, Integer.MIN_VALUE,
                    Integer.MIN_VALUE, Integer.MAX_VALUE);
            startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH);
            setScrollState(SCROLL_STATE_SETTLING);
            mLastScrollerY = mSecondScrollY;
            invalidate();
        }
    }

    /**
     * Attempts to find a snap point and smooth scroll to it.
     * Returns true if a snap action is initiated, false otherwise.
     */
    private boolean tryPredictiveSnap(int velocityY) {
        if (mSnapPoints == null || mSnapPoints.isEmpty()) {
            return false;
        }

        int currentY = mSecondScrollY;
        Integer targetY = null;
        int minDiff = Integer.MAX_VALUE;
        int maxSnapPoint = Integer.MIN_VALUE;

        // Find the nearest snap point in the direction of the velocity
        for (int point : mSnapPoints) {
            if (point > maxSnapPoint) {
                maxSnapPoint = point;
            }
            if (velocityY > 0) {
                // Scrolling Down, looking for point > currentY
                if (point > currentY) {
                    int diff = point - currentY;
                    if (diff < minDiff) {
                        minDiff = diff;
                        targetY = point;
                    }
                }
            } else {
                // Scrolling Up, looking for point < currentY
                if (point < currentY) {
                    int diff = currentY - point;
                    if (diff < minDiff) {
                        minDiff = diff;
                        targetY = point;
                    }
                }
            }
        }

        if (targetY != null) {
            // Check if target is within reasonable distance (1 screen height)
            if (Math.abs(targetY - currentY) < mScreenHeightPixels) {
                // Fix for unsmooth scrolling when header is collapsed:
                // If we are flinging UP (velocityY > 0) and targeting the last snap point (collapsed state),
                // AND we are already very close to it, do NOT snap.
                // Instead, let the standard fling continue so momentum transfers to the child (RecyclerView).
                boolean isBottomSnap = (targetY == maxSnapPoint);
                boolean isAlreadyClose = Math.abs(targetY - currentY) < 50; // Threshold

                if (velocityY > 0 && isBottomSnap && isAlreadyClose) {
                    return false; // Fall through to standard fling
                } else {
                    smoothScrollToY(targetY);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void computeScroll() {
        if (mScrollToIndex != -1 && mSmoothScrollOffset != 0) {

            if (mSmoothScrollOffset > 0 && mSmoothScrollOffset < 200) {
                // 逐渐加速
                mSmoothScrollOffset += 5;
            }

            if (mSmoothScrollOffset < 0 && mSmoothScrollOffset > -200) {
                // 逐渐加速
                mSmoothScrollOffset -= 5;
            }

            // 正在平滑滑动到某个子view
            dispatchScroll(mSmoothScrollOffset);
            mCycleCount++;
            invalidate();
        } else {

            // fling
            if (mScroller.computeScrollOffset()) {
                int y = mScroller.getCurrY();
                int unconsumed = y - mLastScrollerY;
                mLastScrollerY = y;

                mScrollConsumed[1] = 0;

                dispatchNestedPreScroll(0, unconsumed, mScrollConsumed, null,
                        ViewCompat.TYPE_NON_TOUCH);
                unconsumed -= mScrollConsumed[1];

                final int oldScrollY = mSecondScrollY;
                dispatchScroll(unconsumed);

                final int scrolledByMe = mSecondScrollY - oldScrollY;
                unconsumed -= scrolledByMe;
                if ((unconsumed < 0 && isScrollTop()) || (unconsumed > 0 && isScrollBottom())) {
                    dispatchNestedScroll(0, scrolledByMe, 0, unconsumed, mScrollOffset,
                            ViewCompat.TYPE_NON_TOUCH);
                    unconsumed += mScrollOffset[1];
                }

                // 判断滑动方向和是否滑动到边界
                if ((unconsumed < 0 && isScrollTop()) || (unconsumed > 0 && isScrollBottom())) {
                    if (mOverDragMode) {
                        //fling 越界了, 停止滑动，转为动画控制继续滑动
                        int finalY = mScroller.getFinalY();
                        float velocity = finalY > 0 ? mScroller.getCurrVelocity() : -mScroller.getCurrVelocity();
                        startOverScrollBounce(velocity);
                        mScroller.forceFinished(true);
                    } else {
                        final int mode = getOverScrollMode();
                        final boolean canOverscroll = mode == OVER_SCROLL_ALWAYS
                                || (mode == OVER_SCROLL_IF_CONTENT_SCROLLS && getScrollRange() > 0);
                        if (canOverscroll) {
                            ensureGlows();
                            if (unconsumed < 0) {
                                // 设置上边界阴影
                                if (mEdgeGlowTop.isFinished()) {
                                    mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
                                }
                            } else {
                                // 设置下边界阴影
                                if (mEdgeGlowBottom.isFinished()) {
                                    mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
                                }
                            }
                        }
                        stopScroll();
                    }
                }

                invalidate();
            }

            if (mScrollState == SCROLL_STATE_SETTLING && mScroller.isFinished()) {
                // 滚动结束，校验子view内容的滚动位置
                stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
                checkTargetsScroll(false, false);
                setScrollState(SCROLL_STATE_IDLE);
                checkSnap();
            }
        }
    }

    /**
     * 在动画执行时，触摸屏幕，打断动画，转为拖动状态
     *
     * @param action MotionEvent
     * @return 是否成功打断
     */
    protected boolean interceptAnimatorByAction(int action) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (mReboundAnimator != null) {
                mReboundAnimator.setDuration(0);//cancel会触发End调用，可以判断0来确定是否被cancel
                mReboundAnimator.cancel();//会触发 cancel 和 end 调用
                mReboundAnimator = null;
            }
            mAnimationRunnable = null;
        }
        return mReboundAnimator != null;
    }

    protected class BounceRunnable implements Runnable {
        int mFrame = 0;
        int mFrameDelay = 10;
        int mSmoothDistance;
        long mLastTime;
        float mOffset = 0;
        float mVelocity;

        /**
         * @param velocity 大于0 为往底部走 否则 为往顶部走
         */
        BounceRunnable(float velocity, int smoothDistance) {
            mVelocity = velocity;
            mSmoothDistance = smoothDistance;
            mLastTime = AnimationUtils.currentAnimationTimeMillis();
            mHandler.postDelayed(this, mFrameDelay);
        }

        @Override
        public void run() {
            if (mAnimationRunnable == this) {
//                mVelocity *= Math.pow(0.45f, ++mFrame * 2);//回弹滚动数度衰减
                mVelocity *= Math.pow(0.85f, ++mFrame * 2);//回弹滚动数度衰减
                long now = AnimationUtils.currentAnimationTimeMillis();
                float t = 1f * (now - mLastTime) / 1000;
                float velocity = mVelocity * t;
                if (Math.abs(velocity) >= 1) {
                    mLastTime = now;
                    mOffset += velocity;
                    int oldScrollY = getScrollY();
                    moveOverScroll(mOffset);
                    if (oldScrollY != mSecondScrollY) {
                        scrollChange(mSecondScrollY, oldScrollY);
                    }
                    mHandler.postDelayed(this, mFrameDelay);
                } else {
                    mAnimationRunnable = null;
                    int startY = getScrollY();
                    int duration;
                    duration = 10 * Math.min(Math.max((int) Util.px2dp(Math.abs(startY - mSmoothDistance)), 30), 100);
                    startSpringAnimation(startY, mSmoothDistance, 0, mReboundInterpolator, duration);
                }
            }
        }
    }

    /**
     * 黏性移动 (Over-scroll movement)
     *
     * @param offset 偏移量
     */
    protected void moveOverScroll(float offset) {
        final View thisView = this;

        final float maxDragHeight;

        final double M;
        final double H = Math.max(mScreenHeightPixels / 2, thisView.getHeight());
        final double x;
        final double y;
        int finalScrollY;
        if (offset > 0) {
            //偏移量为正数 则是 底部越界 向上拖拽
            maxDragHeight = mOverDragMaxDistanceOfBottom;
            M = maxDragHeight;
            x = Math.max(0, offset * mDragRate);
            y = Math.min(M * (1 - Math.pow(100, -x / (H == 0 ? 1 : H))), x);// 公式 y = M(1-100^(-x/H))
        } else {
            //偏移量为负数 则是 顶部越界 拖拽
            maxDragHeight = mOverDragMaxDistanceOfBottom;
            M = maxDragHeight;
            x = -Math.min(0, offset * mDragRate);
            y = -Math.min(M * (1 - Math.pow(100, -x / (H == 0 ? 1 : H))), x);// 公式 y = M(1-100^(-x/H))
        }
        int mSpinner = (int) y;
        if (Math.abs(offset) >= 1 && mSpinner == 0) {
            mSpinner = (int) offset;
        }
        finalScrollY = getScrollY() + mSpinner;
        mSecondScrollY += mSpinner;
        scrollSelf(finalScrollY);
    }

    /**
     * 执行回弹动画 (Start spring back animation)
     *
     * @param endSpinner   目标值
     * @param startDelay   延时参数
     * @param interpolator 加速器
     * @param duration     时长
     * @return ValueAnimator or null
     */
    protected ValueAnimator startSpringAnimation(int startSpinner, int endSpinner, int startDelay, Interpolator interpolator, int duration) {
        if (startSpinner != endSpinner) {
            if (mReboundAnimator != null) {
                mReboundAnimator.setDuration(0);//cancel会触发End调用，可以判断0来确定是否被cancel
                mReboundAnimator.cancel();//会触发 cancel 和 end 调用
                mReboundAnimator = null;
            }
            mAnimationRunnable = null;
            mReboundAnimator = ValueAnimator.ofInt(startSpinner, endSpinner);
            mReboundAnimator.setDuration(duration);
            mReboundAnimator.setInterpolator(interpolator);
            mReboundAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation != null && animation.getDuration() == 0) {
                        /*
                         * 2020-3-15 修复
                         * onAnimationEnd 因为 cancel 调用是, 同样触发 onAnimationEnd 导致的各种问题
                         * 在取消之前调用 reboundAnimator.setDuration(0) 来标记动画被取消
                         */
                        return;
                    }
                    mReboundAnimator = null;
                    checkTargetsScroll(false, false);
                }
            });
            mReboundAnimator.addUpdateListener(animation -> {
                int animatedValue = (int) animation.getAnimatedValue();
                int oldScrollY = computeVerticalScrollOffset();
                scrollSelf(animatedValue);
                mSecondScrollY = computeVerticalScrollOffset();
                if (oldScrollY != mSecondScrollY) {
                    scrollChange(mSecondScrollY, oldScrollY);
                }
            });
            mReboundAnimator.setStartDelay(startDelay);
            mReboundAnimator.start();
            return mReboundAnimator;
        }
        return null;
    }

    /**
     * 越界回弹动画
     *
     * @param velocity 速度
     */
    protected void startOverScrollBounce(final float velocity) {
        if (mReboundAnimator == null) {
            if (velocity < 0 && mOverDragMaxDistanceOfTop > 0) {
                //顶部越界 回弹到 0
                mAnimationRunnable = new BounceRunnable(velocity, 0);
            } else if (velocity > 0 && mOverDragMaxDistanceOfBottom > 0) {
                //底部越界 回弹到 mScrollRange
                mAnimationRunnable = new BounceRunnable(velocity, mScrollRange);
            }
        }
    }

    /**
     * 手势拖动结束
     * 开始执行回弹动画
     */
    protected void springBack() {
        //还在滑动中则无视
        int mScrollY = getScrollY();
        if (mScrollY < 0) {
            if (mReboundAnimator == null) {
                startSpringAnimation(mScrollY, 0,
                        0, mReboundInterpolator, mReboundDuration);
            }
        } else if (mScrollY > mScrollRange) {
            if (mReboundAnimator == null) {
                //这里是底部回弹，需要用总滑动高度减去超出的部分
                startSpringAnimation(mScrollY, mScrollRange,
                        0, mReboundInterpolator, mReboundDuration);
            }
        }
    }

    private void endDrag() {
        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
        }
    }

    private void ensureGlows() {
        if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
            if (mEdgeGlowTop == null) {
                Context context = getContext();
                mEdgeGlowTop = new EdgeEffect(context);
                mEdgeGlowBottom = new EdgeEffect(context);
            }
        } else {
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
    }

    /**
     * 分发处理滑动
     *
     * @param offset
     */
    private void dispatchScroll(int offset) {
        if (offset > 0) {
            scrollUp(offset);
        } else if (offset < 0) {
            scrollDown(offset);
        }
    }

    /**
     * 将内容面板向上移动（手指拖动从下往上）
     *
     * @param offset 移动距离，正数
     */
    private void scrollUp(int offset) {
        int scrollOffset = 0;
        int remainder = offset;
        int oldScrollY = computeVerticalScrollOffset();
        do {
            int scrollAnchor = 0;
            int viewScrollOffset = 0;
            // 如果是要滑动到指定的View，判断滑动到目标位置，就停止滑动
            if (mScrollToIndex != -1) {
                View view = getChildAt(mScrollToIndex);
                scrollAnchor = view.getTop() - mScrollToIndexWithOffset;
                scrollAnchor -= getAdjustHeightForChild(view);
                if (mScrollToIndexWithOffset < 0) {
                    viewScrollOffset = getViewsScrollOffset(mScrollToIndex);
                }
                if (mCycleCount >= MAX_CYCLE_COUNT || getScrollY() + getPaddingTop() + viewScrollOffset >= scrollAnchor || isScrollBottom()) {
                    mScrollToIndex = -1;
                    mSmoothScrollOffset = 0;
                    mScrollToIndexWithOffset = 0;
                    mCycleCount = 0;
                    setScrollState(SCROLL_STATE_IDLE);
                    break;
                }
            }
            scrollOffset = 0;
            int scrollY = getScrollY();
            if (!isScrollBottom() && scrollY >= 0) {
                // 找到当前显示的第一个View
                View firstVisibleView = null;
                if (getScrollY() < mScrollRange) {
                    firstVisibleView = findFirstVisibleView();
                } else {
                    firstVisibleView = getBottomView();
                }
                if (firstVisibleView != null) {
                    awakenScrollBars();
                    int bottomOffset = ScrollUtils.getScrollBottomOffset(firstVisibleView);
                    if (bottomOffset > 0) {
                        scrollOffset = Math.min(remainder, bottomOffset);
                        if (mScrollToIndex != -1) {
                            scrollOffset = Math.min(scrollOffset, scrollAnchor - (getScrollY() + getPaddingTop() + viewScrollOffset));
                        }
                        scrollChild(firstVisibleView, scrollOffset);
                    } else {
                        scrollOffset = Math.min(remainder,
                                firstVisibleView.getBottom() - getPaddingTop() - getScrollY());
                        if (mScrollToIndex != -1) {
                            scrollOffset = Math.min(scrollOffset, scrollAnchor - (getScrollY() + getPaddingTop() + viewScrollOffset));
                        }
                        scrollSelf(scrollY + scrollOffset);
                    }
                    mSecondScrollY += scrollOffset;
                    remainder = remainder - scrollOffset;
                }
            } else if (mTouching) {
                //向上拖动底部越界 或 顶部越界 向上拖动自身退回顶部
                if (scrollY < 0 && remainder > Math.abs(scrollY)) {
                    //向上拖动自身退回顶部, 自身只消费该消费的，剩余的给到子view
                    scrollOffset = remainder - Math.abs(scrollY);
                    remainder -= scrollOffset;
                    moveOverScroll(scrollOffset);
                } else {
                    //向上越界拖动，全消费掉
                    dispatchNestedScroll(0, 0, 0, remainder, mScrollOffset,
                            ViewCompat.TYPE_TOUCH);
                    if (mScrollOffset[1] == 0) {
                        if (mOverDragMode && mOverDragMaxDistanceOfBottom >= 0) {
                            moveOverScroll(remainder);
                        }
                    }
                    remainder = 0;
                }
            } else {
                //通常是 fling 出现越界或者从越界返回边界
                //如果fling的停止点没有越界，则停止回弹动画，走这里
                if (!mScroller.isFinished() && mScroller.getFinalY() > 0 && scrollY < 0) {
                    //如果正在走回弹动画，这里直接截停回弹动画
                    if (mReboundAnimator != null) {
                        interceptAnimatorByAction(MotionEvent.ACTION_DOWN);
                    }
                    if (remainder > Math.abs(scrollY)) {
                        scrollOffset = remainder - Math.abs(scrollY);
                        remainder -= scrollOffset;
                    } else {
                        scrollOffset = remainder;
                        remainder = 0;
                    }
                    mSecondScrollY += scrollOffset;
                    scrollSelf(scrollY + scrollOffset);
                } else if (scrollY < 0) {
                    //走回弹动画去了，这里要停掉mScroller的动画
                    mScroller.forceFinished(true);
                }
            }
        } while (scrollOffset > 0 && remainder > 0);

        int newScrollY = computeVerticalScrollOffset();
        if (oldScrollY != newScrollY) {
            scrollChange(newScrollY, oldScrollY);
        }
    }

    /**
     * 将内容面板向下移动（手指拖动从上往下）
     *
     * @param offset 移动距离，负数
     */
    private void scrollDown(int offset) {
        int scrollOffset = 0;
        int remainder = offset;
        int oldScrollY = computeVerticalScrollOffset();
        do {
            int scrollAnchor = 0;
            int viewScrollOffset = 0;
            // 如果是要滑动到指定的View，判断滑动到目标位置，就停止滑动
            if (mScrollToIndex != -1) {
                View view = getChildAt(mScrollToIndex);
                scrollAnchor = view.getTop() - mScrollToIndexWithOffset;
                scrollAnchor -= getAdjustHeightForChild(view);
                viewScrollOffset = getViewsScrollOffset(mScrollToIndex);
                if (mCycleCount >= MAX_CYCLE_COUNT || getScrollY() + getPaddingTop() + viewScrollOffset <= scrollAnchor || isScrollTop()) {
                    mScrollToIndex = -1;
                    mSmoothScrollOffset = 0;
                    mScrollToIndexWithOffset = 0;
                    mCycleCount = 0;
                    setScrollState(SCROLL_STATE_IDLE);
                    break;
                }
            }

            scrollOffset = 0;
            int scrollY = getScrollY();
            if (!isScrollTop() && scrollY <= mScrollRange && scrollY >= 0) {
                // 找到当前显示的最后一个View
                View lastVisibleView = null;
                if (scrollY < mScrollRange) {
                    lastVisibleView = findLastVisibleView();
                } else {
                    lastVisibleView = getBottomView();
                }
                if (lastVisibleView != null) {
                    awakenScrollBars();
                    int childScrollOffset = ScrollUtils.getScrollTopOffset(lastVisibleView);
                    if (childScrollOffset < 0) {
                        scrollOffset = Math.max(remainder, childScrollOffset);
                        if (mScrollToIndex != -1) {
                            scrollOffset = Math.max(scrollOffset, scrollAnchor - (getScrollY() + getPaddingTop() + viewScrollOffset));
                        }
                        scrollChild(lastVisibleView, scrollOffset);
                    } else {
                        scrollOffset = Math.max(remainder,
                                lastVisibleView.getTop() + getPaddingBottom() - scrollY - getHeight());
                        scrollOffset = Math.max(scrollOffset, -scrollY);
                        if (mScrollToIndex != -1) {
                            scrollOffset = Math.max(scrollOffset, scrollAnchor - (getScrollY() + getPaddingTop() + viewScrollOffset));
                        }
                        scrollSelf(scrollY + scrollOffset);
                    }
                    mSecondScrollY += scrollOffset;
                    remainder = remainder - scrollOffset;
                }
            } else if (mTouching) {
                //顶部越界 向下拖动增加越界距离 或 底部越界 向下拖动自身退回底部，减少越界距离
                //当前越界距离
                int diff = scrollY - mScrollRange;
                if (scrollY > mScrollRange && Math.abs(remainder) > diff) {
                    //向下拖动自身退回底部, 自身只消费该消费的，剩余的给到子view
                    scrollOffset = -diff;
                    remainder -= scrollOffset;
                    moveOverScroll(scrollOffset);
                } else {
                    //向下越界拖动
                    dispatchNestedScroll(0, 0, 0, remainder, mScrollOffset,
                            ViewCompat.TYPE_TOUCH);
                    remainder += mScrollOffset[1];
                    if (remainder != 0) {
                        moveOverScroll(remainder);
                    }
                    remainder = 0;
                }
            } else {
                //通常是 fling 出现越界或者从越界返回边界
                //如果fling的停止点没有越界，则停止回弹动画，走这里
                if (!mScroller.isFinished() && mScroller.getFinalY() < mScrollRange && scrollY > mScrollRange) {
                    //如果正在走回弹动画，这里直接截停回弹动画
                    if (mReboundAnimator != null) {
                        interceptAnimatorByAction(MotionEvent.ACTION_DOWN);
                    }
                    int diff = mScrollRange - scrollY;
                    if (remainder < diff) {
                        scrollOffset = diff;
                        remainder -= scrollOffset;
                    } else {
                        scrollOffset = remainder;
                        remainder = 0;
                    }
                    mSecondScrollY += scrollOffset;
                    scrollSelf(scrollY + scrollOffset);
                } else if (scrollY > mScrollRange) {
                    //走回弹动画去了，这里要停掉mScroller的动画
                    mScroller.forceFinished(true);
                }
            }
        } while (scrollOffset < 0 && remainder < 0);

        int newScrollY = computeVerticalScrollOffset();
        if (oldScrollY != newScrollY) {
            scrollChange(newScrollY, oldScrollY);
        }
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollTo(0, mSecondScrollY + y);
    }

    @Override
    public void scrollTo(int x, int y) {
        //所有的scroll操作都交由dispatchScroll()来分发处理
        dispatchScroll(y - mSecondScrollY);
    }

    private void scrollChange(int scrollY, int oldScrollY) {
        if (mOnScrollChangeListener != null) {
            mOnScrollChangeListener.onScrollChange(this, scrollY, oldScrollY, mScrollState);
        }
    }

    private void stickyChange(View oldStickyView, View newStickyView) {
        if (mOnStickyChangeListener != null) {
            mOnStickyChangeListener.onStickyChange(oldStickyView, newStickyView);
        }
    }

    private void permanentStickyChange(List<View> mCurrentStickyViews) {
        if (mOnPermanentStickyChangeListener != null) {
            mOnPermanentStickyChangeListener.onStickyChange(mCurrentStickyViews);
        }
    }

    /**
     * 滑动自己
     *
     * @param y
     */
    private void scrollSelf(int y) {
        int scrollY = y;
        // 边界检测 新增条件为 超过最大越界距离才重设
        if (scrollY < 0 && Math.abs(scrollY) > Math.abs(mOverDragMaxDistanceOfTop)) {
            scrollY = mOverDragMaxDistanceOfTop <= 0 ? 0 : -mOverDragMaxDistanceOfTop;
        } else if (scrollY > mScrollRange && scrollY > mScrollRange + Math.abs(mOverDragMaxDistanceOfBottom)) {
            scrollY = mOverDragMaxDistanceOfBottom <= 0 ? mScrollRange : mScrollRange + mOverDragMaxDistanceOfBottom;
        }
        super.scrollTo(0, scrollY);
    }

    private void scrollChild(View child, int y) {
        View scrolledView = ScrollUtils.getScrolledView(child);
        if (scrolledView instanceof AbsListView) {
            AbsListView listView = (AbsListView) scrolledView;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                listView.scrollListBy(y);
            }
        } else {
            boolean isInterceptRequestLayout = false;
            if (scrolledView instanceof RecyclerView) {
                isInterceptRequestLayout = ScrollUtils.startInterceptRequestLayout((RecyclerView) scrolledView);
            }

            scrolledView.scrollBy(0, y);

            if (isInterceptRequestLayout) {
                final RecyclerView view = (RecyclerView) scrolledView;
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ScrollUtils.stopInterceptRequestLayout(view);
                    }
                }, 0);
            }
        }
    }

    public void checkLayoutChange() {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                checkLayoutChange(false, true);
            }
        }, 20);
    }

    /**
     * 布局发生变化，重新检查所有子View是否正确显示
     */
    private void checkLayoutChange(boolean changed, boolean isForce) {

        int y = mSecondScrollY;

        if (mScrollToTopView != null && changed) {
            if (indexOfChild(mScrollToTopView) != -1) {
                scrollSelf(mScrollToTopView.getTop() + mAdjust);
            }
        } else {
            scrollSelf(getScrollY());
        }

        checkTargetsScroll(true, isForce);

        // 如果正在显示的子布局和滑动偏移量mScrollToTopView都改变了，则有可能是因为布局发生改变，并且影响到正在显示的布局部分。
        // scrollTo(0, y)把布局的滑动位置恢复为原来的mScrollToTopView，可以避免正在显示的布局显示异常。
        // 注意：由于RecyclerView的computeVerticalScrollOffset()方法计算到的不是真实的滑动偏移量，而是根据item平均高度估算的值。
        // 所以当RecyclerView的item高度不一致时，可能会导致mScrollToTopView计算的偏移量和布局的实际偏移量不一致，从而导致scrollTo(0, y)恢复
        // 原滑动位置时产生上下偏移的误差。
        if (y != mSecondScrollY && mScrollToTopView != findFirstVisibleView()) {
            scrollTo(0, y);
        }

        mScrollToTopView = null;
        mAdjust = 0;

        resetSticky();
    }

    /**
     * 校验子view内容滚动位置是否正确
     */
    private void checkTargetsScroll(boolean isLayoutChange, boolean isForce) {
        if (!isForce && (mTouching || !mScroller.isFinished() || mScrollToIndex != -1)) {
            return;
        }
        int oldScrollY = computeVerticalScrollOffset();
        View target = findFirstVisibleView();
        if (target == null) {
            return;
        }
        int index = indexOfChild(target);

        if (isLayoutChange) {
            while (true) {
                int bottomOffset = ScrollUtils.getScrollBottomOffset(target);
                int scrollTopOffset = target.getTop() - getScrollY();
                if (bottomOffset > 0 && scrollTopOffset < 0) {
                    int offset = Math.min(bottomOffset, -scrollTopOffset);
                    scrollSelf(getScrollY() - offset);
                    scrollChild(target, offset);
                } else {
                    break;
                }
            }
        }

        for (int i = 0; i < index; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            if (ScrollUtils.isConsecutiveScrollerChild(child)) {
                View scrollChild = ScrollUtils.getScrollChild(child);
                if (scrollChild instanceof IConsecutiveScroller) {
                    List<View> views = ((IConsecutiveScroller) scrollChild).getScrolledViews();
                    if (views != null && !views.isEmpty()) {
                        int size = views.size();
                        for (int c = 0; c < size; c++) {
                            scrollChildContentToBottom(views.get(c));
                        }
                    }
                } else {
                    scrollChildContentToBottom(scrollChild);
                }
            }
        }

        for (int i = index + 1; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            if (ScrollUtils.isConsecutiveScrollerChild(child)) {
                if (i == getChildCount() - 1 && child.getHeight() < this.getHeight() && getScrollY() >= mScrollRange) {
                    continue;
                }
                View scrollChild = ScrollUtils.getScrollChild(child);
                if (scrollChild instanceof IConsecutiveScroller) {
                    List<View> views = ((IConsecutiveScroller) scrollChild).getScrolledViews();
                    if (views != null && !views.isEmpty()) {
                        int size = views.size();
                        for (int c = 0; c < size; c++) {
                            scrollChildContentToTop(views.get(c));
                        }
                    }
                } else {
                    scrollChildContentToTop(scrollChild);
                }
            }
        }

        computeOwnScrollOffset();
        if (isLayoutChange) {
            int newScrollY = computeVerticalScrollOffset();
            if (oldScrollY != newScrollY) {
                scrollChange(newScrollY, oldScrollY);
            }
        }

        resetSticky();
    }

    /**
     * 滚动指定子view的内容到顶部
     *
     * @param target
     */
    void scrollChildContentToTop(View target) {
        int scrollY = 0;
        do {
            scrollY = 0;
            int offset = ScrollUtils.getScrollTopOffset(target);
            if (offset < 0) {
                int childOldScrollY = ScrollUtils.computeVerticalScrollOffset(target);
                scrollChild(target, offset);
                scrollY = childOldScrollY - ScrollUtils.computeVerticalScrollOffset(target);
            }
        } while (scrollY != 0);
    }

    /**
     * 滚动指定子view的内容到底部
     *
     * @param target
     */
    void scrollChildContentToBottom(View target) {
        int scrollY = 0;
        do {
            scrollY = 0;
            int offset = ScrollUtils.getScrollBottomOffset(target);
            if (offset > 0) {
                int childOldScrollY = ScrollUtils.computeVerticalScrollOffset(target);
                scrollChild(target, offset);
                scrollY = childOldScrollY - ScrollUtils.computeVerticalScrollOffset(target);
            }
        } while (scrollY != 0);
    }

    /**
     * 重新计算mOwnScrollY
     *
     * @return
     */
    private void computeOwnScrollOffset() {
        mSecondScrollY = computeVerticalScrollOffset();
    }

    /**
     * 初始化VelocityTracker
     */
    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    /**
     * 初始化VelocityTracker
     */
    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    /**
     * 回收VelocityTracker
     */
    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * 初始化VelocityTracker
     */
    private void initOrResetAdjustVelocityTracker() {
        if (mAdjustVelocityTracker == null) {
            mAdjustVelocityTracker = VelocityTracker.obtain();
        } else {
            mAdjustVelocityTracker.clear();
        }
    }

    /**
     * 初始化VelocityTracker
     */
    private void initAdjustVelocityTrackerIfNotExists() {
        if (mAdjustVelocityTracker == null) {
            mAdjustVelocityTracker = VelocityTracker.obtain();
        }
    }

    /**
     * 回收VelocityTracker
     */
    private void recycleAdjustVelocityTracker() {
        if (mAdjustVelocityTracker != null) {
            mAdjustVelocityTracker.recycle();
            mAdjustVelocityTracker = null;
        }
    }

    /**
     * 停止滑动
     */
    public void stopScroll() {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
            if (mScrollToIndex == -1) {
                setScrollState(SCROLL_STATE_IDLE);
            }
        }
    }

    private View getBottomView() {
        List<View> views = getEffectiveChildren();
        if (!views.isEmpty()) {
            return views.get(views.size() - 1);
        }
        return null;
    }


    /**
     * 返回所有的非GONE子View
     *
     * @return
     */
    private List<View> getNonGoneChildren() {
        List<View> children = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * 返回所有高度不为0的view
     */
    private List<View> getEffectiveChildren() {
        List<View> children = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE && child.getHeight() > 0) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * 返回所有的吸顶子View(非GONE)
     *
     * @return
     */
    private List<View> getStickyChildren() {
        List<View> children = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE && isStickyView(child)) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * 是否是需要吸顶的View
     *
     * @param child
     * @return
     */
    public boolean isStickyView(View child) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp instanceof LayoutParams) {
            return ((LayoutParams) lp).isSticky;
        }
        return false;
    }

    /**
     * 吸顶view是否是下沉模式
     *
     * @param stickyView
     * @return
     */
    public boolean isSink(View stickyView) {
        ViewGroup.LayoutParams lp = stickyView.getLayoutParams();
        if (lp instanceof LayoutParams) {
            return ((LayoutParams) lp).isSink;
        }
        return false;
    }

    /**
     * 重置脱离吸顶的view的TranslationY
     *
     * @param child
     */
    private void resetTranslationYOffSticky(View child) {
        child.setTranslationY(0);
    }

    private void resetTranslationYOffSticky(List<View> newStickyViews, List<View> oldStickyViews) {
        for (View child : oldStickyViews) {
            if (!newStickyViews.contains(child)) {
                resetTranslationYOffSticky(child);
            }
        }
    }

    /**
     * 重置吸顶
     */
    private void resetSticky() {
        List<View> children = getStickyChildren();
        if (!children.isEmpty()) {
            int count = children.size();
            // 让所有的View恢复原来的状态
            for (int i = 0; i < count; i++) {
                View child = children.get(i);
                child.setTranslationY(0);
            }
            if (mIsPermanent) {//常驻
                clearCurrentStickyView();
                permanentStickyChild(children);
            } else {

                clearCurrentStickyViews();

                // 需要吸顶的View
                View stickyView = null;
                // 下一个需要吸顶的View
                View nextStickyView = null;

                // 找到需要吸顶的View
                for (int i = count - 1; i >= 0; i--) {
                    View child = children.get(i);
                    //新增处理顶部越界下拉时，让吸顶view继续吸在顶部
                    int scrollY = getScrollY();
                    boolean find = scrollY < 0 && child.getTop() + scrollY <= getStickyY(child);
                    if (find || child.getTop() <= getStickyY(child)) {
                        stickyView = child;
                        if (i != count - 1) {
                            nextStickyView = children.get(i + 1);
                        }
                        break;
                    }
                }

                View oldStickyView = mCurrentStickyView;
                View newStickyView = stickyView;

                if (stickyView != null) {
                    int offset = 0;
                    if (nextStickyView != null && !isSink(stickyView)) {
                        int mOffset = stickyView.getHeight() - (nextStickyView.getTop() - getStickyY(stickyView));
                        offset = Math.max(0, mOffset);
                    }
                    stickyChild(stickyView, offset);
                }

                if (oldStickyView != newStickyView) {
                    mCurrentStickyView = newStickyView;
                    if (oldStickyView != null) {
                        resetTranslationYOffSticky(oldStickyView);
                    }
                    stickyChange(oldStickyView, newStickyView);
                }
            }
        } else {
            // 没有吸顶view
            clearCurrentStickyView();
            clearCurrentStickyViews();
        }
    }

    private void clearCurrentStickyView() {
        if (mCurrentStickyView != null) {
            View oldStickyView = mCurrentStickyView;
            mCurrentStickyView = null;
            resetTranslationYOffSticky(oldStickyView);
            stickyChange(oldStickyView, null);
        }
    }

    private void clearCurrentStickyViews() {
        if (!mCurrentStickyViews.isEmpty()) {
            for (View child : mCurrentStickyViews) {
                resetTranslationYOffSticky(child);
            }
            mCurrentStickyViews.clear();
            permanentStickyChange(mCurrentStickyViews);
        }
    }

    /**
     * 子View吸顶
     *
     * @param child
     * @param offset
     */
    private void stickyChild(View child, int offset) {
        child.setY(getStickyY(child) - offset);

        // 把View设置为可点击的，避免吸顶View与其他子View重叠是，触摸事件透过吸顶View传递给下面的View，
        // 导致ConsecutiveScrollerLayout追踪布局的滑动出现偏差
        child.setClickable(true);
    }

    /**
     * 获取吸顶的位置。
     *
     * @return
     */
    private int getStickyY() {
        return getScrollY() + getPaddingTop() + mStickyOffset;
    }

    private int getStickyY(View child) {
        int offset = 0;
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp instanceof LayoutParams) {
            offset = ((LayoutParams) lp).stickyOffset;
        }
        return getStickyY() + offset;
    }

    /**
     * 子View吸顶常驻
     *
     * @param children
     */
    private void permanentStickyChild(List<View> children) {
        mTempStickyViews.clear();
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            int permanentHeight = getPermanentHeight(children, i);
            if (child.getTop() <= getStickyY(child) + permanentHeight) {
                child.setY(getStickyY(child) + permanentHeight);
                child.setClickable(true);
                mTempStickyViews.add(child);
            }
        }

        resetTranslationYOffSticky(mTempStickyViews, mCurrentStickyViews);

        if (!isListEqual()) {
            mCurrentStickyViews.clear();
            mCurrentStickyViews.addAll(mTempStickyViews);
            mTempStickyViews.clear();
            permanentStickyChange(mCurrentStickyViews);
        }
    }

    private int getPermanentHeight(List<View> children, int currentPosition) {
        int height = 0;
        for (int i = 0; i < currentPosition; i++) {
            View child = children.get(i);
            if (!isSink(child)) {
                height += child.getMeasuredHeight();
            }
        }
        return height;
    }

    private boolean isListEqual() {
        if (mTempStickyViews.size() == mCurrentStickyViews.size()) {
            int size = mTempStickyViews.size();
            for (int i = 0; i < size; i++) {
                if (mTempStickyViews.get(i) != mCurrentStickyViews.get(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Return the current scrolling state of the RecyclerView.
     *
     * @return {@link #SCROLL_STATE_IDLE}, {@link #SCROLL_STATE_DRAGGING} or
     * {@link #SCROLL_STATE_SETTLING}
     */
    public int getScrollState() {
        return mScrollState;
    }

    void setScrollState(int state) {
        if (state == mScrollState) {
            return;
        }
        mScrollState = state;
        int newScrollY = computeVerticalScrollOffset();
        scrollChange(newScrollY, newScrollY);
    }

    /**
     * 使用这个方法取代View的getScrollY
     *
     * @return
     */
    public int getOwnScrollY() {
        return computeVerticalScrollOffset();
    }

    /**
     * 找到当前显示的第一个View
     *
     * @return
     */
    public View findFirstVisibleView() {
        int offset = getScrollY() + getPaddingTop();
        List<View> children = getEffectiveChildren();
        int count = children.size();
        for (int i = 0; i < count; i++) {
            View child = children.get(i);
            if (child.getTop() <= offset && child.getBottom() > offset) {
                return child;
            }
        }
        return null;
    }

    /**
     * 找到当前显示的最后一个View
     *
     * @return
     */
    public View findLastVisibleView() {
        int offset = getHeight() - getPaddingBottom() + getScrollY();
        List<View> children = getEffectiveChildren();
        int count = children.size();
        for (int i = 0; i < count; i++) {
            View child = children.get(i);
            if (child.getTop() < offset && child.getBottom() >= offset) {
                return child;
            }
        }
        return null;
    }

    /**
     * 是否滑动到顶部
     *
     * @return
     */
    public boolean isScrollTop() {
        List<View> children = getEffectiveChildren();
        int size = children.size();
        if (size > 0) {
            View child = children.get(0);

            boolean isScrollTop = getScrollY() <= 0 && !ScrollUtils.canScrollVertically(child, -1);

            if (isScrollTop) {
                for (int i = size - 1; i >= 0; i--) {
                    View view = children.get(i);
                    if (ScrollUtils.isConsecutiveScrollerChild(view)
                            && ScrollUtils.canScrollVertically(view, -1)) {
                        return false;
                    }
                }
            }

            return isScrollTop;
        }
        return true;
    }

    /**
     * 是否滑动到底部
     *
     * @return
     */
    public boolean isScrollBottom() {
        List<View> children = getEffectiveChildren();
        int size = children.size();
        if (size > 0) {
            View child = children.get(children.size() - 1);
            boolean isScrollBottom = getScrollY() >= mScrollRange && !ScrollUtils.canScrollVertically(child, 1);

            if (isScrollBottom) {
                for (int i = size - 1; i >= 0; i--) {
                    View view = children.get(i);
                    if (ScrollUtils.isConsecutiveScrollerChild(view)
                            && ScrollUtils.canScrollVertically(view, 1)) {
                        return false;
                    }
                }
            }
            return isScrollBottom;
        }
        return true;
    }

    @Override
    public boolean canScrollVertically(int direction) {
        if (direction > 0) {
            return !isScrollBottom();
        } else {
            return !isScrollTop();
        }
    }

    /**
     * 禁止设置滑动监听，因为这个监听器已无效
     * 若想监听容器的滑动，请使用
     *
     * @param l
     * @see #setOnVerticalScrollChangeListener(OnScrollChangeListener)
     */
    @Deprecated
    @Override
    public void setOnScrollChangeListener(View.OnScrollChangeListener l) {
    }

    /**
     * 设置滑动监听
     *
     * @param l
     */
    public void setOnVerticalScrollChangeListener(OnScrollChangeListener l) {
        mOnScrollChangeListener = l;
    }

    public OnScrollChangeListener getOnVerticalScrollChangeListener() {
        return mOnScrollChangeListener;
    }

    @Override
    public int computeHorizontalScrollRange() {
        return super.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        return super.computeHorizontalScrollOffset();
    }

    @Override
    public int computeHorizontalScrollExtent() {
        return super.computeHorizontalScrollExtent();
    }

    @Override
    public int computeVerticalScrollRange() {
        int range = 0;

        List<View> children = getNonGoneChildren();
        int count = children.size();
        for (int i = 0; i < count; i++) {
            View child = children.get(i);
            if (!ScrollUtils.isConsecutiveScrollerChild(child)) {
                range += child.getHeight();
            } else {
                if (ScrollUtils.canScrollVertically(child)) {
                    View view = ScrollUtils.getScrolledView(child);
                    range += ScrollUtils.computeVerticalScrollRange(view) + view.getPaddingTop() + view.getPaddingBottom();
                } else {
                    range += child.getHeight();
                }
            }
        }

        return range;
    }

    @Override
    public int computeVerticalScrollOffset() {
        int scrollOffset = getScrollY();
        List<View> children = getNonGoneChildren();
        int count = children.size();
        for (int i = 0; i < count; i++) {
            View child = children.get(i);
            if (ScrollUtils.isConsecutiveScrollerChild(child)) {
                scrollOffset += ScrollUtils.computeVerticalScrollOffset(child);
            }
        }

        return scrollOffset;
    }

    @Override
    public int computeVerticalScrollExtent() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    //根据坐标返回触摸到的View
    private View getTouchTarget(int touchX, int touchY) {
        View targetView = null;
        // 获取可触摸的View
        List<View> touchableViews = getNonGoneChildren();
        for (View touchableView : touchableViews) {
            if (ScrollUtils.isTouchPointInView(touchableView, touchX, touchY)) {
                targetView = touchableView;
                break;
            }
        }
        return targetView;
    }

    /**
     * 判断是否需要拦截事件
     *
     * @param ev
     * @return
     */
    private boolean isIntercept(MotionEvent ev) {
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0 || pointerIndex >= ev.getPointerCount()) {
            // 无效的触摸，不要往下传递
            return true;
        }
        return isIntercept(ScrollUtils.getRawX(this, ev, pointerIndex),
                ScrollUtils.getRawY(this, ev, pointerIndex));
    }

    /**
     * 判断是否需要拦截事件
     *
     * @return
     */
    private boolean isIntercept(int touchX, int touchY) {
        View target = getTouchTarget(touchX, touchY);
        if (target != null) {
            return ScrollUtils.isConsecutiveScrollerChild(target);
        }

        return false;
    }

    public static class LayoutParams extends MarginLayoutParams {

        /**
         * 是否与父布局整体滑动，设置为false时，父布局不会拦截它的事件，滑动事件将由子view处理。
         * 可以实现子view内部的垂直滑动。
         */
        public boolean isConsecutive = true;

        /**
         * 是否支持嵌套滑动，默认支持，如果子view或它内部的下级view实现了NestedScrollingChild接口，
         * 它可以与ConsecutiveScrollerLayout嵌套滑动，把isNestedScroll设置为false可以禁止它与ConsecutiveScrollerLayout嵌套滑动。
         */
        public boolean isNestedScroll = true;

        /**
         * 设置子view吸顶悬浮
         */
        public boolean isSticky = false;

        /**
         * 在View吸顶的状态下，是否可以触摸view来滑动ConsecutiveScrollerLayout布局。
         * 默认为false，则View吸顶的状态下，不能触摸它来滑动布局
         */
        public boolean isTriggerScroll = false;

        /**
         * 吸顶下沉模式
         * 默认情况下，吸顶view在吸顶状态下，会显示在布局上层，覆盖其他布局。
         * 如果设置了下沉模式，则会相反，view在吸顶时会显示在下层，被其他布局覆盖，隐藏在下面。
         */
        public boolean isSink = false;

        /**
         * 可滑动子 View 的 id
         */
        public int scrollChild = View.NO_ID;

        /**
         * 子view与父布局的对齐方式
         */
        public Align align = Align.LEFT;

        /**
         * 子View吸顶时的偏移量
         */
        public int stickyOffset = 0;

        /**
         * 子view与父布局的对齐方式
         */
        public enum Align {
            //左对齐。（默认）
            LEFT(1),
            //右对齐。
            RIGHT(2),
            //中间对齐。
            CENTER(3);

            int value;

            Align(int value) {
                this.value = value;
            }

            static Align get(int value) {
                switch (value) {
                    case 1:
                        return LEFT;
                    case 2:
                        return RIGHT;
                    case 3:
                        return CENTER;
                }
                return LEFT;
            }
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = null;
            try {
                a = c.obtainStyledAttributes(attrs, R.styleable.ConsecutiveScrollerLayout_Layout);

                isConsecutive = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_Layout_layout_isConsecutive, true);
                isNestedScroll = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_Layout_layout_isNestedScroll, true);
                isSticky = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_Layout_layout_isSticky, false);
                isTriggerScroll = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_Layout_layout_isTriggerScroll, false);
                isSink = a.getBoolean(R.styleable.ConsecutiveScrollerLayout_Layout_layout_isSink, false);
                int type = a.getInt(R.styleable.ConsecutiveScrollerLayout_Layout_layout_align, 1);
                align = Align.get(type);
                scrollChild = a.getResourceId(R.styleable.ConsecutiveScrollerLayout_Layout_layout_scrollChild, View.NO_ID);
                stickyOffset = a.getDimensionPixelOffset(R.styleable.ConsecutiveScrollerLayout_Layout_layout_stickyOffset, 0);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (a != null) {
                    a.recycle();
                }
            }
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    /**
     * 滑动到指定的view
     *
     * @param view
     */
    public void scrollToChild(View view) {
        scrollToChildWithOffset(view, 0);
    }

    public void scrollToChildWithOffset(View view, int offset) {
        int scrollToIndex = indexOfChild(view);
        if (scrollToIndex != -1) {

            int scrollAnchor = view.getTop() - offset;
            scrollAnchor -= getAdjustHeightForChild(view);

            // 滑动方向。
            int scrollOrientation = 0;

            if (offset >= 0) {
                if (getScrollY() + getPaddingTop() > scrollAnchor) {
                    scrollOrientation = -1;
                } else if (getScrollY() + getPaddingTop() < scrollAnchor) {
                    scrollOrientation = 1;
                } else if (ScrollUtils.canScrollVertically(view, -1)) {
                    scrollOrientation = -1;
                }
            } else {
                int viewScrollOffset = getViewsScrollOffset(scrollToIndex);
                if (getScrollY() + getPaddingTop() + viewScrollOffset > scrollAnchor) {
                    scrollOrientation = -1;
                } else if (getScrollY() + getPaddingTop() + viewScrollOffset < scrollAnchor) {
                    scrollOrientation = 1;
                }
            }

            if (scrollOrientation != 0) {
                mScrollToIndex = scrollToIndex;
                // 停止fling
                stopScroll();
                mScrollToIndexWithOffset = offset;
                setScrollState(SCROLL_STATE_SETTLING);
                do {
                    if (scrollOrientation < 0) {
                        dispatchScroll(-200);
                    } else {
                        dispatchScroll(200);
                    }
                    mCycleCount++;
                } while (mScrollToIndex != -1);
            }
        }
    }

    /**
     * 平滑滑动到指定的view
     *
     * @param view
     */
    public void smoothScrollToChild(View view) {
        smoothScrollToChildWithOffset(view, 0);
    }

    public void smoothScrollToChildWithOffset(View view, int offset) {
        int scrollToIndex = indexOfChild(view);
        if (scrollToIndex != -1) {

            int scrollAnchor = view.getTop() - offset;
            scrollAnchor -= getAdjustHeightForChild(view);

            // 滑动方向。
            int scrollOrientation = 0;

            if (offset >= 0) {
                if (getScrollY() + getPaddingTop() > scrollAnchor) {
                    scrollOrientation = -1;
                } else if (getScrollY() + getPaddingTop() < scrollAnchor) {
                    scrollOrientation = 1;
                } else if (ScrollUtils.canScrollVertically(view, -1)) {
                    scrollOrientation = -1;
                }
            } else {
                int viewScrollOffset = getViewsScrollOffset(scrollToIndex);
                if (getScrollY() + getPaddingTop() + viewScrollOffset > scrollAnchor) {
                    scrollOrientation = -1;
                } else if (getScrollY() + getPaddingTop() + viewScrollOffset < scrollAnchor) {
                    scrollOrientation = 1;
                }
            }

            if (scrollOrientation != 0) {
                mScrollToIndex = scrollToIndex;
                // 停止fling
                stopScroll();
                mScrollToIndexWithOffset = offset;
                setScrollState(SCROLL_STATE_SETTLING);
                if (scrollOrientation < 0) {
                    mSmoothScrollOffset = -50;
                } else {
                    mSmoothScrollOffset = 50;
                }
                invalidate();
            }
        }
    }

    /**
     * 获取从index到最后view，所有view的滑动offset总量
     *
     * @param index
     */
    private int getViewsScrollOffset(int index) {
        int offset = 0;
        int count = getChildCount();
        for (int i = index; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE && ScrollUtils.isConsecutiveScrollerChild(child)) {
                offset += ScrollUtils.computeVerticalScrollOffset(child);
            }
        }
        return offset;
    }

    public boolean isAutoAdjustHeightAtBottomView() {
        return mAutoAdjustHeightAtBottomView;
    }

    public void setAutoAdjustHeightAtBottomView(boolean autoAdjustHeightAtBottomView) {
        if (mAutoAdjustHeightAtBottomView != autoAdjustHeightAtBottomView) {
            mAutoAdjustHeightAtBottomView = autoAdjustHeightAtBottomView;
            requestLayout();
        }
    }

    public int getAdjustHeightOffset() {
        return mAdjustHeightOffset;
    }

    public void setAdjustHeightOffset(int adjustHeightOffset) {
        if (mAdjustHeightOffset != adjustHeightOffset) {
            mAdjustHeightOffset = adjustHeightOffset;
            requestLayout();
        }
    }

    /**
     * 设置吸顶常驻
     *
     * @param isPermanent
     */
    public void setPermanent(boolean isPermanent) {
        if (mIsPermanent != isPermanent) {
            mIsPermanent = isPermanent;
            if (mAutoAdjustHeightAtBottomView) {
                requestLayout();
            } else {
                resetSticky();
            }
        }
    }

    public boolean isPermanent() {
        return mIsPermanent;
    }


    public boolean isDisableChildHorizontalScroll() {
        return mDisableChildHorizontalScroll;
    }

    /**
     * 禁用子view的水平滑动，如果ConsecutiveScrollerLayout下没有水平滑动的下级view，应该把它设置为true
     * 为true时，将不会分发滑动事件给子view，而是由ConsecutiveScrollerLayout处理，可以优化ConsecutiveScrollerLayout的滑动
     * 注意：如果你的ConsecutiveScrollerLayout下使用了ViewPager、HorizontalScrollView、水平滑动RecyclerView等，
     * 就不要设置disableChildHorizontalScroll为true.因为它会禁止水平滑动
     *
     * @param disableChildHorizontalScroll
     */
    public void setDisableChildHorizontalScroll(boolean disableChildHorizontalScroll) {
        this.mDisableChildHorizontalScroll = disableChildHorizontalScroll;
    }

    /**
     * 设置吸顶view到顶部的偏移量，允许吸顶view在距离顶部offset偏移量的地方吸顶停留。
     *
     * @param offset
     */
    public void setStickyOffset(int offset) {
        if (mStickyOffset != offset) {
            mStickyOffset = offset;
            resetSticky();
        }
    }

    public int getStickyOffset() {
        return mStickyOffset;
    }

    /**
     * 获取正在吸顶的view
     *
     * @return
     */
    public View getCurrentStickyView() {
        return mCurrentStickyView;
    }

    /**
     * 常驻模式下，获取正在吸顶的view
     *
     * @return
     */
    public List<View> getCurrentStickyViews() {
        return mCurrentStickyViews;
    }

    /**
     * 判断子view是否是吸顶状态
     *
     * @param child
     * @return
     */
    public boolean theChildIsStick(View child) {
        return (!mIsPermanent && mCurrentStickyView == child)
                || (mIsPermanent && mCurrentStickyViews.contains(child));
    }

    public OnStickyChangeListener getOnStickyChangeListener() {
        return mOnStickyChangeListener;
    }

    /**
     * 普通吸顶模式,监听吸顶变化
     *
     * @param l
     */
    public void setOnStickyChangeListener(OnStickyChangeListener l) {
        this.mOnStickyChangeListener = l;
    }

    public OnPermanentStickyChangeListener getOnPermanentStickyChangeListener() {
        return mOnPermanentStickyChangeListener;
    }

    /**
     * 常驻吸顶模式,监听吸顶变化
     *
     * @param l
     */
    public void setOnPermanentStickyChangeListener(OnPermanentStickyChangeListener l) {
        this.mOnPermanentStickyChangeListener = l;
    }

    /**
     * 滑动监听
     */
    public interface OnScrollChangeListener {
        void onScrollChange(View v, int scrollY, int oldScrollY, int scrollState);
    }

    /**
     * 监听吸顶变化
     */
    public interface OnStickyChangeListener {
        /**
         * @param oldStickyView 旧的吸顶view，可能为空
         * @param newStickyView 新的吸顶view，可能为空
         */
        void onStickyChange(@Nullable View oldStickyView, @Nullable View newStickyView);
    }

    /**
     * 监听常驻吸顶变化
     */
    public interface OnPermanentStickyChangeListener {

        /**
         * @param mCurrentStickyViews 正在吸顶的view
         */
        void onStickyChange(@NonNull List<View> mCurrentStickyViews);

    }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return mChildHelper.startNestedScroll(axes, type);
    }

    @Override
    public void stopNestedScroll(int type) {
        mChildHelper.stopNestedScroll(type);
    }

    @Override
    public void stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return mChildHelper.hasNestedScrollingParent(type);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                offsetInWindow);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type) {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow, int type) {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        boolean isNestedScroll = false;
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp instanceof LayoutParams) {
            isNestedScroll = ((LayoutParams) lp).isNestedScroll;
        }
        if (isNestedScroll) {
            return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        } else {
            return false;
        }
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        mParentHelper.onNestedScrollAccepted(child, target, axes, type);
        checkTargetsScroll(false, false);
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type);
        interceptAnimatorByAction(MotionEvent.ACTION_DOWN);
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        mParentHelper.onStopNestedScroll(target, type);
        stopNestedScroll(type);
        springBack();
        if (mReboundAnimator == null) {
            checkSnap();
        }
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        onNestedScrollInternal(dyUnconsumed, type);
    }

    private void onNestedScrollInternal(int dyUnconsumed, int type) {
        final int oldScrollY = mSecondScrollY;
        dispatchScroll(dyUnconsumed);
        final int myConsumed = mSecondScrollY - oldScrollY;
        final int myUnconsumed = dyUnconsumed - myConsumed;
        mChildHelper.dispatchNestedScroll(0, myConsumed, 0, myUnconsumed, null, type);
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return onStartNestedScroll(child, target, nestedScrollAxes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        onNestedScrollAccepted(child, target, nestedScrollAxes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onStopNestedScroll(View target) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
                               int dyUnconsumed) {
        onNestedScrollInternal(dyUnconsumed, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public int getNestedScrollAxes() {
        return mParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        dispatchNestedPreScroll(dx, dy, consumed, null, type);
    }

    @Override
    public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        if (!consumed) {
            dispatchNestedFling(0, velocityY, true);
            fling((int) velocityY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    /**
     * @return 是否开启了越界滑动
     */
    public boolean isEnableOverDragMode() {
        return mOverDragMode || mOverDragMaxDistanceOfTop > 0 || mOverDragMaxDistanceOfBottom > 0;
    }

    /**
     * 配置越界滑动
     * 若未设置越界滑动距离，则默认最大滑动距离为180dp
     *
     * @param enable 是否启用
     */
    public void enableOverDragMode(boolean enable) {
        mOverDragMode = enable;
        if (enable) {
            int defaultDistance = Util.dp2px(180);
            if (mOverDragMaxDistanceOfTop <= 0) {
                mOverDragMaxDistanceOfTop = defaultDistance;
            }
            if (mOverDragMaxDistanceOfBottom <= 0) {
                mOverDragMaxDistanceOfBottom = defaultDistance;
            }
        } else {
            mOverDragMaxDistanceOfTop = 0;
            mOverDragMaxDistanceOfBottom = 0;
        }
    }

    /**
     * 配置越界滑动
     *
     * @param enable         是否启用
     * @param topDistance    顶部最大越界滑动距离，单位px
     * @param bottomDistance 底部最大越界滑动距离，单位px
     */
    public void enableOverDragMode(boolean enable, int topDistance, int bottomDistance) {
        mOverDragMode = enable;
        if (enable) {
            mOverDragMaxDistanceOfTop = topDistance;
            mOverDragMaxDistanceOfBottom = bottomDistance;
        } else {
            mOverDragMaxDistanceOfTop = 0;
            mOverDragMaxDistanceOfBottom = 0;
        }
    }

    /**
     * 设置顶部最大越界滑动距离
     *
     * @param distance 最大越界滑动距离，单位px
     */
    public void setOverDragMaxDistanceOfTop(int distance) {
        if (!isEnableOverDragMode()) {
            enableOverDragMode(true, distance, mOverDragMaxDistanceOfBottom);
        } else {
            mOverDragMaxDistanceOfTop = distance;
        }
    }

    /**
     * 设置底部最大越界滑动距离
     *
     * @param distance 最大越界滑动距离，单位px
     */
    public void setOverDragMaxDistanceOfBottom(int distance) {
        if (!isEnableOverDragMode()) {
            enableOverDragMode(true, mOverDragMaxDistanceOfTop, distance);
        } else {
            mOverDragMaxDistanceOfBottom = distance;
        }
    }

    /**
     * 设置越界拖动阻尼
     *
     * @param rate 阻尼
     */
    public void setOverDragRate(float rate) {
        mDragRate = rate;
    }

    public void setSnapPoints(List<Integer> snapPoints) {
        mSnapPoints = snapPoints;
    }

    public void addSnapPoint(int point) {
        if (mSnapPoints == null) {
            mSnapPoints = new ArrayList<>();
        }
        mSnapPoints.add(point);
    }

    public void clearSnapPoints() {
        if (mSnapPoints != null) {
            mSnapPoints.clear();
        }
    }

    private void checkSnap() {
        if (mSnapPoints != null && !mSnapPoints.isEmpty()) {
            int scrollY = mSecondScrollY; // Use mSecondScrollY for accurate comparison
            int minDistance = Integer.MAX_VALUE;
            int targetPoint = -1;
            int maxSnapPoint = Integer.MIN_VALUE;

            for (int point : mSnapPoints) {
                if (point > maxSnapPoint) {
                    maxSnapPoint = point;
                }
                int distance = Math.abs(scrollY - point);
                if (distance < minDistance) {
                    minDistance = distance;
                    targetPoint = point;
                }
            }

            // Fix for bug: List jumps back to top after scrolling finishes.
            // Strategy:
            // 1. Only snap if the distance is very short (e.g., < 60dp), making it feel like a gentle magnetic effect.
            // 2. If the current scroll position is far beyond the last snap point (deep in the content), disable snapping completely.
            int snapThreshold = Util.dp2px(60);
            if (snapThreshold <= 0) snapThreshold = 150; // Fallback safe value

            // If we are in the "deep water" zone (far below the last snap point), do not snap.
            if (scrollY > maxSnapPoint + snapThreshold) {
                return;
            }

            if (targetPoint != -1 && targetPoint != scrollY && minDistance < snapThreshold) {
                smoothScrollToY(targetPoint);
            }
        }
    }

    /**
     * Smoothly scroll to a specific Y position (total offset).
     *
     * @param y Target Y position (total offset)
     */
    public void smoothScrollToY(int y) {
        int dy = y - mSecondScrollY;
        if (dy == 0) return;

        // Calculate a reasonable duration
        int duration = 500;
        int absDy = Math.abs(dy);
        if (absDy < 100) {
            duration = 200;
        } else if (absDy > mScreenHeightPixels) {
            duration = 600;
        } else {
            // Linear interpolation between 200 and 600
            float ratio = (float) absDy / mScreenHeightPixels;
            duration = 200 + (int)(ratio * 400);
        }

        mScroller.startScroll(0, mSecondScrollY, 0, dy, duration);
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH);
        setScrollState(SCROLL_STATE_SETTLING);
        mLastScrollerY = mSecondScrollY;
        invalidate();
    }

}
