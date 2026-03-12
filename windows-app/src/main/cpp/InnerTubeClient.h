#ifndef INNERTUBECLIENT_H
#define INNERTUBECLIENT_H

#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QString>
#include <QList>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>

struct VideoItem {
    QString id;
    QString title;
    QString uploader;
    QString duration;
    QString thumbnailUrl;
    QString webUrl;
};

struct StreamInfo {
    QString url;
    bool isHls;
};

class InnerTubeClient : public QObject {
    Q_OBJECT

public:
    explicit InnerTubeClient(QObject *parent = nullptr);
    ~InnerTubeClient();

    void search(const QString& query);
    void getStreamUrl(const QString& videoId);
    void fetchMetadata(const QString& videoId);

signals:
    void searchFinished(const QList<VideoItem>& results);
    void searchFailed(const QString& error);

    void streamUrlFetched(const StreamInfo& info);
    void streamUrlFailed(const QString& error);

    void metadataFetched(const VideoItem& item);
    void metadataFailed(const QString& error);

private:
    QNetworkAccessManager *manager;
    QString apiKey;
    QString baseUrl;
    QString playerUrl;
};

#endif // INNERTUBECLIENT_H