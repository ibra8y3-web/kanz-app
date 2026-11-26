package store.almasdar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.*;
import android.app.DownloadManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.util.LruCache;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
  static class App {
    String pk, name, sum, lic, apk, sha, vn, icon, af, web, src, desc;
    int[] cats; long size, vc, added, upd; int minSdk, repo; boolean ok = true, nat;
    String lname, lsum; JSONObject gh;
  }
  static class Rec { String key, name, sub, pkg, file; int state; long ts; boolean gh; }
  static class Inst { long vc; String vn; }

  static final int WHITE = 0xFFFFFFFF, P1 = 0xFF2E1F9E, P2 = 0xFF7C3AED, DARKTXT = 0xFF0F172A;
  static int INK, SUB, BG, CARD, PRIMARY, OK, OKBG, WARN, WARNBG, BLUE, BLUEBG, RED, LINE, ERRBG, SOFTBG;
  static boolean DARK;
  void applyTheme() {
    int th = sp.getInt("theme", 0);
    DARK = th == 2 || (th == 0 && (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES);
    if (DARK) { INK = 0xFFE5E7EB; SUB = 0xFF9CA3AF; BG = 0xFF0B1020; CARD = 0xFF161B2E; PRIMARY = 0xFF6366F1; OK = 0xFF10B981; OKBG = 0xFF064E3B; WARN = 0xFFF59E0B; WARNBG = 0xFF4A3410; BLUE = 0xFF3B82F6; BLUEBG = 0xFF172554; RED = 0xFFEF4444; LINE = 0xFF2A3350; ERRBG = 0xFF4C1515; SOFTBG = 0xFF1E1B4B; }
    else { INK = 0xFF0F172A; SUB = 0xFF64748B; BG = 0xFFF4F6FB; CARD = WHITE; PRIMARY = 0xFF4F46E5; OK = 0xFF059669; OKBG = 0xFFD1FAE5; WARN = 0xFFD97706; WARNBG = 0xFFFEF3C7; BLUE = 0xFF2563EB; BLUEBG = 0xFFDBEAFE; RED = 0xFFDC2626; LINE = 0xFFE2E8F0; ERRBG = ERRBG; SOFTBG = 0xFFE0E7FF; }
  }
  static final String[][] BASES = {
    {"https://f-droid.org/repo/", "https://cloudflare.f-droid.org/repo/", "https://mirror.cyberbits.eu/fdroid/repo/"},
    {"https://apt.izzysoft.de/fdroid/repo/"},
    {"https://f-droid.org/archive/"},
    {"https://guardianproject.info/fdroid/repo/"},
    {"https://calyxos.gitlab.io/calyx-fdroid-repo/fdroid/repo/"},
    {"https://repo.microg.org/fdroid/repo/"},
    {"https://molly.im/fdroid/foss/fdroid/repo/"},
    {"https://mobileapp.bitwarden.com/fdroid/repo/"},
    {"https://cdn.kde.org/android/stable-releases/fdroid/repo/"},
    {"https://cdn.kde.org/android/nightly/fdroid/repo/"}};
  static final String[] REPO_NAMES = {"كنز", "كنز", "كنز", "كنز", "كنز", "كنز", "كنز", "كنز", "كنز", "كنز", "كنز"};
  static final String[] EMOJI = {"🎮", "💬", "🌐", "🔒", "🎵", "🖼️", "📚", "🎓", "🕌", "✅", "💰", "❤️", "🗺️", "⚙️", "📡", "💻", "🤖", "📦"};
  static final int[] CATCOL = {0xFFEDE9FE, 0xFFDBEAFE, 0xFFCFFAFE, ERRBG, 0xFFFCE7F3, 0xFFFEF3C7, 0xFFD1FAE5, 0xFFE0E7FF, 0xFFDCFCE7, 0xFFFFEDD5, 0xFFFEF9C3, 0xFFFFE4E6, 0xFFCCFBF1, 0xFFE2E8F0, 0xFFE0F2FE, 0xFFF3E8FF, 0xFFD9F99D, 0xFFE7E5E4};
  static final String[] FEATURED = {"org.schabi.newpipe", "org.videolan.vlc", "app.organicmaps", "com.aurora.store", "org.kde.kdeconnect_tp", "com.termux", "org.tasks", "de.danoeh.antennapod", "org.kiwix.kiwixmobile", "com.github.libretube", "com.quran.labs.androidquran", "org.fossify.gallery", "org.fossify.messages", "org.fossify.camera", "org.fossify.musicplayer", "org.fossify.filemanager", "net.thunderbird.android", "com.fsck.k9", "org.torproject.android", "com.duckduckgo.mobile.android", "org.jellyfin.mobile", "com.nextcloud.client", "com.ichi2.anki", "org.localsend.localsend_app", "org.proninyaroslav.libretorrent", "org.mozilla.fennec_fdroid", "org.mozilla.focus", "com.beemdevelopment.aegis", "im.vector.app", "org.briarproject.briar.android", "io.github.muntashirakon.AppManager", "net.osmand.plus"};
  static final String[] GH_TOPICS = {"android vpn", "android media player", "android launcher", "android browser", "android keyboard", "android notes", "android file manager", "android arabic", "android quran", "android video downloader"};

  List<App> all = new ArrayList<>();
  Map<String, App> byPk = new HashMap<>();
  String[] cats = new String[0]; int[] catCount = new int[0];
  Set<String> favs = new HashSet<>();
  Map<String, Inst> installed = new HashMap<>();
  Map<String, Rec> recs = new LinkedHashMap<>();
  SharedPreferences sp;
  Handler ui = new Handler(Looper.getMainLooper());
  ExecutorService net = Executors.newFixedThreadPool(5);
  final Set<String> miss = Collections.synchronizedSet(new HashSet<String>());
  LruCache<String, Bitmap> cache = new LruCache<>(400);
  Set<String> downloading = Collections.synchronizedSet(new HashSet<String>());
  Map<String, String> dlMsg = new ConcurrentHashMap<>();
  Map<String, Integer> dlProg = new ConcurrentHashMap<>();

  FrameLayout content; LinearLayout nav; TextView[] navItems = new TextView[4];
  int tab = 0; String dlFilter = "all";
  ArrayList<View> stack = new ArrayList<>();
  String curKey; TextView dlText; ProgressBar dlBar; LinearLayout detailBox; Runnable detailFill;
  boolean loaded;
  String ghQuery = ""; List<JSONObject> ghResults = new ArrayList<>(); String ghStatus = "";
  BroadcastReceiver pkgRx;

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

  // ================= lifecycle =================
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    sp = getSharedPreferences("s", 0);
    applyTheme(); loadLang(this); Dl.live = this;
    favs = new HashSet<>(sp.getStringSet("favs", new HashSet<String>()));
    loadRecs();
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
    root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = new LinearLayout(this); nav.setBackgroundColor(CARD); nav.setElevation(dp(10));
    String[] nt = {"🏠\nالرئيسية", "🗂\nالأقسام", "⬇\nتنزيلاتي", "🔄\nالتحديثات"};
    for (int i = 0; i < 4; i++) {
      final int t = i; TextView v = new TextView(this); v.setText(tr(nt[i])); v.setGravity(Gravity.CENTER); v.setTextSize(11); v.setPadding(0, dp(8), 0, dp(8));
      v.setOnClickListener(x -> { tab = t; stack.clear(); detailBox = null; detailFill = null; curKey = null; show(); });
      navItems[i] = v; nav.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
    }
    root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
    setContentView(root); getWindow().getDecorView().setBackgroundColor(BG);
    LinearLayout sp1 = new LinearLayout(this); sp1.setOrientation(LinearLayout.VERTICAL); sp1.setGravity(Gravity.CENTER); sp1.setBackground(grad(P1, P2, 0));
    sp1.addView(tv("كنز", 44, WHITE, true)); sp1.addView(tv("Kanz • متجر التطبيقات المفتوحة", 14, 0xFFE0E7FF, false));
    content.addView(sp1, new FrameLayout.LayoutParams(-1, -1));
    new Thread(() -> { loadCatalog(); loadLists(); ui.post(() -> { loaded = true; CatSync.schedule(this); refreshInstalled(); show(); syncRemote(false); ui.removeCallbacks(tick); ui.postDelayed(tick, 10L * 60000L); afterLoad(); }); }).start();
    pkgRx = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { if (loaded) { refreshInstalled(); refreshCurrent(); } } };
    IntentFilter f = new IntentFilter(); f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addAction(Intent.ACTION_PACKAGE_REPLACED); f.addAction(Intent.ACTION_PACKAGE_REMOVED); f.addDataScheme("package");
    try { if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgRx, f, Context.RECEIVER_EXPORTED); else registerReceiver(pkgRx, f); } catch (Exception e) { }
  }

  @Override protected void onDestroy() { super.onDestroy(); destroyed = true; if (Dl.live == this) Dl.live = null; try { unregisterReceiver(pkgRx); } catch (Exception e) { } }

  @Override protected void onResume() { super.onResume(); resumed = true; if (loaded) { refreshInstalled(); refreshCurrent(); if (System.currentTimeMillis() - sp.getLong("rcchk", 0) > 3L * 60000L) syncRemote(false); ui.removeCallbacks(tick); ui.postDelayed(tick, 10L * 60000L); } }
  final Runnable tick = new Runnable() { public void run() { if (resumed && loaded) { syncRemote(false); ui.postDelayed(this, 10L * 60000L); } } };

  void refreshCurrent() {
    if (detailBox != null && detailFill != null) detailFill.run();
    else if (stack.isEmpty() && tab != 2) show();
  }

  @Override public void onBackPressed() {
    if (!stack.isEmpty()) { stack.remove(stack.size() - 1); curKey = null; detailBox = null; detailFill = null; show(); }
    else if (tab != 0) { tab = 0; show(); }
    else super.onBackPressed();
  }

  void show() {
    if (!loaded) return;
    for (int i = 0; i < 4; i++) { navItems[i].setTextColor(i == tab ? PRIMARY : SUB); navItems[i].setTypeface(i == tab ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); }
    int up = 0; for (App a : all) if (hasUpdate(a)) up++;
    navItems[3].setText(tr("🔄\nالتحديثات" + (up > 0 ? " (" + up + ")" : "")));
    content.removeAllViews();
    View v;
    if (!stack.isEmpty()) v = stack.get(stack.size() - 1);
    else if (tab == 0) v = homeView();
    else if (tab == 1) v = catsView();
    else if (tab == 2) v = downloadsView();
    else v = updatesView();
    content.addView(v, new FrameLayout.LayoutParams(-1, -1));
  }

  void push(View v) { stack.add(v); show(); }

  // ================= data =================
  void loadCatalog() {
    try {
      BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("cats.txt"), "UTF-8"));
      List<String> cl = new ArrayList<>(); String l; while ((l = r.readLine()) != null) cl.add(l); r.close();
      cats = cl.toArray(new String[0]); catCount = new int[cats.length];
      r = new BufferedReader(new InputStreamReader(getAssets().open("catalog.tsv"), "UTF-8"), 1 << 16);
      Set<String> abis = new HashSet<>(Arrays.asList(Build.SUPPORTED_ABIS));
      while ((l = r.readLine()) != null) {
        String[] f = l.split("\t", -1);
        if (f.length < 21) continue;
        App a = new App();
        a.pk = f[0]; a.name = f[1].isEmpty() ? f[0] : f[1]; a.sum = f[2]; a.lic = f[4]; a.apk = f[5]; a.sha = f[6];
        a.size = Long.parseLong(f[7]); a.vn = f[8]; a.vc = Long.parseLong(f[9]); a.added = Long.parseLong(f[10]); a.upd = Long.parseLong(f[11]);
        a.minSdk = Integer.parseInt(f[12]); a.icon = f[13]; a.af = f[14]; a.web = f[15]; a.src = f[16]; a.repo = Integer.parseInt(f[19]); a.desc = f[20];
        if (a.minSdk > Build.VERSION.SDK_INT) a.ok = false;
        boolean nat = !f[17].isEmpty() || !f[18].isEmpty(); a.nat = nat;
        if (nat && !((f[17].equals("1") && abis.contains("arm64-v8a")) || (f[18].equals("1") && abis.contains("armeabi-v7a")))) a.ok = false;
        String[] cs = f[3].split(","); a.cats = new int[cs.length];
        for (int i = 0; i < cs.length; i++) { a.cats[i] = Integer.parseInt(cs[i]); if (a.cats[i] < catCount.length) catCount[a.cats[i]]++; }
        a.lname = a.name.toLowerCase(Locale.ROOT); a.lsum = a.sum.toLowerCase(Locale.ROOT);
        all.add(a); byPk.put(a.pk, a);
      }
      r.close();
      Collections.sort(all, (x, y) -> x.lname.compareTo(y.lname));
    } catch (Exception e) { }
    try { loadRemote(); mergeRemote(); } catch (Exception e) { }
  }

  void refreshInstalled() {
    installed.clear();
    try {
      for (PackageInfo p : getPackageManager().getInstalledPackages(0)) {
        Inst i = new Inst(); i.vc = Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode; i.vn = p.versionName == null ? "" : p.versionName; installed.put(p.packageName, i);
      }
    } catch (Exception e) { }
    boolean ch = false;
    for (Rec r : new ArrayList<>(recs.values())) {
      String pk = r.gh ? r.pkg : r.key;
      boolean in = pk != null && installed.containsKey(pk);
      if (in && r.state == 2 && r.file != null && new File(r.file).exists()) {
        try { PackageInfo ap = getPackageManager().getPackageArchiveInfo(r.file, 0); Inst ii = installed.get(pk); long avc = ap == null ? 0 : (Build.VERSION.SDK_INT >= 28 ? ap.getLongVersionCode() : ap.versionCode);
          if (ap == null || (ii != null && ii.vc >= avc)) { r.state = 3; ch = true; new File(r.file).delete(); } } catch (Exception e) { }
      }
      else if (in && (r.state == 4 || r.state == 0)) { r.state = 3; ch = true; if (r.file != null) new File(r.file).delete(); }
      else if (!in && r.state == 3) { recs.remove(r.key); ch = true; }
    }
    if (ch) saveRecs();
  }

  boolean isInstalled(App a) { if (a.gh != null) { Rec r = recs.get(a.pk); return r != null && r.state == 3; } return installed.containsKey(a.pk); }
  boolean hasUpdate(App a) { if (a.gh != null) { Rec r = recs.get(a.pk); String t = sp.getString("ghv:" + a.pk, ""); return r != null && r.state == 3 && !t.isEmpty() && !t.equals(a.vn); } Inst i = installed.get(a.pk); return i != null && i.vc < topVc(a) && !i.vn.equals(topVn(a)); }

  void loadRecs() {
    try {
      JSONArray ar = new JSONArray(sp.getString("recs", "[]"));
      for (int i = 0; i < ar.length(); i++) {
        JSONObject o = ar.getJSONObject(i); Rec r = new Rec();
        r.key = o.getString("key"); r.name = o.optString("name"); r.sub = o.optString("sub"); r.pkg = o.isNull("pkg") ? null : o.optString("pkg"); r.file = o.isNull("file") ? null : o.optString("file");
        r.state = o.optInt("state"); r.ts = o.optLong("ts"); r.gh = o.optBoolean("gh");
        if ((r.state == 1 || r.state == 5) && !Dl.queued(this, r.key)) r.state = 4;
        recs.put(r.key, r);
      }
    } catch (Exception e) { }
  }

  void saveRecs() {
    try {
      JSONArray ar = new JSONArray();
      for (Rec r : recs.values()) { JSONObject o = new JSONObject(); o.put("key", r.key); o.put("name", r.name); o.put("sub", r.sub); o.put("pkg", r.pkg == null ? JSONObject.NULL : r.pkg); o.put("file", r.file == null ? JSONObject.NULL : r.file); o.put("state", r.state); o.put("ts", r.ts); o.put("gh", r.gh); ar.put(o); }
      sp.edit().putString("recs", ar.toString()).apply();
    } catch (Exception e) { }
  }

  Rec rec(String key, String name, String sub, boolean gh) {
    Rec r = recs.get(key);
    if (r == null) { r = new Rec(); r.key = key; recs.put(key, r); }
    r.name = name; r.sub = sub; r.gh = gh; r.ts = System.currentTimeMillis();
    return r;
  }

  List<App> sorted(List<App> in, final String how) {
    List<App> l = new ArrayList<>(in);
    if (how.equals("new")) Collections.sort(l, (x, y) -> Long.compare(y.added, x.added));
    else if (how.equals("upd")) Collections.sort(l, (x, y) -> Long.compare(y.upd, x.upd));
    else if (how.equals("small")) Collections.sort(l, (x, y) -> Long.compare(x.size, y.size));
    else Collections.sort(l, (x, y) -> x.lname.compareTo(y.lname));
    return l;
  }

  // ================= UI helpers =================
  TextView tv(String t, int sz, int col, boolean bold) { TextView x = new TextView(this); x.setText(tr(t)); x.setTextSize(sz); x.setTextColor(col); if (bold) x.setTypeface(Typeface.DEFAULT_BOLD); return x; }
  GradientDrawable bg(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
  GradientDrawable gradBottom() { GradientDrawable g = grad(P1, P2, 0); float r = dp(22); g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r}); return g; }
  GradientDrawable grad(int c1, int c2, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2}); g.setCornerRadius(dp(radius)); return g; }
  GradientDrawable stroke(int fill, int col, int radius) { GradientDrawable g = bg(fill, radius); g.setStroke(dp(1), col); return g; }
  String mb(long s) { return s >= 1048576 ? String.format(Locale.US, "%.1f MB", s / 1048576.0) : String.format(Locale.US, "%d KB", Math.max(1, s / 1024)); }
  LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w > 0 ? dp(w) : w, h > 0 ? dp(h) : h); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }

  LinearLayout vbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
  LinearLayout page() { LinearLayout l = vbox(); l.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR); return l; }

  LinearLayout topBar(String title, String sub, boolean back) {
    LinearLayout bar = vbox(); bar.setBackground(gradBottom()); bar.setPadding(dp(16), dp(12), dp(16), dp(14)); bar.setElevation(dp(4));
    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
    if (back) { TextView b = tv("➜", 22, WHITE, true); b.setPadding(0, 0, dp(14), 0); b.setOnClickListener(x -> onBackPressed()); row.addView(b); }
    TextView t = tv(title, 20, WHITE, true); t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    bar.addView(row);
    if (sub != null) bar.addView(tv(sub, 12, 0xFFD6DCFF, false));
    return bar;
  }

  LinearLayout card() { LinearLayout c = vbox(); c.setBackground(bg(CARD, 18)); c.setPadding(dp(16), dp(14), dp(16), dp(14)); c.setElevation(dp(1)); return c; }

  TextView verBadge(String lic, String date) {
    boolean unk = lic == null || lic.isEmpty() || lic.equals("UNSPECIFIED");
    String t = unk ? "ℹ ترخيص غير معلن" : "✓ ترخيص مفتوح (" + lic + ")";
    if (date != null && !date.isEmpty()) t += " · آخر إصدار: " + ltr(date);
    return unk ? pill(t, SUB, LINE) : pill(t, OK, OKBG);
  }
  TextView pill(String t, int fg, int fill) { TextView p = tv(t, 11, fg, true); p.setPadding(dp(10), dp(4), dp(10), dp(4)); p.setBackground(bg(fill, 20)); return p; }

  Button pbtn(String t, int fill) { Button b = new Button(this); b.setText(tr(t)); b.setTextColor(WHITE); b.setTextSize(15); b.setAllCaps(false); b.setBackground(bg(fill, 14)); b.setStateListAnimator(null); return b; }
  Button obtn(String t) { Button b = new Button(this); b.setText(tr(t)); b.setTextColor(PRIMARY); b.setTextSize(14); b.setAllCaps(false); b.setBackground(stroke(CARD, PRIMARY, 14)); b.setStateListAnimator(null); return b; }

  View searchPill(final Runnable open) {
    TextView e = tv("🔍  ابحث بين " + all.size() + " تطبيق…", 15, SUB, false); e.setBackground(bg(CARD, 26)); e.setPadding(dp(18), dp(13), dp(18), dp(13));
    e.setOnClickListener(x -> open.run()); return e;
  }

  void openSearch() {
    LinearLayout pg = page(); pg.addView(topBar("بحث", null, true));
    final List<App> res = new ArrayList<>(); final ListView lv = new ListView(this); final BaseAdapter ad = appAdapter(res);
    final EditText e = edit(); e.setHint(tr("اكتب اسم التطبيق…")); e.setSingleLine(true); e.setTextSize(16); e.setBackground(stroke(CARD, LINE, 24)); e.setPadding(dp(16), dp(10), dp(16), dp(10));
    e.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
      public void onTextChanged(CharSequence s, int a, int b, int c) { }
      public void afterTextChanged(Editable ed) {
        if (searchLocal != null) searchLocal.run();
        ui.removeCallbacks(liveTimer); final String q0 = ed.toString().trim();
        if (q0.length() >= 3 && res.size() < 8 && !metered()) { liveTimer = () -> liveSearch(q0, false); ui.postDelayed(liveTimer, 900); }
      }});
    pg.addView(e, lp(-1, -2, 12, 10, 12, 6));
    final TextView st = tv("", 12, SUB, false); st.setPadding(dp(18), 0, dp(18), dp(4)); pg.addView(st);
    liveQ = ""; liveRes = new ArrayList<>();
    final Runnable local = () -> {
      String q = e.getText().toString().trim().toLowerCase(Locale.ROOT); res.clear();
      if (q.length() > 0) {
        List<App> pri = new ArrayList<>(), sec = new ArrayList<>();
        for (App x : all) { if (x.gh != null && x.gh.optInt("live") == 1) continue; if (x.lname.startsWith(q)) pri.add(x); else if (x.lname.contains(q) || x.pk.contains(q) || x.lsum.contains(q)) sec.add(x); }
        res.addAll(pri); res.addAll(sec); while (res.size() > 300) res.remove(res.size() - 1);
        if (liveQ.equals(q)) res.addAll(liveRes);
      }
      ad.notifyDataSetChanged();
    };
    searchLocal = local; searchStatus = st; searchEdit = e;
    e.setOnEditorActionListener((v, a, ev) -> { liveSearch(e.getText().toString().trim(), true); return true; });
    lv.setDivider(null); lv.setAdapter(ad); lv.setOnItemClickListener((p, v, i, id) -> openDetail(res.get(i)));
    pg.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
    push(pg); e.requestFocus();
    ui.postDelayed(() -> { InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (im != null) im.showSoftInput(e, 0); }, 250);
  }

  // ================= icons =================
  boolean iconsOn() {
    int m = sp.getInt("iconmode", 1);
    if (m == 1) return true; if (m == 2) return false;
    try { ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); return cm != null && !cm.isActiveNetworkMetered(); } catch (Exception e) { return false; }
  }

  Bitmap letter(String key, String name) {
    String k = "L" + key; Bitmap b = cache.get(k); if (b != null) return b;
    b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); int h = Math.abs(key.hashCode()) % 360;
    p.setShader(new LinearGradient(0, 0, 96, 96, Color.HSVToColor(new float[]{h, 0.55f, 0.9f}), Color.HSVToColor(new float[]{(h + 40) % 360, 0.65f, 0.7f}), Shader.TileMode.CLAMP));
    c.drawRoundRect(new RectF(0, 0, 96, 96), 26, 26, p); p.setShader(null);
    p.setColor(WHITE); p.setTextSize(50); p.setTextAlign(Paint.Align.CENTER);
    c.drawText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 48, 66, p); cache.put(k, b); return b;
  }

  void setIcon(final ImageView iv, final String key, final String name, final String url) {
    iv.setTag(key);
    Bitmap b = cache.get(key);
    if (b != null) { iv.setImageBitmap(b); return; }
    iv.setImageBitmap(letter(key, name));
    final File f0 = new File(getCacheDir(), "i_" + key.replace('/', '_').replace(':', '_') + ".png");
    if (f0.exists()) { Bitmap bm = decode(f0); if (bm != null) { cache.put(key, bm); iv.setImageBitmap(bm); } return; }
    if (url == null || !iconsOn() || miss.contains(url)) return;
    net.execute(() -> {
      String[] tryUrls;
      if (url.startsWith(BASES[0][0])) tryUrls = new String[]{url, BASES[0][1] + url.substring(BASES[0][0].length()), url};
      else if (url.startsWith(DEF_BASE)) tryUrls = new String[]{url, OLD_BASE + url.substring(DEF_BASE.length())};
      else if (url.startsWith(OLD_BASE)) tryUrls = new String[]{url, DEF_BASE + url.substring(OLD_BASE.length())};
      else tryUrls = new String[]{url, url};
      for (int t = 0; t < tryUrls.length; t++) {
        try {
          HttpURLConnection cn = (HttpURLConnection) new URL(tryUrls[t]).openConnection(); cn.setConnectTimeout(12000); cn.setReadTimeout(15000);
          int rc = cn.getResponseCode(); if (rc == 404) { miss.add(url); return; } if (rc != 200) { Thread.sleep(800); continue; }
          InputStream in = cn.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close();
          byte[] raw = bo.toByteArray(); BitmapFactory.Options op = new BitmapFactory.Options(); op.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(raw, 0, raw.length, op);
          int ss = 1; while (op.outWidth / (ss * 2) >= 128) ss *= 2; op = new BitmapFactory.Options(); op.inSampleSize = ss;
          final Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length, op); if (bm == null) return;
          try { FileOutputStream o = new FileOutputStream(f0); bm.compress(Bitmap.CompressFormat.PNG, 100, o); o.close(); } catch (Exception e) { }
          try { int at = key.indexOf('@'); if (at > 0) { String pre = "i_" + key.substring(0, at).replace('/', '_').replace(':', '_') + "@"; File[] olds = getCacheDir().listFiles(); if (olds != null) for (File of : olds) if (of.getName().startsWith(pre) && !of.getName().equals(f0.getName())) of.delete(); } } catch (Exception e) { }
          cache.put(key, bm);
          ui.post(() -> { if (key.equals(iv.getTag())) iv.setImageBitmap(bm); });
          return;
        } catch (Exception e) { try { Thread.sleep(800); } catch (Exception e2) { } }
      }
    });
  }

  void setIcon(ImageView iv, App a) {
    String url = a.gh != null ? (a.icon.isEmpty() ? null : a.icon) : a.icon.isEmpty() ? curBase() + "icons/fd_" + a.pk.replaceAll("[^A-Za-z0-9._-]", "_") + ".webp" : (a.repo >= 0 && a.repo < BASES.length ? BASES[a.repo][0] : BASES[0][0]) + a.icon.replaceFirst("^/", "");
    String vtag = a.vc > 0 ? String.valueOf(a.vc) : (a.vn == null ? "" : a.vn.trim());
    vtag = vtag.replaceAll("[^A-Za-z0-9._-]", "");
    setIcon(iv, a.pk + (vtag.isEmpty() ? "" : "@" + vtag), a.name, url);
  }

  Bitmap decode(File f) { BitmapFactory.Options op = new BitmapFactory.Options(); op.inSampleSize = 2; return BitmapFactory.decodeFile(f.getPath(), op); }

  // ================= rows =================
  View genericRow(String key, String name, String line2, String line3, String pillTxt, int pillFg, int pillBg, String iconUrl) {
    FrameLayout w = new FrameLayout(this); w.setPadding(dp(10), dp(3), dp(10), dp(3));
    LinearLayout row = new LinearLayout(this); row.setPadding(dp(10), dp(8), dp(10), dp(8)); row.setGravity(Gravity.CENTER_VERTICAL); row.setBackground(bg(CARD, 18)); row.setElevation(dp(1));
    ImageView iv = new ImageView(this); row.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40))); setIcon(iv, key, name, iconUrl);
    LinearLayout col = vbox(); col.setPadding(dp(10), 0, dp(6), 0);
    TextView t1 = tv(name, 14, INK, true); t1.setSingleLine(true); t1.setEllipsize(TextUtils.TruncateAt.END);
    TextView t2 = tv(line2, 12, SUB, false); t2.setMaxLines(1); t2.setEllipsize(TextUtils.TruncateAt.END);
    col.addView(t1); if (!line2.isEmpty()) col.addView(t2); { TextView t3 = tv(line3, 11, PRIMARY, false); t3.setSingleLine(true); t3.setEllipsize(TextUtils.TruncateAt.END); col.addView(t3); }
    row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(pill(pillTxt, pillFg, pillBg));
    w.addView(row); return w;
  }

  View rowView(App a) {
    String t; int fg, bgc;
    if (!a.ok && !isInstalled(a) && !downloading.contains(a.pk)) { t = "غير متوافق"; fg = SUB; bgc = LINE; }
    else if (downloading.contains(a.pk)) { t = "⏳ جاري"; fg = BLUE; bgc = BLUEBG; }
    else if (hasUpdate(a)) { t = "تحديث"; fg = WARN; bgc = WARNBG; }
    else if (isInstalled(a)) { t = "✓ مثبّت"; fg = OK; bgc = OKBG; }
    else { Rec r = recs.get(a.pk); if (r != null && r.state == 2 && r.file != null && new File(r.file).exists()) { t = "محمّل"; fg = BLUE; bgc = BLUEBG; } else { t = "تثبيت"; fg = WHITE; bgc = PRIMARY; } }
    View v = genericRow(a.pk, a.name, a.sum, a.gh != null ? (a.size > 0 ? mb(a.size) + "  •  " : "") + (a.vn.isEmpty() ? "★ " + a.gh.optInt("s") + "  •  " : a.vn + "  •  ") + "غير مُراجَع يدويًا" : mb(a.size) + "  •  " + a.vn + "  •  " + REPO_NAMES[a.repo], (a.gh != null && a.gh.optInt("live") == 1 && t.equals("تثبيت")) ? "عرض" : t, fg, bgc, null);
    LinearLayout row = (LinearLayout) ((FrameLayout) v).getChildAt(0);
    ImageView iv = (ImageView) row.getChildAt(0); setIcon(iv, a);
    final TextView fv = tv(favs.contains(a.pk) ? "⭐" : "☆", 15, WARN, false); fv.setPadding(dp(8), 0, dp(2), 0);
    fv.setOnClickListener(x -> { toggleFav(a.pk); fv.setText(tr(favs.contains(a.pk) ? "⭐" : "☆")); });
    row.addView(fv);
    return v;
  }

  BaseAdapter appAdapter(final List<App> items) {
    return new BaseAdapter() {
      public int getCount() { return items.size(); }
      public Object getItem(int i) { return items.get(i); }
      public long getItemId(int i) { return i; }
      public View getView(int i, View v, ViewGroup p) { return rowView(items.get(i)); }
    };
  }

  View listPage(String title, final List<App> base, boolean sortable, String emptyMsg) {
    LinearLayout pg = page(); pg.addView(topBar(title, listSub != null ? listSub : base.size() + " تطبيق", true));
    final List<App> shown = new ArrayList<>(base); final BaseAdapter ad = appAdapter(shown);
    if (sortable) {
      HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
      final LinearLayout ch = new LinearLayout(this); ch.setPadding(dp(8), dp(10), dp(8), dp(4)); hs.addView(ch);
      final String[][] opts = {{"الاسم", "name"}, {"الأحدث إضافة", "new"}, {"آخر تحديث", "upd"}, {"الأصغر حجمًا", "small"}};
      final String[] curSort = {"name"}; final Runnable[] rb = new Runnable[1]; final boolean[] onlyOk = {false};
      rb[0] = () -> {
        ch.removeAllViews();
        for (final String[] o : opts) {
          boolean on = o[1].equals(curSort[0]); TextView c = tv(o[0], 13, on ? WHITE : PRIMARY, on); c.setPadding(dp(14), dp(7), dp(14), dp(7)); c.setBackground(on ? bg(PRIMARY, 18) : stroke(CARD, LINE, 18));
          c.setOnClickListener(x -> { curSort[0] = o[1]; shown.clear(); for (App q : sorted(base, o[1])) if (!onlyOk[0] || q.ok) shown.add(q); ad.notifyDataSetChanged(); rb[0].run(); });
          ch.addView(c, lp(-2, -2, 4, 0, 4, 0));
        }
        TextView ok = tv("✅ المتوافق فقط", 13, onlyOk[0] ? WHITE : PRIMARY, onlyOk[0]); ok.setPadding(dp(14), dp(7), dp(14), dp(7)); ok.setBackground(onlyOk[0] ? bg(OK, 18) : stroke(CARD, LINE, 18));
        ok.setOnClickListener(x -> { onlyOk[0] = !onlyOk[0]; shown.clear(); for (App q : sorted(base, curSort[0])) if (!onlyOk[0] || q.ok) shown.add(q); ad.notifyDataSetChanged(); rb[0].run(); });
        ch.addView(ok, lp(-2, -2, 4, 0, 4, 0));
      };
      rb[0].run(); pg.addView(hs);
    }
    ListView lv = new ListView(this); lv.setDivider(null); lv.setAdapter(ad); lv.setOnItemClickListener((p, v, i, id) -> openDetail(shown.get(i)));
    if (base.isEmpty()) { TextView e = tv(emptyMsg, 15, SUB, false); e.setGravity(Gravity.CENTER); e.setPadding(dp(24), dp(60), dp(24), 0); pg.addView(e); }
    else pg.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
    return pg;
  }

  // ================= home =================
  View homeView() {
    LinearLayout pg = page();
    LinearLayout bar = vbox(); bar.setBackground(gradBottom()); bar.setPadding(dp(12), dp(6), dp(8), dp(6)); bar.setElevation(dp(3));
    LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
    TextView ttl = tv("كنز", 17, WHITE, true); ttl.setOnLongClickListener(x -> { catUrlDialog(); return true; }); top.addView(ttl);
    TextView e = tv("🔍  ابحث بين " + all.size() + " تطبيق…", 13, SUB, false); e.setBackground(bg(CARD, 20)); e.setPadding(dp(14), dp(7), dp(14), dp(7)); e.setSingleLine(true); e.setOnClickListener(x -> openSearch());
    top.addView(e, lp(0, -2, 1, 0, 0, 0)); ((LinearLayout.LayoutParams) e.getLayoutParams()).weight = 1; ((LinearLayout.LayoutParams) e.getLayoutParams()).setMargins(dp(10), 0, dp(2), 0);
    TextView rf = tv("🔄", 18, WHITE, false); rf.setPadding(dp(8), 0, dp(2), 0); rf.setOnClickListener(x -> syncRemote(true)); top.addView(rf);
    TextView menu = tv("⋮", 24, WHITE, true); menu.setPadding(dp(10), 0, dp(2), 0); menu.setOnClickListener(x -> showMenu()); top.addView(menu);
    bar.addView(top);
    pg.addView(bar);
    ScrollView sv = new ScrollView(this); LinearLayout c = vbox(); c.setPadding(0, dp(4), 0, dp(20)); sv.addView(c);
    pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView cnt = tv(all.size() + " تطبيق • " + compatCount() + " المتوافق مع موبايلك • " + installedCount() + " مثبّت", 11, SUB, false); cnt.setPadding(dp(16), dp(6), dp(16), 0); c.addView(cnt);
    pullRefresh(sv);
    if (!rcMsg.isEmpty()) { TextView sb = tv(rcMsg, 12, rcMsg.startsWith("⚠") ? RED : OK, true); sb.setPadding(dp(14), dp(8), dp(14), dp(8)); sb.setBackground(bg(rcMsg.startsWith("⚠") ? ERRBG : OKBG, 12)); sb.setOnClickListener(x -> { rcMsg = ""; show(); }); c.addView(sb, lp(-1, -2, 12, 8, 12, 0)); }
    if (rcPending > 0) { TextView pb = tv("📦 في تحديث للكتالوج (" + mb(rcPending) + "). اضغط هنا للتحديث", 12, PRIMARY, true); pb.setPadding(dp(14), dp(8), dp(14), dp(8)); pb.setBackground(bg(SOFTBG, 12)); pb.setOnClickListener(x -> syncRemote(true)); c.addView(pb, lp(-1, -2, 12, 8, 12, 0)); }
    kanzCard(c);
    final List<App> favl = new ArrayList<>(); for (App a : sorted(all, "name")) if (favs.contains(a.pk)) favl.add(a);
    if (!favl.isEmpty()) section(c, "⭐ المفضلة", favl, 24, () -> push(listPage("المفضلة", favl, true, tr("مفيش مفضلة لسه. دوس ☆ على أي تطبيق."))));
    HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); LinearLayout cr = new LinearLayout(this); cr.setPadding(dp(8), dp(8), dp(8), dp(0)); hs.addView(cr);
    for (int i = 0; i < cats.length; i++) {
      final int ci = i; TextView ch = tv(EMOJI[Math.min(i, EMOJI.length - 1)] + " " + cats[i], 12, DARKTXT, true); ch.setPadding(dp(11), dp(6), dp(11), dp(6)); ch.setBackground(bg(CATCOL[Math.min(i, CATCOL.length - 1)], 20));
      ch.setOnClickListener(x -> openCat(ci)); cr.addView(ch, lp(-2, -2, 4, 0, 4, 0));
    }
    c.addView(hs);
    final List<App> feat = new ArrayList<>(); Set<String> seen = new HashSet<>();
    for (String pk : FEATURED) { App a = byPk.get(pk); if (a != null && seen.add(pk)) feat.add(a); }
    if (!feat.isEmpty()) section(c, "⭐ مختارات لك", feat, 24, () -> push(listPage("مختارات", feat, false, "")));
    if (!lists.isEmpty()) {
      TextView lh = tv("📚 قوائم مختارة وبدائل", 15, INK, true); lh.setPadding(dp(16), dp(12), dp(16), dp(4)); c.addView(lh);
      HorizontalScrollView lhs = new HorizontalScrollView(this); lhs.setHorizontalScrollBarEnabled(false); LinearLayout lr = new LinearLayout(this); lr.setPadding(dp(8), 0, dp(8), 0); lhs.addView(lr);
      for (final String[] L : lists) { if (listApps(L).isEmpty()) continue; TextView ch = tv(L[0], 12, PRIMARY, true); ch.setPadding(dp(12), dp(8), dp(12), dp(8)); ch.setBackground(stroke(CARD, LINE, 18)); ch.setOnClickListener(x -> openList(L)); lr.addView(ch, lp(-2, -2, 4, 0, 4, 0)); }
      c.addView(lhs);
    }
    final List<App> nw = sorted(all, "new"); section(c, "🆕 أُضيفت حديثًا", nw, 24, () -> push(listPage("أُضيفت حديثًا", nw.subList(0, Math.min(150, nw.size())), false, "")));
    final List<App> up = sorted(all, "upd"); section(c, "🔄 حُدِّثت مؤخرًا", up, 24, () -> push(listPage("حُدِّثت مؤخرًا", up.subList(0, Math.min(150, up.size())), false, "")));
    final List<App> light = new ArrayList<>(); for (App a : sorted(all, "small")) { if (a.size > 1500000) break; if (a.sum.length() > 0 && a.repo != 2) light.add(a); }
    Collections.shuffle(light, new Random(42));
    section(c, "🪶 خفيفة وتوفّر نت", light, 24, () -> push(listPage("تطبيقات خفيفة (أقل من 1.5 MB)", sorted(light, "name"), true, "")));
    Button all_ = pbtn("تصفّح كل التطبيقات (" + all.size() + ")", PRIMARY); all_.setOnClickListener(x -> push(listPage("كل التطبيقات", all, true, ""))); c.addView(all_, lp(-1, 52, 16, 10, 16, 0));
    return pg;
  }

  int installedCount() { int n = 0; for (App a : all) if (isInstalled(a)) n++; return n; }

  // the store lists itself first on home, with its own install/update state
  static boolean newerVer(String latest, String cur) {
    try {
      String[] a = latest.replaceFirst("^[vV]", "").trim().split("[^0-9]+"), b = cur.replaceFirst("^[vV]", "").trim().split("[^0-9]+");
      for (int i = 0; i < Math.max(a.length, b.length); i++) {
        long x = i < a.length && !a[i].isEmpty() ? Long.parseLong(a[i]) : 0, y = i < b.length && !b[i].isEmpty() ? Long.parseLong(b[i]) : 0;
        if (x != y) return x > y;
      }
    } catch (Exception e) { }
    return false;
  }

  void selfUpdate(final App self) {
    try {
      JSONObject as = new JSONObject();
      as.put("browser_download_url", curBase() + "kanz.apk"); as.put("name", "kanz.apk"); as.put("size", self.size); as.put("digest", "sha256:" + self.sha); as.put("tag", self.vn);
      ghDownload(SELF_ID, as);   // ghDownload fetches fresh app.json for the store's own APK
    } catch (Exception e) { }
  }

  void kanzCard(LinearLayout parent) {
    App s0 = byPk.get("gh:" + SELF_ID); final App self = s0 != null ? s0 : byPk.get("gh:" + SELF_ID2);
    String curVn = "";
    try { PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0); curVn = p.versionName == null ? "" : p.versionName; } catch (Exception e) { }
    String latest = self == null ? "" : self.vn;
    final boolean upd = !latest.isEmpty() && newerVer(latest, curVn);
    LinearLayout k = card();
    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
    ImageView iv = new ImageView(this); row.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
    if (self != null) setIcon(iv, self); else iv.setImageBitmap(letter("store.almasdar", "كنز"));
    LinearLayout col = vbox(); col.setPadding(dp(12), 0, dp(4), 0);
    col.addView(tv("كنز — المتجر نفسه", 15, INK, true));
    col.addView(tv("المثبّت عندك: " + (curVn.isEmpty() ? "؟" : ltr(curVn)) + (latest.isEmpty() ? "" : "  •  أحدث نسخة: " + ltr(latest)), 12, SUB, false));
    row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
    k.addView(row);
    if (self != null) {
      Button b = pbtn(upd ? "تحديث كنز إلى " + latest : "✅ كنز محدّث", upd ? WARN : OK);
      b.setOnClickListener(x -> { if (upd) { if (!canInstall()) return; selfUpdate(self); } else openDetail(self); });
      k.addView(b, lp(-1, 46, 0, 10, 0, 0));
      k.setOnClickListener(x -> openDetail(self));
    } else {
      TextView w = tv("اسحب لتحديث الكتالوج عشان يظهر وضع تحديث كنز هنا.", 12, SUB, false); w.setPadding(0, dp(8), 0, 0); k.addView(w);
    }
    parent.addView(k, lp(-1, -2, 12, 8, 12, 0));
  }

  void section(LinearLayout parent, String title, List<App> items, int max, Runnable more) {
    LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(16), dp(10), dp(16), dp(4));
    head.addView(tv(title, 15, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    if (more != null) { TextView m = tv("عرض الكل ‹", 13, PRIMARY, true); m.setOnClickListener(x -> more.run()); head.addView(m); }
    parent.addView(head);
    HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
    LinearLayout row = new LinearLayout(this); row.setPadding(dp(8), dp(0), dp(8), dp(2)); hs.addView(row);
    for (int i = 0; i < Math.min(max, items.size()); i++) row.addView(tile(items.get(i)));
    parent.addView(hs);
  }

  View tile(final App a) {
    FrameLayout wrap = new FrameLayout(this);
    LinearLayout c = vbox(); c.setGravity(Gravity.CENTER_HORIZONTAL); c.setPadding(dp(4), dp(8), dp(4), dp(8)); c.setBackground(bg(CARD, 14)); c.setElevation(dp(1));
    ImageView iv = new ImageView(this); c.addView(iv, new LinearLayout.LayoutParams(dp(42), dp(42))); setIcon(iv, a);
    TextView n = tv(a.name, 11, INK, true); n.setGravity(Gravity.CENTER); n.setLines(2); n.setEllipsize(TextUtils.TruncateAt.END); n.setPadding(0, dp(5), 0, 0); c.addView(n);
    String st = hasUpdate(a) ? "تحديث" : isInstalled(a) ? "✓ مثبّت" : !a.ok ? "⚠ غير متوافق" : mb(a.size);
    TextView s = tv(st, 10, isInstalled(a) ? OK : SUB, isInstalled(a)); c.addView(s);
    wrap.addView(c, new FrameLayout.LayoutParams(-1, -2));
    final TextView fv = tv(favs.contains(a.pk) ? "⭐" : "☆", 13, WARN, false); fv.setPadding(dp(5), dp(3), dp(5), dp(3));
    fv.setOnClickListener(x -> { toggleFav(a.pk); fv.setText(tr(favs.contains(a.pk) ? "⭐" : "☆")); });
    wrap.addView(fv, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
    wrap.setLayoutParams(lp(78, -2, 3, 2, 3, 4)); wrap.setOnClickListener(x -> openDetail(a)); return wrap;
  }

  void toggleFav(String pk) { if (favs.contains(pk)) favs.remove(pk); else favs.add(pk); sp.edit().putStringSet("favs", new HashSet<>(favs)).apply(); }

  // ================= categories =================
  void openCat(int ci) { List<App> l = new ArrayList<>(); for (App a : all) for (int cc : a.cats) if (cc == ci) { l.add(a); break; } push(listPage(cats[ci], l, true, "")); }

  View catsView() {
    LinearLayout pg = page(); pg.addView(topBar("الأقسام", cats.length + " قسم • " + all.size() + " تطبيق", false));
    ScrollView sv = new ScrollView(this); LinearLayout grid = vbox(); grid.setPadding(dp(10), dp(10), dp(10), dp(20)); sv.addView(grid);
    pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1)); LinearLayout row = null;
    for (int i = 0; i < cats.length; i++) {
      if (i % 2 == 0) { row = new LinearLayout(this); grid.addView(row); }
      final int ci = i; LinearLayout t = vbox(); t.setGravity(Gravity.CENTER); t.setPadding(dp(8), dp(20), dp(8), dp(20)); t.setBackground(bg(CATCOL[Math.min(i, CATCOL.length - 1)], 20));
      t.addView(tv(EMOJI[Math.min(i, EMOJI.length - 1)], 32, DARKTXT, false)); TextView n = tv(cats[i], 15, DARKTXT, true); n.setGravity(Gravity.CENTER); t.addView(n);
      t.addView(tv(catCount[i] + " تطبيق", 12, 0xFF475569, false)); t.setOnClickListener(x -> openCat(ci));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(6), dp(6), dp(6), dp(6)); row.addView(t, p);
    }
    if (cats.length % 2 == 1 && row != null) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    return pg;
  }

  // ================= updates =================
  // ================= downloads / library =================
  View downloadsView() {
    LinearLayout pg = page(); pg.addView(topBar("تنزيلاتي", "كل اللي نزّلته أو ثبّته من المتجر", false));
    final String[][] seg = {{"all", "الكل"}, {"active", "جارٍ"}, {"ready", "محمّل"}, {"inst", "مثبّت"}, {"fav", "⭐ المفضلة"}};
    HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
    LinearLayout ch = new LinearLayout(this); ch.setPadding(dp(8), dp(10), dp(8), dp(4)); hs.addView(ch);
    for (final String[] s : seg) {
      boolean on = s[0].equals(dlFilter); TextView c = tv(s[1], 13, on ? WHITE : PRIMARY, on); c.setPadding(dp(14), dp(7), dp(14), dp(7)); c.setBackground(on ? bg(PRIMARY, 18) : stroke(CARD, LINE, 18));
      c.setOnClickListener(x -> { dlFilter = s[0]; show(); }); ch.addView(c, lp(-2, -2, 4, 0, 4, 0));
    }
    pg.addView(hs);
    ScrollView sv = new ScrollView(this); LinearLayout list = vbox(); list.setPadding(0, dp(4), 0, dp(16)); sv.addView(list);
    pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    int n = 0; Set<String> done = new HashSet<>();
    List<Rec> rl = new ArrayList<>(recs.values()); Collections.sort(rl, (x, y) -> Long.compare(y.ts, x.ts));
    if (!dlFilter.equals("fav")) for (final Rec r : rl) {
      int st = r.state; if (downloading.contains(r.key) && st != 5) st = 1;
      done.add(r.key);
      boolean sh = dlFilter.equals("all") || (dlFilter.equals("active") && (st == 1 || st == 5)) || (dlFilter.equals("ready") && st == 2) || (dlFilter.equals("inst") && st == 3);
      if (!sh) continue;
      String t; int fg, bgc;
      if (st == 5) { t = "⏰ في انتظار الواي فاي"; fg = WARN; bgc = WARNBG; } else if (st == 1) { t = "⏳ جاري التحميل"; fg = BLUE; bgc = BLUEBG; } else if (st == 2) { t = "تم التحميل"; fg = BLUE; bgc = BLUEBG; } else if (st == 3) { t = "✓ مثبّت"; fg = OK; bgc = OKBG; } else { t = "فشل"; fg = RED; bgc = ERRBG; }
      View v = genericRow(r.key, r.name, r.gh || r.sub == null ? "" : r.sub, r.gh ? "من مصدر إضافي" : "من المتجر", t, fg, bgc, null);
      v.setOnClickListener(x -> { if (r.gh) { loadRemote(); if (rc.containsKey(r.key.substring(3))) openRemoteApp(r.key.substring(3)); else ghOpen(r.key.substring(3), null); } else { App a = byPk.get(r.key); if (a != null) openDetail(a); } });
      list.addView(v); n++;
    }
    if (dlFilter.equals("all") || dlFilter.equals("inst")) {
      for (App a : sorted(all, "name")) if (isInstalled(a) && !done.contains(a.pk)) {
        final App aa = a; View v = rowView(a); v.setOnClickListener(x -> openDetail(aa)); list.addView(v); n++;
      }
    }
    if (dlFilter.equals("fav")) {
      if (!favs.isEmpty()) { Button sb2 = obtn("↗ " + tr("مشاركة قائمة المفضلة")); sb2.setOnClickListener(x -> shareFavs()); list.addView(sb2, lp(-1, 44, 12, 8, 12, 4)); }
      for (App a : sorted(all, "name")) if (favs.contains(a.pk)) { final App aa = a; View v = rowView(a); v.setOnClickListener(x -> openDetail(aa)); list.addView(v); n++; }
    }
    if (n == 0) { TextView e = tv(dlFilter.equals("fav") ? "المفضلة فاضية. افتح أي تطبيق واضغط ☆" : "مفيش حاجة هنا لسه", 15, SUB, false); e.setGravity(Gravity.CENTER); e.setPadding(dp(24), dp(60), dp(24), 0); list.addView(e); }
    return pg;
  }

  // ================= catalog app detail =================
  String afText(String af) {
    if (af.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (String s : af.split(",")) {
      String t = s.equals("Ads") ? "يعرض إعلانات" : s.equals("Tracking") ? "يتتبّع نشاطك" : s.equals("NonFreeNet") ? "بيعتمد على خدمة إنترنت غير حرة" : s.equals("KnownVuln") ? "فيه ثغرة معروفة" : s.equals("NoSourceSince") ? "الكود المصدري للنسخة الأحدث غير متاح" : s.equals("TetheredNet") ? "يحتاج خدمة محددة على الإنترنت" : s.equals("Archived") ? "تطبيق مؤرشف: قديم وغير مُحدَّث، ممكن ما يشتغلش كويس أو يكون فيه ثغرات" : s.equals("Nightly") ? "نسخة تجريبية يومية، ممكن تكون غير مستقرة" : s;
      sb.append("⚠ ").append(t).append("\n");
    }
    return sb.toString().trim();
  }

  void openDetail(final App a) {
    if (a.gh != null) { String gid = a.gh.optString("id"); loadRemote(); if (rc.containsKey(gid)) openRemoteApp(gid); else ghOpen(gid, null); return; }
    LinearLayout pg = page(); pg.addView(topBar(a.name, a.pk, true));
    ScrollView sv = new ScrollView(this); detailBox = vbox(); detailBox.setPadding(dp(14), dp(14), dp(14), dp(24)); sv.addView(detailBox);
    pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    curKey = a.pk; detailFill = () -> fillDetail(a); fillDetail(a); push(pg);
  }

  void statGrid(LinearLayout parent, String[][] cells) {
    LinearLayout row = card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(dp(8), dp(12), dp(8), dp(12));
    for (int i = 0; i < cells.length; i++) {
      LinearLayout c = vbox(); c.setGravity(Gravity.CENTER); TextView v = tv(cells[i][0], 15, INK, true); v.setGravity(Gravity.CENTER); v.setSingleLine(true); v.setEllipsize(TextUtils.TruncateAt.END);
      c.addView(v); TextView l = tv(cells[i][1], 11, SUB, false); l.setGravity(Gravity.CENTER); c.addView(l);
      row.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
    }
    parent.addView(row, lp(-1, -2, 0, 12, 0, 0));
  }

  void fillDetail(final App a) {
    LinearLayout c = detailBox; c.removeAllViews();
    LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
    ImageView iv = new ImageView(this); setIcon(iv, a); top.addView(iv, new LinearLayout.LayoutParams(dp(80), dp(80)));
    LinearLayout col = vbox(); col.setPadding(dp(14), 0, 0, 0);
    col.addView(tv(a.name, 22, INK, true)); col.addView(tv(a.sum, 13, SUB, false));
    top.addView(col, new LinearLayout.LayoutParams(0, -2, 1)); c.addView(top);

    Inst in = installed.get(a.pk); boolean busy = downloading.contains(a.pk); boolean upd = hasUpdate(a);
    Rec r = recs.get(a.pk); boolean fileReady = r != null && r.state == 2 && r.file != null && new File(r.file).exists();
    String stTxt = busy ? "⏳ جاري التحميل" : upd ? "🔄 يوجد تحديث" : in != null ? "✅ مثبّت" : fileReady ? "📥 تم التحميل ولم يتثبّت" : "لم يُثبَّت";
    int stFg = busy || fileReady ? BLUE : upd ? WARN : in != null ? OK : SUB, stBg = busy || fileReady ? BLUEBG : upd ? WARNBG : in != null ? OKBG : LINE;
    c.addView(pill(stTxt, stFg, stBg), lp(-2, -2, 0, 12, 0, 0));

    statGrid(c, new String[][]{{mb(a.size), "الحجم"}, {a.vn, "الإصدار"}, {a.lic, "الرخصة"}});
    c.addView(verBadge(a.lic, a.upd > 0 ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(a.upd * 1000)) : ""), lp(-2, -2, 0, 8, 0, 0));

    dlBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); dlBar.setMax(1000); c.addView(dlBar, lp(-1, -2, 0, 12, 0, 0));
    dlText = tv("", 12, SUB, false); c.addView(dlText);
    dlBar.setVisibility(busy ? View.VISIBLE : View.GONE);
    Button b;
    if (!a.ok && in == null && !busy) { b = pbtn("غير متوافق مع جهازك", SUB); b.setEnabled(false); }
    else if (busy) { b = pbtn("جاري التحميل…", SUB); b.setEnabled(false); String m = dlMsg.get(a.pk); dlText.setText(tr(m == null ? "" : m)); Integer p = dlProg.get(a.pk); dlBar.setProgress(p == null ? 0 : p); }
    else if (upd && !fileReady) { b = pbtn("تحديث إلى " + topVn(a) + "  (" + mb(a.size) + ")", WARN); b.setOnClickListener(v -> startDownload(a)); }
    else if (in != null && !fileReady) { b = pbtn("فتح التطبيق", OK); b.setOnClickListener(v -> { Intent i = getPackageManager().getLaunchIntentForPackage(a.pk); if (i != null) startActivity(i); else mk("التطبيق ده مالوش شاشة تشغيل", Toast.LENGTH_SHORT).show(); }); }
    else if (fileReady) { final File f = new File(r.file); b = pbtn("تثبيت الآن (تم تحميله)", BLUE); b.setOnClickListener(v -> { if (!canInstall()) return; review(a.pk); }); }
    else { b = pbtn("تحميل وتثبيت  (" + mb(a.size) + ")", PRIMARY); b.setOnClickListener(v -> startDownload(a)); }
    c.addView(b, lp(-1, 54, 0, 10, 0, 0));
    extraButtons(c, a, busy, in != null, upd);
    if (in != null && !upd) { TextView iv2 = tv("المثبّت عندك: " + in.vn, 12, SUB, false); iv2.setPadding(dp(4), dp(6), 0, 0); c.addView(iv2); }

    if (!a.desc.isEmpty()) descCard(c, a.pk, a.desc + (a.desc.length() >= 320 ? "…" : ""), true);
    LinearLayout info = card(); info.addView(tv("معلومات", 15, PRIMARY, true));
    info.addView(tv("المصدر: " + REPO_NAMES[a.repo], 13, INK, false));
    info.addView(tv("آخر تحديث: " + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(a.upd * 1000)), 13, INK, false));
    info.addView(tv("يتطلب أندرويد API " + a.minSdk + " أو أحدث" + (a.ok ? "" : "  ❌ مش مناسب لجهازك (أندرويد أو المعالج)"), 13, a.ok ? INK : RED, false));
    info.addView(tv("الحزمة: " + a.pk, 12, SUB, false));
    c.addView(info, lp(-1, -2, 0, 10, 0, 0));
    String af = afText(a.af);
    if (!af.isEmpty()) { TextView w = tv(af, 13, WARN, false); w.setPadding(dp(14), dp(10), dp(14), dp(10)); w.setBackground(bg(WARNBG, 14)); c.addView(w, lp(-1, -2, 0, 10, 0, 0)); }

    final Button fav = obtn(favs.contains(a.pk) ? "⭐ في المفضلة (اضغط للإزالة)" : "☆ أضف للمفضلة");
    fav.setOnClickListener(v -> { if (favs.contains(a.pk)) favs.remove(a.pk); else favs.add(a.pk); sp.edit().putStringSet("favs", new HashSet<>(favs)).apply(); fav.setText(tr(favs.contains(a.pk) ? "⭐ في المفضلة (اضغط للإزالة)" : "☆ أضف للمفضلة")); });
    c.addView(fav, lp(-1, 46, 0, 10, 0, 0));
    if (in != null) { Button un = obtn("🗑 إلغاء التثبيت"); un.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pk)))); c.addView(un, lp(-1, 46, 0, 8, 0, 0)); }
    if (!a.web.isEmpty()) c.addView(link("🌐 موقع التطبيق", a.web), lp(-1, 46, 0, 8, 0, 0));
    if (!a.src.isEmpty()) c.addView(link("💻 الكود المصدري", a.src), lp(-1, 46, 0, 8, 0, 0));
    Button sh = obtn("↗ مشاركة"); sh.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, a.name + " - " + (a.web.isEmpty() ? a.src : a.web)); startActivity(Intent.createChooser(i, "مشاركة")); });
    c.addView(sh, lp(-1, 46, 0, 8, 0, 0));
  }

  Button link(String t, final String u) { Button b = obtn(t); b.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception e) { } }); return b; }

  // ================= download / install =================
  boolean canInstall() {
    if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
      mk("اسمح للمتجر بتثبيت التطبيقات ثم ارجع واضغط تحميل", Toast.LENGTH_LONG).show();
      startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); return false;
    }
    return true;
  }

  void post(final String key, final String t, final int prog) {
    dlMsg.put(key, t); dlProg.put(key, prog);
    ui.post(() -> { if (key.equals(curKey) && dlText != null) { dlText.setText(tr(t)); if (dlBar != null) { dlBar.setVisibility(View.VISIBLE); dlBar.setProgress(prog); } } });
  }

  String sha(File f) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256"); FileInputStream in = new FileInputStream(f); byte[] b = new byte[65536]; int n;
    while ((n = in.read(b)) > 0) md.update(b, 0, n); in.close();
    StringBuilder sb = new StringBuilder(); for (byte x : md.digest()) sb.append(String.format("%02x", x)); return sb.toString();
  }

  void install(File f) throws Exception { Dl.install(this, f, false); }

  // ================= GitHub =================
  Set<String> ghSources() {
    if (!sp.contains("ghsrc")) { Set<String> d = new LinkedHashSet<>(); d.add("ibra8y3-web"); return d; }
    return new LinkedHashSet<>(sp.getStringSet("ghsrc", new HashSet<String>()));
  }
  void saveSources(Set<String> s) { sp.edit().putStringSet("ghsrc", new HashSet<>(s)).apply(); }

  String ghGet(String url) throws IOException {
    HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection(); cn.setConnectTimeout(12000); cn.setReadTimeout(20000);
    cn.setRequestProperty("Accept", "application/vnd.github+json"); cn.setRequestProperty("User-Agent", "Kanz-Store");
    int code = cn.getResponseCode();
    if (code == 403 || code == 429) throw new IOException("وصلت حد البحث الموسّع المجاني. جرّب بعد شوية.");
    if (code == 404) throw new IOException("غير موجود في المصدر");
    if (code != 200) throw new IOException("المصدر رد HTTP " + code);
    BufferedReader r = new BufferedReader(new InputStreamReader(cn.getInputStream(), "UTF-8")); StringBuilder sb = new StringBuilder(); String l; while ((l = r.readLine()) != null) sb.append(l).append('\n'); r.close();
    return sb.toString();
  }

  View ghView() {
    LinearLayout pg = page(); pg.addView(topBar("مصدر خارجي", "بحث مباشر في مصدر خارجي (اختياري). تطبيقات مصدر خارجي الموثّقة موجودة بالفعل في الرئيسية والأقسام", false));
    ScrollView sv = new ScrollView(this); LinearLayout c = vbox(); c.setPadding(dp(14), dp(12), dp(14), dp(20)); sv.addView(c);
    pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView w = tv("⚠ التطبيقات هنا من مصدر خارجي مباشرة وغير موثّقة من المتجر. ثبّت بس من مطوّر بتثق فيه. البحث بيشتغل لما تضغط بحث عشان يوفّر نتك.", 12, WARN, false); w.setPadding(dp(12), dp(10), dp(12), dp(10)); w.setBackground(bg(WARNBG, 14)); c.addView(w);

    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
    final EditText e = edit(); e.setHint(tr("ابحث عن تطبيق أندرويد…")); e.setText(tr(ghQuery)); e.setSingleLine(true); e.setTextSize(15); e.setBackground(stroke(CARD, LINE, 24)); e.setPadding(dp(16), dp(10), dp(16), dp(10));
    row.addView(e, new LinearLayout.LayoutParams(0, -2, 1));
    Button go = pbtn("بحث", PRIMARY); row.addView(go, lp(80, 46, 8, 0, 0, 0)); c.addView(row, lp(-1, -2, 0, 12, 0, 0));
    final Runnable doSearch = () -> ghSearch(e.getText().toString().trim());
    go.setOnClickListener(x -> doSearch.run());
    e.setOnEditorActionListener((v, a, ev) -> { doSearch.run(); return true; });
    HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); LinearLayout cr = new LinearLayout(this); cr.setPadding(0, dp(8), 0, dp(4)); hs.addView(cr);
    for (final String t : GH_TOPICS) { TextView ch = tv(t, 12, PRIMARY, false); ch.setPadding(dp(12), dp(6), dp(12), dp(6)); ch.setBackground(stroke(CARD, LINE, 16)); ch.setOnClickListener(x -> { e.setText(tr(t)); ghSearch(t); }); cr.addView(ch, lp(-2, -2, 3, 0, 3, 0)); }
    c.addView(hs);

    TextView rh = tv(ghResults.isEmpty() && ghStatus.isEmpty() ? "" : "نتائج البحث", 17, INK, true); rh.setPadding(0, dp(16), 0, dp(4)); c.addView(rh);
    if (!ghStatus.isEmpty()) { TextView st = tv(ghStatus, 13, SUB, false); c.addView(st); }
    for (JSONObject o : ghResults) c.addView(ghRepoRow(o));
    return pg;
  }

  View ghRepoRow(final JSONObject o) {
    final String full = o.optString("full_name"); JSONObject li = o.optJSONObject("license"); String lic = li == null ? "بدون ترخيص" : li.optString("spdx_id", "?");
    String d = o.isNull("description") ? "" : o.optString("description", "");
    JSONObject ow = o.optJSONObject("owner");
    View v = genericRow("gh:" + full, full.substring(full.indexOf('/') + 1), d, "★ " + o.optInt("stargazers_count") + "  •  " + lic + "  •  " + full.substring(0, full.indexOf('/')), "عرض", PRIMARY, 0xFFE0E7FF, ow != null ? ow.optString("avatar_url", null) : null);
    v.setOnClickListener(x -> ghOpen(full, o)); return v;
  }

  void ghSearch(final String q) {
    if (q.isEmpty()) return;
    ghQuery = q; ghStatus = "جاري البحث في مصدر خارجي…"; ghResults = new ArrayList<>(); show();
    new Thread(() -> {
      try {
        String url = "https://api.github.com/search/repositories?q=" + URLEncoder.encode(q + " topic:android", "UTF-8") + "&sort=stars&order=desc&per_page=30";
        JSONArray items = new JSONObject(ghGet(url)).getJSONArray("items"); List<JSONObject> l = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
          JSONObject o = items.getJSONObject(i); JSONObject li = o.optJSONObject("license");
          if (li == null || li.optString("spdx_id").equals("NOASSERTION") || li.optString("spdx_id").isEmpty()) continue;
          if (o.optBoolean("archived")) continue;
          l.add(o);
        }
        final List<JSONObject> res = l;
        ui.post(() -> { ghResults = res; ghStatus = res.isEmpty() ? "ما لقيتش نتائج بترخيص مفتوح. جرّب كلمة تانية." : res.size() + " مشروع بترخيص مفتوح. افتح المشروع عشان تشوف لو فيه APK."; if (tab == 2 && stack.isEmpty()) show(); });
      } catch (final Exception e) { ui.post(() -> { ghStatus = "⚠ " + e.getMessage(); if (tab == 2 && stack.isEmpty()) show(); }); }
    }).start();
  }

  void ghUser(final String user) {
    final LinearLayout pg = page(); pg.addView(topBar("🐙 " + user, "مشاريع الحساب", true));
    final LinearLayout list = vbox(); final ScrollView sv = new ScrollView(this); sv.addView(list); pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    final TextView st = tv("جاري التحميل من مصدر خارجي…", 14, SUB, false); st.setPadding(dp(18), dp(18), dp(18), 0); list.addView(st);
    push(pg);
    new Thread(() -> {
      try {
        JSONArray ar = new JSONArray(ghGet("https://api.github.com/users/" + user + "/repos?per_page=100&sort=pushed")); final List<JSONObject> l = new ArrayList<>();
        for (int i = 0; i < ar.length(); i++) l.add(ar.getJSONObject(i));
        ui.post(() -> { list.removeAllViews(); if (l.isEmpty()) { st.setText(tr("مفيش مشاريع عامة")); list.addView(st); } for (JSONObject o : l) list.addView(ghRepoRow(o)); });
      } catch (final Exception e) { ui.post(() -> st.setText(tr("⚠ " + e.getMessage()))); }
    }).start();
  }

  void addSourceDialog() {
    final EditText e = edit(); e.setHint(tr("ibra8y3-web أو https://github.com/اسم/مشروع")); e.setSingleLine(true);
    new TB(this).setTitle("أضف حساب أو مشروع من مصدر خارجي").setView(e).setPositiveButton("إضافة", (d, w) -> {
      String s = e.getText().toString().trim().replaceAll("^https?://(www\\.)?github\\.com/", "").replaceAll("^github\\.com/", "").replaceAll("\\.git$", "").replaceAll("/+$", "");
      String[] p = s.split("/");
      if (p.length == 0 || !p[0].matches("[A-Za-z0-9_.-]+") || (p.length > 1 && !p[1].matches("[A-Za-z0-9_.-]+"))) { mk("الاسم مش مظبوط", Toast.LENGTH_SHORT).show(); return; }
      String v = p.length > 1 ? p[0] + "/" + p[1] : p[0];
      Set<String> n = ghSources(); n.add(v); saveSources(n); show();
    }).setNegativeButton("إلغاء", null).show();
  }

  void ghOpen(final String full, final JSONObject repoInfo) {
    LinearLayout pg = page(); pg.addView(topBar(full.substring(full.indexOf('/') + 1), null, true));
    ScrollView sv = new ScrollView(this); detailBox = vbox(); detailBox.setPadding(dp(14), dp(14), dp(14), dp(24)); sv.addView(detailBox);
    pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    final String key = "gh:" + full; curKey = key;
    final JSONObject[] info = {repoInfo}; final JSONObject[] rel = {null}; final String[] err = {null}; final boolean[] loadedF = {false};
    detailFill = () -> ghFill(full, info[0], rel[0], err[0], loadedF[0]);
    detailFill.run(); push(pg);
    new Thread(() -> {
      try {
        if (info[0] == null) info[0] = new JSONObject(ghGet("https://api.github.com/repos/" + full));
        JSONArray rs = new JSONArray(ghGet("https://api.github.com/repos/" + full + "/releases?per_page=8"));
        for (int i = 0; i < rs.length() && rel[0] == null; i++) { JSONObject r = rs.getJSONObject(i); JSONArray as = r.getJSONArray("assets"); for (int j = 0; j < as.length(); j++) if (as.getJSONObject(j).optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")) { rel[0] = r; break; } }
      } catch (Exception e) { err[0] = e.getMessage(); }
      loadedF[0] = true; ui.post(() -> { if (key.equals(curKey) && detailBox != null && detailFill != null) detailFill.run(); });
    }).start();
  }

  void ghFill(final String full, JSONObject info, JSONObject rel, String err, boolean loadedF) {
    LinearLayout c = detailBox; c.removeAllViews(); final String key = "gh:" + full;
    TextView w = tv("⚠ غير مُراجَع يدويًا. التطبيق من مصدر خارجي مباشر. ثبّت بس لو بتثق في المطوّر.", 13, WARN, false); w.setPadding(dp(14), dp(10), dp(14), dp(10)); w.setBackground(bg(WARNBG, 14)); c.addView(w);
    if (info != null) {
      JSONObject li = info.optJSONObject("license"); String lic = li == null ? "غير محدد" : li.optString("spdx_id", "?");
      String d = info.isNull("description") ? "" : info.optString("description", ""); if (!d.isEmpty()) { TextView dt = tv(d, 14, INK, false); dt.setPadding(0, dp(12), 0, 0); c.addView(dt); }
      statGrid(c, new String[][]{{"★ " + info.optInt("stargazers_count"), "النجوم"}, {lic, "الرخصة"}, {info.optString("pushed_at", "").length() >= 10 ? ltr(info.optString("pushed_at").substring(0, 10)) : "-", "آخر نشاط"}});
      if (li == null) { TextView n = tv("المشروع بدون ترخيص معلن. لو مشروعك إنت فده عادي، ولو لحد تاني اتأكد إنه مسموح.", 12, SUB, false); n.setPadding(0, dp(6), 0, 0); c.addView(n); }
    }
    if (!loadedF) { TextView l = tv("جاري قراءة النسخ المنشورة…", 14, SUB, false); l.setPadding(0, dp(16), 0, 0); c.addView(l); }
    else if (err != null) { TextView l = tv("⚠ " + err, 14, RED, false); l.setPadding(0, dp(16), 0, 0); c.addView(l); }
    else if (rel == null) { TextView l = tv("المشروع ده مفيهوش ملف APK في صفحة النسخ (Releases) لحد دلوقتي.", 14, SUB, false); l.setPadding(0, dp(16), 0, 0); c.addView(l); }
    else {
      TextView h = tv("النسخة " + rel.optString("tag_name") + "  •  " + (rel.optString("published_at").length() >= 10 ? ltr(rel.optString("published_at").substring(0, 10)) : ""), 15, PRIMARY, true); h.setPadding(0, dp(16), 0, dp(4)); c.addView(h);
      dlBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); dlBar.setMax(1000); c.addView(dlBar, lp(-1, -2, 0, 4, 0, 0)); dlText = tv("", 12, SUB, false); c.addView(dlText);
      boolean busy = downloading.contains(key); dlBar.setVisibility(busy ? View.VISIBLE : View.GONE); if (busy) { String m = dlMsg.get(key); dlText.setText(tr(m == null ? "" : m)); Integer p = dlProg.get(key); dlBar.setProgress(p == null ? 0 : p); }
      Rec r = recs.get(key);
      if (r != null && r.state == 3) { c.addView(pill("✅ مثبّت عندك", OK, OKBG), lp(-2, -2, 0, 4, 0, 4)); if (r.pkg != null) { final String pk = r.pkg; Button op = pbtn("فتح التطبيق", OK); op.setOnClickListener(v -> { Intent i = getPackageManager().getLaunchIntentForPackage(pk); if (i != null) startActivity(i); }); c.addView(op, lp(-1, 50, 0, 6, 0, 6)); } }
      JSONArray as = rel.optJSONArray("assets"); String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
      for (int i = 0; as != null && i < as.length(); i++) {
        final JSONObject a = as.optJSONObject(i); final String nm = a.optString("name"); if (!nm.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
        final String dg = a.isNull("digest") ? "" : a.optString("digest", ""); final long sz = a.optLong("size");
        boolean recd = nm.toLowerCase(Locale.ROOT).contains(abi.toLowerCase(Locale.ROOT)); boolean otherAbi = !recd && (nm.contains("arm64") || nm.contains("armeabi") || nm.contains("x86"));
        LinearLayout cd = card(); cd.addView(tv(nm, 14, INK, true)); cd.addView(tv(mb(sz) + (recd ? "  •  ✅ مناسب لجهازك" : otherAbi ? "  •  ⚠ لمعالج تاني" : "") + "  •  " + (dg.startsWith("sha256:") ? "SHA-256 متاح" : "بدون بصمة"), 12, recd ? OK : SUB, false));
        Button b = pbtn("تحميل وتثبيت (" + mb(sz) + ")", recd ? PRIMARY : SUB); b.setEnabled(!busy);
        b.setOnClickListener(v -> new TB(this).setTitle("تحميل من مصدر خارجي").setMessage("الملف: " + nm + "\nالحجم: " + mb(sz) + "\n" + (dg.startsWith("sha256:") ? "هنتأكد من البصمة قبل التثبيت." : "مفيش بصمة للتأكد منها.") + "\n\nده تطبيق من مصدر خارجي غير موثّق من المتجر. تكمل؟").setPositiveButton("تحميل", (d, q) -> ghDownload(full, a)).setNegativeButton("إلغاء", null).show());
        cd.addView(b, lp(-1, 48, 0, 8, 0, 0)); c.addView(cd, lp(-1, -2, 0, 8, 0, 0));
      }
    }
    c.addView(link("🌐 المصدر", "https://github.com/" + full), lp(-1, 46, 0, 14, 0, 0));
  }

  static String ltr(String t) { return t == null || t.isEmpty() ? t : "\u2066" + t + "\u2069"; }
  void ghDownload(final String full, final JSONObject a) {
    if (!canInstall()) return;
    if (full.equals(SELF_ID) || full.equals(SELF_ID2)) {   // the store's own APK: always use the fresh hash/size from app.json (the catalog can lag behind a new upload)
      new Thread(() -> {
        JSONObject use = a;
        for (String base : catBases()) {
          try { JSONObject j = new JSONObject(AutoUpd.get(base + "app.json?t=" + System.currentTimeMillis(), 12000)); JSONObject b = new JSONObject(a.toString()); b.put("digest", "sha256:" + j.getString("sha256")); b.put("size", j.optLong("size")); b.put("browser_download_url", base + j.optString("file", "kanz.apk") + "?t=" + System.currentTimeMillis()); use = b; sp.edit().putString("catbase_ok", base).apply(); break; } catch (Exception e) { }
        }
        final JSONObject fu = use; ui.post(() -> ghDownload2(full, fu));
      }).start(); return;
    }
    ghDownload2(full, a);
  }
  void ghDownload2(final String full, final JSONObject a) {
    String key = "gh:" + full; String url = a.optString("browser_download_url"), nm = a.optString("name"); String dg = a.isNull("digest") ? "" : a.optString("digest", "");
    List<String> urls = new ArrayList<>(); urls.add(url);
    enqueue(key, full.substring(full.indexOf('/') + 1), "", true, urls, "gh_" + (full + "_" + nm).replaceAll("[^A-Za-z0-9._-]", "_"), dg.startsWith("sha256:") ? dg.substring(7) : "", sp.getBoolean("wifionly", false), false, a.optString("tag"));
  }

  // ======== Kanz remote catalog (built by the crawler in the user's catalog repo) ========
  static final String DEF_BASE = "https://raw.githubusercontent.com/ibra8y3-web/kanz/main/docs/";
  static final String OLD_BASE = "https://raw.githubusercontent.com/ibra8y3-web/kanz-catalog/main/docs/";
  static final String SELF_ID = "ibra8y3-web/kanz-catalog";
  static final String SELF_ID2 = "ibra8y3-web/kanz";
  Map<String, JSONObject> rc; long rcRev = 0; String rcGen = ""; String rcStatus = "";

  String catBase() { String b = sp.getString("catbase", DEF_BASE); if (!b.endsWith("/")) b += "/"; return b; }
  String[] catBases() {
    String over = sp.getString("catbase", "");
    if (!over.isEmpty()) { if (!over.endsWith("/")) over += "/"; return new String[]{over}; }
    LinkedHashSet<String> s = new LinkedHashSet<>();
    s.add(sp.getString("catbase_ok", DEF_BASE)); s.add(DEF_BASE); s.add(OLD_BASE);
    return s.toArray(new String[0]);
  }
  String curBase() { String b = sp.getString("catbase", ""); if (b.isEmpty()) b = sp.getString("catbase_ok", DEF_BASE); if (!b.endsWith("/")) b += "/"; return b; }
  byte[] httpBytesAny(String rel) throws IOException {
    IOException err = null;
    for (String b : catBases()) {
      try { byte[] r = httpBytes(b + rel); sp.edit().putString("catbase_ok", b).apply(); return r; }
      catch (IOException e) { err = e; }
    }
    throw err == null ? new IOException("no catalog base") : err;
  }
  boolean metered() { try { return ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).isActiveNetworkMetered(); } catch (Exception e) { return true; } }

  void loadRemote() {
    if (rc != null) return; rc = new LinkedHashMap<>(); rcRev = 0; rcGen = "";
    try { new File(getFilesDir(), "rc2.jsonl").delete(); } catch (Exception e) { }
    try {
      File f = new File(getFilesDir(), "rc2.jsonl"); if (!f.exists()) return;
      BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 1 << 16);
      String l = r.readLine(); JSONObject h = new JSONObject(l); rcRev = h.optLong("rev"); rcGen = h.optString("gen");
      while ((l = r.readLine()) != null) { JSONObject x = new JSONObject(l); rc.put(x.getString("id"), x); }
      r.close();
    } catch (Exception e) { rc = new LinkedHashMap<>(); rcRev = 0; rcGen = ""; }
  }

  void saveRemote() throws Exception {
    File t = new File(getFilesDir(), "rc2.tmp"); BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(t), "UTF-8"), 1 << 16);
    JSONObject h = new JSONObject(); h.put("rev", rcRev); h.put("gen", rcGen); w.write(h.toString()); w.write("\n");
    for (JSONObject x : rc.values()) { w.write(x.toString()); w.write("\n"); }
    w.close(); File d = new File(getFilesDir(), "rc2.jsonl"); d.delete(); t.renameTo(d);
  }

  JSONArray vers(JSONObject a) { try { String s = a.optString("vs", ""); return s.isEmpty() ? new JSONArray() : new JSONArray(s); } catch (Exception e) { return new JSONArray(); } }

  Object readVal(android.util.JsonReader jr) throws Exception {
    switch (jr.peek()) {
      case BEGIN_OBJECT: { JSONObject o = new JSONObject(); jr.beginObject(); while (jr.hasNext()) o.put(jr.nextName(), readVal(jr)); jr.endObject(); return o; }
      case BEGIN_ARRAY: { JSONArray a = new JSONArray(); jr.beginArray(); while (jr.hasNext()) a.put(readVal(jr)); jr.endArray(); return a; }
      case STRING: return jr.nextString();
      case NUMBER: { String n = jr.nextString(); try { return Long.parseLong(n); } catch (Exception e) { return Double.parseDouble(n); } }
      case BOOLEAN: return jr.nextBoolean();
      default: jr.nextNull(); return JSONObject.NULL;
    }
  }

  byte[] httpBytes(String url) throws IOException {
    HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection(); cn.setConnectTimeout(12000); cn.setReadTimeout(30000); cn.setRequestProperty("User-Agent", "Kanz-Store");
    cn.setRequestProperty("Cache-Control", "no-cache");
    int code = cn.getResponseCode(); if (code == 404) throw new IOException("الكتالوج مش موجود على الرابط ده. راجع رابط الكتالوج (⚙) أو شغّل الـ crawler مرة."); if (code != 200) throw new IOException("HTTP " + code);
    InputStream in = cn.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); return bo.toByteArray();
  }

  long rcPending = 0; boolean askedFirst = false, syncing = false; String rcMsg = "";

  // Silent catalog sync. manual=true: user asked (menu), may show dialogs.
  long lastMetaRev = -1;
  void syncRemote(final boolean manual) {
    if (System.currentTimeMillis() - sp.getLong("missclr", 0) > 30L * 60000L) { miss.clear(); sp.edit().putLong("missclr", System.currentTimeMillis()).apply(); }
    if (syncing) return; syncing = true; loadRemote(); if (manual) { rcStatus = "جاري فحص الكتالوج…"; }
    new Thread(() -> {
      try {
        final JSONObject m = new JSONObject(new String(httpBytesAny("meta.json"), "UTF-8"));
        final long rev = m.optLong("rev");
        lastMetaRev = rev;
        sp.edit().putLong("rcchk", System.currentTimeMillis()).apply();
        if (rev == rcRev && !rc.isEmpty()) { rcPending = 0; ui.post(() -> { syncing = false; rcStatus = "الكتالوج محدّث ✅"; if (manual) { rcMsg = "✅ الكتالوج محدّث (" + rc.size() + " تطبيق إضافي)"; if (tab == 0 && stack.isEmpty()) show(); } if (manual) mk("الكتالوج محدّث (" + rc.size() + " تطبيق من مصدر خارجي)", Toast.LENGTH_SHORT).show(); }); return; }
        final boolean useDelta = rcRev > 0 && rcRev >= m.optLong("delta_base", Long.MAX_VALUE) && !rc.isEmpty();
        final long bytes = useDelta ? m.optLong("delta_bytes") : m.optLong("full_bytes");
        final boolean first = rc.isEmpty(); final boolean met = metered();
        ui.post(() -> {
          if (met && !manual && sp.getBoolean("limdata", false) && bytes > 150 * 1024 && !first) { rcPending = bytes; syncing = false; }
          else doRemote(useDelta, manual);
        });
      } catch (final Exception e) { ui.post(() -> { syncing = false; rcStatus = "⚠ " + e.getMessage(); if (manual) rcMsg = "⚠ مفيش اتصال بالكتالوج دلوقتي، هنحاول تاني."; if (manual) mk(rcStatus, Toast.LENGTH_LONG).show(); }); }
    }).start();
  }

  void doRemote(final boolean delta, final boolean manual) {
    rcMsg = "⏳ جاري تحديث الكتالوج…"; if (tab == 0 && stack.isEmpty()) show();
    new Thread(() -> {
      try {
        byte[] gz = null; File pf = new File(getFilesDir(), "pre.gz");
        if (pf.exists() && sp.getLong("prerev", -1) == lastMetaRev && sp.getBoolean("predelta", false) == delta) { try { FileInputStream fi = new FileInputStream(pf); ByteArrayOutputStream bo0 = new ByteArrayOutputStream(); byte[] b0 = new byte[16384]; int n0; while ((n0 = fi.read(b0)) > 0) bo0.write(b0, 0, n0); fi.close(); gz = bo0.toByteArray(); } catch (Exception e0) { gz = null; } }
        if (gz == null) gz = httpBytesAny(delta ? "delta.json.gz" : "catalog.json.gz");
        android.util.JsonReader jr = new android.util.JsonReader(new InputStreamReader(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(gz)), "UTF-8"));
        Map<String, JSONObject> nm = delta ? new LinkedHashMap<>(rc) : new LinkedHashMap<String, JSONObject>(); final int before = nm.size();
        long rev = 0; String gen = "";
        jr.beginObject();
        while (jr.hasNext()) {
          String k = jr.nextName();
          if (k.equals("apps")) {
            jr.beginArray();
            while (jr.hasNext()) { JSONObject x = (JSONObject) readVal(jr); Object v = x.opt("v"); x.put("vs", v == null ? "[]" : v.toString()); x.remove("v"); nm.put(x.getString("id"), x); }
            jr.endArray();
          } else if (k.equals("rev")) rev = jr.nextLong(); else if (k.equals("gen")) gen = jr.nextString(); else jr.skipValue();
        }
        jr.endObject(); jr.close();
        if (nm.isEmpty() && rev == 0) throw new IOException("الكتالوج فاضي");
        final int added = nm.size() - (delta ? before : 0);
        rc = nm; rcRev = rev; rcGen = gen; saveRemote(); rcPending = 0;
        new File(getFilesDir(), "pre.gz").delete(); miss.clear();
        final int prevTotal = before;
        ui.post(() -> { syncing = false; mergeRemote(); rcMsg = delta ? (added > 0 ? "✅ تم تحديث الكتالوج: +" + added + " تطبيق جديد" : "✅ تم تحديث الكتالوج (تحديثات على تطبيقات موجودة)") : "✅ تم تحميل الكتالوج: " + rc.size() + " تطبيق"; if (manual) mk(rcMsg, Toast.LENGTH_SHORT).show(); if (stack.isEmpty()) show(); });
      } catch (final Throwable e) { ui.post(() -> { syncing = false; rcMsg = "⚠ تعذّر تحديث الكتالوج، هنحاول تاني. (" + e.getClass().getSimpleName() + ")"; if (manual) mk(rcMsg, Toast.LENGTH_LONG).show(); if (tab == 0 && stack.isEmpty()) show(); }); }
    }).start();
  }

  // put the synced GitHub/GitLab/Codeberg apps into the main list, in their categories
  void mergeRemote() {
    loadRemote();
    for (Iterator<App> it = all.iterator(); it.hasNext(); ) { App x = it.next(); if (x.gh != null) { it.remove(); byPk.remove(x.pk); } }
    int other = cats.length - 1; Map<String, Integer> ci = new HashMap<>(); for (int i = 0; i < cats.length; i++) ci.put(cats[i], i);
    String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0].toLowerCase(Locale.ROOT) : "";
    for (JSONObject o : rc.values()) {
      JSONArray vs = vers(o); if (vs.length() == 0) continue;
      String dup = o.optString("dup", ""); if (!o.isNull("dup") && dup.length() > 0) continue;
      if (o.optInt("arch") == 1) continue;
      JSONArray v0 = null; for (int i = 0; i < vs.length(); i++) { JSONArray v = vs.optJSONArray(i); if (v.optInt(7) == 0 && v.optInt(8) == 0) { v0 = v; break; } }
      if (v0 == null) v0 = vs.optJSONArray(0);
      // prefer the file matching the phone's ABI inside the latest tag
      for (int i = 0; i < vs.length(); i++) { JSONArray v = vs.optJSONArray(i); if (v.optString(0).equals(v0.optString(0)) && v.optString(2).toLowerCase(Locale.ROOT).contains(abi)) { v0 = v; break; } }
      App a = new App(); String id = o.optString("id"); a.pk = "gh:" + id; a.gh = o; a.name = o.optString("n"); a.sum = o.optString("d"); a.lic = o.optString("l"); a.apk = ""; a.sha = v0.optString(4);
      a.size = v0.optLong(3); a.vn = v0.optString(0); a.vc = 0; a.icon = o.optInt("ic") == 1 ? curBase() + "icons/" + id.replaceAll("[^A-Za-z0-9._-]", "_") + ".webp" : ""; a.af = ""; a.web = o.optString("u"); a.src = o.optString("h"); a.repo = REPO_NAMES.length - 1; a.desc = a.sum; a.minSdk = 0;
      Integer c = ci.get(o.optString("c")); int cc = c == null ? other : c; a.cats = new int[]{cc};
      a.upd = ts(v0.optString(1)); long ad = a.upd; for (int i = 0; i < vs.length(); i++) ad = Math.min(ad, ts(vs.optJSONArray(i).optString(1))); a.added = Math.max(ad, ts(o.optString("add")) > 0 ? Math.min(ts(o.optString("add")), a.upd) : ad);
      a.lname = a.name.toLowerCase(Locale.ROOT); a.lsum = a.sum.toLowerCase(Locale.ROOT);
      all.add(a); byPk.put(a.pk, a);
    }
    Collections.sort(all, (x, y) -> x.lname.compareTo(y.lname));
    java.util.Arrays.fill(catCount, 0); for (App a : all) for (int cc : a.cats) if (cc < catCount.length) catCount[cc]++;
  }

  long ts(String d) { try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d).getTime() / 1000L; } catch (Exception e) { return 0; } }

  View dlgBox(EditText e) {
    LinearLayout l = vbox(); l.setPadding(dp(16), 0, dp(16), 0); l.addView(e);
    final CheckBox cb = new CheckBox(this); cb.setText(tr("توفير البيانات: ما تحدّثش الكتالوج الكبير على داتا الموبايل")); cb.setChecked(sp.getBoolean("limdata", false));
    cb.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("limdata", c).apply()); l.addView(cb); return l;
  }
  void catUrlDialog() {
    final EditText e = edit(); e.setText(tr(catBase())); e.setSingleLine(true); e.setTextSize(12); e.setTextDirection(View.TEXT_DIRECTION_LTR);
    new TB(this).setTitle("رابط الكتالوج").setMessage("رابط مجلد الكتالوج (إعداد متقدّم).").setView(dlgBox(e))
      .setPositiveButton("حفظ", (d, w) -> { String u = e.getText().toString().trim(); if (u.startsWith("https://")) { sp.edit().putString("catbase", u).apply(); rc = null; new File(getFilesDir(), "rc2.jsonl").delete(); rcStatus = ""; mergeRemote(); syncRemote(true); show(); } else mk("لازم يبدأ بـ https://", Toast.LENGTH_SHORT).show(); })
      .setNeutralButton("الافتراضي", (d, w) -> { sp.edit().remove("catbase").apply(); rc = null; new File(getFilesDir(), "rc2.jsonl").delete(); mergeRemote(); syncRemote(true); show(); }).setNegativeButton("إلغاء", null).show();
  }

  void openRemoteApp(final String id) {
    final JSONObject a = rc.get(id); if (a == null) return;
    LinearLayout pg = page(); pg.addView(topBar(a.optString("n"), a.optString("c"), true));
    ScrollView sv = new ScrollView(this); detailBox = vbox(); detailBox.setPadding(dp(14), dp(14), dp(14), dp(24)); sv.addView(detailBox);
    pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1)); final String key = "gh:" + id; curKey = key;
    detailFill = () -> remoteFill(a); detailFill.run(); push(pg);
  }

  void remoteFill(final JSONObject a) {
    LinearLayout c = detailBox; c.removeAllViews(); final String id = a.optString("id"), key = "gh:" + id; final String host = a.optString("h");
    TextView w = tv("⚠ غير مُراجَع يدويًا. متفحّص آليًا (ترخيص مفتوح + بصمة) لكن مش مراجَع بواسطتنا. ثبّت بس لو بتثق في المطوّر.", 13, WARN, false); w.setPadding(dp(14), dp(10), dp(14), dp(10)); w.setBackground(bg(WARNBG, 14)); c.addView(w);
    String d = a.optString("d"); if (!d.isEmpty()) descCard(c, key, d, false);
    JSONArray vs = vers(a); int nv = vs.length();
    statGrid(c, new String[][]{{"★ " + a.optInt("s"), "النجوم"}, {a.optString("l"), "الرخصة"}, {nv + "", "عدد النسخ"}});
    TextView meta = tv("القسم: " + a.optString("c") + "  •  أُضيف: " + a.optString("add"), 11, SUB, false); meta.setPadding(0, dp(8), 0, 0); c.addView(meta);
    { JSONArray v0b = vs.optJSONArray(0); c.addView(verBadge(a.optString("l"), v0b != null ? v0b.optString(1) : ""), lp(-2, -2, 0, 8, 0, 0)); }
    if (a.optInt("arch") == 1) { TextView n = tv("⚠ المشروع اتأرشف أو اتحذف من المصدر.", 12, RED, false); c.addView(n); }
    String md = a.optString("md", ""); if (md.length() > 0 && !md.equals("null")) { TextView n = tv("ℹ فيه مشروع تاني بنفس الاسم (ممكن نسخة أو fork).", 12, SUB, false); c.addView(n); }
    dlBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); dlBar.setMax(1000); c.addView(dlBar, lp(-1, -2, 0, 8, 0, 0)); dlText = tv("", 12, SUB, false); c.addView(dlText);
    final boolean busy = downloading.contains(key); dlBar.setVisibility(busy ? View.VISIBLE : View.GONE); if (busy) { String m = dlMsg.get(key); dlText.setText(tr(m == null ? "" : m)); Integer p = dlProg.get(key); dlBar.setProgress(p == null ? 0 : p); }
    Rec r = recs.get(key);
    if (r != null && r.state == 3) { c.addView(pill("✅ مثبّت عندك", OK, OKBG), lp(-2, -2, 0, 6, 0, 4)); if (r.pkg != null) { final String pk = r.pkg; Button op = pbtn("فتح التطبيق", OK); op.setOnClickListener(v -> { Intent i = getPackageManager().getLaunchIntentForPackage(pk); if (i != null) startActivity(i); }); c.addView(op, lp(-1, 50, 0, 6, 0, 6)); } }
    { final App ga = byPk.get(key); if (ga != null) addShare(c, ga); }
    TextView hh = tv("سجل النسخ (" + nv + ") — كل النسخ القديمة محفوظة", 15, PRIMARY, true); hh.setPadding(0, dp(14), 0, dp(4)); c.addView(hh);
    String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0].toLowerCase(Locale.ROOT) : "";
    for (int i = 0; vs != null && i < vs.length(); i++) {
      JSONArray v = vs.optJSONArray(i); final String tag = v.optString(0), date = v.optString(1), file = v.optString(2), sha = v.optString(4), url = v.optString(5); final long size = v.optLong(3);
      boolean pre = v.optInt(7) == 1, gone = v.optInt(8) == 1, sup = v.optInt(9) == 1; boolean recd = file.toLowerCase(Locale.ROOT).contains(abi); boolean other = !recd && (file.contains("arm64") || file.contains("armeabi") || file.contains("x86"));
      LinearLayout cd = card(); cd.addView(tv(tag + (i == 0 ? "  (الأحدث)" : "") + "  •  " + ltr(date), 14, INK, true));
      cd.addView(tv(file + "\n" + (size > 0 ? mb(size) : "الحجم غير معروف") + (recd ? "  •  ✅ مناسب لجهازك" : other ? "  •  ⚠ لمعالج تاني" : "") + (pre ? "  •  تجريبية" : "") + (sup ? "  •  اتعدّلت بعد النشر" : "") + (gone ? "  •  اتشالت من المصدر" : ""), 12, recd ? OK : SUB, false));
      TextView hs = tv("SHA-256: " + sha.substring(0, Math.min(20, sha.length())) + "… (اضغط مطوّلًا للنسخ)", 11, SUB, false); hs.setTextDirection(View.TEXT_DIRECTION_LTR);
      hs.setOnLongClickListener(x -> { ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("sha256", sha)); mk("اتنسخت البصمة كاملة", Toast.LENGTH_SHORT).show(); return true; }); cd.addView(hs);
      Button b = pbtn((i == 0 ? "تحميل وتثبيت" : "تحميل النسخة دي") + (size > 0 ? " (" + mb(size) + ")" : ""), i == 0 && !other ? PRIMARY : SUB); b.setEnabled(!busy);
      b.setOnClickListener(x -> new TB(this).setTitle("تحميل " + tag).setMessage("الملف: " + file + "\nالحجم: " + (size > 0 ? mb(size) : "غير معروف") + "\nهنتأكد من البصمة قبل التثبيت." + (gone ? "\n\nالنسخة دي اتشالت من المصدر وممكن التحميل يفشل." : "") + (metered() ? "\n\n⚠ أنت على بيانات الموبايل." : "") + "\n\nتكمل؟")
        .setPositiveButton("تحميل", (d2, q) -> { try { JSONObject as = new JSONObject(); as.put("browser_download_url", url); as.put("name", file); as.put("size", size); as.put("digest", "sha256:" + sha); as.put("tag", tag); ghDownload(id, as); } catch (Exception ex) { } }).setNegativeButton("إلغاء", null).show());
      cd.addView(b, lp(-1, 46, 0, 8, 0, 0)); c.addView(cd, lp(-1, -2, 0, 8, 0, 0));
    }
    c.addView(link("🌐 المصدر", a.optString("u")), lp(-1, 46, 0, 14, 0, 0));
  }

  // ===== background source search, merged into the normal search results =====
  List<App> liveRes = new ArrayList<>(); String liveQ = ""; Runnable searchLocal, liveTimer = new Runnable() { public void run() { } }; TextView searchStatus; EditText searchEdit;
  static final java.util.regex.Pattern BADWORDS = java.util.regex.Pattern.compile("(?i)\\b(mod|modded|cracked?|hacked|patched|premium|unlocked|revanced|pirate\\w*)\\b");

  void liveSearch(final String qraw, final boolean explicit) {
    final String q = qraw.trim(); if (q.length() < 2 || searchStatus == null) return;
    if (liveQ.equals(q.toLowerCase(Locale.ROOT))) return;
    if (explicit && metered()) {
      new TB(this).setMessage("البحث الموسّع في مصادر إضافية هيستهلك حوالي 50-100 KB من بيانات الموبايل. تكمل؟").setPositiveButton("ابحث", (d, w) -> doLive(q)).setNegativeButton("لا", null).show();
    } else if (!metered() || explicit) doLive(q);
  }

  void doLive(final String q) {
    final TextView st = searchStatus; st.setText(tr("جاري البحث في مصادر إضافية…"));
    new Thread(() -> {
      try {
        String url = "https://api.github.com/search/repositories?q=" + URLEncoder.encode(q + " topic:android", "UTF-8") + "&sort=stars&order=desc&per_page=30";
        JSONArray items = new JSONObject(ghGet(url)).getJSONArray("items"); final List<App> out = new ArrayList<>(); int other = cats.length - 1;
        for (int i = 0; i < items.length(); i++) {
          JSONObject o = items.getJSONObject(i); JSONObject li = o.optJSONObject("license");
          if (li == null || li.optString("spdx_id").equals("NOASSERTION") || li.optString("spdx_id").isEmpty() || o.optBoolean("archived") || o.optBoolean("fork")) continue;
          String full = o.optString("full_name"); String desc = o.isNull("description") ? "" : o.optString("description", "");
          if (BADWORDS.matcher(full + " " + desc).find()) continue;
          if (byPk.containsKey("gh:" + full)) continue;
          JSONObject g = new JSONObject(); g.put("id", full); g.put("n", o.optString("name")); g.put("d", desc); g.put("l", li.optString("spdx_id")); g.put("s", o.optInt("stargazers_count")); g.put("u", o.optString("html_url")); g.put("live", 1);
          App a = new App(); a.pk = "gh:" + full; a.gh = g; a.name = o.optString("name"); a.sum = desc; a.lic = li.optString("spdx_id"); a.apk = ""; a.sha = ""; a.vn = ""; a.icon = ""; a.af = "";
          a.web = a.pk; a.src = ""; a.repo = REPO_NAMES.length - 1; a.desc = desc; a.cats = new int[]{other}; a.lname = a.name.toLowerCase(Locale.ROOT); a.lsum = desc.toLowerCase(Locale.ROOT); out.add(a);
        }
        final String lq = q.toLowerCase(Locale.ROOT);
        ui.post(() -> {
          liveQ = lq; liveRes = out; st.setText(tr(out.isEmpty() ? "مفيش نتائج إضافية. (النتايج الإضافية غير مُراجَعة يدويًا)" : out.size() + " نتيجة إضافية من مصادر خارجية (غير مُراجَعة يدويًا). افتح التطبيق وشوف لو ليه ملف تثبيت."));
          if (searchLocal != null) searchLocal.run();
        });
      } catch (final Exception e) { ui.post(() -> st.setText(tr("⚠ " + e.getMessage()))); }
    }).start();
  }

  void pullRefresh(final ScrollView sv) {
    final float[] y0 = {-1};
    sv.setOnTouchListener((v, ev) -> {
      switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN: y0[0] = sv.getScrollY() == 0 ? ev.getY() : -1; break;
        case MotionEvent.ACTION_MOVE: if (y0[0] < 0 && sv.getScrollY() == 0) y0[0] = ev.getY(); break;
        case MotionEvent.ACTION_UP: if (y0[0] >= 0 && sv.getScrollY() == 0 && ev.getY() - y0[0] > dp(110)) syncRemote(true); y0[0] = -1; break;
        case MotionEvent.ACTION_CANCEL: y0[0] = -1; break;
      }
      return false;
    });
  }

  // ================= v4: language, permissions, trackers =================
  static String lang = null; static boolean rtl = true;
  static final Map<String, String> trm = new HashMap<>(); static final List<String> trKeys = new ArrayList<>();
  static final String[][] LANGS = {{"ar", "العربية"}, {"en", "English"}, {"fr", "Français"}};
  static final java.util.regex.Pattern ARP = java.util.regex.Pattern.compile("[\u0600-\u06FF]");
  boolean resumed, destroyed, liveBusy, polling;

  static void ensureLang(Context c) { if (lang == null) loadLang(c); }
  static void loadLang(Context c) {
    lang = c.getSharedPreferences("s", 0).getString("lang", "ar"); rtl = lang.equals("ar") || lang.equals("fa") || lang.equals("ur") || lang.equals("he");
    trm.clear(); trKeys.clear(); if (lang.equals("ar")) return;
    try {
      BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("l_" + lang + ".tsv"), "UTF-8")); String l;
      while ((l = r.readLine()) != null) {
        int i = l.indexOf('\t'); if (i <= 0) continue;
        String k = l.substring(0, i).replace("\\n", "\n").trim(), v = l.substring(i + 1).replace("\\n", "\n").trim(); if (v.isEmpty() || k.isEmpty()) continue;
        trm.put(k, v);
      }
      r.close(); trKeys.addAll(trm.keySet());
      Collections.sort(trKeys, (x, y) -> y.length() - x.length());
    } catch (Exception e) { }
  }
  static boolean isArL(char c) { return c >= 0x0600 && c <= 0x06FF; }
  static String tr(CharSequence cs) {
    if (cs == null) return null; String s = cs.toString();
    if (lang == null || lang.equals("ar") || s.isEmpty() || !ARP.matcher(s).find()) return s;
    String st = s.trim(); String r = trm.get(st);
    if (r != null) { int i0 = s.indexOf(st); return s.substring(0, i0) + r + s.substring(i0 + st.length()); }
    StringBuilder out = new StringBuilder(s);
    for (String k : trKeys) {
      int from = 0, i;
      while ((i = out.indexOf(k, from)) >= 0) {
        int e = i + k.length();
        boolean lb = i == 0 || !isArL(out.charAt(i - 1)), rb = e >= out.length() || !isArL(out.charAt(e));
        if (lb && rb) { String v = trm.get(k); out.replace(i, e, v); from = i + v.length(); } else from = i + 1;
      }
    }
    return out.toString();
  }

  class TB extends AlertDialog.Builder {
    TB(Context c) { super(c, DARK ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert); }
    @Override public AlertDialog.Builder setTitle(CharSequence t) { return super.setTitle(tr(t)); }
    @Override public AlertDialog.Builder setMessage(CharSequence t) { return super.setMessage(tr(t)); }
    @Override public AlertDialog.Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { return super.setPositiveButton(tr(t), l); }
    @Override public AlertDialog.Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { return super.setNegativeButton(tr(t), l); }
    @Override public AlertDialog.Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { return super.setNeutralButton(tr(t), l); }
    CharSequence[] trAll(CharSequence[] it) { CharSequence[] o = new CharSequence[it.length]; for (int i = 0; i < it.length; i++) o[i] = tr(it[i]); return o; }
    @Override public AlertDialog.Builder setItems(CharSequence[] it, DialogInterface.OnClickListener l) { return super.setItems(trAll(it), l); }
    @Override public AlertDialog.Builder setMultiChoiceItems(CharSequence[] it, boolean[] ch, DialogInterface.OnMultiChoiceClickListener l) { return super.setMultiChoiceItems(trAll(it), ch, l); }
    @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] it, int c, DialogInterface.OnClickListener l) { return super.setSingleChoiceItems(trAll(it), c, l); }
  }

  Toast mk(CharSequence m, int d) { return Toast.makeText(this, tr(m), d); }
  EditText edit() { EditText e = new EditText(this); e.setTextColor(INK); e.setHintTextColor(SUB); return e; }

  static final String[][] PERMS = {
    {"CAMERA", "📷 الكاميرا"}, {"RECORD_AUDIO", "🎤 الميكروفون"}, {"ACCESS_FINE_LOCATION", "📍 موقعك بدقة"}, {"ACCESS_COARSE_LOCATION", "📍 موقعك التقريبي"}, {"ACCESS_BACKGROUND_LOCATION", "📍 موقعك في الخلفية"},
    {"READ_CONTACTS", "👤 قراءة جهات الاتصال"}, {"WRITE_CONTACTS", "👤 تعديل جهات الاتصال"}, {"READ_SMS", "✉ قراءة الرسائل"}, {"SEND_SMS", "✉ إرسال رسائل SMS"}, {"RECEIVE_SMS", "✉ استقبال رسائل SMS"},
    {"READ_CALL_LOG", "📞 سجل المكالمات"}, {"CALL_PHONE", "📞 إجراء مكالمات"}, {"READ_PHONE_STATE", "📞 حالة الهاتف وهويته"}, {"READ_EXTERNAL_STORAGE", "📁 قراءة الملفات"}, {"WRITE_EXTERNAL_STORAGE", "📁 الكتابة في التخزين"},
    {"MANAGE_EXTERNAL_STORAGE", "📁 الوصول لكل الملفات"}, {"READ_MEDIA_IMAGES", "🖼 الصور"}, {"READ_MEDIA_VIDEO", "🎞 الفيديو"}, {"READ_MEDIA_AUDIO", "🎵 الصوتيات"}, {"READ_CALENDAR", "📅 قراءة التقويم"},
    {"WRITE_CALENDAR", "📅 تعديل التقويم"}, {"BODY_SENSORS", "❤ حساسات الجسم"}, {"ACTIVITY_RECOGNITION", "🏃 النشاط البدني"}, {"BLUETOOTH_CONNECT", "🔵 الاتصال ببلوتوث"}, {"BLUETOOTH_SCAN", "🔵 البحث عن أجهزة بلوتوث"},
    {"NEARBY_WIFI_DEVICES", "📶 أجهزة واي فاي قريبة"}, {"SYSTEM_ALERT_WINDOW", "🪟 الرسم فوق التطبيقات"}, {"REQUEST_INSTALL_PACKAGES", "📲 تثبيت تطبيقات"}, {"QUERY_ALL_PACKAGES", "📋 معرفة كل التطبيقات المثبّتة"},
    {"BIND_ACCESSIBILITY_SERVICE", "♿ خدمة الوصول (صلاحية قوية)"}, {"POST_NOTIFICATIONS", "🔔 الإشعارات"}, {"RECEIVE_BOOT_COMPLETED", "🔁 التشغيل مع الجهاز"}, {"READ_MEDIA_VISUAL_USER_SELECTED", "🖼 الصور المختارة"}};
  static String permLabel(String p) { String s = p.substring(p.lastIndexOf('.') + 1); for (String[] x : PERMS) if (x[0].equals(s)) return x[1]; return null; }
  static boolean permRisky(String p) { String s = p.substring(p.lastIndexOf('.') + 1); return !(s.equals("POST_NOTIFICATIONS") || s.equals("RECEIVE_BOOT_COMPLETED")); }

  static final String[][] TRK = {
    {"Google Firebase Analytics", "L|com/google/firebase/analytics/"}, {"Google Analytics", "L|com/google/android/gms/analytics/"}, {"Google AdMob", "L|com/google/android/gms/ads/"}, {"Firebase Crashlytics", "L|com/google/firebase/crashlytics/"},
    {"Facebook SDK", "L|com/facebook/appevents/"}, {"Facebook Ads", "L|com/facebook/ads/"}, {"Flurry", "L|com/flurry/"}, {"Sentry", "L|io/sentry/"}, {"Mixpanel", "L|com/mixpanel/"}, {"AppsFlyer", "L|com/appsflyer/"},
    {"Adjust", "L|com/adjust/sdk/"}, {"Amplitude", "L|com/amplitude/"}, {"OneSignal", "L|com/onesignal/"}, {"Unity Ads", "L|com/unity3d/ads/"}, {"AppLovin", "L|com/applovin/"}, {"ironSource", "L|com/ironsource/"},
    {"Vungle", "L|com/vungle/"}, {"Chartboost", "L|com/chartboost/"}, {"InMobi", "L|com/inmobi/"}, {"Yandex Metrica", "L|com/yandex/metrica/"}, {"Microsoft AppCenter", "L|com/microsoft/appcenter/"},
    {"Bugsnag", "L|com/bugsnag/"}, {"Segment", "L|com/segment/analytics/"}, {"Braze", "L|com/braze/"}, {"Branch", "L|io/branch/"}, {"Kochava", "L|com/kochava/"}, {"Tapjoy", "L|com/tapjoy/"},
    {"Matomo (analytics)", "L|org/matomo/"}, {"ACRA (crash reports)", "L|org/acra/"}, {"HockeyApp", "L|net/hockeyapp/"}};

    static String[][] TRKP;  // signatures are stored with a "|" so this app does not contain the strings it scans for
  static synchronized String[][] trkp() { if (TRKP == null) { TRKP = new String[TRK.length][]; for (int i = 0; i < TRK.length; i++) TRKP[i] = new String[]{TRK[i][0], TRK[i][1].replace("|", "")}; } return TRKP; }

  List<String> scanTrackers(File apk) {
    Set<String> found = new TreeSet<>();
    try {
      java.util.zip.ZipFile z = new java.util.zip.ZipFile(apk);
      for (Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
        java.util.zip.ZipEntry e = en.nextElement(); if (!e.getName().matches("classes\\d*\\.dex")) continue;
        InputStream in = z.getInputStream(e); byte[] buf = new byte[1 << 17]; int keep = 0, n;
        while ((n = in.read(buf, keep, buf.length - keep)) > 0) {
          int len = keep + n; String sx = new String(buf, 0, len, "ISO-8859-1");
          for (String[] t : trkp()) if (!found.contains(t[0]) && sx.contains(t[1])) found.add(t[0]);
          keep = Math.min(48, len); System.arraycopy(buf, len - keep, buf, 0, keep);
        }
        in.close();
      }
      z.close();
    } catch (Exception ex) { }
    return new ArrayList<>(found);
  }

  void review(final String key) {
    final Rec r = recs.get(key);
    if (r == null || r.file == null || !new File(r.file).exists()) { mk("الملف مش موجود، نزّله تاني", Toast.LENGTH_LONG).show(); return; }
    final File f = new File(r.file); final App app = byPk.get(key);
    mk("جاري فحص الملف…", Toast.LENGTH_SHORT).show();
    new Thread(() -> {
      PackageManager pm = getPackageManager();
      final PackageInfo pi = pm.getPackageArchiveInfo(f.getPath(), PackageManager.GET_PERMISSIONS);
      if (pi == null) { ui.post(() -> mk("الملف مش APK سليم", Toast.LENGTH_LONG).show()); return; }
      final List<String> risky = new ArrayList<>(); final int[] other = {0}; final boolean[] net = {false};
      if (pi.requestedPermissions != null) for (String p : pi.requestedPermissions) {
        String sfx = p.substring(p.lastIndexOf('.') + 1); if (sfx.equals("INTERNET")) net[0] = true;
        String lb = permLabel(p); if (lb != null && permRisky(p)) risky.add(lb); else other[0]++;
      }
      final List<String> trk = scanTrackers(f);
      Inst in = installed.get(pi.packageName); final long newVc = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
      final boolean down = in != null && in.vc > newVc;
      final List<String> addp = new ArrayList<>(); if (in != null) for (String p : Dl.newPerms(this, f, pi.packageName)) addp.add(permLabel(p));
      ui.post(() -> showReview(key, r.name, f, pi, risky, other[0], net[0], trk, down, addp, app == null ? "" : afText(app.af)));
    }).start();
  }

  void showReview(final String key, String name, final File f, final PackageInfo pi, List<String> risky, int other, boolean net, List<String> trk, boolean down, List<String> addp, String af) {
    LinearLayout box = vbox(); box.setPadding(dp(18), dp(8), dp(18), dp(4)); ScrollView sv = new ScrollView(this); sv.addView(box);
    box.addView(tv("📦 " + pi.packageName + "  •  " + (pi.versionName == null ? "" : pi.versionName), 12, SUB, false));
    TextView n1 = tv(net ? "🌐 بيتصل بالإنترنت" : "🔒 مش بيطلب إذن الإنترنت", 13, net ? WARN : OK, true); n1.setPadding(0, dp(8), 0, 0); box.addView(n1);
    TextView h1 = tv("الأذونات الحساسة", 14, PRIMARY, true); h1.setPadding(0, dp(10), 0, dp(2)); box.addView(h1);
    if (risky.isEmpty()) box.addView(tv("✅ مفيش أذونات حساسة معروفة", 13, OK, false));
    for (String p : risky) box.addView(tv("• " + p, 13, INK, false));
    if (other > 0) box.addView(tv("+ " + other + " أذونات عادية أخرى", 12, SUB, false));
    if (!addp.isEmpty()) { TextView w = tv("⚠ أذونات جديدة في التحديث ده: " + TextUtils.join("، ", addp), 13, WARN, true); w.setPadding(0, dp(8), 0, 0); box.addView(w); }
    TextView h2 = tv("المتتبّعات والإعلانات", 14, PRIMARY, true); h2.setPadding(0, dp(10), 0, dp(2)); box.addView(h2);
    if (trk.isEmpty()) box.addView(tv("✅ مفيش متتبّعات معروفة (فحص تقريبي للكود)", 13, OK, false));
    else box.addView(tv("⚠ " + TextUtils.join("، ", trk), 13, WARN, false));
    if (!af.isEmpty()) { TextView a2 = tv(af, 12, WARN, false); a2.setPadding(0, dp(8), 0, 0); box.addView(a2); }
    if (down) { TextView d2 = tv("⚠ ده إصدار أقدم من المثبّت عندك. أندرويد مش بيسمح بالرجوع لإصدار أقدم من غير إلغاء تثبيت الحالي (وهتخسر بياناته).", 13, RED, true); d2.setPadding(0, dp(10), 0, 0); box.addView(d2); }
    TextView note = tv("الفحص تقريبي: بيقرأ ملف التطبيق نفسه قبل ما يتثبّت. مش بديل لمراجعة المطوّر.", 11, SUB, false); note.setPadding(0, dp(10), 0, 0); box.addView(note);
    final String pkg = pi.packageName;
    new TB(this).setTitle("مراجعة قبل التثبيت: " + name).setView(sv)
      .setPositiveButton(down ? "إلغاء تثبيت الحالي أولًا" : "تثبيت", (d, w) -> {
        if (down) { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))); return; }
        new Thread(() -> { try { Dl.install(this, f, false); } catch (Exception e) { post(key, "فشل: " + e.getMessage(), 0); } }).start();
      }).setNegativeButton("إلغاء", null).show();
  }

  // ================= v4: background downloads =================
  void enqueue(String key, String name, String sub, boolean gh, List<String> urls, String fileName, String sha, boolean wifi, boolean auto, String tag) {
    try {
      JSONObject job = new JSONObject(); job.put("key", key); job.put("name", name); job.put("gh", gh); job.put("urls", new JSONArray(urls)); job.put("ui", 0); job.put("sha", sha == null ? "" : sha);
      job.put("wifi", wifi); job.put("auto", auto); job.put("tag", tag == null ? "" : tag);
      job.put("file", new File(new File(getExternalFilesDir(null), "dl"), fileName).getPath());
      Dl.start(this, job);
      Rec r = rec(key, name, gh ? "" : sub, gh); r.state = wifi ? 5 : 1; saveRecs();
      downloading.add(key); dlMsg.put(key, wifi ? "⏰ في انتظار الواي فاي…" : "بدأ التحميل…"); dlProg.put(key, 0);
      startPoll(); if (detailFill != null) detailFill.run(); else refreshCurrent();
      if (wifi) mk("اتحط في الطابور: هيتنزّل لوحده لما تتصل بواي فاي، حتى لو قفلت المتجر.", Toast.LENGTH_LONG).show();
      else mk("التحميل شغّال في الخلفية، تقدر تقفل المتجر.", Toast.LENGTH_SHORT).show();
    } catch (Exception e) { mk("فشل بدء التحميل: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
  }

  void startDownload(final App a) { startDownload(a, sp.getBoolean("wifionly", false)); }
  void startDownload(final App a, boolean wifi) {
    if (!canInstall() || a.gh != null) return;
    String file = a.apk, sha = a.sha;
    if (topVc(a) > a.vc) { file = a.pk + "_" + topVc(a) + ".apk"; sha = ""; }
    List<String> urls = new ArrayList<>(); for (String m : BASES[a.repo]) urls.add(m + file);
    enqueue(a.pk, a.name, a.sum, false, urls, file.replace('/', '_'), sha, wifi, false, "");
  }

  void reconcileDownloads() {
    try {
      JSONObject q = new JSONObject(sp.getString("dmq", "{}")); Iterator<String> it = q.keys();
      while (it.hasNext()) { JSONObject j = q.getJSONObject(it.next()); downloading.add(j.optString("key")); }
      if (q.length() > 0) startPoll();
    } catch (Exception e) { }
  }
  void afterAutoQueue() { reconcileDownloads(); }

  void startPoll() {
    if (polling) return; polling = true;
    new Thread(() -> {
      try {
        while (!destroyed) {
          JSONObject q = new JSONObject(sp.getString("dmq", "{}")); if (q.length() == 0) break;
          DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE); Iterator<String> it = q.keys(); List<String> ids = new ArrayList<>(); while (it.hasNext()) ids.add(it.next());
          for (String sid : ids) {
            final long id = Long.parseLong(sid); JSONObject job = q.getJSONObject(sid); final String key = job.optString("key");
            Cursor cu = dm.query(new DownloadManager.Query().setFilterById(id));
            if (cu == null || !cu.moveToFirst()) { if (cu != null) cu.close(); ui.post(() -> Dl.handle(this, id)); continue; }
            int st = cu.getInt(cu.getColumnIndex(DownloadManager.COLUMN_STATUS)), reason = cu.getInt(cu.getColumnIndex(DownloadManager.COLUMN_REASON));
            long done = cu.getLong(cu.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)), tot = cu.getLong(cu.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)); cu.close();
            if (st == DownloadManager.STATUS_RUNNING) {
              post(key, "تم تحميل " + mb(done) + (tot > 0 ? " من " + mb(tot) : ""), tot > 0 ? (int) (done * 1000 / tot) : 0);
              ui.post(() -> { Rec r = recs.get(key); if (r != null && r.state == 5) { r.state = 1; saveRecs(); } });
            } else if (st == DownloadManager.STATUS_PAUSED) post(key, reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI ? "⏰ في انتظار الواي فاي…" : "⏸ في انتظار الشبكة (هيكمل لوحده)…", dlProg.containsKey(key) ? dlProg.get(key) : 0);
            else if (st == DownloadManager.STATUS_PENDING) post(key, "في الطابور…", 0);
            else if (st == DownloadManager.STATUS_SUCCESSFUL || st == DownloadManager.STATUS_FAILED) ui.post(() -> Dl.handle(this, id));
          }
          Thread.sleep(800);
        }
      } catch (Exception e) { }
      polling = false;
    }).start();
  }

  void onDlDone(JSONObject job, File f, String pkg, String err) {
    String key = job.optString("key"); downloading.remove(key);
    Rec r = recs.get(key); if (r == null) r = rec(key, job.optString("name"), "", job.optBoolean("gh"));
    if (err != null) { r.state = 4; post(key, "فشل: " + err, 0); }
    else {
      r.state = 2; r.file = f.getPath(); r.pkg = pkg; r.ts = System.currentTimeMillis();
      if (job.optBoolean("gh") && job.optString("tag").length() > 0) sp.edit().putString("ghv:" + key, job.optString("tag")).apply();
      post(key, "تم التحميل والتحقق ✅", 1000);
    }
    saveRecs(); refreshCurrent();
  }

  // ===== v4.6: share links + deep links (kanz://app?id=owner/repo, or https://.../kanz/a/<slug>/) =====
  static final String SITE = "https://ibra8y3-web.github.io/kanz/";
  static String slugOf(String id) {
    String s = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    try { byte[] h = MessageDigest.getInstance("SHA-1").digest(id.getBytes("UTF-8")); StringBuilder sb = new StringBuilder(); for (int i = 0; i < 3; i++) sb.append(String.format(Locale.US, "%02x", h[i] & 255)); String x = sb.toString(); return s.isEmpty() ? x + "0000" : s + "-" + x; } catch (Exception e) { return s; }
  }
  String shareText(App a) {
    String link = SITE + "a/" + shareSlug(a) + "/";
    return a.name + "\n" + link + "\n\n" + tr("حمّله من كنز، متجر التطبيقات المفتوحة:") + "\n" + SITE + "kanz.apk";
  }
  static String shareSlug(App a) { return slugOf(a.gh != null ? a.gh.optString("id") : a.pk); }
  void shareFavs() {
    try {
      List<App> fl = new ArrayList<>(); for (App a : sorted(all, "name")) if (favs.contains(a.pk)) fl.add(a);
      if (fl.isEmpty()) return; StringBuilder sb = new StringBuilder(); int n = 0;
      for (App a : fl) { if (n >= 60) break; if (n > 0) sb.append(','); sb.append(shareSlug(a)); n++; }
      String link = SITE + "l/#ids=" + sb + "&n=" + java.net.URLEncoder.encode(tr("قائمتي"), "UTF-8");
      Intent s = new Intent(Intent.ACTION_SEND); s.setType("text/plain"); s.putExtra(Intent.EXTRA_TEXT, tr("قائمة تطبيقات مفتوحة المصدر على كنز:") + "\n" + link + "\n\n" + tr("حمّل كنز:") + "\n" + SITE + "kanz.apk");
      startActivity(Intent.createChooser(s, tr("مشاركة القائمة")));
    } catch (Exception e) { }
  }
  void openSharedList(String ids, String name) {
    List<App> la = new ArrayList<>(); Map<String, App> m = new HashMap<>();
    for (App a : all) m.put(shareSlug(a), a);
    for (String sl : ids.split(",")) { App a = m.get(sl.trim()); if (a != null) la.add(a); }
    push(listPage(name == null || name.isEmpty() ? tr("قائمة مشتركة") : name, la, false, tr("مفيش تطبيقات من القائمة دي في الفهرس عندك. اسحب للتحديث وجرّب تاني.")));
  }
  void shareApp(App a) {
    try { Intent s = new Intent(Intent.ACTION_SEND); s.setType("text/plain"); s.putExtra(Intent.EXTRA_TEXT, shareText(a)); startActivity(Intent.createChooser(s, tr("مشاركة"))); } catch (Exception e) { }
  }
  void addShare(LinearLayout c, final App a) {
    Button b = obtn("↗ " + tr("مشاركة التطبيق")); b.setOnClickListener(v -> shareApp(a)); c.addView(b, lp(-1, 44, 0, 8, 0, 0));
    Button q = obtn("▦ " + tr("اعرض QR للتطبيق")); q.setOnClickListener(v -> showQr(a)); c.addView(q, lp(-1, 44, 0, 8, 0, 0));
    final List<App> same = sameDev(a);
    if (same.size() >= 2) { Button d = obtn("👤 " + tr("تطبيقات تانية لنفس المطوّر") + " (" + same.size() + ")"); d.setOnClickListener(v -> push(listPage(devKey(a), same, false, ""))); c.addView(d, lp(-1, 44, 0, 8, 0, 0)); }
  }
  static final java.util.Set<String> DEV_HOSTS = new HashSet<>(Arrays.asList("io.github", "com.github", "io.gitlab", "com.gitlab", "page.codeberg", "org.codeberg", "me.github", "dev.github"));
  static final java.util.Set<String> DEV_NOGROUP = new HashSet<>(Arrays.asList("com.android", "com.google", "android", "org.fdroid", "com.example"));
  /** owner for GitHub-style ids, otherwise package namespace. Same rule as dev_key() in crawler/pages.py */
  static String devKey(App a) {
    String id = a.gh != null ? a.gh.optString("id") : a.pk; id = id.toLowerCase(Locale.ROOT);
    if (id.indexOf('/') > 0) return id.substring(0, id.indexOf('/'));
    String[] p = id.split("\\."); String h2 = p.length >= 2 ? p[0] + "." + p[1] : "";
    if (p.length >= 4 && DEV_HOSTS.contains(h2)) return h2 + "." + p[2];
    if (p.length >= 3 && !DEV_NOGROUP.contains(h2) && !DEV_HOSTS.contains(h2)) return h2;
    return id;
  }
  List<App> sameDev(App a) {
    String k = devKey(a); List<App> l = new ArrayList<>();
    for (App o : all) if (devKey(o).equals(k)) l.add(o);
    Collections.sort(l, (x, y) -> x.name.compareToIgnoreCase(y.name));
    return l.size() > 300 ? new ArrayList<>(l.subList(0, 300)) : l;
  }
  void showQr(final App a) {
    final String url = SITE + "a/" + shareSlug(a) + "/";
    final ImageView iv = new ImageView(this); iv.setPadding(dp(16), dp(16), dp(16), dp(8)); iv.setBackgroundColor(0xFFFFFFFF);
    final TextView note = tv(tr("جاري تحميل الكود…"), 13, SUB, false); note.setGravity(Gravity.CENTER); note.setPadding(dp(16), dp(8), dp(16), dp(8));
    LinearLayout box = vbox(); box.addView(iv, new LinearLayout.LayoutParams(dp(260), dp(260))); box.addView(note); box.setGravity(Gravity.CENTER_HORIZONTAL);
    new TB(this).setTitle(a.name).setView(box).setPositiveButton(tr("مشاركة الرابط"), (d, w) -> shareApp(a)).setNegativeButton(tr("إغلاق"), null).show();
    new Thread(() -> {
      try {
        byte[] b = rawGet(curBase() + "a/" + shareSlug(a) + "/qr.png", 15000);
        android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length);
        if (bm == null) throw new IOException("bad");
        final android.graphics.Bitmap big = android.graphics.Bitmap.createScaledBitmap(bm, bm.getWidth() * 8, bm.getHeight() * 8, false);
        ui.post(() -> { iv.setImageBitmap(big); iv.setScaleType(ImageView.ScaleType.FIT_CENTER); note.setText(tr("امسح الكود لفتح صفحة التطبيق")); });
      } catch (Exception e) { ui.post(() -> note.setText(tr("الكود لسه مش متاح لهذا التطبيق (يظهر بعد تحديث الموقع) أو مفيش نت."))); }
    }).start();
  }
  boolean openLink(Uri u) {
    try {
      String id = null, sl = null;
      String lids = "kanz".equals(u.getScheme()) ? ("list".equals(u.getHost()) ? u.getQueryParameter("ids") : null) : null, lname = null;
      if ("kanz".equals(u.getScheme())) lname = u.getQueryParameter("n");
      if (!"kanz".equals(u.getScheme()) && u.getPathSegments().contains("l") && u.getFragment() != null) {
        for (String kv : u.getFragment().split("&")) { int q = kv.indexOf('='); if (q < 0) continue; String k2 = kv.substring(0, q), v2 = Uri.decode(kv.substring(q + 1)); if (k2.equals("ids")) lids = v2; else if (k2.equals("n")) lname = v2; }
      }
      if (lids != null) { if (lids.length() > 4000) lids = lids.substring(0, 4000); if (lname != null && lname.length() > 60) lname = lname.substring(0, 60); openSharedList(lids, lname); return true; }
      if ("kanz".equals(u.getScheme())) id = u.getQueryParameter("id");
      else { List<String> ps = u.getPathSegments(); int k = ps.indexOf("a"); if (k >= 0 && k + 1 < ps.size()) sl = ps.get(k + 1); }
      App hit = null;
      if (id != null && !id.isEmpty()) { hit = byPk.get("gh:" + id); if (hit == null) hit = byPk.get(id); }
      else if (sl != null) { for (App a : all) if (shareSlug(a).equals(sl)) { hit = a; break; } }
      if (hit == null) { mk(tr("التطبيق ده مش لاقيه في الكتالوج عندك لسه. افتح كنز واسحب للتحديث وجرّب تاني."), Toast.LENGTH_LONG).show(); return false; }
      openDetail(hit); return true;
    } catch (Exception e) { return false; }
  }
  void handleIntent(Intent i) {
    if (i == null || !loaded) return;
    Uri u = i.getData(); if (u != null && Intent.ACTION_VIEW.equals(i.getAction())) { i.setData(null); openLink(u); return; }
    String k = i.getStringExtra("review");
    if (k != null) { i.removeExtra("review"); if (recs.containsKey(k)) review(k); }
  }
  @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleIntent(i); }
  @Override protected void onPause() { super.onPause(); resumed = false; ui.removeCallbacks(tick); }

  // One-time, polite request so Android does not delay the hourly background sync. Asked once only.
  void askBattery() {
    try {
      if (sp.getBoolean("batAsked", false) || Build.VERSION.SDK_INT < 23 || !resumed) return;
      android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
      if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) { sp.edit().putBoolean("batAsked", true).apply(); return; }
      sp.edit().putBoolean("batAsked", true).apply();
      new TB(this).setTitle("خلّي كنز يحدّث لوحده").setMessage("عشان الكتالوج والأيقونات والتحديثات تيجي لوحدها في الخلفية من غير ما تفتح التطبيق، اسمح لكنز يشتغل في الخلفية. هتظهر لك نافذة من أندرويد، دوس \"سماح\". ده بيتطلب مرة واحدة بس.")
        .setPositiveButton("تمام", (d, w) -> { try { Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); i.setData(Uri.parse("package:" + getPackageName())); startActivity(i); } catch (Exception e) { } })
        .setNegativeButton("مش دلوقتي", null).show();
    } catch (Throwable e) { }
  }

  void afterLoad() {
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED && !sp.getBoolean("notifAsked", false)) {
      sp.edit().putBoolean("notifAsked", true).apply(); try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7); } catch (Exception e) { }
    }
    AutoUpd.schedule(this, sp.getBoolean("autoupd", true)); CatSync.schedule(this);
    ui.postDelayed(this::askBattery, 2500);
    reconcileDownloads(); handleIntent(getIntent()); liveCheckMaybe();
  }

  // ================= v4: live update check (F-Droid / IzzyOnDroid version API) =================
  String[] lvOf(String pk) { String v = sp.getString("lv:" + pk, ""); if (v.isEmpty()) return null; String[] p = v.split(";", 2); return p.length == 2 ? p : null; }
  long topVc(App a) { String[] l = a.gh == null ? lvOf(a.pk) : null; if (l != null) { try { return Math.max(a.vc, Long.parseLong(l[0])); } catch (Exception e) { } } return a.vc; }
  String topVn(App a) { String[] l = a.gh == null ? lvOf(a.pk) : null; if (l != null) { try { if (Long.parseLong(l[0]) > a.vc && !l[1].isEmpty()) return l[1]; } catch (Exception e) { } } return a.vn; }

  void liveCheckMaybe() { if (metered() || System.currentTimeMillis() - sp.getLong("lvchk", 0) < 6L * 3600000L) return; liveCheck(false); }
  void liveCheck(final boolean manual) {
    if (liveBusy) return; liveBusy = true; if (manual) mk("جاري فحص التحديثات…", Toast.LENGTH_SHORT).show();
    new Thread(() -> {
      int found = 0, n = 0;
      List<App> cand = new ArrayList<>(); for (App a : all) if (a.gh == null && a.repo <= 1 && !a.nat && installed.containsKey(a.pk)) cand.add(a);
      for (App a : cand) {
        if (n++ >= 120) break;
        try {
          String api = (a.repo == 0 ? "https://f-droid.org/api/v1/packages/" : "https://apt.izzysoft.de/fdroid/api/v1/packages/") + a.pk;
          JSONObject o = new JSONObject(new String(rawGet(api, 10000), "UTF-8")); long sv = o.optLong("suggestedVersionCode");
          if (sv > a.vc) {
            String vn = ""; JSONArray ps = o.optJSONArray("packages"); if (ps != null) for (int i = 0; i < ps.length(); i++) if (ps.getJSONObject(i).optLong("versionCode") == sv) vn = ps.getJSONObject(i).optString("versionName");
            sp.edit().putString("lv:" + a.pk, sv + ";" + vn).apply(); Inst in = installed.get(a.pk); if (in != null && in.vc < sv) found++;
          } else sp.edit().remove("lv:" + a.pk).apply();
        } catch (Exception e) { }
      }
      sp.edit().putLong("lvchk", System.currentTimeMillis()).apply(); final int fd = found;
      ui.post(() -> { liveBusy = false; if (manual) mk(fd > 0 ? fd + " تحديث جديد متاح" : "كل تطبيقاتك محدّثة ✅", Toast.LENGTH_LONG).show(); refreshCurrent(); show(); });
    }).start();
  }

  byte[] rawGet(String url, int ms) throws IOException {
    HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection(); cn.setConnectTimeout(ms); cn.setReadTimeout(ms); cn.setRequestProperty("User-Agent", "Mozilla/5.0 Kanz-Store");
    int code = cn.getResponseCode(); if (code != 200) throw new IOException("HTTP " + code);
    InputStream in = cn.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); in.close(); return bo.toByteArray();
  }

  View updatesView() {
    final List<App> ups = new ArrayList<>(); for (App a : all) if (hasUpdate(a)) ups.add(a);
    LinearLayout pg = page(); pg.addView(topBar("التحديثات", ups.size() + " تحديث متاح", false));
    Button chk = obtn("🔍 فحص التحديثات الآن"); chk.setOnClickListener(x -> liveCheck(true)); pg.addView(chk, lp(-1, 44, 14, 10, 14, 0));
    TextView au = tv(sp.getBoolean("autoupd", true) ? "🔔 التحديث التلقائي على الواي فاي: مفعّل (من القائمة ⋮ تقدر توقفه)" : "التحديث التلقائي متوقف (فعّله من القائمة ⋮)", 11, SUB, false); au.setPadding(dp(18), dp(6), dp(18), 0); pg.addView(au);
    if (ups.isEmpty()) { TextView e = tv("✅ كل تطبيقاتك المثبّتة من المتجر محدّثة", 16, SUB, false); e.setGravity(Gravity.CENTER); e.setPadding(dp(24), dp(60), dp(24), 0); pg.addView(e); return pg; }
    long tot = 0; for (App a : ups) tot += a.size; final long tt = tot;
    Button b = pbtn("تحديث الكل (" + ups.size() + " تطبيق • " + mb(tot) + ")", PRIMARY);
    b.setOnClickListener(x -> new TB(this).setMessage("هيتحمّل حوالي " + mb(tt) + " من النت في الخلفية. تكمل؟").setPositiveButton("تحديث الآن", (d, w) -> updateAll(ups, false)).setNeutralButton("على الواي فاي بس", (d, w) -> updateAll(ups, true)).setNegativeButton("إلغاء", null).show());
    pg.addView(b, lp(-1, 50, 14, 10, 14, 4));
    ListView lv = new ListView(this); lv.setDivider(null); lv.setAdapter(appAdapter(ups)); lv.setOnItemClickListener((p, v, i, id) -> openDetail(ups.get(i)));
    pg.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1)); return pg;
  }

  void updateAll(final List<App> ups, boolean wifi) {
    if (!canInstall()) return;
    for (App a : new ArrayList<>(ups)) if (a.gh == null && !downloading.contains(a.pk)) startDownload(a, wifi);
    mk("بدأ التحديث. هتوصلك إشعارات لما يجهزوا، وبعدها راجع وثبّت.", Toast.LENGTH_LONG).show();
  }

  // ================= v4: description translation =================
  void descCard(LinearLayout c, final String key, final String text, boolean withCard) {
    LinearLayout d = withCard ? card() : vbox(); if (withCard) d.addView(tv("الوصف", 15, PRIMARY, true));
    final TextView dt = tv(text, 14, INK, false); dt.setPadding(0, dp(withCard ? 4 : 12), 0, 0); d.addView(dt);
    final String tl = lang == null ? "ar" : lang; final String ck = "trn:" + key + ":" + tl; final String orig = text;
    final TextView bt = tv("🌐 ترجمة الوصف", 13, PRIMARY, true); bt.setPadding(0, dp(8), 0, dp(2)); d.addView(bt);
    final boolean[] shown = {false};
    bt.setOnClickListener(v -> {
      if (shown[0]) { dt.setText(orig); bt.setText(tr("🌐 ترجمة الوصف")); shown[0] = false; return; }
      String cached = sp.getString(ck, null);
      if (cached != null) { dt.setText(cached); bt.setText(tr("↩ النص الأصلي")); shown[0] = true; return; }
      bt.setText(tr("جاري الترجمة…"));
      new Thread(() -> {
        try { final String out = translateText(orig, tl); sp.edit().putString(ck, out).apply(); ui.post(() -> { dt.setText(out); bt.setText(tr("↩ النص الأصلي")); shown[0] = true; }); }
        catch (final Exception e) { ui.post(() -> { bt.setText(tr("🌐 ترجمة الوصف")); mk("تعذّرت الترجمة: خدمة الترجمة المجانية مش متاحة دلوقتي. جرّب بعدين.", Toast.LENGTH_LONG).show(); }); }
      }).start();
    });
    if (withCard) c.addView(d, lp(-1, -2, 0, 12, 0, 0)); else c.addView(d);
  }

  String translateText(String text, String tl) throws Exception {
    if (text.length() > 1800) text = text.substring(0, 1800);
    try { // 1) Google's free web endpoint (unofficial, no key; may refuse)
      String u = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&tl=" + tl + "&q=" + URLEncoder.encode(text, "UTF-8");
      String r = new String(rawGet(u, 8000), "UTF-8");
      if (r.startsWith("[")) { JSONArray ar = new JSONArray(r).getJSONArray(0); StringBuilder sb = new StringBuilder(); for (int i = 0; i < ar.length(); i++) sb.append(ar.getJSONArray(i).optString(0)); if (sb.length() > 0) return sb.toString(); }
    } catch (Exception e) { }
    // 2) MyMemory free API (no key; limited daily quota), in small chunks
    StringBuilder out = new StringBuilder(); String rest = text.trim();
    while (!rest.isEmpty()) {
      int cut = Math.min(450, rest.length());
      if (cut < rest.length()) { int p = Math.max(rest.lastIndexOf(". ", cut), Math.max(rest.lastIndexOf("\n", cut), rest.lastIndexOf(" ", cut))); if (p > 100) cut = p + 1; }
      String ch = rest.substring(0, cut); rest = rest.substring(cut).trim();
      JSONObject o = new JSONObject(new String(rawGet("https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(ch, "UTF-8") + "&langpair=Autodetect%7C" + tl, 12000), "UTF-8"));
      String t = o.getJSONObject("responseData").optString("translatedText", "");
      if (o.optInt("responseStatus") != 200 || t.isEmpty() || t.startsWith("MYMEMORY WARNING")) throw new IOException("quota");
      out.append(Html.fromHtml(t).toString()).append(' ');
    }
    return out.toString().trim();
  }

  // ================= v4: screenshots =================
  void shotsBlock(final LinearLayout c, final App a) {
    final Button b = obtn("🖼 لقطات الشاشة"); final LinearLayout holder = vbox();
    b.setOnClickListener(v -> {
      if (metered()) new TB(this).setMessage("اللقطات بتتنزّل من النت (حوالي 1-3 MB) وأنت على بيانات الموبايل. تكمل؟").setPositiveButton("تحميل", (d, w) -> loadShots(a, holder, b)).setNegativeButton("لا", null).show();
      else loadShots(a, holder, b);
    });
    c.addView(b, lp(-1, 46, 0, 8, 0, 0)); c.addView(holder);
  }
  void loadShots(final App a, final LinearLayout holder, final Button b) {
    b.setEnabled(false); b.setText(tr("جاري التحميل…"));
    new Thread(() -> {
      try {
        final List<String> urls = new ArrayList<>();
        for (int i = 1; i <= 2; i++) { String mu = curBase() + "shots/" + shareSlug(a) + "/" + i + ".webp"; try { rawGet(mu, 8000); urls.add(mu); } catch (Exception e) { break; } }
        if (urls.isEmpty()) {
          String html = new String(rawGet("https://f-droid.org/en/packages/" + a.pk + "/", 15000), "UTF-8");
          java.util.regex.Matcher m = java.util.regex.Pattern.compile("https://f-droid\\.org/repo/[^\"'\\s]+/phoneScreenshots/[^\"'\\s]+\\.(?:png|jpg|jpeg|webp)").matcher(html);
          while (m.find() && urls.size() < 8) if (!urls.contains(m.group())) urls.add(m.group());
        }
        ui.post(() -> {
          b.setVisibility(View.GONE);
          if (urls.isEmpty()) { holder.addView(tv("مفيش لقطات شاشة متاحة للتطبيق ده.", 12, SUB, false)); return; }
          HorizontalScrollView hs = new HorizontalScrollView(this); LinearLayout row = new LinearLayout(this); hs.addView(row);
          for (final String u : urls) { ImageView iv = new ImageView(this); iv.setBackground(bg(LINE, 10)); iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setOnClickListener(x -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception e) { } }); row.addView(iv, lp(130, 230, 4, 4, 4, 4)); loadImg(u, iv); }
          holder.addView(hs);
        });
      } catch (Exception e) { ui.post(() -> { b.setEnabled(true); b.setText(tr("🖼 لقطات الشاشة")); mk("مفيش لقطات متاحة أو مفيش اتصال.", Toast.LENGTH_LONG).show(); }); }
    }).start();
  }
  void loadImg(final String url, final ImageView iv) {
    final File cf = new File(getCacheDir(), "sc_" + Integer.toHexString(url.hashCode())); iv.setTag(url);
    net.execute(() -> {
      try {
        if (!cf.exists()) { byte[] bb = rawGet(url, 20000); FileOutputStream o = new FileOutputStream(cf); o.write(bb); o.close(); }
        BitmapFactory.Options op = new BitmapFactory.Options(); op.inSampleSize = url.contains("/shots/") ? 1 : 3; final Bitmap bm = BitmapFactory.decodeFile(cf.getPath(), op);
        if (bm != null) ui.post(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); });
      } catch (Exception e) { }
    });
  }

  // ================= v4: older versions (F-Droid / IzzyOnDroid) =================
  void versionsPage(final App a) {
    LinearLayout pg = page(); pg.addView(topBar("الإصدارات السابقة", a.name, true));
    ScrollView sv = new ScrollView(this); final LinearLayout box = vbox(); box.setPadding(dp(14), dp(10), dp(14), dp(24)); sv.addView(box); pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    box.addView(tv("جاري تحميل قائمة الإصدارات…", 13, SUB, false)); push(pg);
    new Thread(() -> {
      try {
        String api = (a.repo == 0 ? "https://f-droid.org/api/v1/packages/" : "https://apt.izzysoft.de/fdroid/api/v1/packages/") + a.pk;
        JSONArray ps = new JSONObject(new String(rawGet(api, 15000), "UTF-8")).getJSONArray("packages"); final List<long[]> vc = new ArrayList<>(); final Map<Long, String> vn = new HashMap<>();
        for (int i = 0; i < ps.length(); i++) { long v = ps.getJSONObject(i).optLong("versionCode"); if (!vn.containsKey(v)) { vn.put(v, ps.getJSONObject(i).optString("versionName")); vc.add(new long[]{v}); } }
        Collections.sort(vc, (x, y) -> Long.compare(y[0], x[0]));
        ui.post(() -> {
          box.removeAllViews();
          TextView n = tv("المصدر بيحتفظ بآخر إصدارات قليلة بس. ملفات الإصدارات القديمة هنا بدون بصمة SHA-256 مسبقة، لكن أندرويد بيتأكد إن التوقيع هو توقيع المطوّر نفسه قبل التثبيت.", 12, SUB, false); box.addView(n);
          Inst in = installed.get(a.pk);
          for (long[] x : vc) {
            final long v = x[0]; final String name = vn.get(v); LinearLayout cd = card();
            cd.addView(tv(name + "   (" + v + ")" + (v == a.vc ? "  •  الأحدث في الفهرس" : ""), 14, INK, true));
            if (in != null && in.vc == v) cd.addView(tv("✅ ده المثبّت عندك دلوقتي", 12, OK, true));
            else if (in != null && in.vc > v) cd.addView(tv("⚠ أقدم من المثبّت: لازم تلغي تثبيت الحالي الأول", 12, WARN, false));
            Button b = pbtn("تحميل هذا الإصدار", v == a.vc ? PRIMARY : SUB);
            b.setOnClickListener(y -> new TB(this).setTitle("تحميل " + name).setMessage("الحجم غير معروف مسبقًا (غالبًا بين 2 و40 MB). هيتحمّل في الخلفية وبعدها تراجع الأذونات وتثبّت.").setPositiveButton("تحميل", (d, w) -> {
              if (!canInstall()) return; List<String> urls = new ArrayList<>(); urls.add(BASES[a.repo][0] + a.pk + "_" + v + ".apk"); if (a.repo == 0) urls.add("https://f-droid.org/archive/" + a.pk + "_" + v + ".apk");
              enqueue(a.pk, a.name, a.sum, false, urls, a.pk + "_" + v + ".apk", "", sp.getBoolean("wifionly", false), false, "");
            }).setNegativeButton("إلغاء", null).show());
            cd.addView(b, lp(-1, 44, 0, 8, 0, 0)); box.addView(cd, lp(-1, -2, 0, 8, 0, 0));
          }
          if (vc.isEmpty()) box.addView(tv("مفيش إصدارات متاحة.", 14, SUB, false));
        });
      } catch (Exception e) { ui.post(() -> { box.removeAllViews(); box.addView(tv("⚠ مقدرناش نجيب الإصدارات (" + e.getMessage() + ")", 13, RED, false)); }); }
    }).start();
  }

  // ================= v4: compare, curated lists =================
  interface Pick { void on(App a); }

  void pickApp(String title, final Pick cb) {
    LinearLayout box = vbox(); box.setPadding(dp(14), dp(4), dp(14), 0); final EditText e = edit(); e.setHint(tr("اكتب اسم التطبيق…")); e.setSingleLine(true); box.addView(e);
    final List<App> res = new ArrayList<>(); final ListView lv = new ListView(this);
    final BaseAdapter ad = new BaseAdapter() {
      public int getCount() { return res.size(); } public Object getItem(int i) { return res.get(i); } public long getItemId(int i) { return i; }
      public View getView(int i, View v, ViewGroup p) { TextView t = tv(res.get(i).name + "  •  " + res.get(i).vn, 14, INK, false); t.setPadding(dp(8), dp(10), dp(8), dp(10)); return t; }
    };
    lv.setAdapter(ad); box.addView(lv, new LinearLayout.LayoutParams(-1, dp(280)));
    final AlertDialog dlg = new TB(this).setTitle(title).setView(box).setNegativeButton("إلغاء", null).create();
    e.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) { } public void onTextChanged(CharSequence s, int a, int b, int c) { }
      public void afterTextChanged(Editable ed) {
        String q = ed.toString().trim().toLowerCase(Locale.ROOT); res.clear();
        if (q.length() >= 2) { for (App x : all) if (x.lname.startsWith(q)) res.add(x); for (App x : all) if (res.size() < 40 && !x.lname.startsWith(q) && x.lname.contains(q)) res.add(x); }
        while (res.size() > 40) res.remove(res.size() - 1); ad.notifyDataSetChanged();
      }});
    lv.setOnItemClickListener((p, v, i, id) -> { dlg.dismiss(); cb.on(res.get(i)); });
    dlg.show();
  }

  void compareFlow() { pickApp("اختار التطبيق الأول", a -> pickApp("اختار التطبيق التاني", b -> push(comparePage(a, b)))); }

  View comparePage(final App a, final App b) {
    LinearLayout pg = page(); pg.addView(topBar("مقارنة", a.name + "  ↔  " + b.name, true));
    ScrollView sv = new ScrollView(this); LinearLayout box = vbox(); box.setPadding(dp(10), dp(10), dp(10), dp(24)); sv.addView(box); pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    LinearLayout hd = new LinearLayout(this); hd.setGravity(Gravity.CENTER_VERTICAL); hd.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 0.8f));
    for (final App x : new App[]{a, b}) { LinearLayout col = vbox(); col.setGravity(Gravity.CENTER); ImageView iv = new ImageView(this); setIcon(iv, x); col.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44))); TextView n = tv(x.name, 12, INK, true); n.setGravity(Gravity.CENTER); n.setMaxLines(2); col.addView(n); col.setOnClickListener(y -> openDetail(x)); hd.addView(col, new LinearLayout.LayoutParams(0, -2, 1)); }
    box.addView(hd);
    LinearLayout t = card();
    long sa = a.size, sb = b.size;
    addCmp(t, "الحجم", a.size > 0 ? mb(a.size) : "؟", b.size > 0 ? mb(b.size) : "؟", sa > 0 && sb > 0 ? (sa < sb ? 0 : sa > sb ? 1 : -1) : -1);
    addCmp(t, "الإصدار", a.vn.isEmpty() ? "؟" : a.vn, b.vn.isEmpty() ? "؟" : b.vn, -1);
    addCmp(t, "الترخيص", a.lic.isEmpty() ? "؟" : a.lic, b.lic.isEmpty() ? "؟" : b.lic, -1);
    addCmp(t, "آخر تحديث", a.upd > 0 ? df.format(new Date(a.upd * 1000)) : "؟", b.upd > 0 ? df.format(new Date(b.upd * 1000)) : "؟", a.upd > b.upd ? 0 : a.upd < b.upd ? 1 : -1);
    addCmp(t, "المصدر", a.gh != null ? "مصدر إضافي" : REPO_NAMES[a.repo], b.gh != null ? "مصدر إضافي" : REPO_NAMES[b.repo], -1);
    int fa = a.af.isEmpty() ? 0 : a.af.split(",").length, fb = b.af.isEmpty() ? 0 : b.af.split(",").length;
    addCmp(t, "تنبيهات", fa == 0 ? "✅ لا يوجد" : "⚠ " + fa, fb == 0 ? "✅ لا يوجد" : "⚠ " + fb, fa < fb ? 0 : fa > fb ? 1 : -1);
    addCmp(t, "يتطلب أندرويد", a.minSdk > 0 ? "API " + a.minSdk : "؟", b.minSdk > 0 ? "API " + b.minSdk : "؟", -1);
    addCmp(t, "مناسب لجهازك", a.ok ? "✅" : "❌", b.ok ? "✅" : "❌", a.ok && !b.ok ? 0 : !a.ok && b.ok ? 1 : -1);
    addCmp(t, "حالته عندك", isInstalled(a) ? "✅ مثبّت" : "—", isInstalled(b) ? "✅ مثبّت" : "—", -1);
    if (a.gh != null || b.gh != null) addCmp(t, "عدد الإصدارات", a.gh != null ? vers(a.gh).length() + "" : "—", b.gh != null ? vers(b.gh).length() + "" : "—", -1);
    box.addView(t, lp(-1, -2, 0, 8, 0, 0));
    TextView note = tv("الأفضل في كل صف متعلّم بـ ⭐ (الأصغر حجمًا، الأحدث تحديثًا، الأقل تنبيهات).", 11, SUB, false); note.setPadding(dp(6), dp(8), dp(6), 0); box.addView(note);
    return pg;
  }
  void addCmp(LinearLayout t, String label, String va, String vb, int best) {
    LinearLayout row = new LinearLayout(this); row.setPadding(0, dp(7), 0, dp(7)); row.setGravity(Gravity.CENTER_VERTICAL);
    TextView l = tv(label, 12, SUB, true); row.addView(l, new LinearLayout.LayoutParams(0, -2, 0.8f));
    TextView x = tv((best == 0 ? "⭐ " : "") + va, 13, best == 0 ? OK : INK, best == 0); x.setGravity(Gravity.CENTER); row.addView(x, new LinearLayout.LayoutParams(0, -2, 1));
    TextView y = tv((best == 1 ? "⭐ " : "") + vb, 13, best == 1 ? OK : INK, best == 1); y.setGravity(Gravity.CENTER); row.addView(y, new LinearLayout.LayoutParams(0, -2, 1));
    t.addView(row);
  }

  List<String[]> lists = new ArrayList<>(); String listSub;
  void loadLists() {
    lists.clear();
    try { BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("lists.tsv"), "UTF-8")); String l; while ((l = r.readLine()) != null) { String[] p = l.split("\t", -1); if (p.length >= 3) lists.add(p); } r.close(); } catch (Exception e) { }
  }
  List<App> listApps(String[] L) { List<App> o = new ArrayList<>(); for (String pk : L[2].split(",")) { App a = byPk.get(pk.trim()); if (a != null) o.add(a); } return o; }
  void openList(String[] L) { List<App> la = listApps(L); listSub = L[1]; View v = listPage(L[0], la, false, "مفيش تطبيقات من القائمة دي في الفهرس."); listSub = null; push(v); }
  View listsView() {
    LinearLayout pg = page(); pg.addView(topBar("قوائم مختارة", "بدائل مفتوحة المصدر وتشكيلات مفيدة", true));
    ScrollView sv = new ScrollView(this); LinearLayout box = vbox(); box.setPadding(dp(10), dp(8), dp(10), dp(24)); sv.addView(box); pg.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    for (final String[] L : lists) {
      List<App> la = listApps(L); if (la.isEmpty()) continue; LinearLayout cd = card();
      cd.addView(tv(L[0], 15, INK, true)); cd.addView(tv(L[1], 12, SUB, false));
      StringBuilder sb = new StringBuilder(); for (int i = 0; i < Math.min(4, la.size()); i++) sb.append(i > 0 ? " • " : "").append(la.get(i).name); if (la.size() > 4) sb.append(" …");
      TextView nm = tv(sb.toString(), 12, PRIMARY, false); nm.setPadding(0, dp(4), 0, 0); cd.addView(nm); cd.addView(tv(la.size() + " تطبيق", 11, SUB, false));
      cd.setOnClickListener(x -> openList(L)); box.addView(cd, lp(-1, -2, 0, 8, 0, 0));
    }
    return pg;
  }
  void altBlock(LinearLayout c, App a) {
    for (final String[] L : lists) { boolean has = false; for (String pk : L[2].split(",")) if (pk.trim().equals(a.pk)) has = true; if (!has || listApps(L).size() < 2) continue;
      Button b = obtn("📚 " + L[0]); b.setOnClickListener(x -> openList(L)); c.addView(b, lp(-1, 44, 0, 6, 0, 0)); }
  }

  void extraButtons(LinearLayout c, final App a, boolean busy, boolean inst, boolean upd) {
    addShare(c, a);
    if (!busy && (!inst || upd) && a.ok) { Button w = obtn("⏰ نزّله لما أكون على واي فاي"); w.setOnClickListener(v -> startDownload(a, true)); c.addView(w, lp(-1, 44, 0, 8, 0, 0)); }
    if (a.repo <= 1) { Button v = obtn("📜 الإصدارات السابقة (تثبيت نسخة قديمة)"); v.setOnClickListener(x -> versionsPage(a)); c.addView(v, lp(-1, 44, 0, 8, 0, 0)); }
    if (a.repo == 0) shotsBlock(c, a);
    Button cm = obtn("⚖ قارن مع تطبيق آخر"); cm.setOnClickListener(x -> pickApp("اختار تطبيق للمقارنة", o -> push(comparePage(a, o)))); c.addView(cm, lp(-1, 44, 0, 8, 0, 0));
    altBlock(c, a);
  }

  // ================= v4: backup / restore of the apps list =================
  String pendingExport;
  void exportList() {
    try {
      String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
      JSONObject o = new JSONObject(); o.put("app", "kanz"); o.put("v", 1); o.put("date", date); JSONArray ar = new JSONArray();
      for (App a : all) if (a.gh == null && installed.containsKey(a.pk)) { JSONObject x = new JSONObject(); x.put("pk", a.pk); x.put("n", a.name); x.put("vn", installed.get(a.pk).vn); ar.put(x); }
      for (Rec r : recs.values()) if (r.gh && r.state == 3) { JSONObject x = new JSONObject(); x.put("pk", r.key); x.put("n", r.name); ar.put(x); }
      o.put("apps", ar); o.put("favs", new JSONArray(new ArrayList<>(favs))); pendingExport = o.toString(1);
      Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE, "kanz-apps-" + date + ".json");
      startActivityForResult(i, 41);
    } catch (Exception e) { mk("فشل: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
  }
  void importList() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, 42); }
  @Override protected void onActivityResult(int rq, int rs, Intent d) {
    super.onActivityResult(rq, rs, d); if (rs != RESULT_OK || d == null || d.getData() == null) return;
    try {
      if (rq == 41) { OutputStream o = getContentResolver().openOutputStream(d.getData()); o.write(pendingExport.getBytes("UTF-8")); o.close(); mk("اتحفظت النسخة الاحتياطية ✅", Toast.LENGTH_LONG).show(); }
      else if (rq == 42) { InputStream in = getContentResolver().openInputStream(d.getData()); ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n, tot = 0; while ((n = in.read(b)) > 0 && tot < 2000000) { bo.write(b, 0, n); tot += n; } in.close(); restoreFrom(new JSONObject(bo.toString("UTF-8"))); }
    } catch (Exception e) { mk("فشل: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
  }
  void restoreFrom(JSONObject o) {
    if (!"kanz".equals(o.optString("app"))) { mk("الملف ده مش نسخة احتياطية من كنز", Toast.LENGTH_LONG).show(); return; }
    JSONArray fv = o.optJSONArray("favs"); if (fv != null) { for (int i = 0; i < fv.length(); i++) favs.add(fv.optString(i)); sp.edit().putStringSet("favs", new HashSet<>(favs)).apply(); }
    JSONArray ar = o.optJSONArray("apps"); final List<App> todo = new ArrayList<>(); int other = 0, have = 0;
    for (int i = 0; ar != null && i < ar.length(); i++) {
      String pk = ar.optJSONObject(i).optString("pk"); App a = byPk.get(pk);
      if (pk.startsWith("gh:") || a == null || !a.ok) { other++; continue; } if (installed.containsKey(pk)) { have++; continue; } todo.add(a);
    }
    String note = "\n\nموجود عندك بالفعل: " + have + (other > 0 ? "\nمش هيتسترجع تلقائيًا (من مصدر إضافي أو مش في الفهرس أو مش متوافق): " + other : "");
    if (todo.isEmpty()) { new TB(this).setTitle("استرجاع القائمة").setMessage("مفيش تطبيقات ناقصة للاسترجاع." + note).setPositiveButton("تمام", null).show(); return; }
    final CharSequence[] labels = new CharSequence[todo.size()]; final boolean[] chk = new boolean[todo.size()]; long tot = 0;
    for (int i = 0; i < todo.size(); i++) { labels[i] = todo.get(i).name + "  (" + mb(todo.get(i).size) + ")"; chk[i] = true; tot += todo.get(i).size; }
    new TB(this).setTitle("استرجاع " + todo.size() + " تطبيق (حوالي " + mb(tot) + ")").setMultiChoiceItems(labels, chk, (d, w, c) -> chk[w] = c)
      .setPositiveButton("نزّل المحدد على الواي فاي", (d, w) -> { if (!canInstall()) return; for (int i = 0; i < todo.size(); i++) if (chk[i]) startDownload(todo.get(i), true); })
      .setNegativeButton("إلغاء", null).show();
  }

  // ================= v4: settings =================
  int compatCount() { int n = 0; for (App a : all) if (a.ok) n++; return n; }
  String langName() { for (String[] l : LANGS) if (l[0].equals(lang)) return l[1]; return lang; }

  void devDialog() {
    final String[] d = {"📤 قدّم تطبيقك (فورم خارجي)", "📋 حالة طلبات التقديم (صفحة خارجية)", "ℹ إزاي بيشتغل التقديم؟"};
    new TB(this).setTitle("🛠 للمطورين").setItems(d, (dd, k) -> {
      if (k == 2) { new TB(this).setTitle("إزاي بيشتغل التقديم؟").setMessage("قدّم رابط تطبيقك (مستودع عام مفتوح المصدر أو صفحته على F-Droid) من الفورم الخارجي. فحص آلي بيراجع الترخيص والبصمة والصلاحيات والمتتبعين. اللي يعدي بيدخل المتجر لوحده؛ تطبيقات المطورين الجدد واللي عليها ملاحظة بتستنى مراجعة صاحب المتجر.").setPositiveButton("تمام", null).show(); return; }
      String u = k == 0 ? "https://github.com/ibra8y3-web/kanz/issues/new?template=submit-app.yml" : "https://github.com/ibra8y3-web/kanz/issues?q=is%3Aissue+label%3Asubmit-app";
      try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception e) { }
    }).show();
  }

  void showMenu() {
    final String[] icm = {"الأيقونات: واي فاي فقط", "الأيقونات: دائمًا", "الأيقونات: إيقاف (أوفر نت)"};
    final String[] thm = {"المظهر: تلقائي (زي الجهاز)", "المظهر: فاتح", "المظهر: داكن"};
    final String[] items = {
      "🌐 اللغة: " + langName(), thm[sp.getInt("theme", 0)].replace("المظهر", "🌙 المظهر"),
      sp.getBoolean("wifionly", false) ? "⬇ التنزيل: واي فاي فقط" : "⬇ التنزيل: أي شبكة",
      sp.getBoolean("autoupd", true) ? "🔔 التحديث التلقائي على الواي فاي: مفعّل" : "🔔 التحديث التلقائي على الواي فاي: متوقف",
      "📚 قوائم مختارة وبدائل", "⚖ مقارنة تطبيقين", "💾 نسخة احتياطية لقائمة تطبيقاتي", "♻ استرجاع قائمة تطبيقات",
      icm[sp.getInt("iconmode", 1)], "مسح كاش الأيقونات واللقطات", "حذف الملفات المحمّلة (توفير مساحة)", "تحديث الكتالوج الآن", "🛠 للمطورين", "🌐 موقع كنز", "عن كنز"};
    new TB(this).setItems(items, (d, w) -> {
      switch (w) {
        case 0: { CharSequence[] ln = new CharSequence[LANGS.length]; for (int i = 0; i < ln.length; i++) ln[i] = LANGS[i][1];
          new TB(this).setTitle("اللغة").setItems(ln, (d2, k) -> { sp.edit().putString("lang", LANGS[k][0]).apply(); loadLang(this); stack.clear(); recreate(); }).show(); break; }
        case 1: sp.edit().putInt("theme", (sp.getInt("theme", 0) + 1) % 3).apply(); stack.clear(); recreate(); break;
        case 2: sp.edit().putBoolean("wifionly", !sp.getBoolean("wifionly", false)).apply(); mk(sp.getBoolean("wifionly", false) ? "التنزيل على الواي فاي فقط" : "التنزيل على أي شبكة", Toast.LENGTH_SHORT).show(); break;
        case 3: { boolean on = !sp.getBoolean("autoupd", true); sp.edit().putBoolean("autoupd", on).apply(); AutoUpd.schedule(this, on); mk(on ? "التحديث التلقائي اتفعّل" : "التحديث التلقائي اتوقف", Toast.LENGTH_SHORT).show(); break; }
        case 4: push(listsView()); break;
        case 5: compareFlow(); break;
        case 6: exportList(); break;
        case 7: importList(); break;
        case 8: sp.edit().putInt("iconmode", (sp.getInt("iconmode", 1) + 1) % 3).apply(); mk(icm[sp.getInt("iconmode", 1)], Toast.LENGTH_SHORT).show(); show(); break;
        case 9: { File[] fs = getCacheDir().listFiles(); if (fs != null) for (File f : fs) f.delete(); cache.evictAll(); show(); break; }
        case 10: { File[] fs = new File(getExternalFilesDir(null), "dl").listFiles(); if (fs != null) for (File f : fs) f.delete(); for (Rec r : new ArrayList<>(recs.values())) if (r.state == 2 || r.state == 4) recs.remove(r.key); saveRecs(); show(); break; }
        case 11: syncRemote(true); break;
        case 12: devDialog(); break;
        case 13: try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(sp.getString("site", "https://ibra8y3-web.github.io/kanz/")))); } catch (Exception e) { } break;
        default: new TB(this).setTitle("كنز 4.19").setMessage("متجر لتطبيقات أندرويد المفتوحة المصدر والمجانية فقط.\n\n• الفهرس: " + all.size() + " تطبيق (متوافق مع جهازك: " + compatCount() + "). الرقم الإجمالي نفسه على كل الأجهزة لنفس نسخة الكتالوج؛ المتوافق بيختلف حسب أندرويد والمعالج.\n• نسخة الكتالوج: " + (rcRev > 0 ? "r" + rcRev + " • " + rcGen : "المدمج فقط") + "\n• بنتأكد من البصمة (SHA-256) لما تكون متاحة، وبنعرض الأذونات والمتتبّعات قبل التثبيت.\n• التحميل في الخلفية وبيكمل من حيث وقف.\n• مصادر إضافية غير مُراجَعة يدويًا.").setPositiveButton("تمام", null).show();
      }
    }).show();
  }

}
