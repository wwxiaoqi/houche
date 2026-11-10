package com.wwxiaoqi.gongjiao;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * 系统栏高度（像素）—— 边到边适配的唯一来源。
 *
 * 写法照观色（`guanshe/ui/Bars.java`）那套，因为它解决了我踩的两个坑：
 *
 * 1. **主动读，不靠回调**。`Skin.padToBars` 依赖 `OnApplyWindowInsetsListener`，
 *    而视图是**后加**到容器里的 —— 回调在它挂到窗口之前就请求过了，等于没收到 insets，
 *    结果就是标题压在状态栏上 / 底栏贴着屏幕最下沿。这里改成在需要的时候主动读一次，时序无关。
 * 2. **底部取并集**。手势导航下有些 ROM 的 `systemBars.bottom` 是 0，但手势条其实还要占位置，
 *    所以取 `systemBars ∪ tappableElement` 的 bottom，谁大听谁的。
 *
 * 拿不到实时的就退系统 dimen（`status_bar_height` / `navigation_bar_height`），再不行给个 dp 兜底。
 */
public final class Bars {

    private Bars() {
    }

    public static int top(Activity a) {
        WindowInsets in = insets(a);
        if (in != null) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return in.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()).top;
            }
            return in.getSystemWindowInsetTop();
        }
        return dimen(a, "status_bar_height", 24);
    }

    public static int bottom(Activity a) {
        WindowInsets in = insets(a);
        if (in != null) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // 有些 ROM 在手势导航下把 systemBars.bottom / tappableElement.bottom 都报 0，
                // 所以再问 navigationBars 与 stableInset，四个取最大。
                int sys = in.getInsets(WindowInsets.Type.systemBars()).bottom;
                int tap = in.getInsets(WindowInsets.Type.tappableElement()).bottom;
                int nav = in.getInsets(WindowInsets.Type.navigationBars()).bottom;
                int stable = in.getStableInsetBottom();
                return Math.max(Math.max(sys, tap), Math.max(nav, stable));
            }
            return in.getSystemWindowInsetBottom();
        }
        return dimen(a, "navigation_bar_height", 48);
    }

    /** 先问 DecorView 的实时 insets；拿不到退回窗口度量（Android 11+）。 */
    private static WindowInsets insets(Activity a) {
        try {
            WindowInsets in = a.getWindow().getDecorView().getRootWindowInsets();
            if (in != null) return in;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return a.getWindowManager().getCurrentWindowMetrics().getWindowInsets();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int dimen(Context c, String name, int fallbackDp) {
        int id = c.getResources().getIdentifier(name, "dimen", "android");
        if (id > 0) {
            int px = c.getResources().getDimensionPixelSize(id);
            if (px > 0) return px;
        }
        return Math.round(fallbackDp * c.getResources().getDisplayMetrics().density);
    }

    /**
     * 按页面底色决定状态栏/导航栏图标明暗。
     *
     * **必须先 getDecorView()**：API 37（Android 16）上 `PhoneWindow.getInsetsController()`
     * 在 DecorView 还是 null 时直接抛 NullPointerException —— `Skin.systemBars()` 走的正是这条路。
     * 没挂到窗口时先记一次，post 之后补设（那时才真正生效）。
     */
    public static void icons(final Activity a, final int pageBg) {
        try {
            final View decor = a.getWindow().getDecorView();
            Skin.systemBars(a, pageBg);
            if (!decor.isAttachedToWindow()) {
                decor.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Skin.systemBars(a, pageBg);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
            // 系统栏图标是锦上添花，任何机型差异都不该让页面起不来
        }
    }

    /** 兼容：有些地方只要 WindowManager。 */
    public static WindowManager wm(Activity a) {
        return a.getWindowManager();
    }
}
