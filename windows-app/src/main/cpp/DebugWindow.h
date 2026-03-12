#ifndef DEBUGWINDOW_H
#define DEBUGWINDOW_H

#include <QDialog>
#include <QTextEdit>
#include <QVBoxLayout>

class DebugWindow : public QDialog {
    Q_OBJECT

public:
    static DebugWindow* instance();
    void appendLog(const QString& log);

protected:
    void closeEvent(QCloseEvent *event) override;

private:
    explicit DebugWindow(QWidget *parent = nullptr);
    ~DebugWindow() = default;

    QTextEdit *textEdit;
};

#endif // DEBUGWINDOW_H
