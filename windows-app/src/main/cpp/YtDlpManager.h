#ifndef YTDLPMANAGER_H
#define YTDLPMANAGER_H

#include <QObject>
#include <QProcess>
#include <QString>
#include <QNetworkAccessManager>

class YtDlpManager : public QObject {
    Q_OBJECT

public:
    explicit YtDlpManager(QObject *parent = nullptr);
    ~YtDlpManager();

    void fetchStreamUrl(const QString& videoId);
    void downloadSong(const QString& videoId, const QString& title);
    void updateYtDlp();

    QString getExecutablePath() const;
    QString getDownloadsDir() const;
    bool isSongDownloaded(const QString& videoId) const;
    QString getDownloadedSongPath(const QString& videoId) const;

signals:
    void streamUrlFetched(const QString& videoId, const QString& url);
    void streamUrlFailed(const QString& videoId, const QString& error);

    void downloadProgress(const QString& videoId, int percentage);
    void downloadFinished(const QString& videoId, const QString& filePath);
    void downloadFailed(const QString& videoId, const QString& error);

    void updateFinished(bool success, const QString& message);

private:
    void ensureExecutableExists();
    void downloadExecutable();
    void downloadFfmpeg();
    void ensureFfmpegExists();
    void ensureDenoExists();
    void downloadDeno();

    QString executablePath;
    QString ffmpegPath;
    QString denoPath;
    QString denoZipPath;
    QString downloadsDir;
    QNetworkAccessManager *networkManager;
};

#endif // YTDLPMANAGER_H
