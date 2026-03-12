#include "MainWindow.h"
#include <QVBoxLayout>
#include <QGridLayout>
#include <QScrollArea>
#include <QTimer>
#include <QDebug>
#include <QSettings>
#include <QMessageBox>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
      player(new MusicPlayer(this)),
      db(new DatabaseManager("music.db")),
      innerTube(new InnerTubeClient(this))
{
    setupUI();

    // Timer for updating player progress UI
    QTimer *timer = new QTimer(this);
    connect(timer, &QTimer::timeout, this, &MainWindow::updatePlayerUI);
    timer->start(1000);

    connect(innerTube, &InnerTubeClient::searchFinished, this, [this](const QList<VideoItem>& results) {
        searchResultsList->clear();
        for (const auto& item : results) {
            QListWidgetItem* listItem = new QListWidgetItem(item.title + " - " + item.uploader);
            listItem->setData(Qt::UserRole, item.id);
            searchResultsList->addItem(listItem);
        }
    });

    // Play selected search result
    connect(searchResultsList, &QListWidget::itemDoubleClicked, this, [this](QListWidgetItem *item) {
        QString videoId = item->data(Qt::UserRole).toString();

        // Find the full VideoItem from list (we just mocked the full object search here)
        VideoItem song;
        song.id = videoId;
        song.title = item->text().split(" - ").first();
        song.uploader = item->text().split(" - ").last();

        player->playSong(song);
        db->addToHistory({song.id, song.title, song.uploader, song.duration, song.thumbnailUrl});

        // Automatically add played songs to Library for demo/testing since we don't have a context menu
        db->insertLibrarySong({song.id, song.title, song.uploader, song.duration, song.thumbnailUrl});
    });
}

MainWindow::~MainWindow() {
    delete db;
}

void MainWindow::setupUI() {
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
}

