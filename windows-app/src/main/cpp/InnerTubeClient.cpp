#include "InnerTubeClient.h"
#include <QNetworkRequest>
#include <QUrl>
#include <QDebug>
#include <QFile>
#include <QJsonDocument>

InnerTubeClient::InnerTubeClient(QObject *parent) : QObject(parent) {
    manager = new QNetworkAccessManager(this);

    // Default fallback if config fails
    apiKey = "";

    // Try to load from config.json
    QFile configFile("config.json");
    if (configFile.open(QIODevice::ReadOnly)) {
        QByteArray data = configFile.readAll();
        QJsonDocument doc = QJsonDocument::fromJson(data);
        QJsonObject json = doc.object();
        if (json.contains("youtubeApiKey")) {
            apiKey = json.value("youtubeApiKey").toString();
        }
    }

    // If not in config (for backward compatibility), set a placeholder,
    // real app would require users to set it or fetch remotely
    if (apiKey.isEmpty()) {
        apiKey = "AIzaSyD2C07e2_49XC2sT5e0M_E2"; // Fallback to public WEB key
    }

    baseUrl = "https://youtubei.googleapis.com/youtubei/v1/search?key=" + apiKey;
    playerUrl = "https://youtubei.googleapis.com/youtubei/v1/player?key=" + apiKey;
}

InnerTubeClient::~InnerTubeClient() {
}

void InnerTubeClient::search(const QString& query) {
    QJsonObject client;
    client["clientName"] = "WEB";
    client["clientVersion"] = "2.20230920.00.00";
    client["hl"] = "en";
    client["gl"] = "US";

    QJsonObject context;
    context["client"] = client;

    QJsonObject jsonBody;
    jsonBody["context"] = context;
    jsonBody["query"] = query;
    jsonBody["params"] = "EgIQAQ%3D%3D";

    QJsonDocument doc(jsonBody);
    QByteArray data = doc.toJson();

    QNetworkRequest request(QUrl(baseUrl));
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    request.setRawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");

    QNetworkReply *reply = manager->post(request, data);
    connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        if (reply->error() == QNetworkReply::NoError) {
            QByteArray responseData = reply->readAll();
            QJsonDocument responseDoc = QJsonDocument::fromJson(responseData);
            QJsonObject json = responseDoc.object();

            QList<VideoItem> videos;

            QJsonObject contents = json.value("contents").toObject()
                .value("twoColumnSearchResultsRenderer").toObject()
                .value("primaryContents").toObject()
                .value("sectionListRenderer").toObject();

            QJsonArray contentsArray = contents.value("contents").toArray();
            for (int i = 0; i < contentsArray.size(); ++i) {
                QJsonObject itemSection = contentsArray[i].toObject().value("itemSectionRenderer").toObject();
                QJsonArray results = itemSection.value("contents").toArray();
                for (int j = 0; j < results.size(); ++j) {
                    QJsonObject videoRenderer = results[j].toObject().value("videoRenderer").toObject();
                    if (!videoRenderer.isEmpty()) {
                        VideoItem item;
                        item.id = videoRenderer.value("videoId").toString();
                        item.title = videoRenderer.value("title").toObject().value("runs").toArray()[0].toObject().value("text").toString();
                        item.duration = videoRenderer.value("lengthText").toObject().value("simpleText").toString();
                        item.uploader = videoRenderer.value("ownerText").toObject().value("runs").toArray()[0].toObject().value("text").toString();

                        QJsonArray thumbnails = videoRenderer.value("thumbnail").toObject().value("thumbnails").toArray();
                        if (thumbnails.size() > 0) {
                            item.thumbnailUrl = thumbnails[thumbnails.size() - 1].toObject().value("url").toString();
                        } else {
                            item.thumbnailUrl = "https://i.ytimg.com/vi/" + item.id + "/mqdefault.jpg";
                        }
                        item.webUrl = "https://www.youtube.com/watch?v=" + item.id;

                        if (!item.id.isEmpty()) {
                            videos.append(item);
                        }
                    }
                }
            }
            emit searchFinished(videos);
        } else {
            emit searchFailed(reply->errorString());
        }
        reply->deleteLater();
    });
}

