#include "DatabaseManager.h"
#include <QSqlError>
#include <QDebug>

DatabaseManager::DatabaseManager(const QString& dbName, QObject *parent) : QObject(parent) {
    m_db = QSqlDatabase::addDatabase("QSQLITE");
    m_db.setDatabaseName(dbName);

    if (!m_db.open()) {
        qCritical() << "Error opening database:" << m_db.lastError().text();
    } else {
        qDebug() << "Database opened successfully:" << dbName;
        initializeDatabase();
    }
}

DatabaseManager::~DatabaseManager() {
    if (m_db.isOpen()) {
        m_db.close();
    }
}

bool DatabaseManager::initializeDatabase() {
    QSqlQuery query(m_db);
    bool success = true;

    // Library Table
    success &= query.exec("CREATE TABLE IF NOT EXISTS library_songs ("
                          "id TEXT PRIMARY KEY, "
                          "title TEXT NOT NULL, "
                          "uploader TEXT NOT NULL, "
                          "duration TEXT, "
                          "thumbnailUrl TEXT, "
                          "addedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

    // Play History Table
    success &= query.exec("CREATE TABLE IF NOT EXISTS play_history ("
                          "history_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                          "id TEXT NOT NULL, "
                          "title TEXT NOT NULL, "
                          "uploader TEXT NOT NULL, "
                          "duration TEXT, "
                          "thumbnailUrl TEXT, "
                          "playedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

    // Feeds Table (Recommended, Trending, Made for You)
    success &= query.exec("CREATE TABLE IF NOT EXISTS feeds ("
                          "feedType TEXT NOT NULL, "
                          "id TEXT NOT NULL, "
                          "title TEXT NOT NULL, "
                          "uploader TEXT NOT NULL, "
                          "duration TEXT, "
                          "thumbnailUrl TEXT, "
                          "PRIMARY KEY(feedType, id))");

    if (!success) {
        qCritical() << "Error creating database tables:" << query.lastError().text();
    }
    return success;
}

bool DatabaseManager::insertLibrarySong(const DbSong& song) {
    QSqlQuery query(m_db);
    query.prepare("INSERT OR REPLACE INTO library_songs (id, title, uploader, duration, thumbnailUrl) "
                  "VALUES (:id, :title, :uploader, :duration, :thumbnailUrl)");
    query.bindValue(":id", song.id);
    query.bindValue(":title", song.title);
    query.bindValue(":uploader", song.uploader);
    query.bindValue(":duration", song.duration);
    query.bindValue(":thumbnailUrl", song.thumbnailUrl);

    if (!query.exec()) {
        qCritical() << "Error inserting library song:" << query.lastError().text();
        return false;
    }
    return true;
}

bool DatabaseManager::removeLibrarySong(const QString& id) {
    QSqlQuery query(m_db);
    query.prepare("DELETE FROM library_songs WHERE id = :id");
    query.bindValue(":id", id);
    return query.exec();
}

bool DatabaseManager::isSongInLibrary(const QString& id) {
    QSqlQuery query(m_db);
    query.prepare("SELECT 1 FROM library_songs WHERE id = :id");
    query.bindValue(":id", id);
    if (query.exec() && query.next()) {
        return true;
    }
    return false;
}

QList<DbSong> DatabaseManager::getLibrarySongs() {
    QList<DbSong> songs;
    QSqlQuery query("SELECT id, title, uploader, duration, thumbnailUrl FROM library_songs ORDER BY addedAt DESC", m_db);
    while (query.next()) {
        DbSong song;
        song.id = query.value("id").toString();
        song.title = query.value("title").toString();
        song.uploader = query.value("uploader").toString();
        song.duration = query.value("duration").toString();
        song.thumbnailUrl = query.value("thumbnailUrl").toString();
        songs.append(song);
    }
    return songs;
}

bool DatabaseManager::addToHistory(const DbSong& song) {
    QSqlQuery query(m_db);
    query.prepare("INSERT INTO play_history (id, title, uploader, duration, thumbnailUrl) "
                  "VALUES (:id, :title, :uploader, :duration, :thumbnailUrl)");
    query.bindValue(":id", song.id);
    query.bindValue(":title", song.title);
    query.bindValue(":uploader", song.uploader);
    query.bindValue(":duration", song.duration);
    query.bindValue(":thumbnailUrl", song.thumbnailUrl);

    bool ok = query.exec();

    // Enforce max 400 entries like Android app
    if (ok) {
        query.exec("DELETE FROM play_history WHERE history_id NOT IN ("
                   "SELECT history_id FROM play_history ORDER BY playedAt DESC LIMIT 400"
                   ")");
    }

    return ok;
}

QList<DbSong> DatabaseManager::getPlayHistory() {
    QList<DbSong> songs;
    QSqlQuery query("SELECT id, title, uploader, duration, thumbnailUrl FROM play_history ORDER BY playedAt DESC", m_db);
    while (query.next()) {
        DbSong song;
        song.id = query.value("id").toString();
        song.title = query.value("title").toString();
        song.uploader = query.value("uploader").toString();
        song.duration = query.value("duration").toString();
        song.thumbnailUrl = query.value("thumbnailUrl").toString();
        songs.append(song);
    }
    return songs;
}

bool DatabaseManager::insertFeedSong(const QString& feedType, const DbSong& song) {
    QSqlQuery query(m_db);
    query.prepare("INSERT OR REPLACE INTO feeds (feedType, id, title, uploader, duration, thumbnailUrl) "
                  "VALUES (:feedType, :id, :title, :uploader, :duration, :thumbnailUrl)");
    query.bindValue(":feedType", feedType);
    query.bindValue(":id", song.id);
    query.bindValue(":title", song.title);
    query.bindValue(":uploader", song.uploader);
    query.bindValue(":duration", song.duration);
    query.bindValue(":thumbnailUrl", song.thumbnailUrl);

    return query.exec();
}

QList<DbSong> DatabaseManager::getFeedSongs(const QString& feedType) {
    QList<DbSong> songs;
    QSqlQuery query(m_db);
    query.prepare("SELECT id, title, uploader, duration, thumbnailUrl FROM feeds WHERE feedType = :feedType");
    query.bindValue(":feedType", feedType);

    if (query.exec()) {
        while (query.next()) {
            DbSong song;
            song.id = query.value("id").toString();
            song.title = query.value("title").toString();
            song.uploader = query.value("uploader").toString();
            song.duration = query.value("duration").toString();
            song.thumbnailUrl = query.value("thumbnailUrl").toString();
            songs.append(song);
        }
    }
    return songs;
}

bool DatabaseManager::clearFeed(const QString& feedType) {
    QSqlQuery query(m_db);
    query.prepare("DELETE FROM feeds WHERE feedType = :feedType");
    query.bindValue(":feedType", feedType);
    return query.exec();
}
