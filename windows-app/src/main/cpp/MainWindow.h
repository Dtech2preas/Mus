#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QVBoxLayout>
#include <QLabel>
#include <QPushButton>
#include "MusicPlayer.h"
#include "DatabaseManager.h"
#include "NetworkClient.h"

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    MainWindow(QWidget *parent = nullptr);
    ~MainWindow();

private slots:
    void onPlayPauseClicked();
    void onSearchClicked();

private:
    void setupUI();

    QWidget *centralWidget;
    QVBoxLayout *mainLayout;
    QLabel *greetingLabel;
    QPushButton *playPauseButton;
    QPushButton *searchButton;

    MusicPlayer *player;
    DatabaseManager *db;
    NetworkClient *networkClient;
};

#endif // MAINWINDOW_H
