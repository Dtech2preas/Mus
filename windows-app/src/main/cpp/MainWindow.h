#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QStackedWidget>
#include <QSlider>
#include <QLineEdit>
#include <QListWidget>
#include <QScrollArea>
#include "MusicPlayer.h"
#include "DatabaseManager.h"
#include "InnerTubeClient.h"

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    MainWindow(QWidget *parent = nullptr);
    ~MainWindow();

private slots:
    // Navigation
    void navigateToHome();
    void navigateToSearch();
    void navigateToLibrary();
    void navigateToSettings();

    // Player Controls
    void onPlayPauseClicked();
    void onNextClicked();
    void onPrevClicked();
    void updatePlayerUI();

    // Feature actions
    void performSearch();

private:
    void setupUI();
    void createSidebar();
    void createPlayerBar();

    // Screens
    QWidget* createHomeScreen();
    QWidget* createSearchScreen();
    QWidget* createLibraryScreen();
    QWidget* createSettingsScreen();

    // Core Components
    QWidget *centralWidget;
    QHBoxLayout *mainLayout;
    QStackedWidget *stackedWidget;

    // Sidebar
    QWidget *sidebar;
    QPushButton *btnHome;
    QPushButton *btnSearch;
    QPushButton *btnLibrary;
    QPushButton *btnSettings;

    // Bottom Player Bar
    QWidget *playerBar;
    QLabel *lblCurrentSong;
    QPushButton *btnPrev;
    QPushButton *btnPlayPause;
    QPushButton *btnNext;
    QSlider *progressSlider;
    QLabel *lblTime;

    // Screen specific widgets
    QLineEdit *searchInput;
    QListWidget *searchResultsList;

    QListWidget *libraryList;
    QPushButton *btnAllSongs;
    QPushButton *btnPlaylists;
    QPushButton *btnArtists;

    // Services
    MusicPlayer *player;
    DatabaseManager *db;
    InnerTubeClient *innerTube;
};

#endif // MAINWINDOW_H