void InnerTubeClient::getStreamUrl(const QString& videoId) {
    QJsonObject client;
    client["clientName"] = "ANDROID";
    client["clientVersion"] = "19.29.35";
    client["platform"] = "MOBILE";
    client["osName"] = "Android";
    client["osVersion"] = "14";
    client["androidSdkVersion"] = 34;
    client["hl"] = "en";
    client["gl"] = "US";

    QJsonObject context;
    context["client"] = client;

    QJsonObject jsonBody;
    jsonBody["videoId"] = videoId;
    jsonBody["contentCheckOk"] = true;
    jsonBody["racyCheckOk"] = true;
    jsonBody["context"] = context;

    QJsonDocument doc(jsonBody);
    QByteArray data = doc.toJson();

    QNetworkRequest request(QUrl(playerUrl));
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    request.setRawHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; Build/UPB2.230407.019)");

    QNetworkReply *reply = manager->post(request, data);
    connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        if (reply->error() == QNetworkReply::NoError) {
            QByteArray responseData = reply->readAll();
            QJsonDocument responseDoc = QJsonDocument::fromJson(responseData);
            QJsonObject json = responseDoc.object();

            QJsonObject streamingData = json.value("streamingData").toObject();
            if (streamingData.isEmpty()) {
                emit streamUrlFailed("No streaming data");
                reply->deleteLater();
                return;
            }

            QString hlsManifestUrl = streamingData.value("hlsManifestUrl").toString();
            if (!hlsManifestUrl.isEmpty()) {
                StreamInfo info{hlsManifestUrl, true};
                emit streamUrlFetched(info);
                reply->deleteLater();
                return;
            }

            QJsonArray adaptiveFormats = streamingData.value("adaptiveFormats").toArray();
            for (int i = 0; i < adaptiveFormats.size(); ++i) {
                QJsonObject format = adaptiveFormats[i].toObject();
                QString mimeType = format.value("mimeType").toString();
                QString url = format.value("url").toString();

                if (mimeType.contains("audio") && !url.isEmpty()) {
                    StreamInfo info{url, false};
                    emit streamUrlFetched(info);
                    reply->deleteLater();
                    return;
                }
            }
            emit streamUrlFailed("No valid audio stream found");
        } else {
            emit streamUrlFailed(reply->errorString());
        }
        reply->deleteLater();
    });
}

void InnerTubeClient::fetchMetadata(const QString& videoId) {
    QJsonObject client;
    client["clientName"] = "ANDROID";
    client["clientVersion"] = "19.29.35";
    client["platform"] = "MOBILE";
    client["osName"] = "Android";
    client["osVersion"] = "14";
    client["androidSdkVersion"] = 34;
    client["hl"] = "en";
    client["gl"] = "US";

    QJsonObject context;
    context["client"] = client;

    QJsonObject jsonBody;
    jsonBody["videoId"] = videoId;
    jsonBody["context"] = context;

    QJsonDocument doc(jsonBody);
    QByteArray data = doc.toJson();

    QNetworkRequest request(QUrl(playerUrl));
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    request.setRawHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; Build/UPB2.230407.019)");

    QNetworkReply *reply = manager->post(request, data);
    connect(reply, &QNetworkReply::finished, this, [this, videoId, reply]() {
        if (reply->error() == QNetworkReply::NoError) {
            QByteArray responseData = reply->readAll();
            QJsonDocument responseDoc = QJsonDocument::fromJson(responseData);
            QJsonObject json = responseDoc.object();

            QJsonObject videoDetails = json.value("videoDetails").toObject();
            if (videoDetails.isEmpty()) {
                emit metadataFailed("No videoDetails");
                reply->deleteLater();
                return;
            }

            VideoItem item;
            item.id = videoId;
            item.title = videoDetails.value("title").toString();
            item.uploader = videoDetails.value("author").toString();

            qint64 lengthSeconds = videoDetails.value("lengthSeconds").toString().toLongLong();
            qint64 minutes = lengthSeconds / 60;
            qint64 seconds = lengthSeconds % 60;
            item.duration = QString("%1:%2").arg(minutes, 2, 10, QChar('0')).arg(seconds, 2, 10, QChar('0'));

            QJsonArray thumbnails = videoDetails.value("thumbnail").toObject().value("thumbnails").toArray();
            if (thumbnails.size() > 0) {
                item.thumbnailUrl = thumbnails[thumbnails.size() - 1].toObject().value("url").toString();
            } else {
                item.thumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
            }
            item.webUrl = "https://www.youtube.com/watch?v=" + videoId;

            emit metadataFetched(item);
        } else {
            emit metadataFailed(reply->errorString());
        }
        reply->deleteLater();
    });
}