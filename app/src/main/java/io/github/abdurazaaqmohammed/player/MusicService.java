package io.github.abdurazaaqmohammed.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;


import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

public class MusicService extends Service implements PlayerManager.PlaybackCallback {

    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIF_ID = 1001;
    public static final String ACTION_PLAY_PAUSE = "io.github.abdurazaaqmohammed.player.PLAY_PAUSE";
    public static final String ACTION_NEXT = "io.github.abdurazaaqmohammed.player.NEXT";
    public static final String ACTION_PREV = "io.github.abdurazaaqmohammed.player.PREV";
    public static final String ACTION_STOP = "io.github.abdurazaaqmohammed.player.STOP";
    public static final String ACTION_CLOSE = "io.github.abdurazaaqmohammed.player.CLOSE";
    public static final String ACTION_REFRESH = "io.github.abdurazaaqmohammed.player.REFRESH";

    private NotificationManager notifManager;
    private MediaSessionCompat mediaSession;
    private PlayerManager playerManager;
    private boolean isForeground;
    private final Object sessionLock = new Object();
    private final java.util.concurrent.ThreadPoolExecutor artworkWorker = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private java.util.concurrent.Future<?> artworkLoad;
    private String artworkKey;
    private Bitmap cachedArtwork, metadataArtwork;
    private MediaItem metadataItem;
    private int metadataDuration = -1;
    private long lastProgressAt;
    private int artworkRequest;
    private boolean destroyed;

    @Override
    public void onCreate() {
        super.onCreate();
        playerManager = PlayerManager.getInstance(this);
        playerManager.registerCallback(this);
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "MusicService");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { playerManager.play(); }
            @Override public void onPause() { playerManager.pause(); }



