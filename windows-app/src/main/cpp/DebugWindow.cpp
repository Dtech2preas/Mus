#include "DebugWindow.h"
#include <QScrollBar>
#include <QDateTime>

DebugWindow* DebugWindow::instance() {
    static DebugWindow* _instance = nullptr;
    if (!_instance) {
        _instance = new DebugWindow();
    }
    return _instance;
}

DebugWindow::DebugWindow(QWidget *parent) : QDialog(parent) {
    setWindowTitle("Debug Console");
    resize(800, 600);

    QVBoxLayout *layout = new QVBoxLayout(this);
    textEdit = new QTextEdit(this);
    textEdit->setReadOnly(true);
    textEdit->setStyleSheet("background-color: #0d0d0d; color: #00ff00; font-family: monospace; font-size: 12px; border: 1px solid #333;");

    layout->addWidget(textEdit);
}

void DebugWindow::appendLog(const QString& log) {
    if (!textEdit) return;

    QString timeStr = QDateTime::currentDateTime().toString("hh:mm:ss.zzz");
    QString formattedLog = QString("[%1] %2").arg(timeStr, log);

    textEdit->append(formattedLog);

    // Auto-scroll to bottom
    QScrollBar *sb = textEdit->verticalScrollBar();
    sb->setValue(sb->maximum());
}

void DebugWindow::closeEvent(QCloseEvent *event) {
    // Just hide, don't destroy
    hide();
    event->ignore();
}
