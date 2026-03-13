#include "MainWindow.h"
#include "DebugWindow.h"
#include <QStandardPaths>
#include <QDir>
#include <QVBoxLayout>
#include <QEvent>
#include <QGridLayout>
#include <QScrollArea>
#include <QTimer>
#include <QDebug>
#include <QSettings>
#include <QMessageBox>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
      player(new MusicPlayer(this)),
      innerTube(new InnerTubeClient(this)),
      ytDlp(new YtDlpManager(this))
{
    QString appDataPath = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir dir(appDataPath);
    if (!dir.exists()) {
        dir.mkpath(".");
    }
    db = new DatabaseManager(dir.filePath("music.db"));

    setupUI();

    // Timer for updating player progress UI
    QTimer *timer = new QTimer(this);
    connect(timer, &QTimer::timeout, this, &MainWindow::updatePlayerUI);
    timer->start(1000);

    connect(innerTube, &InnerTubeClient::searchFinished, this, [this](const QList<VideoItem>& results) {
        searchResultsList->clear();
        for (const auto& item : results) {
            QListWidgetItem* listItem = new QListWidgetItem(searchResultsList);
            listItem->setSizeHint(QSize(0, 60)); // Make room for custom widget

            QWidget* widget = createSongItemWidget(item);
            searchResultsList->addItem(listItem);
            searchResultsList->setItemWidget(listItem, widget);
        }
    });

    // Handle updates when a download finishes (handled in specific widgets for accurate metadata)
}

QWidget* MainWindow::createSongItemWidget(const VideoItem& song) {
    QWidget* widget = new QWidget(this);
    QHBoxLayout* layout = new QHBoxLayout(widget);
    layout->setContentsMargins(10, 5, 10, 5);

    QLabel* artLabel = new QLabel(widget);
    artLabel->setFixedSize(50, 50);
    artLabel->setStyleSheet("background-color: #222; border-radius: 4px;");
    if (!song.thumbnailUrl.isEmpty()) {
        ImageLoader::instance()->loadImage(song.thumbnailUrl, artLabel, QSize(50, 50));
    }
    layout->addWidget(artLabel);

    // Playable area
    QPushButton* playBtn = new QPushButton(song.title + "\n" + song.uploader, widget);
    playBtn->setStyleSheet("QPushButton { text-align: left; background: transparent; border: none; color: white; font-size: 13px; padding-left: 5px; } "
                           "QPushButton:hover { color: #00a6ff; }");

    connect(playBtn, &QPushButton::clicked, this, [this, song]() {
        player->playSong(song);
        db->addToHistory({song.id, song.title, song.uploader, song.duration, song.thumbnailUrl});
    });

    layout->addWidget(playBtn, 1); // Takes up most space

    // Download Area
    QWidget* dlWidget = new QWidget(widget);
    QHBoxLayout* dlLayout = new QHBoxLayout(dlWidget);
    dlLayout->setContentsMargins(0, 0, 0, 0);

    QPushButton* btnDownload = new QPushButton("⭳", dlWidget);
    btnDownload->setFixedSize(30, 30);
    btnDownload->setStyleSheet("QPushButton { background-color: #222; border-radius: 15px; color: #00a6ff; font-weight: bold; font-size: 16px; }"
                               "QPushButton:hover { background-color: #333; }");

    QLabel* lblProgress = new QLabel("", dlWidget);
    lblProgress->setStyleSheet("color: #aaa; font-size: 10px;");
    lblProgress->hide();

    dlLayout->addWidget(btnDownload);
    dlLayout->addWidget(lblProgress);

    // Initial state check
    if (ytDlp->isSongDownloaded(song.id)) {
        btnDownload->hide();
        lblProgress->setText("Downloaded");
        lblProgress->show();
    }

    connect(btnDownload, &QPushButton::clicked, this, [this, song, btnDownload, lblProgress]() {
        btnDownload->setEnabled(false);
        lblProgress->setText("Starting...");
        lblProgress->show();
        ytDlp->downloadSong(song.id, song.title);
    });

    connect(ytDlp, &YtDlpManager::downloadProgress, dlWidget, [dlWidget, song, lblProgress](const QString& videoId, int percentage) {
        if (song.id == videoId) {
            lblProgress->setText(QString::number(percentage) + "%");
        }
    });

    connect(ytDlp, &YtDlpManager::downloadFinished, dlWidget, [this, dlWidget, song, btnDownload, lblProgress](const QString& videoId, const QString& filePath) {
        if (song.id == videoId) {
            btnDownload->hide();
            lblProgress->setText("Downloaded");
            db->insertLibrarySong({song.id, song.title, song.uploader, song.duration, song.thumbnailUrl});
        }
    });

    connect(ytDlp, &YtDlpManager::downloadFailed, dlWidget, [dlWidget, song, btnDownload, lblProgress](const QString& videoId, const QString& error) {
        if (song.id == videoId) {
            btnDownload->setEnabled(true);
            lblProgress->setText("Failed");
        }
    });

    layout->addWidget(dlWidget);
    return widget;
}

MainWindow::~MainWindow() {
    delete db;
}