            @Override public void onSkipToNext() { playerManager.next(); }
            @Override public void onSkipToPrevious() { playerManager.previous(); }
            @Override public void onSeekTo(long pos) { playerManager.seekTo((int) pos); }
        });
        mediaSession.setActive(true);

        if (Build.VERSION.SDK_INT > 32) {
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_PLAY_PAUSE), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_NEXT), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_PREV), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_STOP), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_CLOSE), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_REFRESH), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_PLAY_PAUSE));
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_NEXT));
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_PREV));
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_STOP));
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_CLOSE));
            registerReceiver(notificationReceiver, new IntentFilter(ACTION_REFRESH));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE: playerManager.playPause(); break;
                case ACTION_NEXT: playerManager.next(); break;
                case ACTION_PREV: playerManager.previous(); break;
                case ACTION_STOP: playerManager.stop(); break;
                case ACTION_CLOSE: stopSelf(); break;
                default: updateNotification(); break;
            }
        } else {
            updateNotification();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        destroyed = true;
        artworkRequest++;
        artworkWorker.shutdownNow();
        cachedArtwork = null;
        metadataArtwork = null;
        metadataItem = null;
        playerManager.unregisterCallback(this);
        try { unregisterReceiver(notificationReceiver); } catch (Exception ignored) {}
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        stopForeground(true);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_music), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notif_music_desc));
            channel.setShowBadge(false);
            notifManager.createNotificationChannel(channel);
        }
    }

    @SuppressWarnings("deprecation")
    private void updateNotification() {
        if (destroyed) return;
//        if (playerManager.isUIVisible()) {
//            if (isForeground) {
//                stopForeground(true);
//                isForeground = false;
//            }
//            return;
//        }

        MediaItem item = playerManager.getCurrentItem();
        if (item == null) { if (isForeground) { stopForeground(true); isForeground = false; } stopSelf(); return; }

        boolean playing = playerManager.getState() == PlayerManager.PlayState.PLAYING;
        int position = playerManager.getCurrentPosition();
        int duration = playerManager.getDuration();

        PendingIntent openIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent playPauseIntent = PendingIntent.getBroadcast(this, 1,
                new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getBroadcast(this, 2,
                new Intent(ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent prevIntent = PendingIntent.getBroadcast(this, 3,
                new Intent(ACTION_PREV), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent closeIntent = PendingIntent.getBroadcast(this, 4,
                new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        requestArtwork(item);
        Bitmap artwork = cachedArtwork;

        long stateActions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_STOP;

        PlaybackStateCompat.Builder psb = new PlaybackStateCompat.Builder();
        psb.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                position, playing ? 1f : 0f);
        psb.setActions(stateActions);
        synchronized (sessionLock) {
            mediaSession.setPlaybackState(psb.build());
        }

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(closeIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(playing ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
                .setContentTitle(item.title)
                .setContentText(item.artist != null ? item.artist : getString(R.string.music_unknown_artist))
                .setSubText(item.album)
                .setLargeIcon(artwork)
                .setContentIntent(openIntent)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(mediaStyle)
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_previous, getString(R.string.music_previous), prevIntent))
                .addAction(new NotificationCompat.Action(
                        playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? getString(R.string.music_pause) : getString(R.string.music_play), playPauseIntent))
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_next, getString(R.string.music_next), nextIntent));

        Notification notification = builder.build();

        updateSessionMetadata(item, duration, artwork);

        try {
        if (!isForeground) {
            startForeground(NOTIF_ID, notification);
            isForeground = true;
        } else {
            notifManager.notify(NOTIF_ID, notification);
        }
        } catch (RuntimeException error) {
            android.util.Log.w("MusicService", "Cannot publish playback notification", error);
            stopSelf();
        }
    }

    private void requestArtwork(MediaItem item) {
        String key = item.uri == null ? item.path : item.uri.toString();
        if (java.util.Objects.equals(artworkKey, key)) return;
        artworkKey = key;
        cachedArtwork = null;
        int request = ++artworkRequest;
        if (artworkLoad != null) artworkLoad.cancel(true);
        artworkWorker.purge();
        artworkLoad = artworkWorker.submit(() -> {
            Bitmap artwork = loadArtwork(item);
            main.post(() -> {
                if (destroyed || request != artworkRequest) {
                    return;
                }
                cachedArtwork = artwork;
                updateNotification();
            });
        });
    }

    private void updateSessionMetadata(MediaItem item, int duration, Bitmap artwork) {
        if (destroyed || item == metadataItem && duration == metadataDuration && artwork == metadataArtwork) return;
        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, item.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        if (artwork != null) metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
        synchronized (sessionLock) { mediaSession.setMetadata(metadata.build()); }
        metadataItem = item;
        metadataDuration = duration;
        metadataArtwork = artwork;
    }

    private Bitmap loadArtwork(MediaItem item) {
        return MediaArtwork.read(getApplicationContext(), item);
    }

    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (ACTION_PLAY_PAUSE.equals(action)) playerManager.playPause();
            else if (ACTION_NEXT.equals(action)) playerManager.next();
            else if (ACTION_PREV.equals(action)) playerManager.previous();
            else if (ACTION_STOP.equals(action) || ACTION_CLOSE.equals(action)) { playerManager.stop(); stopSelf(); }
            else if (ACTION_REFRESH.equals(action)) updateNotification();
        }
    };

    @Override
    public void onStateChanged(PlayerManager.PlayState newState) {
        if (newState == PlayerManager.PlayState.PLAYING || newState == PlayerManager.PlayState.PAUSED) {
            updateNotification();
        } else if (newState == PlayerManager.PlayState.ENDED && playerManager.getQueue().isEmpty()) {
            stopSelf();
        }
    }

    @Override
    public void onProgress(int position, int duration) {
        if (destroyed) return;
        if (isForeground) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastProgressAt < 1000) return;
            lastProgressAt = now;
            PlaybackStateCompat.Builder psb = new PlaybackStateCompat.Builder();
            boolean playing = playerManager.getState() == PlayerManager.PlayState.PLAYING;
            psb.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                    position, playing ? 1f : 0f);
            psb.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_STOP);
            synchronized (sessionLock) {
                mediaSession.setPlaybackState(psb.build());
            }
            MediaItem item = playerManager.getCurrentItem();
            if (item != null && duration > 0) {
                updateSessionMetadata(item, duration, cachedArtwork);
            }
        }
    }

    @Override public void onMediaItemChanged(MediaItem item) { updateNotification(); }
    @Override public void onError(String message) {}
    @Override public void onBufferUpdate(int percent) {}
}
