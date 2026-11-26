package store.almasdar;

import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Periodic Wi-Fi-only check: downloads updates for installed store apps, then notifies / installs. */
public class AutoUpd extends JobService {
  static final int JOB = 77;

  static void schedule(Context c, boolean on) {
    try {
      JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      if (!on) { js.cancel(JOB); return; }
      for (JobInfo p : js.getAllPendingJobs()) if (p.getId() == JOB) return;
      js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, AutoUpd.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPeriodic(6L * 3600 * 1000).setPersisted(true).build());
    } catch (Exception e) { }
  }

  @Override public boolean onStartJob(final JobParameters p) {
    new Thread(() -> { try { run(getApplicationContext()); } catch (Throwable e) { } jobFinished(p, false); }).start();
    return true;
  }
  @Override public boolean onStopJob(JobParameters p) { return true; }

  static String get(String url, int ms) throws IOException {
    HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection(); cn.setConnectTimeout(ms); cn.setReadTimeout(ms); cn.setRequestProperty("User-Agent", "Kanz-Store");
    if (cn.getResponseCode() != 200) throw new IOException("HTTP " + cn.getResponseCode());
    InputStream in = cn.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); in.close(); return bo.toString("UTF-8");
  }

  static void run(final Context c) throws Exception {
    final SharedPreferences sp = c.getSharedPreferences("s", 0);
    if (!sp.getBoolean("autoupd", true)) return;
    PackageManager pm = c.getPackageManager(); Map<String, long[]> inst = new HashMap<>(); Map<String, String> instVn = new HashMap<>();
    for (PackageInfo p : pm.getInstalledPackages(0)) { inst.put(p.packageName, new long[]{Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode}); instVn.put(p.packageName, p.versionName == null ? "" : p.versionName); }
    int started = 0;
    // 1) F-Droid style catalog apps
    BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("catalog.tsv"), "UTF-8"), 1 << 16); String l;
    while ((l = r.readLine()) != null && started < 6) {
      int t = l.indexOf('\t'); if (t <= 0) continue; String pk = l.substring(0, t); if (!inst.containsKey(pk)) continue;
      String[] f = l.split("\t", -1); if (f.length < 21) continue;
      String name = f[1].isEmpty() ? pk : f[1], apk = f[5], sha = f[6], vn = f[8]; long vc = Long.parseLong(f[9]); int repo = Integer.parseInt(f[19]);
      boolean nat = !f[17].isEmpty() || !f[18].isEmpty();
      String url = null;
      if (repo <= 1 && !nat) {
        try {
          String api = (repo == 0 ? "https://f-droid.org/api/v1/packages/" : "https://apt.izzysoft.de/fdroid/api/v1/packages/") + pk;
          JSONObject o = new JSONObject(get(api, 10000)); long sv = o.optLong("suggestedVersionCode");
          if (sv > vc) { vc = sv; sha = ""; apk = pk + "_" + sv + ".apk"; vn = "";
            JSONArray ps = o.optJSONArray("packages"); if (ps != null) for (int i = 0; i < ps.length(); i++) if (ps.getJSONObject(i).optLong("versionCode") == sv) vn = ps.getJSONObject(i).optString("versionName");
            sp.edit().putString("lv:" + pk, vc + ";" + vn).apply(); }
        } catch (Exception e) { }
      }
      if (inst.get(pk)[0] >= vc || (!vn.isEmpty() && vn.equals(instVn.get(pk)))) continue;
      if (Dl.queued(c, pk)) continue;
      String[] bases = MainActivity.BASES[Math.min(repo, MainActivity.BASES.length - 1)];
      JSONArray urls = new JSONArray(); for (String b : bases) urls.put(b + apk);
      JSONObject job = new JSONObject(); job.put("key", pk); job.put("name", name); job.put("gh", false); job.put("urls", urls); job.put("ui", 0); job.put("sha", sha); job.put("wifi", true); job.put("auto", true); job.put("tag", "");
      job.put("file", new File(new File(c.getExternalFilesDir(null), "dl"), apk).getPath());
      Dl.start(c, job); started++;
    }
    r.close();
    // 2) additional-source apps installed through the store
    try {
      JSONArray ar = new JSONArray(sp.getString("recs", "[]")); String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0].toLowerCase(Locale.ROOT) : "";
      for (int i = 0; i < ar.length() && started < 6; i++) {
        JSONObject o = ar.getJSONObject(i); String key = o.optString("key"); if (!key.startsWith("gh:") || o.optInt("state") != 3) continue;
        String have = sp.getString("ghv:" + key, ""); if (have.isEmpty() || Dl.queued(c, key)) continue;
        try {
          JSONObject rel = new JSONObject(get("https://api.github.com/repos/" + key.substring(3) + "/releases/latest", 12000));
          String tag = rel.optString("tag_name"); if (tag.isEmpty() || tag.equals(have) || rel.optBoolean("prerelease") || rel.optBoolean("draft")) continue;
          JSONArray as = rel.optJSONArray("assets"); if (as == null) continue; JSONObject pick = null; int apks = 0;
          for (int k = 0; k < as.length(); k++) { JSONObject a = as.getJSONObject(k); String n = a.optString("name").toLowerCase(Locale.ROOT); if (!n.endsWith(".apk")) continue; apks++; if (pick == null || (n.contains(abi) && !pick.optString("name").toLowerCase(Locale.ROOT).contains(abi))) pick = a; }
          if (pick == null || (apks > 1 && !pick.optString("name").toLowerCase(Locale.ROOT).contains(abi))) continue;
          String dg = pick.isNull("digest") ? "" : pick.optString("digest", ""); String sha = dg.startsWith("sha256:") ? dg.substring(7) : "";
          JSONObject job = new JSONObject(); job.put("key", key); job.put("name", o.optString("name")); job.put("gh", true); job.put("urls", new JSONArray().put(pick.optString("browser_download_url"))); job.put("ui", 0); job.put("sha", sha); job.put("wifi", true); job.put("auto", true); job.put("tag", tag);
          job.put("file", new File(new File(c.getExternalFilesDir(null), "dl"), "gh_" + (key + "_" + pick.optString("name")).replaceAll("[^A-Za-z0-9._-]", "_")).getPath());
          Dl.start(c, job); started++;
        } catch (Exception e) { }
      }
    } catch (Exception e) { }
    if (started > 0) { final int n = started; Dl.H.post(() -> { if (Dl.live != null) Dl.live.afterAutoQueue(); }); }
  }
}
