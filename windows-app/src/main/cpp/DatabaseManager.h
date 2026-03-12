#ifndef DATABASEMANAGER_H
#define DATABASEMANAGER_H

#include <QObject>
#include <QSqlDatabase>
#include <QSqlQuery>
#include <QString>
#include <QList>

struct DbSong {
    QString id;
    QString title;
    QString uploader;
    QString duration;
    QString thumbnailUrl;
};

class DatabaseManager : public QObject {
    Q_OBJECT

public:
    explicit DatabaseManager(const QString& dbName, QObject *parent = nullptr);
    ~DatabaseManager();

    bool initializeDatabase();

    // Library
    bool insertLibrarySong(const DbSong& song);
    bool removeLibrarySong(const QString& id);
    bool isSongInLibrary(const QString& id);
    QList<DbSong> getLibrarySongs();

    // Play History
    bool addToHistory(const DbSong& song);
    QList<DbSong> getPlayHistory();

    // Made For You / Recommended
    bool insertFeedSong(const QString& feedType, const DbSong& song);
    QList<DbSong> getFeedSongs(const QString& feedType);
    bool clearFeed(const QString& feedType);

private:
    QSqlDatabase m_db;
};

#endif // DATABASEMANAGER_H
