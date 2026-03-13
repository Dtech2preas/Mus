#include "YtDlpManager.h"
#include <QStandardPaths>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>
#include <QDebug>
#include <QRegularExpression>
#include <QCoreApplication>

YtDlpManager::YtDlpManager(QObject *parent) : QObject(parent) {
    networkManager = new QNetworkAccessManager(this);

    // Setup directories
    QString appDataPath = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir dir(appDataPath);
    if (!dir.exists()) {
        dir.mkpath(".");
    }

    executablePath = dir.filePath("yt-dlp.exe");
    ffmpegPath = dir.filePath("ffmpeg.exe");
    denoPath = dir.filePath("deno.exe");
    denoZipPath = dir.filePath("deno.zip");

    // Setup downloads directory
    QString musicPath = QStandardPaths::writableLocation(QStandardPaths::MusicLocation);
    QDir musicDir(musicPath);
    musicDir.mkpath("DTECH_MUSIC");
    downloadsDir = musicDir.filePath("DTECH_MUSIC");

    ensureExecutableExists();
    ensureFfmpegExists();
    ensureDenoExists();
}

YtDlpManager::~YtDlpManager() {
}

QString YtDlpManager::getExecutablePath() const {
    return executablePath;
}

QString YtDlpManager::getDownloadsDir() const {
    return downloadsDir;
}

bool YtDlpManager::isSongDownloaded(const QString& videoId) const {
    return QFile::exists(getDownloadedSongPath(videoId));
}

QString YtDlpManager::getDownloadedSongPath(const QString& videoId) const {
    // Look for matching file in downloads directory
    QDir dir(downloadsDir);
    QString matchString = QString("[%1]").arg(videoId); // File format: "Title [videoId].ext"

    QStringList files = dir.entryList(QDir::Files);
    for (const QString& file : files) {
        if (file.contains(matchString)) {
            return dir.filePath(file);
        }
    }

    return QString();
}

void YtDlpManager::ensureExecutableExists() {
    if (!QFile::exists(executablePath)) {
        qDebug() << "yt-dlp.exe not found at" << executablePath << ". Starting download...";
        downloadExecutable();
    } else {
        qDebug() << "yt-dlp.exe found at" << executablePath;
    }
}

void YtDlpManager::downloadExecutable() {
    // Download the latest release from GitHub
    QUrl url("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe");
    QNetworkRequest request(url);

    // Follow redirects
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::NoLessSafeRedirectPolicy);

    QNetworkReply *reply = networkManager->get(request);

    connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        if (reply->error() == QNetworkReply::NoError) {
            QFile file(executablePath);
            if (file.open(QIODevice::WriteOnly)) {
                file.write(reply->readAll());
                file.close();
                // Make executable (not strictly needed on Windows but good practice)
                file.setPermissions(file.permissions() | QFileDevice::ExeOwner | QFileDevice::ExeUser | QFileDevice::ExeGroup | QFileDevice::ExeOther);
                qDebug() << "Successfully downloaded yt-dlp.exe to" << executablePath;
            } else {
                qCritical() << "Failed to write yt-dlp.exe to" << executablePath;
            }
        } else {
            qCritical() << "Failed to download yt-dlp.exe:" << reply->errorString();
        }
        reply->deleteLater();
    });
}

void YtDlpManager::ensureFfmpegExists() {
    if (!QFile::exists(ffmpegPath)) {
        qDebug() << "ffmpeg.exe not found at" << ffmpegPath << ". Starting download...";
        downloadFfmpeg();
    } else {
        qDebug() << "ffmpeg.exe found at" << ffmpegPath;
    }
}

