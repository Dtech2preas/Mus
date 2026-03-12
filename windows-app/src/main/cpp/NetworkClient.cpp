#include "NetworkClient.h"
#include <QNetworkRequest>
#include <QUrl>
#include <QDebug>

NetworkClient::NetworkClient(QObject *parent) : QObject(parent) {
    manager = new QNetworkAccessManager(this);
    connect(manager, &QNetworkAccessManager::finished, this, &NetworkClient::handleReply);
}

NetworkClient::~NetworkClient() {
    // manager is automatically deleted as a child object
}

void NetworkClient::fetchUrl(const QString &url) {
    QNetworkRequest request((QUrl(url)));
    manager->get(request);
}

void NetworkClient::handleReply(QNetworkReply *reply) {
    if (reply->error() == QNetworkReply::NoError) {
        QByteArray data = reply->readAll();
        qDebug() << "NetworkClient: Received data successfully.";
        emit dataReady(data);
    } else {
        qCritical() << "NetworkClient Error:" << reply->errorString();
        emit errorOccurred(reply->errorString());
    }
    reply->deleteLater();
}
