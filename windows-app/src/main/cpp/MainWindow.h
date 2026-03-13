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
#include "YtDlpManager.h"
#include "ImageLoader.h"

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    MainWindow(QWidget *parent = nullptr);
    ~MainWindow();

protected:
    void resizeEvent(QResizeEvent *event) override;
    bool eventFilter(QObject *watched, QEvent *event) override;

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
    QWidget* createFullScreenPlayer();

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
    QLabel *lblPlayerArt;
    QLabel *lblCurrentSong;
    QPushButton *btnPrev;
    QPushButton *btnPlayPause;
    QPushButton *btnNext;
    QSlider *progressSlider;
    QLabel *lblTime;

    // Full Screen Player UI
    QWidget *fullScreenPlayer;
    QLabel *fsLblAlbumArt;
    QLabel *fsLblTitle;
    QLabel *fsLblArtist;
    QSlider *fsProgressSlider;
    QLabel *fsLblTime;
    QPushButton *fsBtnPrev;
    QPushButton *fsBtnPlayPause;
    QPushButton *fsBtnNext;
    QPushButton *fsBtnLike;
    QPushButton *fsBtnAddLibrary;
    QPushButton *fsBtnClose;

    // Screen specific widgets
    QLineEdit *searchInput;
    QListWidget *searchResultsList;

    // Home Screen Layouts for dynamic population
    QHBoxLayout *trendingHomeLayout;
    QGridLayout *madeForYouHomeLayout;

    QListWidget *libraryList;
    QPushButton *btnAllSongs;
    QPushButton *btnPlaylists;
    QPushButton *btnArtists;

    QWidget* createSongItemWidget(const VideoItem& song);
    void checkOnboarding();
    void loadHomeRecommendations();

    // Services
    MusicPlayer *player;
    DatabaseManager *db;
    InnerTubeClient *innerTube;
    YtDlpManager *ytDlp;
};

#endif // MAINWINDOW_H
