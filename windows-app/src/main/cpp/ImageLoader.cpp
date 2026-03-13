#include "ImageLoader.h"
#include <QNetworkRequest>
#include <QUrl>
#include <QDebug>
#include <QPixmap>

ImageLoader* ImageLoader::instance() {
    static ImageLoader* instance = new ImageLoader();
    return instance;
}

ImageLoader::ImageLoader(QObject* parent) : QObject(parent) {
    manager = new QNetworkAccessManager(this);
    connect(manager, &QNetworkAccessManager::finished, this, &ImageLoader::handleReply);
    cache.setMaxCost(100); // Store up to 100 images
}

ImageLoader::~ImageLoader() {}

void ImageLoader::loadImage(const QString& url, QLabel* label, const QSize& targetSize) {
    if (url.isEmpty() || !label) return;

    if (cache.contains(url)) {
        QPixmap pixmap = *cache.object(url);
        if (targetSize.isValid()) {
            pixmap = pixmap.scaled(targetSize, Qt::KeepAspectRatioByExpanding, Qt::SmoothTransformation);
        }
        label->setPixmap(pixmap);
        return;
    }

    QNetworkRequest request((QUrl(url)));
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::NoLessSafeRedirectPolicy);
    QNetworkReply* reply = manager->get(request);

    RequestInfo info;
    info.label = label;
    info.targetSize = targetSize;
    pendingRequests.insert(reply, info);
}

void ImageLoader::handleReply(QNetworkReply* reply) {
    QString url = reply->request().url().toString();

    if (pendingRequests.contains(reply)) {
        RequestInfo info = pendingRequests.take(reply);

        if (reply->error() == QNetworkReply::NoError) {
            QByteArray data = reply->readAll();
            QPixmap* pixmap = new QPixmap();
            if (pixmap->loadFromData(data)) {
                cache.insert(url, pixmap);

                if (info.label) {
                    QPixmap scaledPixmap = *pixmap;
                    if (info.targetSize.isValid()) {
                        scaledPixmap = scaledPixmap.scaled(info.targetSize, Qt::KeepAspectRatioByExpanding, Qt::SmoothTransformation);
                    }
                    info.label->setPixmap(scaledPixmap);
                }
            } else {
                delete pixmap;
            }
        } else {
            qWarning() << "ImageLoader failed to load:" << url << reply->errorString();
        }
    }

    reply->deleteLater();
}