void MainWindow::setupUI() {
    this->setStyleSheet(
        "QMainWindow { background-color: #121212; color: #ffffff; }"
        "QWidget { color: #ffffff; font-family: 'Segoe UI', Arial, sans-serif; }"
        "QScrollArea { background-color: transparent; border: none; }"
        "QScrollBar:vertical { background: #121212; width: 10px; margin: 0px 0px 0px 0px; }"
        "QScrollBar::handle:vertical { background: #333333; min-height: 20px; border-radius: 5px; }"
        "QScrollBar::handle:vertical:hover { background: #00a6ff; }"
        "QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0px; }"
        "QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical { background: none; }"
        "QScrollBar:horizontal { background: #121212; height: 10px; margin: 0px 0px 0px 0px; }"
        "QScrollBar::handle:horizontal { background: #333333; min-width: 20px; border-radius: 5px; }"
        "QScrollBar::handle:horizontal:hover { background: #00a6ff; }"
        "QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal { width: 0px; }"
        "QScrollBar::add-page:horizontal, QScrollBar::sub-page:horizontal { background: none; }"
        "QSlider::groove:horizontal { border: 1px solid #333; height: 4px; background: #222; border-radius: 2px; }"
        "QSlider::sub-page:horizontal { background: #00a6ff; border-radius: 2px; }"
        "QSlider::handle:horizontal { background: white; width: 12px; margin: -4px 0; border-radius: 6px; }"
        "QSlider::handle:horizontal:hover { background: #00a6ff; }"
    );

    centralWidget = new QWidget(this);
    setCentralWidget(centralWidget);

    QVBoxLayout *rootLayout = new QVBoxLayout(centralWidget);
    rootLayout->setContentsMargins(0, 0, 0, 0);
    rootLayout->setSpacing(0);

    // Main Content Area (Sidebar + Stacked Screens)
    QWidget *contentWidget = new QWidget(this);
    mainLayout = new QHBoxLayout(contentWidget);
    mainLayout->setContentsMargins(0, 0, 0, 0);
    mainLayout->setSpacing(0);

    createSidebar();

    stackedWidget = new QStackedWidget(this);
    stackedWidget->addWidget(createHomeScreen());
    stackedWidget->addWidget(createSearchScreen());
    stackedWidget->addWidget(createLibraryScreen());
    stackedWidget->addWidget(createSettingsScreen());

    mainLayout->addWidget(sidebar);
    mainLayout->addWidget(stackedWidget, 1); // takes remaining space

    rootLayout->addWidget(contentWidget, 1);

    // Bottom Player Bar
    createPlayerBar();
    rootLayout->addWidget(playerBar);

    // Full Screen Player
    fullScreenPlayer = createFullScreenPlayer();
    fullScreenPlayer->hide();

    // Add fullScreenPlayer to the very top so it overlays everything
    fullScreenPlayer->setParent(this);
    fullScreenPlayer->resize(this->size());

    // Check if we need to show onboarding
    QTimer::singleShot(500, this, &MainWindow::checkOnboarding);
}

void MainWindow::checkOnboarding() {
    QSettings settings("DTECH", "Music");
    if (!settings.contains("favoriteGenres") || !settings.contains("favoriteArtists")) {
        QDialog dialog(this);
        dialog.setWindowTitle("Welcome to DTECH MUSIC");
        dialog.setFixedSize(500, 400);
        dialog.setStyleSheet("QDialog { background-color: #121212; color: white; }");

        QVBoxLayout *layout = new QVBoxLayout(&dialog);

        QLabel *title = new QLabel("Welcome to DTECH MUSIC", &dialog);
        title->setStyleSheet("font-size: 24px; font-weight: bold; color: #00a6ff;");
        title->setAlignment(Qt::AlignCenter);
        layout->addWidget(title);

        QLabel *desc = new QLabel("Tell us what you like so we can recommend the best tracks for you.", &dialog);
        desc->setWordWrap(true);
        layout->addWidget(desc);

        layout->addSpacing(20);

        QLabel *lblGenres = new QLabel("Favorite Genres (comma separated):", &dialog);
        QLineEdit *inputGenres = new QLineEdit(&dialog);
        inputGenres->setStyleSheet("padding: 10px; background-color: #222; border: 1px solid #444; border-radius: 5px; color: white;");
        inputGenres->setPlaceholderText("e.g. Synthwave, Cyberpunk, Phonk");

        layout->addWidget(lblGenres);
        layout->addWidget(inputGenres);

        layout->addSpacing(10);

        QLabel *lblArtists = new QLabel("Favorite Artists (comma separated):", &dialog);
        QLineEdit *inputArtists = new QLineEdit(&dialog);
        inputArtists->setStyleSheet("padding: 10px; background-color: #222; border: 1px solid #444; border-radius: 5px; color: white;");
        inputArtists->setPlaceholderText("e.g. Perturbator, Carpenter Brut");

        layout->addWidget(lblArtists);
        layout->addWidget(inputArtists);

        layout->addStretch();

        QPushButton *btnSave = new QPushButton("Save & Continue", &dialog);
        btnSave->setStyleSheet("background-color: #00a6ff; color: black; font-weight: bold; padding: 12px; border-radius: 5px;");
        connect(btnSave, &QPushButton::clicked, &dialog, &QDialog::accept);
        layout->addWidget(btnSave);

        dialog.exec();

        // Save to settings
        settings.setValue("favoriteGenres", inputGenres->text());
        settings.setValue("favoriteArtists", inputArtists->text());
    }

    // Now load recommendations based on preferences
    loadHomeRecommendations();
}