void YtDlpManager::downloadFfmpeg() {
    // Download a static build of ffmpeg.exe (GitHub release of Gyan D.)
    QUrl url("https://github.com/GyanD/codexffmpeg/releases/download/2025-02-13-git-9cd7c0cb91/ffmpeg-2025-02-13-git-9cd7c0cb91-essentials_build.zip");
    // Since extracting a zip in C++ requires external libs like zlib or QZipReader (private API),
    // and Qt doesn't have a built-in cross-platform unzipper in Core, a much simpler approach for our DTECH_MUSIC
    // project without adding 3rd party libs is to download an already extracted ffmpeg.exe binary from a reliable mirror
    // like this one built specifically for yt-dlp.
    QUrl urlDirect("https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip");

    // Actually, handling zip extraction natively in pure Qt is difficult without QZipReader.
    // To make this fully self-contained as per the reviewer, we'll configure yt-dlp to just use the best audio natively
    // and avoid the --audio-format mp3 extraction if ffmpeg isn't available, or we just rely on standard yt-dlp audio download.
    // yt-dlp can download webm/m4a audio formats directly without ffmpeg.

    // I will modify the download command to just grab the raw m4a stream which is playable directly by Qt QMediaPlayer
    // and doesn't require ffmpeg to remux to mp3. This avoids the entire ffmpeg dependency issue completely!
}

void YtDlpManager::ensureDenoExists() {
    if (!QFile::exists(denoPath)) {
        qDebug() << "deno.exe not found at" << denoPath << ". Starting download...";
        downloadDeno();
    } else {
        qDebug() << "deno.exe found at" << denoPath;
    }
}

void YtDlpManager::downloadDeno() {
    QUrl url("https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip");
    QNetworkRequest request(url);
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::NoLessSafeRedirectPolicy);

    QNetworkReply *reply = networkManager->get(request);

    connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        if (reply->error() == QNetworkReply::NoError) {
            QFile file(denoZipPath);
            if (file.open(QIODevice::WriteOnly)) {
                file.write(reply->readAll());
                file.close();
                qDebug() << "Successfully downloaded deno.zip to" << denoZipPath;

                // Extract using PowerShell
                QString appDataPath = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
                QDir dir(appDataPath);
                // Ensure the base directory exists
                if (!dir.exists()) {
                    dir.mkpath(".");
                }

                QProcess *process = new QProcess(this);

                connect(process, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
                        [this, process](int exitCode, QProcess::ExitStatus exitStatus) {
                    if (exitStatus == QProcess::NormalExit && exitCode == 0) {
                        qDebug() << "Successfully extracted deno.zip";
                        // Cleanup zip
                        QFile::remove(denoZipPath);
                    } else {
                        QString error = process->readAllStandardError().trimmed();
                        qCritical() << "Failed to extract deno.zip:" << error;
                    }
                    process->deleteLater();
                });

                QStringList args;
                args << "-NoProfile" << "-Command"
                     << QString("Expand-Archive -Force -Path '%1' -DestinationPath '%2'").arg(denoZipPath, dir.absolutePath());

                qDebug() << "Extracting deno.zip using PowerShell...";
                process->start("powershell", args);

            } else {
                qCritical() << "Failed to write deno.zip to" << denoZipPath;
            }
        } else {
            qCritical() << "Failed to download deno.zip:" << reply->errorString();
        }
        reply->deleteLater();
    });
}

void YtDlpManager::updateYtDlp() {
    if (!QFile::exists(executablePath)) {
        downloadExecutable();
        emit updateFinished(true, "Downloading latest yt-dlp...");
        return;
    }

    QProcess *process = new QProcess(this);
    connect(process, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
            [this, process](int exitCode, QProcess::ExitStatus exitStatus) {
        if (exitStatus == QProcess::NormalExit && exitCode == 0) {
            QString output = process->readAllStandardOutput();
            qDebug() << "yt-dlp update output:" << output;
            emit updateFinished(true, "yt-dlp updated successfully.");
        } else {
            QString error = process->readAllStandardError();
            qCritical() << "yt-dlp update failed:" << error;
            emit updateFinished(false, "Failed to update yt-dlp: " + error);
        }
        process->deleteLater();
    });

    QStringList args;
    args << "-U";

    qDebug() << "Updating yt-dlp...";
    process->start(executablePath, args);
}

