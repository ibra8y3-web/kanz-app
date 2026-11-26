package store.almasdar;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Background downloads (system DownloadManager: resumable, Wi-Fi-only queue, survives app close) + install helpers. */
public class Dl extends BroadcastReceiver {
  static MainActivity live;
  static final Handler H = new Handler(Looper.getMainLooper());

  @Override public void onReceive(Context c, Intent i) {
    if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(i.getAction())) return;
    long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
    if (id >= 0) handle(c.getApplicationContext(), id);
  }

  static synchronized long start(Context c, JSONObject job) throws Exception {
    SharedPreferences sp = c.getSharedPreferences("s", 0);
    DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
    File f = new File(job.getString("file")); f.getParentFile().mkdirs(); if (f.exists()) f.delete();
    JSONArray urls = job.getJSONArray("urls"); int ui = job.optInt("ui");
    DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(urls.getString(ui)));
    rq.setDestinationUri(Uri.fromFile(f)); rq.setTitle(job.optString("name"));
    rq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
    if (job.optBoolean("wifi")) rq.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
    rq.addRequestHeader("User-Agent", "Kanz-Store");
    long id = dm.enqueue(rq);
    JSONObject q = new JSONObject(sp.getString("dmq", "{}")); q.put(String.valueOf(id), job);
    sp.edit().putString("dmq", q.toString()).commit();
    return id;
  }

  static boolean queued(Context c, String key) {
    try { JSONObject q = new JSONObject(c.getSharedPreferences("s", 0).getString("dmq", "{}")); Iterator<String> it = q.keys(); while (it.hasNext()) { if (key.equals(q.getJSONObject(it.next()).optString("key"))) return true; } } catch (Exception e) { }
    return false;
  }

  static void handle(final Context c, final long id) {
    final SharedPreferences sp = c.getSharedPreferences("s", 0); final JSONObject job;
    try {
      JSONObject q = new JSONObject(sp.getString("dmq", "{}")); job = q.optJSONObject(String.valueOf(id)); if (job == null) return;
      q.remove(String.valueOf(id)); sp.edit().putString("dmq", q.toString()).commit();
    } catch (Exception e) { return; }
    new Thread(() -> {
      String err = null; File f = null; String pkg = null;
      try {
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor cu = dm.query(new DownloadManager.Query().setFilterById(id)); int st = -1, reason = 0;
        if (cu != null) { if (cu.moveToFirst()) { st = cu.getInt(cu.getColumnIndex(DownloadManager.COLUMN_STATUS)); reason = cu.getInt(cu.getColumnIndex(DownloadManager.COLUMN_REASON)); } cu.close(); }
        if (st == DownloadManager.STATUS_SUCCESSFUL) {
          f = new File(job.getString("file")); String want = job.optString("sha");
          if (!want.isEmpty() && !sha(f).equalsIgnoreCase(want)) { f.delete(); throw new IOException("البصمة غير مطابقة، تم حذف الملف"); }
          PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
          if (pi == null) { f.delete(); throw new IOException("الملف مش APK سليم"); }
          pkg = pi.packageName; f = f;
        } else if (st == -1) { err = "اتلغى التحميل"; }
        else {
          int ui = job.optInt("ui") + 1; JSONArray urls = job.getJSONArray("urls");
          if (ui < urls.length()) { job.put("ui", ui); start(c, job); return; }
          err = "فشل التحميل (" + reason + ")";
        }
      } catch (Exception e) { err = String.valueOf(e.getMessage()); f = null; }
      final File ff = f; final String fp = pkg, fe = err;
      H.post(() -> done(c, job, ff, fp, fe));
    }).start();
  }

  static String sha(File f) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256"); FileInputStream in = new FileInputStream(f); byte[] b = new byte[65536]; int n;
    while ((n = in.read(b)) > 0) md.update(b, 0, n); in.close();
    StringBuilder sb = new StringBuilder(); for (byte x : md.digest()) sb.append(String.format("%02x", x)); return sb.toString();
  }

  static void done(Context c, JSONObject job, File f, String pkg, String err) {
    MainActivity m = live; String key = job.optString("key"), name = job.optString("name");
    if (m != null) m.onDlDone(job, f, pkg, err); else headlessRec(c, job, f, pkg, err);
    MainActivity.ensureLang(c);
    if (err != null) { if (m == null || !m.resumed) notifyUser(c, key.hashCode(), MainActivity.tr("فشل تنزيل ") + name, MainActivity.tr(err), null); return; }
    if (job.optBoolean("auto")) {
      List<String> np = newPerms(c, f, pkg);
      if (np.isEmpty()) {
        try { install(c, f, true); notifyUser(c, key.hashCode(), MainActivity.tr("جاري تحديث ") + name, MainActivity.tr("تحديث تلقائي على الواي فاي"), null); return; } catch (Exception e) { }
      } else { notifyUser(c, key.hashCode(), name + " " + MainActivity.tr("جاهز للتحديث"), MainActivity.tr("فيه أذونات جديدة، اضغط للمراجعة قبل التثبيت"), reviewIntent(c, key)); return; }
    }
    if (m != null && m.resumed) m.review(key);
    else notifyUser(c, key.hashCode(), name + " " + MainActivity.tr("جاهز للتثبيت"), MainActivity.tr("اضغط للمراجعة والتثبيت"), reviewIntent(c, key));
  }

  static PendingIntent reviewIntent(Context c, String key) {
    Intent i = new Intent(c, MainActivity.class).putExtra("review", key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    return PendingIntent.getActivity(c, key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static void headlessRec(Context c, JSONObject job, File f, String pkg, String err) {
    try {
      SharedPreferences sp = c.getSharedPreferences("s", 0); String key = job.optString("key");
      JSONArray ar = new JSONArray(sp.getString("recs", "[]")); JSONObject o = null;
      for (int i = 0; i < ar.length(); i++) if (key.equals(ar.getJSONObject(i).optString("key"))) o = ar.getJSONObject(i);
      if (o == null) { o = new JSONObject(); o.put("key", key); o.put("name", job.optString("name")); o.put("sub", ""); o.put("gh", job.optBoolean("gh")); ar.put(o); }
      o.put("ts", System.currentTimeMillis());
      if (err != null) o.put("state", 4); else { o.put("state", 2); o.put("file", f.getPath()); o.put("pkg", pkg); if (job.optString("tag").length() > 0) sp.edit().putString("ghv:" + key, job.optString("tag")).apply(); }
      sp.edit().putString("recs", ar.toString()).apply();
    } catch (Exception e) { }
  }

  static List<String> newPerms(Context c, File f, String pkg) {
    List<String> out = new ArrayList<>();
    try {
      PackageManager pm = c.getPackageManager();
      PackageInfo np = pm.getPackageArchiveInfo(f.getPath(), PackageManager.GET_PERMISSIONS); if (np == null || np.requestedPermissions == null) return out;
      Set<String> old = new HashSet<>();
      try { PackageInfo op = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS); if (op.requestedPermissions != null) old.addAll(Arrays.asList(op.requestedPermissions)); } catch (Exception e) { return out; }
      for (String p : np.requestedPermissions) if (!old.contains(p) && MainActivity.permLabel(p) != null && MainActivity.permRisky(p)) out.add(p);
    } catch (Exception e) { }
    return out;
  }

  static void install(Context c, File f, boolean silent) throws Exception {
    PackageInstaller pi = c.getPackageManager().getPackageInstaller();
    PackageInstaller.SessionParams sp2 = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    if (Build.VERSION.SDK_INT >= 31 && silent) sp2.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
    int id = pi.createSession(sp2); PackageInstaller.Session s = pi.openSession(id);
    OutputStream o = s.openWrite("app", 0, f.length()); FileInputStream in = new FileInputStream(f); byte[] b = new byte[65536]; int n;
    while ((n = in.read(b)) > 0) o.write(b, 0, n);
    s.fsync(o); o.close(); in.close();
    Intent i = new Intent(c, InstallReceiver.class);
    PendingIntent pend = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
    s.commit(pend.getIntentSender());
  }

  static void notifyUser(Context c, int id, String title, String text, PendingIntent pi) {
    try {
      NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("kanz", "Kanz", NotificationManager.IMPORTANCE_DEFAULT));
      Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "kanz") : new Notification.Builder(c);
      b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(title).setContentText(text).setAutoCancel(true);
      if (pi != null) b.setContentIntent(pi);
      nm.notify(id, b.build());
    } catch (Exception e) { }
  }
}
