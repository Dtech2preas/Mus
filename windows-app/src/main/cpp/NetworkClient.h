#ifndef NETWORKCLIENT_H
#define NETWORKCLIENT_H

#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>

class NetworkClient : public QObject {
    Q_OBJECT

public:
    explicit NetworkClient(QObject *parent = nullptr);
    ~NetworkClient();

    void fetchUrl(const QString &url);

signals:
    void dataReady(const QByteArray &data);
    void errorOccurred(const QString &errorString);

private slots:
    void handleReply(QNetworkReply *reply);

private:
    QNetworkAccessManager *manager;
};

#endif // NETWORKCLIENT_H
