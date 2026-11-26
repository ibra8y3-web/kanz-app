package store.almasdar;

import android.content.*;

/** Re-arms the background jobs after a reboot or after the app is updated. */
public class BootRcv extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    try { CatSync.schedule(c); AutoUpd.schedule(c, c.getSharedPreferences("s", 0).getBoolean("autoupd", true)); } catch (Throwable e) { }
  }
}
