#ifndef DATABASEMANAGER_H
#define DATABASEMANAGER_H

#include <QObject>
#include <QSqlDatabase>
#include <QSqlQuery>
#include <QString>

class DatabaseManager : public QObject {
    Q_OBJECT

public:
    explicit DatabaseManager(const QString& dbName, QObject *parent = nullptr);
    ~DatabaseManager();

    bool initializeDatabase();
    bool insertSong(const QString& id, const QString& title, const QString& artist);

private:
    QSqlDatabase m_db;
};

#endif // DATABASEMANAGER_H
