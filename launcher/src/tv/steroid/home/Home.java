package tv.steroid.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import java.util.Calendar;

/** Home: time and mood up top, streaming apps, then two "worlds" — Retro TV and Radio. */
public class Home extends Activity {
    /** {package, label, accent} for the Watch row, from config/apps.json. */
    static String[][] APPS = new String[0][];

    static void loadApps() {
        org.json.JSONArray a = Config.apps.optJSONArray("apps");
        int n = a == null ? 0 : a.length();
        String[][] out = new String[n][];
        for (int i = 0; i < n; i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            out[i] = new String[]{o.optString("package"), o.optString("label"), o.optString("accent", "#8FB8D8")};
        }
        APPS = out;
    }

    final Handler h = new Handler();
    ScrollView scroll;
    View firstApp;
    Ambient ambient;
    TextView clock, meridiem, date, greeting, focusTitle;
    TextView retroTitle, retroBlurb, radioState, radioName, chip;
    RetroPanel retroPanel;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        loadApps();
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        ambient = new Ambient(this);
        root.addView(ambient);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.px(52), Ui.px(34), Ui.px(52), Ui.px(96));
        // The page scrolls: focus moving down the deck brings each row into view.
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        col.addView(header());
        col.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.px(30)));

        LinearLayout watchHead = new LinearLayout(this);
        watchHead.setGravity(Gravity.BOTTOM);
        watchHead.addView(Ui.label(this, "Watch", 0x99FFFFFF));
        focusTitle = Ui.text(this, Ui.serifItalic, 17, 0xE6FFFFFF);
        focusTitle.setPadding(Ui.px(12), 0, 0, 0);
        watchHead.addView(focusTitle);
        col.addView(watchHead);

        View first = apps(col);

        LinearLayout worlds = new LinearLayout(this);
        worlds.setClipChildren(false);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, Ui.px(160));
        wlp.topMargin = Ui.px(20);
        boolean hasRetro = Config.feature("retroTv"), hasRadio = Config.feature("radio") && Stations.count() > 0;
        if (hasRetro) worlds.addView(retro(), new LinearLayout.LayoutParams(0, -1, 1.75f));
        if (hasRadio) {
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (hasRetro) rlp.leftMargin = Ui.px(16);
            worlds.addView(radio(), rlp);
        }
        if (hasRetro || hasRadio) col.addView(worlds, wlp);

        boolean hasWindows = Config.feature("worldsWindows") && Places.count() > 0, hasOffice = Config.feature("littleOffice");
        if (hasWindows || hasOffice) {
            TextView more = Ui.label(this, "Look out", 0x99FFFFFF);
            more.setPadding(0, Ui.px(30), 0, Ui.px(12));
            col.addView(more);
            LinearLayout deck = new LinearLayout(this);
            deck.setClipChildren(false);
            if (hasWindows) deck.addView(window(), new LinearLayout.LayoutParams(0, -1, 1.9f));
            if (hasOffice) {
                LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (hasWindows) olp.leftMargin = Ui.px(16);
                deck.addView(office(), olp);
                OfficeService.start(this);
            }
            col.addView(deck, new LinearLayout.LayoutParams(-1, Ui.px(190)));
        }

        setContentView(root);
        firstApp = first;
        if (first != null) first.requestFocus();
    }

    // ---------- Header: time, greeting, system ----------

    View header() {
        LinearLayout top = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        date = Ui.label(this, "", 0xB3FFFFFF);
        left.addView(date);
        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.BOTTOM);
        clock = Ui.text(this, Ui.serif, 92, 0xFFFFFFFF);
        clock.setLetterSpacing(-0.02f);
        time.addView(clock);
        meridiem = Ui.text(this, Ui.serifItalic, 26, 0xB3FFFFFF);
        meridiem.setPadding(Ui.px(8), 0, 0, Ui.px(14));
        time.addView(meridiem);
        left.addView(time);
        greeting = Ui.text(this, Ui.serifItalic, 21, 0xD9FFFFFF);
        left.addView(greeting);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        LinearLayout sys = new LinearLayout(this);
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() != TvInputInfo.TYPE_HDMI || in.isHidden(this)) continue;
            CharSequence l = in.loadLabel(this);
            sys.addView(pill(l == null ? "HDMI" : l.toString(),
                new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.getId()))));
        }
        sys.addView(pill("Wi-Fi", new Intent(Settings.ACTION_WIFI_SETTINGS)));
        sys.addView(pill("Bluetooth", new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        sys.addView(pill("Sound", new Intent(Settings.ACTION_SOUND_SETTINGS)));
        sys.addView(pill("Settings", new Intent(Settings.ACTION_SETTINGS)));
        right.addView(sys, new LinearLayout.LayoutParams(-2, -2));

        chip = Ui.text(this, Ui.mono, 11, 0xFFFFFFFF);
        chip.setLetterSpacing(0.08f);
        chip.setFocusable(true);
        chip.setPadding(Ui.px(14), Ui.px(8), Ui.px(14), Ui.px(8));
        GradientDrawable cbg = Ui.round(0x33FF6B5A, 18);
        chip.setBackground(cbg);
        chip.setOnFocusChangeListener((v, f) -> { cbg.setColor(f ? 0xFFFF6B5A : 0x33FF6B5A); Ui.lift(v, f, 1.05f); });
        chip.setOnClickListener(v -> startActivity(new Intent(this, Radio.class).putExtra("nowPlaying", true)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.px(10);
        left.addView(chip, clp);

        top.addView(right);
        return top;
    }

    // ---------- The World's Windows (deck card: the last view, framed as a window) ----------

    FrameLayout windowCard;
    ImageView windowArt;
    TextView windowName, windowWhere;

    View window() {
        FrameLayout w = new FrameLayout(this);
        w.setFocusable(true);
        w.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1C2A3E, 0xFF0C0E14});
        bg.setCornerRadius(Ui.px(20));
        w.setBackground(bg);

        int artLeft = Ui.px(250);
        windowArt = new ImageView(this);
        windowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-1, -1);
        al.leftMargin = artLeft;
        w.addView(windowArt, al);
        View fade = new View(this); // the view dissolves into the card on its left edge
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF15202F, 0x0015202F}));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.px(180), -1);
        fl.leftMargin = artLeft;
        w.addView(fade, fl);
        // window mullions over the view, so it reads as a window rather than a thumbnail
        View mv = new View(this);
        mv.setBackgroundColor(0x2EFFFFFF);
        FrameLayout.LayoutParams mvl = new FrameLayout.LayoutParams(Ui.px(2), -1, Gravity.END);
        mvl.rightMargin = Ui.px(170);
        w.addView(mv, mvl);
        View mh = new View(this);
        mh.setBackgroundColor(0x2EFFFFFF);
        FrameLayout.LayoutParams mhl = new FrameLayout.LayoutParams(-1, Ui.px(2), Gravity.TOP);
        mhl.topMargin = Ui.px(64);
        mhl.leftMargin = artLeft + Ui.px(120);
        w.addView(mh, mhl);

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.px(26), Ui.px(22), 0, Ui.px(20));
        t.addView(Ui.label(this, "The World's Windows · " + Places.count() + " views", 0xB3FFFFFF));
        TextView title = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        title.setText("Somewhere else");
        title.setPadding(0, Ui.px(10), 0, Ui.px(6));
        t.addView(title);
        TextView sub = Ui.text(this, Ui.sans, 12.5f, 0xB3FFFFFF);
        sub.setText("Train windows, rooms with a view, still nature, the sea and the sky.");
        sub.setLineSpacing(Ui.px(3), 1f);
        t.addView(sub);
        t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        windowName = Ui.text(this, Ui.serifItalic, 17, 0xFFFFFFFF);
        windowName.setSingleLine(true);
        windowName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(windowName);
        windowWhere = Ui.label(this, "", 0x99FFFFFF);
        windowWhere.setTextSize(9.5f);
        windowWhere.setSingleLine(true);
        windowWhere.setPadding(0, Ui.px(4), 0, 0);
        t.addView(windowWhere);
        w.addView(t, new FrameLayout.LayoutParams(artLeft + Ui.px(40), -1));

        View ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(Ui.px(20));
        rd.setStroke(Ui.px(1), 0x1FFFFFFF);
        ring.setBackground(rd);
        w.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        w.setOnFocusChangeListener((v, f) -> {
            Ui.lift(v, f, 1.03f);
            rd.setStroke(Ui.px(f ? 2.5f : 1), f ? 0xFFFFFFFF : 0x1FFFFFFF);
            if (f) ambient.tint(0xFF5FA8C8);
        });
        w.setOnClickListener(v -> startActivity(new Intent(this, Windows.class)));
        windowCard = w;
        return w;
    }

    // ---------- Little Office ----------

    TextView officeCodex, officeClaude, officeNow;
    View officeDot;

    View office() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setFocusable(true);
        p.setPadding(Ui.px(22), Ui.px(22), Ui.px(20), Ui.px(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x3324A36B, 0x1A101418});
        bg.setCornerRadius(Ui.px(20));
        bg.setStroke(Ui.px(1), 0x1FFFFFFF);
        p.setBackground(bg);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        officeDot = new View(this);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.px(8), Ui.px(8));
        dl.rightMargin = Ui.px(10);
        head.addView(officeDot, dl);
        head.addView(Ui.label(this, "Little Office", 0xB3FFFFFF));
        p.addView(head);
        officeCodex = Ui.text(this, Ui.sans, 14, 0xFFFFFFFF);
        officeCodex.setPadding(0, Ui.px(16), 0, Ui.px(6));
        p.addView(officeCodex);
        officeClaude = Ui.text(this, Ui.sans, 14, 0xFFFFFFFF);
        p.addView(officeClaude);
        p.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        officeNow = Ui.text(this, Ui.serifItalic, 15, 0xD9FFFFFF);
        officeNow.setMaxLines(2);
        officeNow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        p.addView(officeNow);
        p.setOnFocusChangeListener((x, f) -> {
            Ui.lift(x, f, 1.04f);
            bg.setStroke(Ui.px(f ? 2.5f : 1), f ? 0xFFFFFFFF : 0x1FFFFFFF);
            if (f) ambient.tint(0xFF24A36B);
        });
        p.setOnClickListener(x -> startActivity(new Intent(this, OfficePanel.class)));
        return p;
    }

    void refreshOffice() {
        if (officeCodex == null) return; // card switched off in features.json
        boolean live = Office.connected();
        if (live) {
            officeCodex.setText(Office.line("Codex"));
            officeClaude.setText(Office.line("Claude"));
            String hi = Office.highlight();
            officeNow.setText(hi == null ? "Nothing running right now." : hi);
        } else {
            officeCodex.setText(Office.everConnected || getSharedPreferences("office", MODE_PRIVATE).getLong("lastOk", 0) > 0
                ? "Computer offline" : "Looking for the computer…");
            officeClaude.setText(Office.lastSeen(this));
            officeNow.setText("Status will return when it reconnects.");
        }
        boolean needs = false, working = false;
        if (live) for (Office.Session s : Office.sessions) { needs |= s.state.equals("needs_you"); working |= s.state.equals("working"); }
        officeDot.setBackground(Ui.round(!live ? 0x66FFFFFF : needs ? 0xFFFFB454 : working ? 0xFF6FD49B : 0xFF8FB8FF, 4));
    }

    final Runnable officeTick = new Runnable() { public void run() {
        new Thread(() -> { Office.poll(Home.this); h.post(Home.this::refreshOffice); }).start();
        h.postDelayed(this, 15000);
    }};

    void refreshWindow() {
        if (windowName == null) return;
        int i = Places.indexOf(getSharedPreferences("windows", MODE_PRIVATE).getString("last", Places.id(13)));
        if (i < 0) i = 0;
        windowName.setText("Last time: " + Places.name(i));
        windowWhere.setText((Places.where(i) + " · " + Windows.localTime(i)).toUpperCase());
        try (java.io.InputStream in = getAssets().open("windows/" + Places.id(i) + ".jpg")) {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            windowArt.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in, null, o));
        } catch (Exception e) { windowArt.setImageDrawable(null); }
    }

    TextView pill(String label, Intent i) {
        TextView t = Ui.text(this, Ui.mono, 11, 0xCCFFFFFF);
        t.setLetterSpacing(0.06f);
        t.setText(label);
        t.setFocusable(true);
        t.setPadding(Ui.px(13), Ui.px(7), Ui.px(13), Ui.px(7));
        GradientDrawable bg = Ui.round(0x14FFFFFF, 16);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.px(6);
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x14FFFFFF);
            t.setTextColor(f ? 0xFF111114 : 0xCCFFFFFF);
        });
        t.setOnClickListener(v -> start(i));
        return t;
    }

    // ---------- Streaming apps ----------

    View apps(LinearLayout col) {
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setPadding(Ui.px(6), Ui.px(12), 0, 0);
        PackageManager pm = getPackageManager();
        View first = null;
        for (String[] a : APPS) {
            Intent li = pm.getLeanbackLaunchIntentForPackage(a[0]);
            if (li == null) li = pm.getLaunchIntentForPackage(a[0]);
            if (li == null) continue;
            View card = appCard(pm, a, li);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(78), 1f);
            lp.setMargins(Ui.px(first == null ? 0 : 7), 0, Ui.px(7), 0);
            row.addView(card, lp);
            if (first == null) first = card;
        }
        col.addView(row);
        return first;
    }

    View appCard(PackageManager pm, String[] a, Intent launch) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClipToOutline(true);
        int accent = Color.parseColor(a[2]);
        GradientDrawable bg = Ui.round(0x26FFFFFF, 14);
        card.setBackground(bg);
        Drawable art = null;
        try { art = pm.getApplicationBanner(a[0]); } catch (Exception ignored) {}
        if (art != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(art);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView t = Ui.text(this, Ui.sansMedium, 15, 0xFFFFFFFF);
            t.setText(a[1]);
            t.setGravity(Gravity.CENTER);
            card.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        View ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(Ui.px(14));
        rd.setStroke(Ui.px(1), 0x1FFFFFFF);
        ring.setBackground(rd);
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, f) -> {
            Ui.lift(v, f, 1.1f);
            rd.setStroke(Ui.px(f ? 2.5f : 1), f ? 0xFFFFFFFF : 0x1FFFFFFF);
            if (f) { focusTitle.setText(a[1]); ambient.tint(accent); }
        });
        card.setOnClickListener(v -> start(launch));
        return card;
    }

    // ---------- Retro TV ----------

    View retro() {
        retroPanel = new RetroPanel(this);
        retroPanel.setClipChildren(false);
        retroPanel.setClipToPadding(false);
        retroPanel.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(16));
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setClipChildren(false);
        v.addView(Ui.label(this, "Retro TV", 0xB3FFFFFF));
        retroTitle = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        retroTitle.setPadding(0, Ui.px(8), 0, Ui.px(4));
        v.addView(retroTitle);
        retroBlurb = Ui.text(this, Ui.sans, 12, 0xB3FFFFFF);
        retroBlurb.setMaxLines(1);
        retroBlurb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        retroBlurb.setPadding(0, 0, Ui.px(118), 0);
        v.addView(retroBlurb);
        v.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout dial = new LinearLayout(this);
        dial.setClipChildren(false);
        for (int i = 0; i < Retro.DECADES.length; i++) dial.addView(decade(i));
        v.addView(dial);
        retroPanel.addView(v, new FrameLayout.LayoutParams(-1, -1));
        setDecade(0);
        return retroPanel;
    }

    View decade(int i) {
        String[] d = Retro.DECADES[i];
        int accent = Color.parseColor(d[3]);
        TextView t = Ui.text(this, Ui.serifItalic, 20, 0xD9FFFFFF);
        t.setText("’" + d[0]);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        GradientDrawable bg = Ui.round(0x1AFFFFFF, 12);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(54), Ui.px(40));
        lp.rightMargin = Ui.px(8);
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener((v, f) -> {
            Ui.lift(v, f, 1.12f);
            bg.setColor(f ? accent : 0x1AFFFFFF);
            t.setTextColor(f ? 0xFF121014 : 0xD9FFFFFF);
            if (f) setDecade(i);
        });
        t.setOnClickListener(v -> startActivity(new Intent(this, Retro.class).putExtra("decade", i)));
        return t;
    }

    void setDecade(int i) {
        String[] d = Retro.DECADES[i];
        int accent = Color.parseColor(d[3]);
        retroTitle.setText("The " + d[1]);
        retroBlurb.setText(d[4]);
        retroPanel.setDecade(i, accent);
        ambient.tint(accent);
    }

    /** Panel background: tinted glass with a little CRT set on the right showing the decade. */
    class RetroPanel extends FrameLayout {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), scan = new Paint(), glass = new Paint(Paint.ANTI_ALIAS_FLAG),
            body = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), digits = new Paint(Paint.ANTI_ALIAS_FLAG);
        int decade, accent = 0xFFD8B47A;

        RetroPanel(Context c) {
            super(c);
            setWillNotDraw(false);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Ui.px(1));
            stroke.setColor(0x1FFFFFFF);
            scan.setColor(0x22000000);
            digits.setTypeface(Ui.serifItalic);
            digits.setTextAlign(Paint.Align.CENTER);
        }

        void setDecade(int d, int a) { decade = d; accent = a; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float r = Ui.px(20);
            fill.setShader(new LinearGradient(0, 0, w, h, Ui.withAlpha(accent, 0x40), 0x1A0E0C12, Shader.TileMode.CLAMP));
            c.drawRoundRect(0, 0, w, h, r, r, fill);
            c.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, r, r, stroke);

            // CRT set
            float tw = Ui.px(100), th = Ui.px(72), tx = w - tw - Ui.px(24), ty = Ui.px(18);
            body.setColor(decade <= 2 ? 0xFF4A3426 : decade <= 4 ? 0xFF2A2A30 : 0xFF141418);
            c.drawRoundRect(tx, ty, tx + tw, ty + th, Ui.px(decade <= 2 ? 16 : 8), Ui.px(decade <= 2 ? 16 : 8), body);
            float inset = Ui.px(decade >= 5 ? 5 : 10);
            float sx = tx + inset, sy = ty + inset, sw = tw - inset * (decade >= 5 ? 2 : 2.8f), sh = th - inset * 2;
            float sr = Ui.px(decade <= 3 ? 14 : 4);
            int screen = decade == 0 ? 0xFFBFC4C0 : accent;
            glass.setShader(new RadialGradient(sx + sw / 2, sy + sh / 2, sw * 0.75f,
                Ambient.blend(screen, 0xFFFFFFFF, 0.35f), Ambient.blend(screen, 0xFF000000, 0.55f), Shader.TileMode.CLAMP));
            c.drawRoundRect(sx, sy, sx + sw, sy + sh, sr, sr, glass);
            c.save();
            c.clipRect(sx, sy, sx + sw, sy + sh);
            for (float y = sy; y < sy + sh; y += Ui.px(3)) c.drawRect(sx, y, sx + sw, y + Ui.px(1), scan);
            c.restore();
            digits.setTextSize(Ui.px(26));
            digits.setColor(0xE6FFFFFF);
            digits.setShadowLayer(Ui.px(10), 0, 0, accent);
            c.drawText("’" + Retro.DECADES[decade][0], sx + sw / 2, sy + sh / 2 + Ui.px(9), digits);
            if (decade < 5) { // knobs
                body.setColor(0x66FFFFFF);
                c.drawCircle(tx + tw - inset * 0.9f, ty + th * 0.35f, Ui.px(2.5f), body);
                c.drawCircle(tx + tw - inset * 0.9f, ty + th * 0.6f, Ui.px(2.5f), body);
            }
            c.drawRect(tx + tw * 0.3f, ty + th, tx + tw * 0.7f, ty + th + Ui.px(5), body);
            super.onDraw(c);
        }
    }

    // ---------- Radio ----------

    View radio() {
        FrameLayout p = new FrameLayout(this);
        p.setFocusable(true);
        p.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x40FF6B5A, 0x1A120E18});
        bg.setCornerRadius(Ui.px(20));
        bg.setStroke(Ui.px(1), 0x1FFFFFFF);
        p.setBackground(bg);
        View arcs = new View(this) {
            final Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                a.setStyle(Paint.Style.STROKE);
                a.setStrokeWidth(Ui.px(1.5f));
                float cx = getWidth() - Ui.px(8), cy = getHeight() - Ui.px(8);
                for (int i = 1; i <= 5; i++) {
                    a.setColor(Ui.withAlpha(0xFFFFB199, 0x50 - i * 8));
                    float rr = Ui.px(22 * i);
                    c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, 180, 90, false, a);
                }
            }
        };
        p.addView(arcs, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.label(this, "Radio · " + Stations.count() + " stations", 0xB3FFFFFF));
        radioName = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        radioName.setPadding(0, Ui.px(8), 0, Ui.px(4));
        radioName.setMaxLines(1);
        radioName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.addView(radioName);
        radioState = Ui.text(this, Ui.sans, 12, 0xB3FFFFFF);
        radioState.setMaxLines(2);
        v.addView(radioState);
        p.addView(v, new FrameLayout.LayoutParams(-1, -1));
        p.setOnFocusChangeListener((x, f) -> {
            Ui.lift(x, f, 1.04f);
            bg.setStroke(Ui.px(f ? 2.5f : 1), f ? 0xFFFFFFFF : 0x1FFFFFFF);
            if (f) ambient.tint(RadioService.station >= 0 ? Stations.HUE[RadioService.station] : 0xFFFF6B5A);
        });
        p.setOnClickListener(x -> startActivity(new Intent(this, Radio.class)
            .putExtra("nowPlaying", RadioService.station >= 0)));
        return p;
    }

    void refreshRadio() {
        if (radioName == null) { chip.setVisibility(View.GONE); return; }
        int s = RadioService.station;
        boolean on = s >= 0;
        radioName.setText(on ? Stations.name(s).split(" — ")[0] : "Tune in");
        radioState.setText(on ? (RadioService.track.isEmpty() ? "On air now" : RadioService.track)
            : "Your CLI AMP stations, on the big screen.");
        chip.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) chip.setText("● ON AIR  " + Stations.name(s).split(" — ")[0].toUpperCase());
    }

    // ---------- Plumbing ----------

    void start(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(this, "Unavailable", Toast.LENGTH_SHORT).show(); }
    }

    final Runnable tick = new Runnable() { public void run() {
        Calendar c = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(Home.this);
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", c));
        meridiem.setText(h24 ? "" : (c.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm"));
        date.setText(DateFormat.format("EEEE · d MMMM", c).toString().toUpperCase());
        int hr = c.get(Calendar.HOUR_OF_DAY);
        greeting.setText(hr < 5 ? "Still up?" : hr < 12 ? "Good morning." : hr < 17 ? "Good afternoon." : hr < 21 ? "Good evening." : "Good night.");
        h.postDelayed(this, 60000 - c.get(Calendar.SECOND) * 1000);
    }};

    @Override protected void onResume() {
        super.onResume();
        tick.run();
        RadioService.listener = this::refreshRadio;
        refreshRadio();
        refreshWindow();
        refreshOffice();
        h.removeCallbacks(officeTick);
        if (officeCodex != null) h.post(officeTick);
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); ambient.run(f); }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(officeTick);
        h.removeCallbacks(tick);
        ambient.run(false);
    }

    @Override public void onBackPressed() { /* home stays home */ }

    /** Pressing Home on the remote always lands at the top, on the first app. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (scroll != null) scroll.scrollTo(0, 0);
        if (firstApp != null) firstApp.requestFocus();
    }
}
