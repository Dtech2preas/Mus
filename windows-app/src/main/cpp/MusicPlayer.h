#ifndef MUSICPLAYER_H
#define MUSICPLAYER_H

#include <QObject>
#include <QMediaPlayer>
#include <QAudioOutput>

class MusicPlayer : public QObject {
    Q_OBJECT

public:
    explicit MusicPlayer(QObject *parent = nullptr);
    ~MusicPlayer();

    void playUrl(const QString &url);
    void pause();
    void resume();
    void stop();
    void setVolume(int volume); // 0-100
    bool isPlaying() const;

signals:
    void stateChanged(QMediaPlayer::PlaybackState state);
    void positionChanged(qint64 position);
    void durationChanged(qint64 duration);

private slots:
    void handleStateChanged(QMediaPlayer::PlaybackState state);

private:
    QMediaPlayer *player;
    QAudioOutput *audioOutput;
};

#endif // MUSICPLAYER_H