void MainWindow::loadHomeRecommendations() {
    QSettings settings("DTECH", "Music");
    QString genres = settings.value("favoriteGenres", "Phonk").toString();
    QString artists = settings.value("favoriteArtists", "Various Artists").toString();

    QStringList genreList = genres.split(",", Qt::SkipEmptyParts);
    QString query = "Trending " + (genreList.isEmpty() ? "Music" : genreList.first().trimmed());

    // Use an isolated InnerTubeClient for fetching recommendations so we don't interfere with search results list
    InnerTubeClient *recClient = new InnerTubeClient(this);

    connect(recClient, &InnerTubeClient::searchFinished, this, [this, recClient](const QList<VideoItem>& results) {
        // Clear trending layout
        QLayoutItem* item;
        while ((item = trendingHomeLayout->takeAt(0)) != nullptr) {
            delete item->widget();
            delete item;
        }

        // Populate Trending
        for (int i = 0; i < qMin(5, static_cast<int>(results.size())); ++i) {
            VideoItem vItem = results[i];

            QWidget *cardWidget = new QWidget(this);
            cardWidget->setFixedSize(150, 200);
            QVBoxLayout *cardLayout = new QVBoxLayout(cardWidget);
            cardLayout->setContentsMargins(5, 5, 5, 5);
            cardWidget->setStyleSheet("QWidget { background-color: #1a1a1a; border-radius: 12px; border: 1px solid #2a2a2a; } "
                                      "QWidget:hover { border-color: #00a6ff; background-color: #222; }");

            QLabel *artLabel = new QLabel(cardWidget);
            artLabel->setFixedSize(138, 138);
            artLabel->setAlignment(Qt::AlignCenter);
            artLabel->setStyleSheet("background-color: #222; border-radius: 4px;");
            if (!vItem.thumbnailUrl.isEmpty()) {
                ImageLoader::instance()->loadImage(vItem.thumbnailUrl, artLabel, QSize(138, 138));
            }
            cardLayout->addWidget(artLabel);

            QPushButton *btnPlay = new QPushButton(vItem.title + "\n" + vItem.uploader, cardWidget);
            btnPlay->setStyleSheet("background: transparent; border: none; color: white; text-align: left; font-size: 11px;");
            btnPlay->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);

            connect(btnPlay, &QPushButton::clicked, this, [this, vItem]() {
                player->playSong(vItem);
                db->addToHistory({vItem.id, vItem.title, vItem.uploader, vItem.duration, vItem.thumbnailUrl});
            });

            cardLayout->addWidget(btnPlay);

            // Re-use download widget logic from recently played
            QHBoxLayout *bottomLayout = new QHBoxLayout();
            bottomLayout->setContentsMargins(0, 0, 0, 0);
            bottomLayout->addStretch();
            QPushButton *btnDownload = new QPushButton("⭳", cardWidget);
            btnDownload->setFixedSize(25, 25);
            btnDownload->setStyleSheet("QPushButton { background-color: #333; border-radius: 12px; color: #00a6ff; font-weight: bold; border: none; }"
                                       "QPushButton:hover { background-color: #555; }");
            QLabel *lblProgress = new QLabel("", cardWidget);
            lblProgress->setStyleSheet("color: #aaa; font-size: 10px; border: none;");
            lblProgress->hide();
            bottomLayout->addWidget(lblProgress);
            bottomLayout->addWidget(btnDownload);

            if (ytDlp->isSongDownloaded(vItem.id)) { btnDownload->hide(); lblProgress->setText("Downloaded"); lblProgress->show(); }
            connect(btnDownload, &QPushButton::clicked, this, [this, vItem, btnDownload, lblProgress]() {
                btnDownload->setEnabled(false); lblProgress->setText("Starting..."); lblProgress->show();
                ytDlp->downloadSong(vItem.id, vItem.title);
            });
            connect(ytDlp, &YtDlpManager::downloadProgress, cardWidget, [cardWidget, vItem, lblProgress](const QString& id, int percent) {
                if (id == vItem.id) lblProgress->setText(QString::number(percent) + "%");
            });
            connect(ytDlp, &YtDlpManager::downloadFinished, cardWidget, [this, cardWidget, vItem, btnDownload, lblProgress](const QString& id, const QString&) {
                if (id == vItem.id) {
                    btnDownload->hide();
                    lblProgress->setText("Downloaded");
                    db->insertLibrarySong({vItem.id, vItem.title, vItem.uploader, vItem.duration, vItem.thumbnailUrl});
                }
            });

            cardLayout->addLayout(bottomLayout);
            trendingHomeLayout->addWidget(cardWidget);
        }

        // Populate Made For You (using the rest of the results as a mock)
        QLayoutItem* mItem;
        while ((mItem = madeForYouHomeLayout->takeAt(0)) != nullptr) {
            delete mItem->widget();
            delete mItem;
        }

        int row = 0, col = 0;
        for (int i = 5; i < results.size() && row < 10; ++i) {
            VideoItem vItem = results[i];

            QWidget *cardWidget = new QWidget(this);
            cardWidget->setFixedSize(150, 200);
            QVBoxLayout *cardLayout = new QVBoxLayout(cardWidget);
            cardLayout->setContentsMargins(5, 5, 5, 5);
            cardWidget->setStyleSheet("QWidget { background-color: #1a1a1a; border-radius: 12px; border: 1px solid #2a2a2a; } "
                                      "QWidget:hover { border-color: #00a6ff; background-color: #222; }");

            QLabel *artLabel = new QLabel(cardWidget);
            artLabel->setFixedSize(138, 138);
            artLabel->setAlignment(Qt::AlignCenter);
            artLabel->setStyleSheet("background-color: #222; border-radius: 4px;");
            if (!vItem.thumbnailUrl.isEmpty()) {
                ImageLoader::instance()->loadImage(vItem.thumbnailUrl, artLabel, QSize(138, 138));
            }
            cardLayout->addWidget(artLabel);

            QPushButton *btnPlay = new QPushButton(vItem.title + "\n" + vItem.uploader, cardWidget);
            btnPlay->setStyleSheet("background: transparent; border: none; color: white; text-align: left; font-size: 11px;");
            btnPlay->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);

            connect(btnPlay, &QPushButton::clicked, this, [this, vItem]() {
                player->playSong(vItem);
                db->addToHistory({vItem.id, vItem.title, vItem.uploader, vItem.duration, vItem.thumbnailUrl});
            });
            cardLayout->addWidget(btnPlay);

            QHBoxLayout *bottomLayout = new QHBoxLayout();
            bottomLayout->setContentsMargins(0, 0, 0, 0);
            bottomLayout->addStretch();
            QPushButton *btnDownload = new QPushButton("⭳", cardWidget);
            btnDownload->setFixedSize(25, 25);
            btnDownload->setStyleSheet("QPushButton { background-color: #333; border-radius: 12px; color: #00a6ff; font-weight: bold; border: none; }"
                                       "QPushButton:hover { background-color: #555; }");
            QLabel *lblProgress = new QLabel("", cardWidget);
            lblProgress->setStyleSheet("color: #aaa; font-size: 10px; border: none;");
            lblProgress->hide();
            bottomLayout->addWidget(lblProgress);
            bottomLayout->addWidget(btnDownload);

            if (ytDlp->isSongDownloaded(vItem.id)) { btnDownload->hide(); lblProgress->setText("Downloaded"); lblProgress->show(); }
            connect(btnDownload, &QPushButton::clicked, this, [this, vItem, btnDownload, lblProgress]() {
                btnDownload->setEnabled(false); lblProgress->setText("Starting..."); lblProgress->show();
                ytDlp->downloadSong(vItem.id, vItem.title);
            });
            connect(ytDlp, &YtDlpManager::downloadProgress, cardWidget, [cardWidget, vItem, lblProgress](const QString& id, int percent) {
                if (id == vItem.id) lblProgress->setText(QString::number(percent) + "%");
            });
            connect(ytDlp, &YtDlpManager::downloadFinished, cardWidget, [this, cardWidget, vItem, btnDownload, lblProgress](const QString& id, const QString&) {
                if (id == vItem.id) {
                    btnDownload->hide();
                    lblProgress->setText("Downloaded");
                    db->insertLibrarySong({vItem.id, vItem.title, vItem.uploader, vItem.duration, vItem.thumbnailUrl});
                }
            });

            cardLayout->addLayout(bottomLayout);

            madeForYouHomeLayout->addWidget(cardWidget, row, col);

            col++;
            if (col >= 5) {
                col = 0;
                row++;
            }
        }

        recClient->deleteLater();
    });

    recClient->search(query);
}

