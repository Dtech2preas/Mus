#include "MusicPlayer.h"
#include <QDebug>

MusicPlayer::MusicPlayer(QObject *parent) : QObject(parent) {
    player = new QMediaPlayer(this);
    audioOutput = new QAudioOutput(this);
    player->setAudioOutput(audioOutput);
    audioOutput->setVolume(1.0); // 100%

    connect(player, &QMediaPlayer::playbackStateChanged, this, &MusicPlayer::handleStateChanged);
    connect(player, &QMediaPlayer::positionChanged, this, &MusicPlayer::positionChanged);
    connect(player, &QMediaPlayer::durationChanged, this, &MusicPlayer::durationChanged);
}

MusicPlayer::~MusicPlayer() {
    // Parent cleans up player and audioOutput
}

void MusicPlayer::playUrl(const QString &url) {
    player->setSource(QUrl(url));
    player->play();
}

void MusicPlayer::pause() {
    player->pause();
}

void MusicPlayer::resume() {
    player->play();
}

void MusicPlayer::stop() {
    player->stop();
}

void MusicPlayer::setVolume(int volume) {
    // audioOutput expects volume from 0.0 to 1.0 linearly
    audioOutput->setVolume(volume / 100.0f);
}

bool MusicPlayer::isPlaying() const {
    return player->playbackState() == QMediaPlayer::PlayingState;
}

void MusicPlayer::handleStateChanged(QMediaPlayer::PlaybackState state) {
    emit stateChanged(state);
    if (state == QMediaPlayer::StoppedState) {
        qDebug() << "MusicPlayer: Playback stopped.";
    }
}
