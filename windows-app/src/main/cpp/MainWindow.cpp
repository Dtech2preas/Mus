#include "MainWindow.h"
#include <QHBoxLayout>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
      player(new MusicPlayer(this)),
      db(new DatabaseManager("music.db")),
      networkClient(new NetworkClient(this))
{
    setupUI();
}

MainWindow::~MainWindow() {
    delete db;
}

void MainWindow::setupUI() {
    centralWidget = new QWidget(this);
    setCentralWidget(centralWidget);

    mainLayout = new QVBoxLayout(centralWidget);

    // Greeting Header
    greetingLabel = new QLabel("Welcome to DTECH MUSIC", this);
    QFont font = greetingLabel->font();
    font.setPointSize(24);
    font.setBold(true);
    greetingLabel->setFont(font);
    greetingLabel->setStyleSheet("color: #00a6ff;");

    mainLayout->addWidget(greetingLabel);

    // Playback Controls
    QHBoxLayout *controlsLayout = new QHBoxLayout();
    playPauseButton = new QPushButton("Play/Pause", this);
    searchButton = new QPushButton("Search (Mock)", this);

    controlsLayout->addWidget(playPauseButton);
    controlsLayout->addWidget(searchButton);

    mainLayout->addLayout(controlsLayout);

    // Connect signals
    connect(playPauseButton, &QPushButton::clicked, this, &MainWindow::onPlayPauseClicked);
    connect(searchButton, &QPushButton::clicked, this, &MainWindow::onSearchClicked);
}

void MainWindow::onPlayPauseClicked() {
    if (player->isPlaying()) {
        player->pause();
    } else {
        player->resume();
    }
}

void MainWindow::onSearchClicked() {
    // For demo purposes, we do a mock network request
    networkClient->fetchUrl("https://jsonplaceholder.typicode.com/todos/1");
}