void MainWindow::createSidebar() {
    sidebar = new QWidget(this);
    sidebar->setFixedWidth(200);
    sidebar->setStyleSheet("background-color: #121212; border-right: 1px solid #333;");

    QVBoxLayout *sidebarLayout = new QVBoxLayout(sidebar);

    QLabel *logoLabel = new QLabel("DTECH MUSIC", this);
    logoLabel->setStyleSheet("color: #00a6ff; font-weight: bold; font-size: 18px; padding: 20px 0; border: none;");
    logoLabel->setAlignment(Qt::AlignCenter);

    btnHome = new QPushButton("Home", this);
    btnSearch = new QPushButton("Search", this);
    btnLibrary = new QPushButton("Library", this);
    btnSettings = new QPushButton("Settings", this);

    QString btnStyle = "QPushButton { text-align: left; padding: 10px 20px; font-size: 14px; border: none; background: transparent; } "
                       "QPushButton:hover { color: #00a6ff; background-color: #1e1e1e; }";

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

    lblCurrentSong = new QLabel("No Song Playing", this);
    lblCurrentSong->setFixedWidth(250);

    btnPrev = new QPushButton("|<", this);
    btnPlayPause = new QPushButton("Play", this);
    btnNext = new QPushButton(">|", this);

    btnPrev->setFixedSize(40, 40);
    btnPlayPause->setFixedSize(50, 50);
    btnNext->setFixedSize(40, 40);

    progressSlider = new QSlider(Qt::Horizontal, this);
    progressSlider->setRange(0, 100);
    lblTime = new QLabel("0:00 / 0:00", this);

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
        QPushButton *card = new QPushButton(history[i].title + "\n" + history[i].uploader, this);
        card->setFixedSize(150, 180);
        card->setStyleSheet("QPushButton { background-color: #1e1e1e; color: white; border-radius: 8px; border: 1px solid #333; text-align: bottom; padding-bottom: 10px; }"
                            "QPushButton:hover { border-color: #00a6ff; }");

        connect(card, &QPushButton::clicked, this, [this, history, i]() {
            VideoItem item;
            item.id = history[i].id;
            item.title = history[i].title;
            item.uploader = history[i].uploader;
            item.duration = history[i].duration;
            item.thumbnailUrl = history[i].thumbnailUrl;
            player->playSong(item);
            db->addToHistory(history[i]);
        });
        recentLayout->addWidget(card);
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
    QHBoxLayout *trendingLayout = new QHBoxLayout(trendingContent);
    trendingLayout->setAlignment(Qt::AlignLeft);

    for (int i = 0; i < 5; ++i) {
        QPushButton *card = new QPushButton(QString("Trending %1").arg(i+1), this);
        card->setFixedSize(150, 180);
        card->setStyleSheet("background-color: #1e1e1e; border-radius: 8px; border: 1px solid #333;");
        trendingLayout->addWidget(card);
    }
    trendingArea->setWidget(trendingContent);
    scrollLayout->addWidget(trendingArea);

    // -- Made For You (5x10 Grid) --
    QLabel *lblMadeForYou = new QLabel("Made For You", this);
    lblMadeForYou->setStyleSheet("font-size: 20px; font-weight: bold; color: white;");
    scrollLayout->addWidget(lblMadeForYou);

    QWidget *gridWidget = new QWidget(this);
    QGridLayout *gridLayout = new QGridLayout(gridWidget);
    gridLayout->setSpacing(15);

    // Simulate 50 items
    for (int r = 0; r < 10; ++r) {
        for (int c = 0; c < 5; ++c) {
            QPushButton *card = new QPushButton(QString("Track %1").arg(r*5 + c + 1), this);
            card->setFixedSize(160, 200);
            card->setStyleSheet("QPushButton { background-color: #1e1e1e; color: white; border-radius: 8px; border: 1px solid #333; }"
                                "QPushButton:hover { border-color: #00a6ff; background-color: #222; }");
            gridLayout->addWidget(card, r, c);
        }
    }
    scrollLayout->addWidget(gridWidget);

    mainScroll->setWidget(scrollContent);
    layout->addWidget(mainScroll);

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
    searchResultsList->setStyleSheet("QListWidget { background-color: transparent; border: none; } QListWidget::item { padding: 15px; border-bottom: 1px solid #333; } QListWidget::item:hover { background-color: #222; }");

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
    libraryList->setStyleSheet("QListWidget { background-color: transparent; border: none; } "
                               "QListWidget::item { padding: 15px; border-bottom: 1px solid #333; } "
                               "QListWidget::item:hover { background-color: #222; }");
    layout->addWidget(libraryList);

    // Initial load
    auto loadLibrary = [this]() {
        libraryList->clear();
        QList<DbSong> songs = db->getLibrarySongs();
        if (songs.isEmpty()) {
            libraryList->addItem("Your library is empty. Search for songs to add them.");
        } else {
            for (const auto& s : songs) {
                QListWidgetItem* item = new QListWidgetItem(s.title + "\n" + s.uploader);
                item->setData(Qt::UserRole, s.id);
                libraryList->addItem(item);
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

    connect(libraryList, &QListWidget::itemDoubleClicked, this, [this](QListWidgetItem *item) {
        QString id = item->data(Qt::UserRole).toString();
        if (!id.isEmpty()) {
            VideoItem vi;
            vi.id = id;
            vi.title = item->text().split("\n").first();
            vi.uploader = item->text().split("\n").last();
            player->playSong(vi);
            db->addToHistory({vi.id, vi.title, vi.uploader, "", ""});
        }
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
    if (player->isPlaying()) {
        btnPlayPause->setText("Pause");
    } else {
        btnPlayPause->setText("Play");
    }

    VideoItem current = player->currentSong();
    if (!current.title.isEmpty()) {
        lblCurrentSong->setText(current.title + "\n" + current.uploader);
    }

    qint64 pos = player->position();
    qint64 dur = player->duration();

    if (dur > 0 && !progressSlider->isSliderDown()) {
        progressSlider->setValue(pos * 100 / dur);
    }

    QString posStr = QString("%1:%2").arg(pos / 60000).arg((pos / 1000) % 60, 2, 10, QChar('0'));
    QString durStr = QString("%1:%2").arg(dur / 60000).arg((dur / 1000) % 60, 2, 10, QChar('0'));
    lblTime->setText(posStr + " / " + durStr);
}
