#include "MusicPlayer.h"
#include <QDebug>

MusicPlayer::MusicPlayer(QObject *parent) : QObject(parent), currentIndex(-1) {
    player = new QMediaPlayer(this);
    audioOutput = new QAudioOutput(this);
    player->setAudioOutput(audioOutput);
    audioOutput->setVolume(1.0); // 100%

    ytDlp = new YtDlpManager(this);

    connect(player, &QMediaPlayer::playbackStateChanged, this, &MusicPlayer::handleStateChanged);
    connect(player, &QMediaPlayer::positionChanged, this, &MusicPlayer::positionChanged);
    connect(player, &QMediaPlayer::durationChanged, this, &MusicPlayer::durationChanged);

    connect(ytDlp, &YtDlpManager::streamUrlFetched, this, &MusicPlayer::handleStreamUrlFetched);
    connect(ytDlp, &YtDlpManager::streamUrlFailed, this, &MusicPlayer::handleStreamUrlFailed);
}

MusicPlayer::~MusicPlayer() {
    // Parent cleans up player, audioOutput, and ytDlp
}

void MusicPlayer::playUrl(const QString &url) {
    player->setSource(QUrl(url));
    player->play();
}

void MusicPlayer::playSong(const VideoItem& song) {
    currentQueue.clear();
    currentQueue.append(song);
    currentIndex = 0;
    emit currentSongChanged(song);

    // Check if we have it downloaded locally first
    if (ytDlp->isSongDownloaded(song.id)) {
        playUrl(QUrl::fromLocalFile(ytDlp->getDownloadedSongPath(song.id)).toString());
    } else {
        ytDlp->fetchStreamUrl(song.id);
    }
}

void MusicPlayer::setQueue(const QList<VideoItem>& queue, int startIndex) {
    currentQueue = queue;
    if (startIndex >= 0 && startIndex < currentQueue.size()) {
        currentIndex = startIndex;
        emit currentSongChanged(currentQueue[currentIndex]);

        VideoItem song = currentQueue[currentIndex];
        if (ytDlp->isSongDownloaded(song.id)) {
            playUrl(QUrl::fromLocalFile(ytDlp->getDownloadedSongPath(song.id)).toString());
        } else {
            ytDlp->fetchStreamUrl(song.id);
        }
    }
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

void MusicPlayer::next() {
    if (currentQueue.isEmpty()) return;

    currentIndex++;
    if (currentIndex >= currentQueue.size()) {
        currentIndex = 0; // Loop queue
    }
    emit currentSongChanged(currentQueue[currentIndex]);

    VideoItem song = currentQueue[currentIndex];
    if (ytDlp->isSongDownloaded(song.id)) {
        playUrl(QUrl::fromLocalFile(ytDlp->getDownloadedSongPath(song.id)).toString());
    } else {
        ytDlp->fetchStreamUrl(song.id);
    }
}

void MusicPlayer::previous() {
    if (currentQueue.isEmpty()) return;

    if (player->position() > 3000) {
        // If more than 3 seconds in, restart current song
        player->setPosition(0);
        return;
    }

    currentIndex--;
    if (currentIndex < 0) {
        currentIndex = currentQueue.size() - 1; // Loop to end
    }
    emit currentSongChanged(currentQueue[currentIndex]);

    VideoItem song = currentQueue[currentIndex];
    if (ytDlp->isSongDownloaded(song.id)) {
        playUrl(QUrl::fromLocalFile(ytDlp->getDownloadedSongPath(song.id)).toString());
    } else {
        ytDlp->fetchStreamUrl(song.id);
    }
}

void MusicPlayer::seek(qint64 position) {
    player->setPosition(position);
}

void MusicPlayer::setVolume(int volume) {
    // audioOutput expects volume from 0.0 to 1.0 linearly
    audioOutput->setVolume(volume / 100.0f);
}

bool MusicPlayer::isPlaying() const {
    return player->playbackState() == QMediaPlayer::PlayingState;
}

VideoItem MusicPlayer::currentSong() const {
    if (currentIndex >= 0 && currentIndex < currentQueue.size()) {
        return currentQueue[currentIndex];
    }
    return VideoItem();
}

qint64 MusicPlayer::position() const {
    return player->position();
}

qint64 MusicPlayer::duration() const {
    return player->duration();
}

void MusicPlayer::handleStateChanged(QMediaPlayer::PlaybackState state) {
    emit stateChanged(state);

    // Auto-advance to next song if finished naturally
    if (state == QMediaPlayer::StoppedState && player->mediaStatus() == QMediaPlayer::EndOfMedia) {
        if (currentQueue.size() == 1) {
            // Stop looping if only 1 item in queue
            stop();
        } else {
            next();
        }
    }
}

void MusicPlayer::handleStreamUrlFetched(const QString& videoId, const QString& url) {
    if (!currentQueue.isEmpty() && currentQueue[currentIndex].id == videoId) {
        playUrl(url);
    }
}

void MusicPlayer::handleStreamUrlFailed(const QString& videoId, const QString& error) {
    qCritical() << "Failed to fetch stream URL for" << videoId << ":" << error;
    // If the currently playing song failed, maybe skip to next?
    // Skipping might cause infinite loops if everything fails, so just stop for now
}
