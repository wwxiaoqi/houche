package com.wwxiaoqi.gongjiao;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生外壳 + WebView 内容（架构 A）。
 *
 *   [原生顶栏 56] 城市 ▾ | ( 搜索 ) | 刷新
 *   [原生 tabbar 48] 附近线路 / 最近使用 / 路线规划   ← 墨胶囊滑动
 *   [WebView]       车来了 H5（headless：它自己的顶栏与 tab 被注入隐藏）
 *
 * 设计依据：review/runs/shell-design/report.md（观色理念翻译成 dp 与色值）
 *          review/runs/tabbar-arch/report.md（为什么是 A、时序、哪些待真机验证）
 */
public class MainActivity extends Activity {

    // ---------- 令牌（与 shell-design §0 对齐） ----------
    // 中性纸面：与网页自己的底色（#F5F5F5）同一族，避免顶栏/内容交界出现冷暖色差
    private static final int C_BG = 0xFFF5F5F5;
    private static final int C_CARD = 0xFFFFFFFF;
    private static final int C_STROKE = 0xFFE5E5E5;
    private static final int C_SEP = 0xFFEDEDED;
    private static final int C_TEXT = 0xFF17171A;
    private static final int C_TEXT2 = 0xFF5B5B5F;
    private static final int C_TEXT3 = 0xFF9A9A9E;
    private static final int C_INK = 0xFF17171A;
    private static final int C_LINE = 0xFF2F6B47;

    private static final String SP = "gj";
    private static final String K_SITE = "site";
    private static final String K_SHIELD = "shield";
    private static final String K_THEME = "theme_on";

    /**
     * 默认入口 = **旧版抓包里那条 URL 一字不差**：
     *   /ch5/index.html?src=webapp_meizu_map&utm_source=...&utm_medium=menu
     *
     * 走过的两个坑（都别再踩）：
     *  · customer_ch5 是「客户定制入口」，非合作渠道会退成「下载APP体验」引导页（不是内容）
     *  · src=wechat_shaoguan 是城市锁定渠道，城市表只给韶关 1 个
     */
    private static final String DEFAULT_SITE =
            "https://web.chelaile.net.cn/ch5/index.html?src=webapp_meizu_map&utm_source=webapp_meizu_map&utm_medium=menu";
    private static final String[] TABS = {"附近线路", "最近使用", "路线规划"};
    private static final String[] HASH = {"#!/linearound", "#!/recent", "#!/linetransfer"};

    private FrameLayout root;
    private LinearLayout column;
    private FrameLayout contentHost;
    private ProgressBar bar;
    private WebView web;

    private TextView cityText;
    private EditText searchInput;
    private TextView searchGo;
    private android.widget.ImageView clearBtn;
    private boolean needCityPick;        // 还没选过城市：首启跳一次城市页
    private boolean redirectedToCity;    // 本次进程已经跳过，绝不重复跳（避免死循环）
    /** 网页当前路由，由注入脚本回传（形如 #!/recent） */
    private String curHash = "";
    private int tabBeforeSearch = -1;    // 进搜索前所在的 tab，退出搜索时还原
    private View topBarView;
    private View tabBarView;
    private View offlineView;
    private boolean pageLoadedOk;
    private FrameLayout fab;
    private long lastBackAt;
    private TextView refreshIcon;
    private android.widget.ImageView refreshImgRef;
    /** 从网页里读到的品牌色（默认先给车来了的绿，读到后覆盖） */
    private int accent = 0xFF3B82F6;      // 车来了 web 的蓝；页面探测到自己的强调色后会自动覆盖
    private LinearLayout tabbar;
    private View pill;
    private final TextView[] tabViews = new TextView[3];
    private int tab = 0;

    private ValueCallback<Uri[]> filePathCallback;
    private android.webkit.GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private android.webkit.PermissionRequest pendingPermissionRequest;
    private static final int REQ_PERM = 2001;

    // ---------- 生命周期 ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Skin.setupWindow(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(C_BG);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topBarView = buildTopBar();
        column.addView(topBarView, lp(56));
        tabBarView = buildTabBar();
        column.addView(tabBarView, lp(48));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setVisibility(View.GONE);
        column.addView(bar, lp(2));

        contentHost = new FrameLayout(this);
        column.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // 底部避让由 applyEdgeToEdge() 统一处理（见那个方法）

