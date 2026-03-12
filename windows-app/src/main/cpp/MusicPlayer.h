#ifndef MUSICPLAYER_H
#define MUSICPLAYER_H

#include <QObject>
#include <QMediaPlayer>
#include <QAudioOutput>
#include <QList>
#include "InnerTubeClient.h"
#include "YtDlpManager.h"

class MusicPlayer : public QObject {
    Q_OBJECT

public:
    explicit MusicPlayer(QObject *parent = nullptr);
    ~MusicPlayer();

    void playUrl(const QString &url);
    void playSong(const VideoItem& song);
    void pause();
    void resume();
    void stop();
    void next();
    void previous();
    void seek(qint64 position);
    void setVolume(int volume); // 0-100
    bool isPlaying() const;

    void setQueue(const QList<VideoItem>& queue, int startIndex = 0);
    VideoItem currentSong() const;
    qint64 position() const;
    qint64 duration() const;

signals:
    void stateChanged(QMediaPlayer::PlaybackState state);
    void positionChanged(qint64 position);
    void durationChanged(qint64 duration);
    void currentSongChanged(const VideoItem& song);

private slots:
    void handleStateChanged(QMediaPlayer::PlaybackState state);
    void handleStreamUrlFetched(const QString& videoId, const QString& url);
    void handleStreamUrlFailed(const QString& videoId, const QString& error);

private:
    QMediaPlayer *player;
    QAudioOutput *audioOutput;
    YtDlpManager *ytDlp;

    QList<VideoItem> currentQueue;
    int currentIndex;
};

#endif // MUSICPLAYER_H
