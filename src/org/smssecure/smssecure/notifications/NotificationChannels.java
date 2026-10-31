package org.smssecure.smssecure.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.util.SilencePreferences;

import java.util.Arrays;

public class NotificationChannels {

  private static final String TAG = NotificationChannels.class.getSimpleName();

  public static String MESSAGES      = "messages";
  public static String FAILURES      = "failures";
  public static String LOCKED_STATUS = "locked_status";
  public static String OTHER         = "other";

  private static final String MESSAGES_PREFIX = "messages_v";

  /**
   * Ensures all of the notification channels are created. No harm in repeat calls. Call is safely
   * ignored for API < 26.
   */
  public static void create(@NonNull Context context) {
    if (Build.VERSION.SDK_INT < 26) {
      return;
    }

    try {
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

      ensureMessagesChannel(context, notificationManager);

      NotificationChannel failures     = new NotificationChannel(FAILURES, context.getString(R.string.NotificationChannel_failures), NotificationManager.IMPORTANCE_HIGH);
      NotificationChannel lockedStatus = new NotificationChannel(LOCKED_STATUS, context.getString(R.string.NotificationChannel_locked_status), NotificationManager.IMPORTANCE_LOW);
      NotificationChannel other        = new NotificationChannel(OTHER, context.getString(R.string.NotificationChannel_other), NotificationManager.IMPORTANCE_LOW);

      notificationManager.createNotificationChannels(Arrays.asList(failures, lockedStatus, other));
    } catch (Exception e) {
      Log.w(TAG, "Unable to create notification channels.", e);
    }
  }

  /** The channel id message notifications post to; changes when in-app notification settings change because channel settings are immutable after creation. */
  public static String getMessagesChannelId(@NonNull Context context) {
    try {
      return MESSAGES_PREFIX + Math.max(1, SilencePreferences.getNotificationChannelVersion(context));
    } catch (Exception e) {
      Log.w(TAG, "Unable to read notification channel version, using default.");
      return MESSAGES_PREFIX + "1";
    }
  }

  /** Creates the next versioned messages channel from the current in-app settings and deletes the previous channel. API 26+ only. */
  public static void updateMessagesChannel(@NonNull Context context) {
    if (Build.VERSION.SDK_INT < 26) {
      return;
    }

    try {
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      int                 nextVersion         = Math.max(1, SilencePreferences.getNotificationChannelVersion(context)) + 1;

      createMessagesChannel(context, notificationManager, MESSAGES_PREFIX + nextVersion);
      notificationManager.deleteNotificationChannel(getMessagesChannelId(context));

      SilencePreferences.setNotificationChannelVersion(context, nextVersion);
    } catch (Exception e) {
      Log.w(TAG, "Unable to update messages notification channel.", e);
    }
  }

  private static void ensureMessagesChannel(@NonNull Context context, @NonNull NotificationManager notificationManager) {
    int version = SilencePreferences.getNotificationChannelVersion(context);

    if (version < 1) {
      createMessagesChannel(context, notificationManager, MESSAGES_PREFIX + "1");
      notificationManager.deleteNotificationChannel(MESSAGES);
      SilencePreferences.setNotificationChannelVersion(context, 1);
    } else {
      createMessagesChannel(context, notificationManager, getMessagesChannelId(context));
    }
  }

  private static void createMessagesChannel(@NonNull Context context,
                                            @NonNull NotificationManager notificationManager,
                                            @NonNull String channelId)
  {
    NotificationChannel channel = new NotificationChannel(channelId,
                                                          context.getString(R.string.NotificationChannel_messages),
                                                          NotificationManager.IMPORTANCE_HIGH);

    try {
      Uri sound = getCurrentSound(context);

      if (sound != null) {
        channel.setSound(sound, new AudioAttributes.Builder()
                                              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                              .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                              .build());
      } else {
        channel.setSound(null, null);
      }
    } catch (Exception e) {
      Log.w(TAG, "Unable to apply notification sound, leaving channel default.", e);
    }

    notificationManager.createNotificationChannel(channel);
  }

  private static @Nullable Uri getCurrentSound(@NonNull Context context) {
    String stored = SilencePreferences.getNotificationRingtone(context);

    if (stored == null || stored.isEmpty()) {
      return null;
    }

    return Uri.parse(stored);
  }
}