        web = new WebView(this);
        contentHost.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 刷新按钮：网页那个是位图（放大就糊），所以自己画一个——矢量图标 + 主色圆底
        fab = new FrameLayout(this);
        fab.setBackground(circle(accent));
        FrameLayout.LayoutParams fabImg = new FrameLayout.LayoutParams(dp(22), dp(22));
        fabImg.gravity = Gravity.CENTER;
        fab.addView(icon(R.drawable.lc_refresh, 0xFFFFFFFF, 22), fabImg);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!online()) {
                    showOffline();
                    return;
                }
                hideOffline();
                web.reload();
            }
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56));
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = dp(20);
        fab.setLayoutParams(flp);
        contentHost.addView(fab, flp);

        setContentView(root);
        applyEdgeToEdge();          // 沉浸式：顶栏底色铺到状态栏后面，内容自己避让
        try {
            Bars.icons(this, C_BG);        // 浅底 → 状态栏深色图标（内部已兜 API37 NPE）
        } catch (Throwable ignored) {
        }

        setupWeb();
        askLocationOnly();
        syncTabBar(false);

        needCityPick = sp().getString("city_picked", "").isEmpty();
        if (cityText != null) cityText.setText(sp().getString("city_name", "选择城市"));
        if (!online()) {
            showOffline();          // 没网：显示原生离线页，不亮 WebView
        } else {
            load(tab);
        }
    }

    private LinearLayout.LayoutParams lp(int h) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /**
     * 沉浸式（edge-to-edge）适配。
     *
     * 与「整窗加 padding」的区别：
     *  · 旧做法：根布局 paddingTop = 状态栏高度 —— 状态栏区域是 root 的底色，顶栏底色到不了顶，
     *    视觉上像被切了一刀（而且底部手势条压根没处理）。
     *  · 现在：**顶栏背景铺到屏幕最顶**，只把「内容」下移一个状态栏高度；底部同理把内容抬起来。
     *
     * 注意：必须 setContentView 之后再挂监听（DecorView 未就绪时碰 WindowInsetsController 会 NPE）。
     */
    /**
     * 沉浸式（edge-to-edge）适配 —— 写法照观色（guanshe/ui/Bars.java）。
     *
     * 两步：**先主动读一次 insets 就摆好**（时序无关，这是关键），
     * 再把监听挂到 `android.R.id.content`（框架容器，一定在窗口里）处理旋转/分屏/键盘的变化。
     *
     * 之前只靠 OnApplyWindowInsetsListener 挂在自己的 root 上，回调时机常常早于视图真正挂载，
     * 于是出现「顶栏压状态栏、底栏贴屏幕最下沿」。
     */
    private void applyEdgeToEdge() {
        if (root == null) return;
        layoutForInsets(Bars.top(this), Bars.bottom(this));

        View host = findViewById(android.R.id.content);
        if (host != null) {
            host.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                    int top, bottom;
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets b = in.getInsets(
                                android.view.WindowInsets.Type.systemBars()
                                        | android.view.WindowInsets.Type.displayCutout());
                        top = b.top;
                        bottom = Math.max(b.bottom,
                                in.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom);
                    } else {
                        top = in.getSystemWindowInsetTop();
                        bottom = in.getSystemWindowInsetBottom();
                    }
                    layoutForInsets(top, bottom);
                    return in;
                }
            });
            host.requestApplyInsets();
        }
    }

    private int lastTopInset = -1, lastBottomInset = -1;

    /**
     * 顶栏：高度 = 56 + 状态栏，paddingTop = 状态栏 → 底色铺到屏幕最顶，内容仍落在 56dp 里。
     * 内容区：paddingBottom = 手势条/导航栏 → 网页那条固定底栏被抬进安全区。
     */
    private void layoutForInsets(int top, int bottom) {
        if (top < 0) top = 0;
        if (bottom < 0) bottom = 0;
        boolean changed = (top != lastTopInset) || (bottom != lastBottomInset);
        lastTopInset = top;
        lastBottomInset = bottom;

        if (topBarView != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) topBarView.getLayoutParams();
            int want = dp(56) + top;
            if (lp.height != want) {
                lp.height = want;
                topBarView.setLayoutParams(lp);
            }
            topBarView.setPadding(dp(18), top, dp(6), 0);
        }
        // WebView **铺满整屏**（含手势条区域），把安全区数值交给页面自己处理：
        //   body 加 padding-bottom → 列表最后一项不被遮
        //   固定底栏加 padding-bottom → 它的白底铺到屏幕最底，按钮仍在手势条之上
        if (web != null) {
            web.evaluateJavascript("try{window.__GJ_SAFE&&window.__GJ_SAFE(" + bottom + ")}catch(e){}", null);
        }
        // 刷新球：底边 = 20dp + 手势条 → 抬到拇指容易够到的位置，不压在手势区里
        if (fab != null) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) fab.getLayoutParams();
            int want = dp(20) + bottom;
            if (flp.bottomMargin != want) {
                flp.bottomMargin = want;
                fab.setLayoutParams(flp);
            }
        }
        if (changed) Bars.icons(this, C_BG);
    }

    private TextView text(String s, float sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        if (medium) t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        return t;
    }

    /** 标准图标（Lucide 矢量，随字符颜色着色），触控区仍由外层给 48dp */
    private android.widget.ImageView icon(int res, int color, float sizeDp) {
        android.widget.ImageView v = new android.widget.ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(color);
        v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    /** 正圆（刷新球用）。之前用 round(...,26) 画 56dp 的方块 —— 半径该是 28，看着就不圆。 */
    private GradientDrawable circle(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    private GradientDrawable round(int fill, int stroke, float radiusDp, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return d;
    }

    // ---------- 原生顶栏 56dp ----------

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_BG);
        bar.setPadding(dp(18), 0, dp(6), 0);

        LinearLayout cityBox = new LinearLayout(this);
        cityBox.setGravity(Gravity.CENTER_VERTICAL);
        cityBox.setMinimumWidth(dp(48));
        cityText = text("韶关", 15, C_TEXT, true);
        cityBox.addView(cityText);
        android.widget.ImageView caret = icon(R.drawable.lc_chevron_down, C_TEXT3, 16);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(16), dp(16));
        clp.leftMargin = dp(4);
        cityBox.addView(caret, clp);
        cityBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCityPageInWeb();
            }
        });
        // 长按城市名 = 原来的长按菜单（改网址 / 主题 / 清空屏蔽 / 刷新 / 浏览器打开）
        cityBox.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showToolMenu();
                return true;
            }
        });
        bar.addView(cityBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        // 搜索：**就地输入**（不先跳页）。点一下就有焦点、弹键盘；回车后把关键词塞进网页搜索页
        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(round(C_CARD, C_STROKE, 20, 1));
        search.setPadding(dp(14), 0, dp(6), 0);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(18), dp(18));
        search.addView(icon(R.drawable.lc_search, C_TEXT3, 18), ilp);

        searchInput = new EditText(this);
        searchInput.setHint("搜索公交线路、车站、地点");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        searchInput.setTextColor(C_TEXT);
        searchInput.setHintTextColor(C_TEXT3);
        searchInput.setBackground(null);
        searchInput.setPadding(dp(8), 0, 0, 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeActionLabel("搜索", EditorInfo.IME_ACTION_SEARCH);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (clearBtn != null) clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (e != null && e.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                    submitSearch(v.getText().toString().trim());
                    return true;
                }
                return false;
            }
        });
        search.addView(searchInput, new LinearLayout.LayoutParams(0, dp(36), 1f));

        clearBtn = icon(R.drawable.lc_x, C_TEXT3, 16);
        clearBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(dp(16), dp(16));
        xlp.leftMargin = dp(6);
        xlp.rightMargin = dp(2);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (searchInput != null) searchInput.setText("");
                clearBtn.setVisibility(View.GONE);
            }
        });
        search.addView(clearBtn, xlp);

        TextView go = text("搜索", 13, accent, true);
        searchGo = go;
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(8), 0, dp(10), 0);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitSearch(searchInput.getText().toString().trim());
            }
        });
        search.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        // 点胶囊空白处也聚焦到输入框
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchInput.requestFocus();
                showKeyboard();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        slp.leftMargin = dp(8);
        slp.rightMargin = dp(8);
        bar.addView(search, slp);

        refreshIcon = null;
        LinearLayout refresh = new LinearLayout(this);
        refresh.setGravity(Gravity.CENTER);
        final android.widget.ImageView refreshImg = icon(R.drawable.lc_refresh, accent, 20);
        refreshImgRef = refreshImg;
        refresh.addView(refreshImg);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.reload();
            }
        });
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    // ---------- 原生 tabbar 48dp（墨胶囊滑动） ----------

    private View buildTabBar() {
        SwipeFrame wrap = new SwipeFrame(this);
        wrap.setBackgroundColor(C_BG);
        wrap.setOnSwipe(new SwipeFrame.OnSwipe() {
            @Override
            public void onSwipeLeft() {   // 手指往左划 → 下一个 tab
                if (tab < 2) onTabClicked(tab + 1);
            }

            @Override
            public void onSwipeRight() {  // 往右划 → 上一个 tab
                if (tab > 0) onTabClicked(tab - 1);
            }
        });

        // 顺序要紧：胶囊先加（在下），文字后加（在上）——上一版反了，选中 tab 的字被盖住
        pill = new View(this);
        pill.setBackground(round(accent, 0, 16, 0));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(96), dp(32));
        plp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        wrap.addView(pill, plp);

        tabbar = new LinearLayout(this);
        tabbar.setOrientation(LinearLayout.HORIZONTAL);
        tabbar.setClipChildren(false);
        wrap.addView(tabbar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = text(TABS[i], 15, C_TEXT2, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onTabClicked(idx);
                }
            });
            tabViews[i] = t;
            tabbar.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        // 底部 0.7dp 发丝（滚动时才显示，先常驻一条极淡的）
        View hair = new View(this);
        hair.setBackgroundColor(C_SEP);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
        hlp.gravity = Gravity.BOTTOM;
        wrap.addView(hair, hlp);

        // 等 tab 测量完再摆胶囊
        wrap.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (tabbar.getWidth() > 0) {
                            syncTabBar(false);
                            tabbar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    }
                });
        return wrap;
    }

    private void onTabClicked(int idx) {
        if (idx == tab) {
            web.scrollTo(0, 0);
            web.evaluateJavascript("window.scrollTo(0,0)", null);
            return;
        }
        tab = idx;
        syncTabBar(true);
        load(idx);
    }

    /** 胶囊滑到第 tab 个位置；animate=false 时直接跳（首帧/旋转后） */
    private void syncTabBar(boolean animate) {
        if (tabbar == null || tabViews[0] == null) return;
        int w = tabbar.getWidth();
        if (w <= 0) return;
        int cell = w / 3;
        TextView cur = tabViews[tab];
        int textW = (int) cur.getPaint().measureText(TABS[tab]);
        int pillW = Math.max(dp(72), textW + dp(28));
        int center = cell * tab + cell / 2;
        int left = center - pillW / 2;

        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) pill.getLayoutParams();
        if (plp.width != pillW) {
            plp.width = pillW;
            pill.setLayoutParams(plp);
        }
        if (animate) {
            pill.animate().translationX(left).setDuration(230)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
        } else {
            pill.setTranslationX(left);
        }
        for (int i = 0; i < 3; i++) {
            tabViews[i].setTextColor(i == tab ? 0xFFFFFFFF : C_TEXT2);
        }
        if (refreshImgRef != null) refreshImgRef.setColorFilter(accent);
    }

    // ---------- 内容：切 tab 只换 hash，不重载文档 ----------

    private void load(int idx) {
        if (web.getUrl() == null) {
            loadWithHeaders(site());
        } else {
            switchHash(HASH[idx]);
        }
    }

    /**
     * 切 tab 用 replaceState（不新增历史记录）。
     * 之前用 location.hash 每切一次就压一条历史，导致「在第二页操作后回第一页，按返回却回到第二页」。
     * Angular 的 $location 监听 hashchange，所以 replaceState 之后手工派发一次。
     */
    private void switchHash(final String hash) {
        web.evaluateJavascript(
                "(function(){var h=" + JSONObject.quote(hash) + ";try{"
                        + "history.replaceState(null,'',h);"
                        + "var e=new Event('hashchange');window.dispatchEvent(e);"
                        + "}catch(x){location.hash=h;}})()", null);
        curHash = hash;
        updateFab(hash);
    }

    /** 主文档带上 X-Requested-With（与旧版一致），减少被当成"外部浏览器"的概率 */
    private void loadWithHeaders(String url) {
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("X-Requested-With", getPackageName());
        h.put("Referer", url);
        web.loadUrl(url, h);
    }

    /** 城市页就用网页自己的那一屏（用户要求：换成 webview 的） */
    /** 长按城市名弹出的工具菜单（原来挂在长按网页上，用户要求关掉长按菜单后搬到这里） */
    private void showToolMenu() {
        final String[] items = {"改网址", "用浏览器打开当前页", "刷新页面",
                themeOn() ? "切回车来了原样" : "套用纸面主题",
                "清空屏蔽规则（" + shieldRules().size() + " 条）"};
        new AlertDialog.Builder(this)
                .setTitle("工具")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                askSite();
                                break;
                            case 1: {
                                String u = web.getUrl();
                                if (u != null && u.startsWith("http")) openExternal(u);
                                break;
                            }
                            case 2:
                                web.reload();
                                break;
                            case 3:
                                setTheme(!themeOn());
                                break;
                            case 4:
                                sp().edit().putString(K_SHIELD, "").apply();
                                applyShield();
                                toast("屏蔽规则已清空");
                                break;
                        }
                    }
                })
                .show();
    }

    private void openCityPageInWeb() {
        switchHash("#!/switchcity");
    }

    /** 打开网页搜索页，并把它的输入框聚焦、唤起键盘（用户要求「默认聚焦」） */
    private void openSearch() {
        web.evaluateJavascript("location.hash='#!/linesearch'", null);
        web.requestFocus();
        focusSearchDelayed(0);
    }

    private void focusSearchDelayed(final int attempt) {
        web.postDelayed(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("try{window.__GJ_FOCUS_SEARCH&&window.__GJ_FOCUS_SEARCH()}catch(e){}", null);
                try {
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager)
                                    getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(web, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                } catch (Exception ignored) {
                }
                if (attempt < 3) focusSearchDelayed(attempt + 1);   // 页面路由切换有延迟，多补几次
            }
        }, 350);
    }

    /** 有没有网（没有就别把 WebView 亮出来，免得白屏） */
    private boolean online() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    /** 原生离线页：一句话 + 重试，绝不显示白屏的 WebView */
    private void showOffline() {
        if (offlineView == null) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackgroundColor(C_BG);
            TextView t1 = text("现在没有网络", 17, C_TEXT, true);
            t1.setGravity(Gravity.CENTER);
            TextView t2 = text("连上网就会自动显示附近的公交", 12, C_TEXT2, false);
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, dp(10), 0, dp(18));
            TextView retry = text("重试", 14, 0xFFFFFFFF, true);
            retry.setGravity(Gravity.CENTER);
            retry.setBackground(round(accent, 0, 12, 0));
            retry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (online()) {
                        hideOffline();
                        web.reload();
                    } else {
                        toast("还是没网");
                    }
                }
            });
            box.addView(t1, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(t2, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(retry, new LinearLayout.LayoutParams(dp(120), dp(44)));
            offlineView = box;
            contentHost.addView(offlineView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        offlineView.setVisibility(View.VISIBLE);
        if (web != null) web.setVisibility(View.INVISIBLE);
        if (fab != null) fab.setVisibility(View.GONE);
    }

    private void hideOffline() {
        if (offlineView != null) offlineView.setVisibility(View.GONE);
        if (web != null) web.setVisibility(View.VISIBLE);
        if (fab != null) fab.setVisibility(View.VISIBLE);
    }

    private void showKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception ignored) {
        }
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    /**
     * 就地输入的关键词交给网页搜索页：跳过去 → 等它的输入框出现 → 写值 + 派发 input 事件
     * （Angular 的 ng-model 是靠 input 事件同步的，只改 value 没用）。
     */
    private void submitSearch(final String kw) {
        hideKeyboard();
        if (tabBeforeSearch < 0) tabBeforeSearch = tab;
        switchHash("#!/linesearch");
        if (kw == null || kw.length() == 0) {
            web.postDelayed(new Runnable() {
                @Override
                public void run() {
                    web.evaluateJavascript("try{window.__GJ_FOCUS_SEARCH&&window.__GJ_FOCUS_SEARCH()}catch(e){}", null);
                }
            }, 350);
            return;
        }
        final String js = "(function(){var n=0;var t=setInterval(function(){"
                + "var i=document.querySelector('.search-ui-wrap input')||document.querySelector('input[type=search]')||document.querySelector('input.search-input');"
                + "if(i){i.value=" + JSONObject.quote(kw) + ";"
                + "try{i.dispatchEvent(new Event('input',{bubbles:true}));}catch(e){}"
                + "try{i.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}"
                + "try{i.focus();}catch(e){}clearInterval(t);}"
                + "if(++n>25)clearInterval(t);},120);})()";
        web.postDelayed(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript(js, null);
            }
        }, 320);
    }

    /** 选城市：记住名字/id，并把地址里的 src=wechat_xxx 换成这个城市的拼音；再尽力写一次 localStorage */
    /** 选城市：记住名字/id；**不动 src**（src 是渠道不是城市），改为写网页自己的 localStorage 再重载 */
    private void pickCity(String name, String id, String pinyin) {
        sp().edit().putString("city_name", name)
                .putString("city_id", id)
                .putString("city_pinyin", pinyin == null ? "" : pinyin)
                .apply();
        if (cityText != null) cityText.setText(name);
        if (id != null && id.length() > 0) {
            String js = "try{"
                    + "localStorage.setItem('che_h5_activeCity'," + JSONObject.quote(id) + ");"
                    + "localStorage.setItem('che_h5_activeCityName'," + JSONObject.quote(name) + ");"
                    + "localStorage.setItem('activeCity'," + JSONObject.quote(id) + ");"
                    + "}catch(e){}";
            web.evaluateJavascript(js, null);
        }
        loadWithHeaders(site());
        toast("已切换到 " + name);
    }

    // ---------- WebView ----------

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        // 千万别去掉 UA 里的 "; wv"：车来了 H5 靠它判断"在 App 里内嵌"，
        // 去掉就会给「下载APP体验」的引导落地页（上一版就是这么翻车的）。

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        if (isDebuggable()) WebView.setWebContentsDebuggingEnabled(true);

        // 用户要求：长按不再弹任何菜单。WebView 自带的长按（选字/存图）也一并关掉。
        web.setLongClickable(false);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;      // 吃掉，不往页面传
            }
        });
        web.setHapticFeedbackEnabled(false);

        web.addJavascriptInterface(new Bridge(), "GJ");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sc = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
                if (sc.startsWith("http")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap icon) {
                pageLoadedOk = false;
                bar.setVisibility(View.VISIBLE);
                bar.setProgress(5);
                inject();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageLoadedOk = true;
                updateFab(curHash);
                bar.setVisibility(View.GONE);
                inject();
                if (needCityPick && !redirectedToCity && (url == null || !url.contains("switchcity"))) {
                    redirectedToCity = true;      // 只跳这一次
                    web.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript("location.hash='#!/switchcity'", null);
                        }
                    }, 500);
                }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, android.webkit.WebResourceError e) {
                if (req != null && req.isForMainFrame()) {
                    pageLoadedOk = false;
                    bar.setVisibility(View.GONE);
                    if (!online()) showOffline();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                bar.setProgress(p);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           android.webkit.GeolocationPermissions.Callback cb) {
                if (hasLocation()) cb.invoke(origin, true, false);
                else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = cb;
                    askRuntimePermissions();
                }
            }

            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 权限已精简掉相机/麦克风：直接拒绝，网页走它自己的降级路径
                        request.deny();
                    }
                });
            }

            @Override
            public boolean onCreateWindow(WebView v, boolean d, boolean g, Message msg) {
                WebView.HitTestResult r = v.getHitTestResult();
                String extra = r == null ? null : r.getExtra();
                if (extra != null) {
                    v.loadUrl(extra);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), 1001);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    android.app.DownloadManager dm =
                            (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    android.app.DownloadManager.Request req =
                            new android.app.DownloadManager.Request(Uri.parse(url));
                    req.setNotificationVisibility(
                            android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                            android.webkit.URLUtil.guessFileName(url, cd, mime));
                    dm.enqueue(req);
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---------- 注入：主题 + 隐藏网页自己的顶栏与 tab ----------

    private String injectCode, injectCss;

    private String asset(String p) {
        if (injectCode == null || injectCss == null) {
            injectCode = "";
            injectCss = "";
            try {
                injectCode = readAsset("inject/inject.js");
                injectCss = readAsset("inject/theme.css");
            } catch (Exception ignored) {
            }
        }
        return "inject/theme.css".equals(p) ? injectCss : injectCode;
    }

    private String readAsset(String p) throws java.io.IOException {
        java.io.InputStream in = getAssets().open(p);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private void inject() {
        if (web == null) return;
        probeAccent();
        String js = asset("inject/inject.js");
        if (js == null || js.length() == 0) return;
        web.evaluateJavascript("(function(){" + js
                + "\n;try{window.__GJ_APPLY&&window.__GJ_APPLY(" + JSONObject.quote(shieldCss()) + ");"
                + "window.__GJ_THEME&&window.__GJ_THEME(" + JSONObject.quote(asset("inject/theme.css"))
                + "," + (themeOn() ? "true" : "false") + ");}catch(e){}})();", null);
    }

    /** 从网页读品牌色：先看它被隐藏的那条顶栏，再退到任何 bg-* 元素 */
    private void probeAccent() {
        // 挑网页自己的强调色：候选按优先级排（当前 tab 的文字色 > meta theme-color > 高饱和的 bg-*）
        web.evaluateJavascript(
                "(function(){try{"
                        + "function bad(x){return !x||x==='rgba(0, 0, 0, 0)'||x==='transparent';}"
                        + "function sat(c){var m=/(\\d+)\\D+(\\d+)\\D+(\\d+)/.exec(c||'');if(!m)return 0;"
                        + "var r=+m[1],g=+m[2],b=+m[3];var mx=Math.max(r,g,b),mn=Math.min(r,g,b);"
                        + "return mx===0?0:(mx-mn)/mx;}"
                        + "var cands=[];"
                        + "var t=document.querySelector('.tab-list .tab-item.active, .tab-item.active');"
                        + "if(t)cands.push(getComputedStyle(t).color);"
                        + "var meta=document.querySelector('meta[name=theme-color]');"
                        + "if(meta)cands.push(meta.getAttribute('content'));"
                        + "['.around-refresh','.btn','.bg-blue','.bg-green','.bg-deepgreen','.fg-blue','.fg-green']"
                        + ".forEach(function(sel){var e=document.querySelector(sel);"
                        + "if(!e)return;var st=getComputedStyle(e);"
                        + "cands.push(st.backgroundColor);cands.push(st.color);});"
                        + "var best='';for(var i=0;i<cands.length;i++){var c=cands[i];"
                        + "if(bad(c))continue;if(sat(c)<0.25)continue;best=c;break;}"
                        + "if(best)GJ.accent(best);"
                        + "}catch(e){}})()", null);
    }

    private boolean themeOn() {
        return sp().getBoolean(K_THEME, true);
    }

    private void setTheme(boolean on) {
        sp().edit().putBoolean(K_THEME, on).apply();
        web.evaluateJavascript("try{window.__GJ_THEME&&window.__GJ_THEME("
                + JSONObject.quote(asset("inject/theme.css")) + "," + (on ? "true" : "false") + ")}catch(e){}", null);
        toast(on ? "已套用纸面主题" : "已恢复车来了原样");
    }

    // ---------- 屏蔽元素 ----------

    private List<String> shieldRules() {
        List<String> out = new ArrayList<String>();
        String raw = sp().getString(K_SHIELD, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String l : raw.split("\n")) if (l.trim().length() > 0) out.add(l.trim());
        return out;
    }

    private String shieldCss() {
        StringBuilder sb = new StringBuilder();
        for (String r : shieldRules()) sb.append(r).append("{display:none !important}\n");
        return sb.toString();
    }

    private void applyShield() {
        web.evaluateJavascript("try{window.__GJ_APPLY&&window.__GJ_APPLY("
                + JSONObject.quote(shieldCss()) + ")}catch(e){}", null);
    }

    private void addShieldRule(String selector) {
        if (selector == null) return;
        selector = selector.trim();
        if (selector.isEmpty() || selector.length() > 300) return;
        List<String> rules = shieldRules();
        if (rules.contains(selector)) return;
        rules.add(selector);
        StringBuilder sb = new StringBuilder();
        for (String r : rules) sb.append(r).append('\n');
        sp().edit().putString(K_SHIELD, sb.toString()).apply();
        applyShield();
        web.evaluateJavascript("try{window.__GJ_CLEAR&&window.__GJ_CLEAR()}catch(e){}", null);
        toast("已屏蔽：" + selector);
    }

    // ---------- 权限 ----------

    private boolean hasPerm(String p) {
        return checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocation() {
        return hasPerm(android.Manifest.permission.ACCESS_FINE_LOCATION)
                || hasPerm(android.Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** 冷启动只要定位（相机/录音等网页真要时再要），别一次弹四个对话框 */
    private void askLocationOnly() {
        if (hasLocation()) return;
        requestPermissions(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERM);
    }

    private void askRuntimePermissions() {
        List<String> want = new ArrayList<String>();
        if (!hasLocation()) {
            want.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            want.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (want.isEmpty()) {
            releasePending();
            return;
        }
        requestPermissions(want.toArray(new String[0]), REQ_PERM);
    }

    private void releasePending() {
        if (pendingGeoCallback != null && pendingGeoOrigin != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, hasLocation(), false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
        if (pendingPermissionRequest != null) {
            pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_PERM) releasePending();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1001) {
            if (filePathCallback != null) {
                Uri[] out = null;
                if (res == RESULT_OK && data != null && data.getData() != null) out = new Uri[]{data.getData()};
                filePathCallback.onReceiveValue(out);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    // ---------- 返回键 ----------

    @Override
    public void onBackPressed() {
        // 只有**确实停在城市页**时才把返回当作"不想选"；加载中/别的页面不能误判
        if (needCityPick && curHash != null && curHash.contains("switchcity")) {
            needCityPick = false;
            sp().edit().putString("city_picked", "1").apply();
            if (web != null) web.evaluateJavascript("location.hash='#!/linearound'", null);
            return;
        }
        // 搜索页 / 城市页：回到「进入它之前」的那个 tab
        if (curHash != null && (curHash.contains("linesearch") || curHash.contains("locationsearch"))) {
            int back = tabBeforeSearch >= 0 ? tabBeforeSearch : tab;
            tabBeforeSearch = -1;
            tab = back;
            syncTabBar(false);
            switchHash(HASH[back]);
            return;
        }
        // 详情类页面 → 回到它所在的 tab 根页（**不再用 web.goBack()**：
        //   历史栈里被首启的城市页污染过，goBack 会退到城市页）
        if (isDetailRoute(curHash)) {
            switchHash(HASH[tab]);
            return;
        }
        if (tab != 0) {
            onTabClicked(0);
            return;
        }
        // 不弹确认；改成「2 秒内再按一次才真退」——防误触又不打断
        long now = System.currentTimeMillis();
        if (now - lastBackAt < 2000) {
            finish();
            return;
        }
        lastBackAt = now;
        toast("再按一次退出");
    }

    // ---------- 桥 ----------

    private class Bridge {
        @JavascriptInterface
        public void accent(final String css) {
            final int c = parseColor(css);
            if (c == 0) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (c == accent) return;
                    accent = c;
                    if (pill != null) pill.setBackground(round(accent, 0, 16, 0));
                    if (fab != null) fab.setBackground(circle(accent));
                    if (refreshImgRef != null) refreshImgRef.setColorFilter(accent);
                    for (int i = 0; i < 3; i++) {
                        if (tabViews[i] != null) tabViews[i].setTextColor(i == tab ? 0xFFFFFFFF : C_TEXT2);
                    }
                }
            });
        }

        /** 网页把当前城市名报上来：原生顶栏与本地记录都跟着更新 */
        @JavascriptInterface
        public void cityName(final String name) {
            if (name == null || name.length() == 0 || name.length() > 8) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (cityText != null) cityText.setText(name);
                    sp().edit().putString("city_name", name).apply();
                }
            });
        }

        /** 用户点了城市格：立刻记为已选，避免再来回跳 */
        @JavascriptInterface
        public void cityPicked() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!needCityPick) return;
                    needCityPick = false;
                    sp().edit().putString("city_picked", "1").apply();
                }
            });
        }

        /** 网页路由变化：离开 switchcity 说明城市选完了，之后启动就直接进附近线路 */
        @JavascriptInterface
        public void hash(final String h) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (h == null) return;
                    curHash = h;
                    // 城市页与搜索页都是「独立动作」，不属于任何一格 tab → 只藏 tab 栏，顶栏留着
                    boolean standalone = h.contains("switchcity")
                            || h.contains("linesearch") || h.contains("locationsearch");
                    if (tabBarView != null) {
                        tabBarView.setVisibility(standalone ? View.GONE : View.VISIBLE);
                    }
                    if (topBarView != null) topBarView.setVisibility(View.VISIBLE);
                    if (!standalone) tabBeforeSearch = -1;   // 离开搜索/城市页，清掉记忆
                    updateFab(h);
                    // 只有「线路搜索页」藏掉网页自己的搜索条；位置搜索页必须留着输入框
                    boolean lineSearch = h.contains("linesearch") && !h.contains("locationsearch");
                    web.evaluateJavascript("try{window.__GJ_HIDE_SEARCHBAR&&window.__GJ_HIDE_SEARCHBAR("
                            + (lineSearch ? "true" : "false") + ")}catch(e){}", null);
                    if (h.contains("switchcity")) {
                        needCityPick = true;
                    } else if (needCityPick) {
                        needCityPick = false;
                        sp().edit().putString("city_picked", "1").apply();
                    }
                }
            });
        }

        @JavascriptInterface
        public void toastMsg(String s) {
            final String m = s == null ? "" : (s.length() > 120 ? s.substring(0, 120) : s);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toast(m);
                }
            });
        }
    }

    // ---------- 网址 ----------

    private SharedPreferences sp() {
        return getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    private String site() {
        String s = sp().getString(K_SITE, null);
        if (s == null || s.length() < 4) s = DEFAULT_SITE;
        if (!s.startsWith("http")) s = "https://" + s;
        return s;
    }

    private void askSite() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("https://");
        int p = dp(16);
        input.setPadding(p, p, p, p);
        String cur = sp().getString(K_SITE, DEFAULT_SITE);
        input.setText(cur);
        input.setSelection(cur.length());
        new AlertDialog.Builder(this)
                .setTitle("网址（含城市 src）")
                .setView(input)
                .setPositiveButton("打开", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String u = input.getText().toString().trim();
                        if (u.length() < 4) return;
                        if (!u.startsWith("http")) u = "https://" + u;
                        sp().edit().putString(K_SITE, u).apply();
                        web.loadUrl(u);
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    private void copy(String s) {
        if (s == null || s.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("gj", s));
        toast("已复制");
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("没有可用的浏览器");
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    /**
     * 原生刷新按钮的显隐规则（用户要求：自己规定好哪些界面显示）：
     *   显示：三个 tab 根页面（附近线路 / 最近使用 / 路线规划）
     *   不显示：城市页、搜索页、线路详情、站点详情（这些页面网页自己有底部栏或自带操作）
     *   另外：离线时、加载失败时也不显示
     */
    private void updateFab(String h) {
        if (fab == null) return;
        boolean tabRoot = (h == null || h.length() == 0
                || h.contains("linearound") || h.contains("recent") || h.contains("linetransfer"));
        boolean show = tabRoot && online() && (offlineView == null || offlineView.getVisibility() != View.VISIBLE);
        fab.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** 详情类路由：这些才值得走网页历史（列表类 tab 不走） */
    private boolean isDetailRoute(String h) {
        if (h == null) return false;
        return h.contains("linedetail") || h.contains("stationdetail") || h.contains("linesearch")
                || h.contains("switchcity") || h.contains("locationsearch") || h.contains("transferdetail");
    }

    /** "rgb(46, 204, 113)" / "rgba(...)" / "#2ecc71" → int；解析失败返回 0 */
    private static int parseColor(String v) {
        if (v == null) return 0;
        v = v.trim();
        try {
            if (v.startsWith("#")) {
                String h = v.substring(1);
                if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
                if (h.length() == 8) return (int) Long.parseLong(h, 16);
                return 0;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)").matcher(v);
            if (m.find()) {
                int r = Integer.parseInt(m.group(1));
                int g = Integer.parseInt(m.group(2));
                int b = Integer.parseInt(m.group(3));
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /** 能识别左右划的 FrameLayout：在 tabbar 上左右滑就切 tab（不影响网页里的横向滚动） */
    static class SwipeFrame extends FrameLayout {
        interface OnSwipe {
            void onSwipeLeft();

            void onSwipeRight();
        }

        private OnSwipe cb;
        private float downX, downY;
        private boolean horizontal;

        SwipeFrame(android.content.Context c) {
            super(c);
        }

        void setOnSwipe(OnSwipe cb) {
            this.cb = cb;
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    horizontal = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE: {
                    float dx = Math.abs(e.getX() - downX);
                    float dy = Math.abs(e.getY() - downY);
                    float th = 24 * getResources().getDisplayMetrics().density;
                    if (dx > th && dx > dy * 1.4f) {
                        horizontal = true;
                        return true;     // 拦截，交给自己的 onTouchEvent 收尾
                    }
                    break;
                }
                default:
                    break;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (!horizontal) return super.onTouchEvent(e);
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                float dx = e.getX() - downX;
                float th = 48 * getResources().getDisplayMetrics().density;
                if (cb != null) {
                    if (dx <= -th) cb.onSwipeLeft();
                    else if (dx >= th) cb.onSwipeRight();
                }
                horizontal = false;
            }
            return true;
        }
    }
}