void MainWindow::resizeEvent(QResizeEvent *event) {
    QMainWindow::resizeEvent(event);
    if (fullScreenPlayer) {
        fullScreenPlayer->resize(this->size());
    }
}

void MainWindow::createSidebar() {
    sidebar = new QWidget(this);
    sidebar->setFixedWidth(200);
    sidebar->setStyleSheet("background-color: #121212; border-right: 1px solid #333;");

    QVBoxLayout *sidebarLayout = new QVBoxLayout(sidebar);

    QLabel *logoLabel = new QLabel("DTECH MUSIC\n// PREASX24", this);
    logoLabel->setStyleSheet("color: #00a6ff; font-weight: 900; font-size: 18px; padding: 30px 0 20px 0; border: none; letter-spacing: 2px;");
    logoLabel->setAlignment(Qt::AlignCenter);

    btnHome = new QPushButton("Home", this);
    btnSearch = new QPushButton("Search", this);
    btnLibrary = new QPushButton("Library", this);
    btnSettings = new QPushButton("Settings", this);

    QString btnStyle = "QPushButton { text-align: left; padding: 12px 20px; font-size: 15px; font-weight: 500; border: none; background: transparent; border-radius: 8px; margin: 2px 10px; } "
                       "QPushButton:hover { color: #00a6ff; background-color: #1a1a1a; }";

    btnHome->setStyleSheet(btnStyle);
    btnSearch->setStyleSheet(btnStyle);
    btnLibrary->setStyleSheet(btnStyle);
    btnSettings->setStyleSheet(btnStyle);

    sidebarLayout->addWidget(logoLabel);
    sidebarLayout->addWidget(btnHome);
    sidebarLayout->addWidget(btnSearch);
    sidebarLayout->addWidget(btnLibrary);
    sidebarLayout->addStretch();
    sidebarLayout->addWidget(btnSettings);

    connect(btnHome, &QPushButton::clicked, this, &MainWindow::navigateToHome);
    connect(btnSearch, &QPushButton::clicked, this, &MainWindow::navigateToSearch);
    connect(btnLibrary, &QPushButton::clicked, this, &MainWindow::navigateToLibrary);
    connect(btnSettings, &QPushButton::clicked, this, &MainWindow::navigateToSettings);
}

void MainWindow::createPlayerBar() {
    playerBar = new QWidget(this);
    playerBar->setFixedHeight(80);
    playerBar->setStyleSheet("background-color: #1a1a1a; border-top: 1px solid #333;");

    QHBoxLayout *layout = new QHBoxLayout(playerBar);

    lblPlayerArt = new QLabel(this);
    lblPlayerArt->setFixedSize(60, 60);
    lblPlayerArt->setStyleSheet("background-color: #222; border-radius: 4px; border: 1px solid #333;");
    lblPlayerArt->setAlignment(Qt::AlignCenter);

    lblCurrentSong = new QLabel("No Song Playing", this);
    lblCurrentSong->setFixedWidth(250);

    btnPrev = new QPushButton("|<", this);
    btnPlayPause = new QPushButton("Play", this);
    btnNext = new QPushButton(">|", this);

    btnPrev->setFixedSize(40, 40);
    btnPlayPause->setFixedSize(50, 50);
    btnNext->setFixedSize(40, 40);

    QString playerBtnStyle = "QPushButton { background: transparent; border: none; font-size: 16px; font-weight: bold; border-radius: 20px; }"
                             "QPushButton:hover { background-color: #333; color: #00a6ff; }";
    btnPrev->setStyleSheet(playerBtnStyle);
    btnNext->setStyleSheet(playerBtnStyle);
    btnPlayPause->setStyleSheet("QPushButton { background-color: #00a6ff; color: #000; border-radius: 25px; font-weight: bold; font-size: 16px; }"
                                "QPushButton:hover { background-color: #33b5e5; }");

    progressSlider = new QSlider(Qt::Horizontal, this);
    progressSlider->setRange(0, 100);
    lblTime = new QLabel("0:00 / 0:00", this);

    layout->addWidget(lblPlayerArt);
    layout->addSpacing(10);
    layout->addWidget(lblCurrentSong);
    layout->addStretch(1);
    layout->addWidget(btnPrev);
    layout->addWidget(btnPlayPause);
    layout->addWidget(btnNext);
    layout->addStretch(1);
    layout->addWidget(progressSlider);
    layout->addWidget(lblTime);

    connect(btnPlayPause, &QPushButton::clicked, this, &MainWindow::onPlayPauseClicked);
    connect(btnNext, &QPushButton::clicked, this, &MainWindow::onNextClicked);
    connect(btnPrev, &QPushButton::clicked, this, &MainWindow::onPrevClicked);

    connect(progressSlider, &QSlider::sliderMoved, this, [this](int position) {
        player->seek(position * player->duration() / 100);
    });

    // Make playerBar clickable to open full screen player
    playerBar->setCursor(Qt::PointingHandCursor);
    playerBar->installEventFilter(this);
}

bool MainWindow::eventFilter(QObject *watched, QEvent *event) {
    if (watched == playerBar && event->type() == QEvent::MouseButtonRelease) {
        fullScreenPlayer->show();
        fullScreenPlayer->raise();
        return true;
    }
    return QMainWindow::eventFilter(watched, event);
}

