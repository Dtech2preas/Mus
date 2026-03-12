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
    bool success = query.exec("CREATE TABLE IF NOT EXISTS songs ("
                              "id TEXT PRIMARY KEY, "
                              "title TEXT NOT NULL, "
                              "artist TEXT NOT NULL)");

    if (!success) {
        qCritical() << "Error creating songs table:" << query.lastError().text();
    }
    return success;
}

bool DatabaseManager::insertSong(const QString& id, const QString& title, const QString& artist) {
    QSqlQuery query(m_db);
    query.prepare("INSERT INTO songs (id, title, artist) VALUES (:id, :title, :artist)");
    query.bindValue(":id", id);
    query.bindValue(":title", title);
    query.bindValue(":artist", artist);

    if (!query.exec()) {
        qCritical() << "Error inserting song:" << query.lastError().text();
        return false;
    }
    return true;
}
