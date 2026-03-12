#include <QApplication>
#include "MainWindow.h"
#include <QStyleFactory>
#include <QPalette>

void setupTechVibeTheme(QApplication& app) {
    app.setStyle(QStyleFactory::create("Fusion"));
    QPalette palette;
    palette.setColor(QPalette::Window, QColor(18, 18, 18));
    palette.setColor(QPalette::WindowText, Qt::white);
    palette.setColor(QPalette::Base, QColor(30, 30, 30));
    palette.setColor(QPalette::AlternateBase, QColor(18, 18, 18));
    palette.setColor(QPalette::ToolTipBase, Qt::white);
    palette.setColor(QPalette::ToolTipText, Qt::white);
    palette.setColor(QPalette::Text, Qt::white);
    palette.setColor(QPalette::Button, QColor(30, 30, 30));
    palette.setColor(QPalette::ButtonText, Qt::white);
    palette.setColor(QPalette::BrightText, Qt::red);
    palette.setColor(QPalette::Link, QColor(0, 166, 255)); // Neon Blue
    palette.setColor(QPalette::Highlight, QColor(0, 166, 255));
    palette.setColor(QPalette::HighlightedText, Qt::black);
    app.setPalette(palette);

    // QSS for finer control over the "Tech Vibe" UI elements
    app.setStyleSheet(
        "QPushButton { background-color: #1e1e1e; border: 1px solid #00a6ff; border-radius: 4px; padding: 5px; color: white; }"
        "QPushButton:hover { background-color: #00a6ff; color: black; }"
        "QScrollBar:vertical { border: none; background: #121212; width: 10px; margin: 0px; }"
        "QScrollBar::handle:vertical { background: #333333; min-height: 20px; border-radius: 5px; }"
        "QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { border: none; background: none; }"
    );
}

int main(int argc, char *argv[]) {
    QApplication a(argc, argv);
    a.setApplicationName("DTECH_MUSIC");
    a.setApplicationDisplayName("DTECH MUSIC // PREASX24");

    setupTechVibeTheme(a);

    MainWindow w;
    w.resize(1200, 800);
    w.show();

    return a.exec();
}