QWidget* MainWindow::createHomeScreen() {
    QWidget *widget = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(widget);

    QScrollArea *mainScroll = new QScrollArea(this);
    mainScroll->setWidgetResizable(true);
    QWidget *scrollContent = new QWidget(mainScroll);
    QVBoxLayout *scrollLayout = new QVBoxLayout(scrollContent);
    scrollLayout->setSpacing(20);

    // -- Recently Played Carousel --
    QLabel *lblRecent = new QLabel("Recently Played", this);
    lblRecent->setStyleSheet("font-size: 20px; font-weight: bold; color: white;");
    scrollLayout->addWidget(lblRecent);

    QScrollArea *recentArea = new QScrollArea(this);
    recentArea->setFixedHeight(220);
    recentArea->setWidgetResizable(true);
    QWidget *recentContent = new QWidget(recentArea);
    QHBoxLayout *recentLayout = new QHBoxLayout(recentContent);
    recentLayout->setAlignment(Qt::AlignLeft);

    QList<DbSong> history = db->getPlayHistory();
    for (int i = 0; i < qMin(10, static_cast<int>(history.size())); ++i) {
        QWidget *cardWidget = new QWidget(this);
        cardWidget->setFixedSize(150, 200);
        QVBoxLayout *cardLayout = new QVBoxLayout(cardWidget);
        cardLayout->setContentsMargins(5, 5, 5, 5);
        cardLayout->setSpacing(5);
        cardWidget->setStyleSheet("QWidget { background-color: #1a1a1a; border-radius: 12px; border: 1px solid #2a2a2a; } "
                                  "QWidget:hover { border-color: #00a6ff; background-color: #222; }");

        QLabel *artLabel = new QLabel(cardWidget);
        artLabel->setFixedSize(138, 138);
        artLabel->setAlignment(Qt::AlignCenter);
        artLabel->setStyleSheet("background-color: #222; border-radius: 4px;");
        if (!history[i].thumbnailUrl.isEmpty()) {
            ImageLoader::instance()->loadImage(history[i].thumbnailUrl, artLabel, QSize(138, 138));
        }
        cardLayout->addWidget(artLabel);

        QPushButton *btnPlay = new QPushButton(history[i].title + "\n" + history[i].uploader, cardWidget);
        btnPlay->setStyleSheet("background: transparent; border: none; color: white; text-align: left; font-size: 11px;");
        btnPlay->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);

        connect(btnPlay, &QPushButton::clicked, this, [this, history, i]() {
            VideoItem item;
            item.id = history[i].id;
            item.title = history[i].title;
            item.uploader = history[i].uploader;
            item.duration = history[i].duration;
            item.thumbnailUrl = history[i].thumbnailUrl;
            player->playSong(item);
            db->addToHistory(history[i]);
        });

        QHBoxLayout *bottomLayout = new QHBoxLayout();
        bottomLayout->setContentsMargins(0, 0, 0, 0);
        bottomLayout->addStretch();

        QPushButton *btnDownload = new QPushButton("⭳", cardWidget);
        btnDownload->setFixedSize(25, 25);
        btnDownload->setStyleSheet("QPushButton { background-color: #333; border-radius: 12px; color: #00a6ff; font-weight: bold; border: none; }"
                                   "QPushButton:hover { background-color: #555; }");

        QLabel *lblProgress = new QLabel("", cardWidget);
        lblProgress->setStyleSheet("color: #aaa; font-size: 10px; border: none;");
        lblProgress->hide();

        bottomLayout->addWidget(lblProgress);
        bottomLayout->addWidget(btnDownload);

        QString vId = history[i].id;
        QString vTitle = history[i].title;

        if (ytDlp->isSongDownloaded(vId)) {
            btnDownload->hide();
            lblProgress->setText("Downloaded");
            lblProgress->show();
        }

        connect(btnDownload, &QPushButton::clicked, this, [this, vId, vTitle, btnDownload, lblProgress]() {
            btnDownload->setEnabled(false);
            lblProgress->setText("Starting...");
            lblProgress->show();
            ytDlp->downloadSong(vId, vTitle);
        });

        connect(ytDlp, &YtDlpManager::downloadProgress, cardWidget, [cardWidget, vId, lblProgress](const QString& id, int percent) {
            if (id == vId) lblProgress->setText(QString::number(percent) + "%");
        });
        connect(ytDlp, &YtDlpManager::downloadFinished, cardWidget, [this, cardWidget, vId, history, i, btnDownload, lblProgress](const QString& id, const QString&) {
            if (id == vId) {
                btnDownload->hide();
                lblProgress->setText("Downloaded");
                DbSong dbSong = history[i];
                db->insertLibrarySong({dbSong.id, dbSong.title, dbSong.uploader, dbSong.duration, dbSong.thumbnailUrl});
            }
        });

        cardLayout->addWidget(btnPlay);
        cardLayout->addLayout(bottomLayout);

        recentLayout->addWidget(cardWidget);
    }
    recentArea->setWidget(recentContent);
    scrollLayout->addWidget(recentArea);

    // -- Trending Carousel --
    QLabel *lblTrending = new QLabel("Trending", this);
    lblTrending->setStyleSheet("font-size: 20px; font-weight: bold; color: white;");
    scrollLayout->addWidget(lblTrending);

    QScrollArea *trendingArea = new QScrollArea(this);
    trendingArea->setFixedHeight(220);
    trendingArea->setWidgetResizable(true);
    QWidget *trendingContent = new QWidget(trendingArea);
    trendingHomeLayout = new QHBoxLayout(trendingContent);
    trendingHomeLayout->setAlignment(Qt::AlignLeft);

    QLabel *lblTrendingLoad = new QLabel("Loading recommendations...", trendingContent);
    lblTrendingLoad->setStyleSheet("color: #aaa;");
    trendingHomeLayout->addWidget(lblTrendingLoad);

    trendingArea->setWidget(trendingContent);
    scrollLayout->addWidget(trendingArea);

    // -- Made For You (5x10 Grid) --
    QLabel *lblMadeForYou = new QLabel("Made For You", this);
    lblMadeForYou->setStyleSheet("font-size: 20px; font-weight: bold; color: white;");
    scrollLayout->addWidget(lblMadeForYou);

    QWidget *gridWidget = new QWidget(this);
    madeForYouHomeLayout = new QGridLayout(gridWidget);
    madeForYouHomeLayout->setSpacing(15);

    QLabel *lblMadeForYouLoad = new QLabel("Loading recommendations...", gridWidget);
    lblMadeForYouLoad->setStyleSheet("color: #aaa;");
    madeForYouHomeLayout->addWidget(lblMadeForYouLoad, 0, 0);

    scrollLayout->addWidget(gridWidget);

    mainScroll->setWidget(scrollContent);
    layout->addWidget(mainScroll);

    return widget;
}