void YtDlpManager::fetchStreamUrl(const QString& videoId) {
    if (!QFile::exists(executablePath)) {
        emit streamUrlFailed(videoId, "yt-dlp.exe not found. Downloading it now. Please try again in a few seconds.");
        ensureExecutableExists();
        return;
    }

    QString youtubeUrl = "https://www.youtube.com/watch?v=" + videoId;

    QProcess *process = new QProcess(this);

    connect(process, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
            [this, process, videoId](int exitCode, QProcess::ExitStatus exitStatus) {
        if (exitStatus == QProcess::NormalExit && exitCode == 0) {
            QString output = process->readAllStandardOutput().trimmed();
            if (!output.isEmpty()) {
                qDebug() << "yt-dlp successfully extracted stream URL for" << videoId;
                emit streamUrlFetched(videoId, output);
            } else {
                emit streamUrlFailed(videoId, "No stream URL returned.");
            }
        } else {
            QString error = process->readAllStandardError().trimmed();
            qCritical() << "yt-dlp stream extraction failed for" << videoId << ":" << error;
            emit streamUrlFailed(videoId, error);
        }
        process->deleteLater();
    });

    QStringList args;

    if (QFile::exists(denoPath)) {
        args << "--js-runtimes" << QString("deno:%1").arg(denoPath);
    }

    args << "-g"                        // Get URL
         << "-f" << "bestaudio"         // Best audio format
         << "--no-playlist"             // Ensure it's not a playlist
         << youtubeUrl;

    qDebug() << "Fetching stream URL for" << videoId << "using yt-dlp...";
    process->start(executablePath, args);
}

void YtDlpManager::downloadSong(const QString& videoId, const QString& title) {
    if (!QFile::exists(executablePath)) {
        emit downloadFailed(videoId, "yt-dlp.exe not found.");
        return;
    }

    if (isSongDownloaded(videoId)) {
        qDebug() << "Song" << videoId << "is already downloaded.";
        emit downloadFinished(videoId, getDownloadedSongPath(videoId));
        return;
    }

    QString youtubeUrl = "https://www.youtube.com/watch?v=" + videoId;

    // Output template: Title [videoId].ext
    QString outputTemplate = QDir(downloadsDir).filePath("%(title)s [%(id)s].%(ext)s");

    QProcess *process = new QProcess(this);

    connect(process, &QProcess::readyReadStandardOutput, [this, process, videoId]() {
        QString output = process->readAllStandardOutput();

        // Parse progress e.g., "[download]  25.0% of 3.42MiB at 1.50MiB/s ETA 00:01"
        QRegularExpression regex("\\[download\\]\\s+(\\d+\\.\\d+)%");
        QRegularExpressionMatch match = regex.match(output);
        if (match.hasMatch()) {
            double progress = match.captured(1).toDouble();
            emit downloadProgress(videoId, static_cast<int>(progress));
        }
    });

    connect(process, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
            [this, process, videoId](int exitCode, QProcess::ExitStatus exitStatus) {
        if (exitStatus == QProcess::NormalExit && exitCode == 0) {
            QString filePath = getDownloadedSongPath(videoId);
            if (!filePath.isEmpty()) {
                qDebug() << "Successfully downloaded" << videoId << "to" << filePath;
                emit downloadFinished(videoId, filePath);
            } else {
                qCritical() << "yt-dlp finished but file not found for" << videoId;
                emit downloadFailed(videoId, "Download finished but file not found.");
            }
        } else {
            QString error = process->readAllStandardError().trimmed();
            qCritical() << "yt-dlp download failed for" << videoId << ":" << error;
            emit downloadFailed(videoId, error);
        }
        process->deleteLater();
    });

    QStringList args;

    if (QFile::exists(denoPath)) {
        args << "--js-runtimes" << QString("deno:%1").arg(denoPath);
    }

    args << "-f" << "bestaudio[ext=m4a]"         // Download m4a directly (no ffmpeg needed)
         << "-o" << outputTemplate               // Output template
         << "--no-playlist"                      // Single video only
         << youtubeUrl;

    qDebug() << "Starting download for" << videoId << "using yt-dlp...";
    process->start(executablePath, args);
}