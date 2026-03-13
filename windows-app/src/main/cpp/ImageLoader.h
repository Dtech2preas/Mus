#ifndef IMAGELOADER_H
#define IMAGELOADER_H

#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QPixmap>
#include <QHash>
#include <QLabel>
#include <QPointer>
#include <QCache>

class ImageLoader : public QObject {
    Q_OBJECT

public:
    static ImageLoader* instance();

    // Loads an image from the given url and sets it to the label
    // If the image is already cached, it sets it immediately.
    void loadImage(const QString& url, QLabel* label, const QSize& targetSize = QSize());

private:
    explicit ImageLoader(QObject* parent = nullptr);
    ~ImageLoader();

    QNetworkAccessManager* manager;
    QCache<QString, QPixmap> cache;

    // We map replies to labels to handle them asynchronously
    struct RequestInfo {
        QPointer<QLabel> label;
        QSize targetSize;
    };
    QHash<QNetworkReply*, RequestInfo> pendingRequests;

private slots:
    void handleReply(QNetworkReply* reply);
};

#endif // IMAGELOADER_H