QWidget* MainWindow::createFullScreenPlayer() {
    QWidget *widget = new QWidget(this);
    widget->setStyleSheet("background-color: #0d0d0d;");

    QVBoxLayout *layout = new QVBoxLayout(widget);
    layout->setContentsMargins(50, 20, 50, 50);

    // Top Bar with Close button
    QHBoxLayout *topLayout = new QHBoxLayout();
    topLayout->addStretch();
    fsBtnClose = new QPushButton("v", widget);
    fsBtnClose->setFixedSize(40, 40);
    fsBtnClose->setStyleSheet("QPushButton { background-color: transparent; border: none; font-size: 24px; color: #fff; font-weight: bold; }"
                              "QPushButton:hover { color: #00a6ff; }");
    topLayout->addWidget(fsBtnClose);
    layout->addLayout(topLayout);

    // Album Art
    fsLblAlbumArt = new QLabel(widget);
    fsLblAlbumArt->setFixedSize(400, 400);
    fsLblAlbumArt->setStyleSheet("background-color: #222; border: 2px solid #333; border-radius: 10px;");
    fsLblAlbumArt->setAlignment(Qt::AlignCenter);
    fsLblAlbumArt->setText("ALBUM\nART");

    QHBoxLayout *artLayout = new QHBoxLayout();
    artLayout->addStretch();
    artLayout->addWidget(fsLblAlbumArt);
    artLayout->addStretch();
    layout->addLayout(artLayout);
    layout->addSpacing(30);

    // Info and Actions
    QHBoxLayout *infoLayout = new QHBoxLayout();

    QVBoxLayout *textLayout = new QVBoxLayout();
    fsLblTitle = new QLabel("Song Title", widget);
    fsLblTitle->setStyleSheet("font-size: 28px; font-weight: bold; color: white;");
    fsLblArtist = new QLabel("Artist Name", widget);
    fsLblArtist->setStyleSheet("font-size: 18px; color: #00a6ff;");
    textLayout->addWidget(fsLblTitle);
    textLayout->addWidget(fsLblArtist);

    infoLayout->addLayout(textLayout);
    infoLayout->addStretch();

    fsBtnLike = new QPushButton("♡", widget);
    fsBtnLike->setFixedSize(40, 40);
    fsBtnLike->setStyleSheet("QPushButton { font-size: 24px; color: white; background: transparent; border: none; }"
                             "QPushButton:hover { color: #00a6ff; }");

    fsBtnAddLibrary = new QPushButton("+", widget);
    fsBtnAddLibrary->setFixedSize(40, 40);
    fsBtnAddLibrary->setStyleSheet("QPushButton { font-size: 30px; color: white; background: transparent; border: none; }"
                                   "QPushButton:hover { color: #00a6ff; }");

    infoLayout->addWidget(fsBtnLike);
    infoLayout->addWidget(fsBtnAddLibrary);

    layout->addLayout(infoLayout);
    layout->addSpacing(20);

    // Progress
    QHBoxLayout *progLayout = new QHBoxLayout();
    fsLblTime = new QLabel("0:00 / 0:00", widget);
    fsLblTime->setStyleSheet("color: #aaa;");
    fsProgressSlider = new QSlider(Qt::Horizontal, widget);
    fsProgressSlider->setRange(0, 100);

    progLayout->addWidget(fsProgressSlider);
    progLayout->addWidget(fsLblTime);
    layout->addLayout(progLayout);
    layout->addSpacing(20);

    // Controls
    QHBoxLayout *controlsLayout = new QHBoxLayout();
    fsBtnPrev = new QPushButton("|<", widget);
    fsBtnPlayPause = new QPushButton("Play", widget);
    fsBtnNext = new QPushButton(">|", widget);

    fsBtnPrev->setFixedSize(60, 60);
    fsBtnPlayPause->setFixedSize(80, 80);
    fsBtnNext->setFixedSize(60, 60);

    QString ctlStyle = "QPushButton { border-radius: 30px; background-color: #222; color: white; font-size: 18px; }"
                       "QPushButton:hover { background-color: #333; }";
    fsBtnPrev->setStyleSheet(ctlStyle);
    fsBtnPlayPause->setStyleSheet("QPushButton { border-radius: 40px; background-color: #00a6ff; color: black; font-size: 20px; font-weight: bold; }"
                                  "QPushButton:hover { background-color: #0088cc; }");
    fsBtnNext->setStyleSheet(ctlStyle);

    controlsLayout->addStretch();
    controlsLayout->addWidget(fsBtnPrev);
    controlsLayout->addSpacing(20);
    controlsLayout->addWidget(fsBtnPlayPause);
    controlsLayout->addSpacing(20);
    controlsLayout->addWidget(fsBtnNext);
    controlsLayout->addStretch();

    layout->addLayout(controlsLayout);
    layout->addStretch();

    // Connections
    connect(fsBtnClose, &QPushButton::clicked, widget, &QWidget::hide);
    connect(fsBtnPlayPause, &QPushButton::clicked, this, &MainWindow::onPlayPauseClicked);
    connect(fsBtnNext, &QPushButton::clicked, this, &MainWindow::onNextClicked);
    connect(fsBtnPrev, &QPushButton::clicked, this, &MainWindow::onPrevClicked);

    connect(fsProgressSlider, &QSlider::sliderMoved, this, [this](int position) {
        player->seek(position * player->duration() / 100);
    });

    connect(fsBtnAddLibrary, &QPushButton::clicked, this, [this]() {
        VideoItem song = player->currentSong();
        if (!song.id.isEmpty()) {
            db->insertLibrarySong({song.id, song.title, song.uploader, song.duration, song.thumbnailUrl});
            QMessageBox::information(this, "Library", "Added to library!");
        }
    });

    connect(fsBtnLike, &QPushButton::clicked, this, [this]() {
        // Mock liked songs adding (if we had a table for it)
        QMessageBox::information(this, "Liked Songs", "Added to Liked Songs!");
    });

    return widget;
}

QWidget* MainWindow::createSearchScreen() {
    QWidget *widget = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(widget);

    QHBoxLayout *searchLayout = new QHBoxLayout();
    searchInput = new QLineEdit(this);
    searchInput->setPlaceholderText("Search songs, artists, albums...");
    searchInput->setStyleSheet("padding: 10px; background-color: #222; border: 1px solid #444; border-radius: 20px; color: white;");

    QPushButton *btnSubmit = new QPushButton("Search", this);
    btnSubmit->setStyleSheet("background-color: #00a6ff; color: black; border-radius: 20px; padding: 10px 20px; font-weight: bold;");

    searchLayout->addWidget(searchInput);
    searchLayout->addWidget(btnSubmit);

    searchResultsList = new QListWidget(this);
    searchResultsList->setStyleSheet("QListWidget { background-color: transparent; border: none; outline: none; } "
                                     "QListWidget::item { padding: 5px; border-bottom: 1px solid #2a2a2a; border-radius: 8px; margin: 4px; } "
                                     "QListWidget::item:hover { background-color: #1a1a1a; border: 1px solid #00a6ff; } "
                                     "QListWidget::item:selected { background-color: #222; }");

    layout->addLayout(searchLayout);
    layout->addWidget(searchResultsList);

    connect(btnSubmit, &QPushButton::clicked, this, &MainWindow::performSearch);
    connect(searchInput, &QLineEdit::returnPressed, this, &MainWindow::performSearch);

    return widget;
}

QWidget* MainWindow::createLibraryScreen() {
    QWidget *widget = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(widget);

    QLabel *lbl = new QLabel("Your Library", this);
    lbl->setStyleSheet("font-size: 24px; font-weight: bold; color: white;");
    layout->addWidget(lbl);

    QHBoxLayout *tabsLayout = new QHBoxLayout();
    btnAllSongs = new QPushButton("All Songs", this);
    btnPlaylists = new QPushButton("Playlists", this);
    btnArtists = new QPushButton("Artists", this);

    QString tabStyle = "QPushButton { background-color: #222; color: white; border-radius: 15px; padding: 8px 15px; } "
                       "QPushButton:hover { background-color: #333; }";
    btnAllSongs->setStyleSheet(tabStyle);
    btnPlaylists->setStyleSheet(tabStyle);
    btnArtists->setStyleSheet(tabStyle);

    tabsLayout->addWidget(btnAllSongs);
    tabsLayout->addWidget(btnPlaylists);
    tabsLayout->addWidget(btnArtists);
    tabsLayout->addStretch();

    layout->addLayout(tabsLayout);

    libraryList = new QListWidget(this);
    libraryList->setStyleSheet("QListWidget { background-color: transparent; border: none; outline: none; } "
                               "QListWidget::item { padding: 5px; border-bottom: 1px solid #2a2a2a; border-radius: 8px; margin: 4px; } "
                               "QListWidget::item:hover { background-color: #1a1a1a; border: 1px solid #00a6ff; } "
                               "QListWidget::item:selected { background-color: #222; }");
    layout->addWidget(libraryList);

    // Initial load
    auto loadLibrary = [this]() {
        libraryList->clear();
        QList<DbSong> songs = db->getLibrarySongs();
        if (songs.isEmpty()) {
            libraryList->addItem("Your library is empty. Search for songs to add them or download them.");
        } else {
            for (const auto& s : songs) {
                VideoItem vi;
                vi.id = s.id;
                vi.title = s.title;
                vi.uploader = s.uploader;
                vi.duration = s.duration;
                vi.thumbnailUrl = s.thumbnailUrl;

                QListWidgetItem* listItem = new QListWidgetItem(libraryList);
                listItem->setSizeHint(QSize(0, 60));
                QWidget* widget = createSongItemWidget(vi);
                libraryList->addItem(listItem);
                libraryList->setItemWidget(listItem, widget);
            }
        }
    };

    connect(btnAllSongs, &QPushButton::clicked, this, loadLibrary);
    connect(btnPlaylists, &QPushButton::clicked, this, [this]() {
        libraryList->clear();
        libraryList->addItem("Playlists (Not Implemented)");
    });
    connect(btnArtists, &QPushButton::clicked, this, [this]() {
        libraryList->clear();
        libraryList->addItem("Artists (Not Implemented)");
    });

    // We'll hook into search results context menu or double click to save to library.
    // But for now, just load whatever is there.
    loadLibrary();

    return widget;
}

QWidget* MainWindow::createSettingsScreen() {
    QWidget *widget = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(widget);

    QLabel *lbl = new QLabel("Settings", this);
    lbl->setStyleSheet("font-size: 24px; font-weight: bold; color: white;");
    layout->addWidget(lbl);

    QSettings settings("DTECH", "Music");

    // Audio Quality (Mocked preference)
    QLabel *lblQuality = new QLabel("Audio Quality", this);
    lblQuality->setStyleSheet("font-size: 16px; color: #aaa; margin-top: 20px;");
    layout->addWidget(lblQuality);

    QPushButton *btnHighEndMode = new QPushButton("High-End Mode (Toggle)", this);
    btnHighEndMode->setCheckable(true);
    btnHighEndMode->setChecked(settings.value("HighEndMode", false).toBool());
    btnHighEndMode->setStyleSheet("QPushButton { background-color: #222; padding: 10px; border-radius: 5px; text-align: left; } "
                                  "QPushButton:checked { border: 1px solid #00a6ff; }");
    layout->addWidget(btnHighEndMode);

    connect(btnHighEndMode, &QPushButton::toggled, this, [](bool checked) {
        QSettings s("DTECH", "Music");
        s.setValue("HighEndMode", checked);
    });

    // Yt-dlp Updates
    QLabel *lblYtdlp = new QLabel("System Components", this);
    lblYtdlp->setStyleSheet("font-size: 16px; color: #aaa; margin-top: 20px;");
    layout->addWidget(lblYtdlp);

    QPushButton *btnUpdateYtdlp = new QPushButton("Update yt-dlp", this);
    btnUpdateYtdlp->setStyleSheet("QPushButton { background-color: #222; padding: 10px; border-radius: 5px; text-align: left; } "
                                  "QPushButton:hover { background-color: #333; }");
    layout->addWidget(btnUpdateYtdlp);

    connect(btnUpdateYtdlp, &QPushButton::clicked, this, [this, btnUpdateYtdlp]() {
        btnUpdateYtdlp->setEnabled(false);
        btnUpdateYtdlp->setText("Updating...");
        ytDlp->updateYtDlp();
    });

    connect(ytDlp, &YtDlpManager::updateFinished, this, [this, btnUpdateYtdlp](bool success, const QString& msg) {
        btnUpdateYtdlp->setEnabled(true);
        btnUpdateYtdlp->setText("Update yt-dlp");
        if (success) QMessageBox::information(this, "Update", msg);
        else QMessageBox::warning(this, "Update Failed", msg);
    });

    // Clear History
    QLabel *lblData = new QLabel("Data & Storage", this);
    lblData->setStyleSheet("font-size: 16px; color: #aaa; margin-top: 20px;");
    layout->addWidget(lblData);

    QPushButton *btnClearHistory = new QPushButton("Clear Play History", this);
    btnClearHistory->setStyleSheet("QPushButton { background-color: #311; color: #f55; padding: 10px; border-radius: 5px; border: 1px solid #511; text-align: left; } "
                                   "QPushButton:hover { background-color: #511; }");
    layout->addWidget(btnClearHistory);

    connect(btnClearHistory, &QPushButton::clicked, this, [this]() {
        QMessageBox::StandardButton reply = QMessageBox::question(this, "Clear History", "Are you sure you want to clear all play history?", QMessageBox::Yes|QMessageBox::No);
        if (reply == QMessageBox::Yes) {
            // Drop and recreate table via DB manager or just execute DELETE
            QSqlQuery q("DELETE FROM play_history");
            q.exec();
            QMessageBox::information(this, "Success", "Play history cleared.");
        }
    });

    // Developer Settings
    QLabel *lblDev = new QLabel("Developer Settings", this);
    lblDev->setStyleSheet("font-size: 16px; color: #aaa; margin-top: 20px;");
    layout->addWidget(lblDev);

    QPushButton *btnShowDebug = new QPushButton("Show Debug Console", this);
    btnShowDebug->setStyleSheet("QPushButton { background-color: #222; padding: 10px; border-radius: 5px; text-align: left; } "
                                "QPushButton:hover { background-color: #333; }");
    layout->addWidget(btnShowDebug);

    connect(btnShowDebug, &QPushButton::clicked, this, [this]() {
        DebugWindow::instance()->show();
        DebugWindow::instance()->raise();
        DebugWindow::instance()->activateWindow();
    });

    layout->addStretch();
    return widget;
}

void MainWindow::navigateToHome() { stackedWidget->setCurrentIndex(0); }
void MainWindow::navigateToSearch() { stackedWidget->setCurrentIndex(1); }
void MainWindow::navigateToLibrary() { stackedWidget->setCurrentIndex(2); }
void MainWindow::navigateToSettings() { stackedWidget->setCurrentIndex(3); }

void MainWindow::onPlayPauseClicked() {
    if (player->isPlaying()) {
        player->pause();
        btnPlayPause->setText("Play");
    } else {
        player->resume();
        btnPlayPause->setText("Pause");
    }
}

void MainWindow::onNextClicked() {
    player->next();
}

void MainWindow::onPrevClicked() {
    player->previous();
}

void MainWindow::performSearch() {
    QString query = searchInput->text().trimmed();
    if (!query.isEmpty()) {
        searchResultsList->clear();
        searchResultsList->addItem("Searching...");
        innerTube->search(query);
    }
}

void MainWindow::updatePlayerUI() {
    bool isPlaying = player->isPlaying();
    btnPlayPause->setText(isPlaying ? "Pause" : "Play");

    if (fullScreenPlayer && fullScreenPlayer->isVisible()) {
        fsBtnPlayPause->setText(isPlaying ? "Pause" : "Play");
    }

    VideoItem current = player->currentSong();
    if (!current.title.isEmpty()) {
        lblCurrentSong->setText(current.title + "\n" + current.uploader);

        if (!current.thumbnailUrl.isEmpty()) {
            ImageLoader::instance()->loadImage(current.thumbnailUrl, lblPlayerArt, QSize(60, 60));
        } else {
            lblPlayerArt->clear();
        }

        if (fullScreenPlayer && fullScreenPlayer->isVisible()) {
            fsLblTitle->setText(current.title);
            fsLblArtist->setText(current.uploader);
            if (!current.thumbnailUrl.isEmpty()) {
                ImageLoader::instance()->loadImage(current.thumbnailUrl, fsLblAlbumArt, QSize(400, 400));
            } else {
                fsLblAlbumArt->setText("ALBUM\nART");
            }
        }
    }

    qint64 pos = player->position();
    qint64 dur = player->duration();

    if (dur > 0) {
        int sliderVal = pos * 100 / dur;
        if (!progressSlider->isSliderDown()) {
            progressSlider->setValue(sliderVal);
        }
        if (fullScreenPlayer && fullScreenPlayer->isVisible() && !fsProgressSlider->isSliderDown()) {
            fsProgressSlider->setValue(sliderVal);
        }
    }

    QString posStr = QString("%1:%2").arg(pos / 60000).arg((pos / 1000) % 60, 2, 10, QChar('0'));
    QString durStr = QString("%1:%2").arg(dur / 60000).arg((dur / 1000) % 60, 2, 10, QChar('0'));
    QString timeStr = posStr + " / " + durStr;

    lblTime->setText(timeStr);

    if (fullScreenPlayer && fullScreenPlayer->isVisible()) {
        fsLblTime->setText(timeStr);
    }
}
